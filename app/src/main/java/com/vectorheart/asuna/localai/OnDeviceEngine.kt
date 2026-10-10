package com.vectorheart.asuna.localai

import kotlinx.coroutines.flow.Flow

/**
 * Общий интерфейс локального on-device движка (ТЗ Фаза 1, п. 3.2).
 *
 * Зачем абстракция: у проекта два разных рантайма, и они несовместимы.
 *  - [MediaPipeEngine]   — MediaPipe tasks-genai, открывает `.task`
 *  - [LiteRtLmEngine]   — LiteRT-LM от Google, открывает `.litertlm`
 *  - Termux/llama.cpp    — внешний HTTP-сервер, ветка в LlmRepository
 *
 * Выбор делает [EngineFactory] по расширению файла. Старый
 * `OnDeviceLlmEngine` переименован в [MediaPipeEngine], его публичное
 * поведение не изменилось.
 */

/** Один ход диалога. [isUser] = true для реплики пользователя. */
data class Turn(val text: String, val isUser: Boolean)

/** Настройки движка, общие для всех реализаций. */
data class EngineSettings(
    /** Размер контекстного окна в токенах. */
    val maxContextTokens: Int = 4096,
    val temperature: Double = 0.9,
    val topP: Double = 0.95,
    val topK: Int = 64,
    /** Сколько токенов максимум отдаёт модель за один ответ. */
    val maxOutputTokens: Int = 320,
    /**
     * GPU-бэкенд. На Mi 11T (Mali-G77) не проверен, поэтому по умолчанию
     * выключен: при ошибке initialize() движок сам откатывается на CPU
     * и сообщает об этом через [onBackendFallback].
     */
    val useGpu: Boolean = false,
    /**
     * Спекулятивное декодирование. Google пишет, что выигрыш в основном на
     * GPU, а модель должна быть скачана после 5 мая 2026. Выключено по
     * умолчанию; включать только после собственного замера (ТЗ п. 3.4).
     */
    val speculativeDecoding: Boolean = false,
    /** Thinking-режим. Для голосового компаньона выключен — меньше задержка. */
    val enableThinking: Boolean = false,
    /** Вызывается, если GPU-бэкенд не удался и движок ушёл на CPU. */
    val onBackendFallback: ((reason: String) -> Unit)? = null
)

interface OnDeviceEngine {

    /** Загружена ли модель. */
    val isLoaded: Boolean

    /** Путь к текущей модели. */
    val loadedModelPath: String?

    /** Сколько токенов займёт текст в контексте этой модели (0 — неизвестно). */
    fun countTokens(text: String): Int

    /** Размер контекстного окна, с которым реально работает движок. */
    val contextLimit: Int

    /**
     * Поднимает модель. Может занять 10+ секунд — вызывать не из UI-потока.
     * Реализация обязана сама откатиться на CPU, если GPU-бэкенд не заработал.
     */
    suspend fun load(path: String, settings: EngineSettings)

    /**
     * Начинает новый диалог: следующий запрос пойдёт без старой истории.
     * [history] восстанавливается как настоящие сообщения, а не пересказом.
     */
    suspend fun reset(systemPrompt: String, history: List<Turn> = emptyList())

    /** Один ход диалога потоком дельт текста. */
    fun stream(userText: String): Flow<String>

    /** Прерывает текущую генерацию (пользователь заговорил или нажал PTT). */
    fun cancel()

    /** Освобождает ресурсы. Вызывается при смене модели и в onTrimMemory. */
    fun close()
}

/** Какой рантайм открывает файл. Определяется по расширению, ТЗ п. 3.2. */
enum class EngineRuntime {
    LITERT_LM,       // .litertlm — LiteRT-LM
    MEDIAPIPE_TASK,  // .task    — MediaPipe tasks-genai
    GGUF_TERMUX      // .gguf    — внешний llama.cpp в Termux
}

