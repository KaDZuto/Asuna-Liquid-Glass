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

object EngineFactory {

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