/**
 * Обрезает историю диалога под остаток контекстного окна.
 *
 * Нужна LiteRT-LM: она получает настоящие сообщения (см. `ConversationConfig
 * .initialMessages`), то есть весь лог чата, а не пересказ. Без обрезки длинная
 * беседа либо вылезает за `maxNumTokens`, либо модель упирается в prefill — на
 * Mi 11T это секунды ожидания перед каждым ответом.
 *
 * Режем с конца по целым парам «реплика пользователя + ответ», чтобы диалог не
 * начинался с ответа Асуны. Оценка длины — [CHARS_PER_TOKEN] символов на токен,
 * та же, что в `LiteRtLmEngine.countTokens`.
 */
const val CHARS_PER_TOKEN = 2.6f

/** Оценка длины текста в токенах для русского текста. */
fun estimateTokens(text: String): Int = (text.length / CHARS_PER_TOKEN).toInt() + 1

/**
 * Возвращает хвост истории, который влезает в [budgetTokens], сохраняя парность
 * «пользователь → модель». Пустой бюджет означает пустую историю.
 */
fun fitHistoryToBudget(history: List<Turn>, budgetTokens: Int): List<Turn> {
    if (history.isEmpty()) return history
    if (budgetTokens <= 0) return emptyList()

    var used = 0
    val kept = ArrayDeque<Turn>()
    for (turn in history.asReversed()) {
        val cost = estimateTokens(turn.text)
        if (used + cost > budgetTokens) break
        used += cost
        kept.addFirst(turn)
    }
    // Обрезка может остановиться посередине пары. Убираем только самую старую
    // реплику, если это ответ модели: диалог должен начинаться с вопроса.
    // Внутренние пары при этом не трогаем.
    if (kept.isNotEmpty() && !kept.first().isUser) kept.removeFirst()
    return kept.toList()
}

object EngineFactory {

    /**
     * Скомпилированный лимит контекста модели, если он зашит в имя файла.
     *
     * Разбирать имя умеет только MediaPipe: `_ekv1280.task` — это метка в
     * имени, которую MediaPipe tasks-genai пишет при конвертации.
     *
     * Для `.litertlm` такого маркера нет: окно задаётся числом
     * (`EngineConfig.maxNumTokens`), и движок не падает на слишком большом
     * значении. Возвращать сюда выдумку `detectModelMaxTokens` нельзя — она
     * отдаёт 2048 для любого неизвестного имени, и контекст Gemma 4 (32k)
     * молча урезался бы в 16 раз.
     *
     * `0` означает «лимита нет, ограничивать нечем».
     */
    fun contextLimitFromName(pathOrFilename: String?): Int {
        if (pathOrFilename.isNullOrBlank()) return 0
        if (runtimeFor(pathOrFilename) != EngineRuntime.MEDIAPIPE_TASK) return 0
        return MediaPipeEngine.detectModelMaxTokens(pathOrFilename)
    }

    /**
     * Выбор движка по расширению файла.
     *
     * `.litertlm` -> LiteRT-LM, `.task` -> MediaPipe, `.gguf` -> Termux.
     * Неизвестное расширение — MediaPipe как historically совместимый вариант,
     * потому что старые `.task` должны работать как раньше.
     */
    fun runtimeFor(path: String?): EngineRuntime = when (path?.substringAfterLast('.', "")?.lowercase()) {
        "litertlm" -> EngineRuntime.LITERT_LM
        "gguf" -> EngineRuntime.GGUF_TERMUX
        "task" -> EngineRuntime.MEDIAPIPE_TASK
        else -> EngineRuntime.MEDIAPIPE_TASK
    }

    /** Открывает ли этот движок файл вообще (false -> Termux-ветка). */
    fun isSupportedOnDevice(path: String?): Boolean = runtimeFor(path) != EngineRuntime.GGUF_TERMUX

    /** Открывает ли файл LiteRT-LM — нужно для выбора бэкенда и подсказок в UI. */
    fun isLiteRtLm(path: String?): Boolean = runtimeFor(path) == EngineRuntime.LITERT_LM
}