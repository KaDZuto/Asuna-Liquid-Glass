package com.vectorheart.asuna.localai

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.ExperimentalFlags
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.ThinkingConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Движок LiteRT-LM — официальный рантайм Google для моделей `.litertlm`
 * (ТЗ Фаза 1). Именно он открывает Gemma 4 и Bonsai; MediaPipe tasks-genai
 * умеет только `.task`.
 *
 * Сигнатуры сверены с распакованным `litertlm-android-0.18.0.aar`
 * (`javap`), а не взяты из примеров документации:
 *  - `Engine(EngineConfig)` + `initialize()` + `close()`
 *  - `engine.createConversation(ConversationConfig)`
 *  - `Conversation.sendMessageAsync(String): Flow<Message>` — поток дельт
 *  - прерывание — `Conversation.cancelProcess()` (не `cancel()`)
 *  - `EngineConfig.maxNumTokens` — размер контекстного окна
 *  - `SamplerConfig(topK, topP, temperature, seed)`, где topP и temperature
 *    имеют тип **Double**, а не Float
 *  - `Conversation` помечен `@ExperimentalApi`, поэтому нужен @OptIn
 *
 * Важно: движок держит нативную память, поэтому [close] обязателен при смене
 * модели и в `onTrimMemory`.
 */
@OptIn(ExperimentalApi::class)
@Singleton
class LiteRtLmEngine @Inject constructor(
    @ApplicationContext private val context: Context
) : OnDeviceEngine {

    private var engine: Engine? = null
    private var conversation: Conversation? = null
    private var loadedPath: String? = null
    private var settings: EngineSettings = EngineSettings()

    /**
     * Ключ, с которым движок реально поднят: путь + значимые параметры
     * [EngineSettings]. Нужен, чтобы не перечитывать модель на каждое
     * сообщение — см. [load].
     */
    private var loadedKey: String? = null
    private var gpuDisabledByFailure = false

    /**
     * Разрядность весов загруженной модели, если её удалось прочитать.
     *
     * `.litertlm` — контейнер tflite-подграфов, и веса там бывают INT8, INT4
     * и INT2. INT2 — это и есть троичные веса `{-a, 0, +a}`: они ложатся в
     * него без потерь (проверено на официальном `Ternary-Bonsai-1.7B`,
     * где коды ровно `{-1, 0, +1}`). Разные разрядности отличаются и
     * качеством, и объёмом ОЗУ, поэтому движок сообщает, что именно открыл.
     *
     * `null`, если модель ещё не загружена или файл не читается.
     */
    var modelPrecision: LiteRtLmModelInspector.Info? = null
        private set

    override val isLoaded: Boolean
        get() = conversation?.isAlive == true

    override val loadedModelPath: String?
        get() = loadedPath

    override val contextLimit: Int
        get() = settings.maxContextTokens

    override fun countTokens(text: String): Int {
        // У LiteRT-LM нет публичного счётчика для произвольной строки.
        // Используем оценку по текущему состоянию диалога: дешевле и стабильнее,
        // чем грубый подсчёт символов, который систематически занижает русский.
        val used = try {
            conversation?.getTokenCount() ?: 0
        } catch (_: Exception) {
            0
        }
        if (used == 0) return 0
        // грубая оценка добавляемых токенов: ~2.6 символа на токен для русского
        return used + estimateTokens(text)
    }

    override suspend fun load(path: String, settings: EngineSettings) = withContext(Dispatchers.Default) {
        // LlmRepository зовёт load() на КАЖДОЕ сообщение. Подъём .litertlm стоит
        // 10–60 секунд и держит гигабайты ОЗУ, поэтому повторный вызов с той же
        // моделью и теми же параметрами обязан быть no-op — иначе диалог с
        // LiteRT-LM был бы невозможен. Смена модели или настроек меняет ключ.
        val key = loadKey(path, settings)
        if (key == loadedKey && engine != null) {
            // Conversation после прошлого хода может быть уже не alive — это
            // нормально: reset() пересоздаст её из истории. Важен только живой
            // Engine с загруженными весами.
            this@LiteRtLmEngine.settings = settings
            Log.d(TAG, "LiteRT-LM уже загружена ($path), переиспользуем движок")
            return@withContext
        }

        close()
        this@LiteRtLmEngine.settings = settings

        // Проверяем валидность контейнера до вызова нативного рантайма
        val file = java.io.File(path)
        if (!file.exists() || !file.isFile || file.length() < 32) {
            throw RuntimeException("Файл модели '$path' не найден или пуст.")
        }
        val inspectorInfo = LiteRtLmModelInspector.inspect(path)
        if (!inspectorInfo.formatOk) {
            throw RuntimeException("Файл '$path' не является валидным контейнером LiteRT-LM (.litertlm) или повреждён.")
        }

        // Часть .litertlm собрана только под GPU: у таких секций
        // backend_constraint=gpu_artisan (веб-сборка Gemma 4), либо вообще нет
        // обычного текстового декодера tf_lite_prefill_decode. На CPU они не
        // поднимаются, поэтому для них сразу и принудительно идём на GPU и
        // игнорируем прошлые неудачи GPU — иначе модель просто не запустится.
        val gpuRequired = inspectorInfo.requiresGpu || !inspectorInfo.hasStandardTextDecoder
        if (gpuRequired) {
            gpuDisabledByFailure = false
            Log.i(
                TAG,
                "Модель требует GPU (${inspectorInfo.backendSummary.ifBlank { "нет tf_lite_prefill_decode" }}) — старт сразу на GPU"
            )
        }

        // Спекулятивное декодирование — до initialize(): флаг читается при старте.
        ExperimentalFlags.enableSpeculativeDecoding = settings.speculativeDecoding

        val wantGpu = (settings.useGpu && !gpuDisabledByFailure) || gpuRequired
        var created: Engine? = null
        var lastError: Exception? = null

        // Вспомогательная функция инициализации с безопасной очисткой при ошибке
        fun tryInit(s: EngineSettings, backend: Backend): Engine? {
            return try {
                val eng = newEngine(path, s, backend)
                eng.initialize()
                eng
            } catch (e: Exception) {
                lastError = e
                Log.w(TAG, "Init failed on ${backend.name} (ctx=${s.maxContextTokens}): ${e.message}")
                null
            }
        }

        // 1. Попытка с целевым бэкендом
        if (wantGpu) {
            created = tryInit(settings, Backend.GPU())
            if (created == null && !gpuRequired) {
                Log.w(TAG, "GPU backend failed, fallback to CPU")
                gpuDisabledByFailure = true
                settings.onBackendFallback?.invoke("GPU-бэкенд недоступен, переключаемся на CPU")
            }
        }

        // 2. CPU — только если модель не требует GPU: иначе попытка бесполезна
        //    и лишь съедает время, такой файл на CPU гарантированно не поднимется.
        if (created == null && !gpuRequired) {
            created = tryInit(settings, Backend.CPU())
        }

        // 3. Если CPU не справился (например, модель собрана под GPU/NPU), пробуем GPU
        if (created == null && !wantGpu && !gpuDisabledByFailure) {
            Log.i(TAG, "CPU init failed, trying GPU backend fallback...")
            created = tryInit(settings, Backend.GPU())
        }

        // 4. Если инициализация упала из-за лимита контекста KV-кэша модели (например, ekv1280/2048),
        // пробуем уменьшить контекст до 2048
        if (created == null && settings.maxContextTokens > 2048) {
            val fallbackSettings = settings.copy(maxContextTokens = 2048)
            Log.i(TAG, "Retrying initialization with reduced context 2048...")
            created = if (gpuRequired) {
                tryInit(fallbackSettings, Backend.GPU())
            } else {
                tryInit(fallbackSettings, if (wantGpu) Backend.GPU() else Backend.CPU())
                    ?: tryInit(fallbackSettings, Backend.CPU())
            }
            if (created != null) {
                this@LiteRtLmEngine.settings = fallbackSettings
            }
        }

        if (created == null) {
            val err = lastError ?: RuntimeException("Unknown engine initialization failure")
            Log.e(TAG, "LiteRT-LM load failed completely: ${err.message}", err)
            throw RuntimeException(
                "Не удалось загрузить модель .litertlm: ${loadFailureHint(inspectorInfo)}${firstLine(err)}", err
            )
        }

        engine = created
        loadedPath = path
        loadedKey = key
        modelPrecision = inspectorInfo
        Log.d(
            TAG,
            "LiteRT-LM loaded: $path ctx=${this@LiteRtLmEngine.settings.maxContextTokens} " +
                "backend=${created.engineConfig.backend.name} weights=${modelPrecision?.summary() ?: "не прочитаны"}"
        )
        Unit
    }

    private fun newEngine(path: String, s: EngineSettings, backend: Backend): Engine =
        Engine(
            EngineConfig(
                modelPath = path,
                backend = backend,
                maxNumTokens = s.maxContextTokens,
                cacheDir = context.cacheDir.path
            )
        )

    /**
     * Ключ загрузки. `onBackendFallback` — колбэк, а не параметр движка, поэтому
     * в ключ не входит: смена только обработчика ошибки перезагрузки не требует.
     */
    private fun loadKey(path: String, s: EngineSettings): String = listOf(
        path,
        s.maxContextTokens,
        s.temperature,
        s.topP,
        s.topK,
        s.maxOutputTokens,
        s.useGpu && !gpuDisabledByFailure,
        s.speculativeDecoding,
        s.enableThinking
    ).joinToString("|")

    /**
     * Обрезает историю так, чтобы она влезала в контекстное окно.
     *
     * Зачем: MediaPipe получает пересказ из 6 реплик, а LiteRT-LM — настоящие
     * сообщения, то есть весь лог чата. Без обрезки длинная беседа либо вылезает
     * за `maxNumTokens`, либо модель упирается в prefill — на Mi 11T это секунды
     * ожидания перед каждым ответом.
     *
     * Режем с конца по целым парам «реплика пользователя + ответ», чтобы диалог
     * не начинался с ответа Асуны. Резерв: системный промпт, ответ модели и
     * служебные токены шаблона чата.
     */
    private fun fitHistory(history: List<Turn>, systemPrompt: String): List<Turn> {
        val budget = settings.maxContextTokens -
            estimateTokens(systemPrompt) -
            settings.maxOutputTokens -
            CONTEXT_OVERHEAD_TOKENS
        if (budget <= 0) {
            Log.w(
                TAG,
                "Контекст ${settings.maxContextTokens} токенов не вмещает даже системный промпт — история не отправляется"
            )
            return emptyList()
        }
        return fitHistoryToBudget(history, budget)
    }

    /** Оценка длины текста в токенах — общая с [estimateTokens] из OnDeviceEngine.kt. */
    private fun estimateTokens(text: String): Int =
        com.vectorheart.asuna.localai.estimateTokens(text)

    override suspend fun reset(systemPrompt: String, history: List<Turn>) = withContext(Dispatchers.Default) {
        val e = engine ?: throw IllegalStateException("Модель не загружена")
        val kept = fitHistory(history, systemPrompt)
        if (kept.size < history.size) {
            Log.i(
                TAG,
                "История не влезает в ${settings.maxContextTokens} токенов: ${history.size} → ${kept.size} реплик"
            )
        }
        conversation?.close()
        conversation = e.createConversation(
            ConversationConfig(
                // Contents.empty() во внутреннем API (internal), поэтому пустой промпт
                // передаём как Contents.of("")
                systemInstruction = Contents.of(systemPrompt),
                // История кладём настоящими сообщениями, а не пересказом прозой:
                // это экономит токены и сохраняет роли (ТЗ п. 4.4).
                initialMessages = kept.map { turn ->
                    if (turn.isUser) Message.user(turn.text) else Message.model(turn.text)
                },
                samplerConfig = SamplerConfig(
                    topK = settings.topK,
                    topP = settings.topP,
                    temperature = settings.temperature,
                    seed = 0
                ),
                thinkingConfig = ThinkingConfig(enableThinking = settings.enableThinking),
                maxOutputToken = settings.maxOutputTokens,
                // Скорость на GPU указана как основной выигрыш, но на этом
                // телефоне не проверена — по умолчанию выключено.
                enableSpeculativeDecoding = settings.speculativeDecoding
            )
        )
    }

    override fun stream(userText: String): Flow<String> {
        val conv = conversation ?: return kotlinx.coroutines.flow.flow {
            throw IllegalStateException("Модель не загружена")
        }
        return conv.sendMessageAsync(userText)
            .map { message -> message.textContent() }
            .catch { e ->
                Log.e(TAG, "LiteRT-LM generation failed: ${e.message}", e)
                throw RuntimeException("Ошибка генерации: ${firstLine(e)}", e)
            }
            .flowOn(Dispatchers.Default)
    }

    private fun Message.textContent(): String =
        contents.contents.filterIsInstance<Content.Text>().joinToString("") { it.text }

    override fun cancel() {
        try {
            conversation?.cancelProcess()
        } catch (e: Exception) {
            Log.w(TAG, "cancelProcess failed: ${e.message}")
        }
    }

    override fun close() {
        try {
            conversation?.close()
        } catch (_: Exception) {
        }
        try {
            engine?.close()
        } catch (_: Exception) {
        }
        conversation = null
        engine = null
        loadedPath = null
        loadedKey = null
        modelPrecision = null
    }

    /** Первая строка исключения — её и показываем пользователю (ТЗ п. 3.5). */
    private fun firstLine(e: Throwable): String =
        (e.message ?: e::class.java.simpleName).lineSequence().first().take(200)

    /**
     * Человеческое объяснение, почему конкретный `.litertlm` не поднялся, —
     * чтобы пользователь не видел «tf_lite_prefill_decode not found» и не
     * думал, что сломалось приложение.
     */
    private fun loadFailureHint(info: LiteRtLmModelInspector.Info): String = when {
        info.requiresGpu ->
            "это сборка только под GPU (backend_constraint=${info.backendSummary}), " +
                "а GPU-бэкенд LiteRT-LM на этом устройстве не поднялся — попробуйте обычную сборку модели. "
        info.vendorSpecific ->
            "модель собрана под конкретный чип/NPU (есть vendor-секции: ${info.modelTypes.joinToString(", ")}), " +
                "на этом устройстве она не запустится — возьмите универсальную сборку. "
        !info.hasStandardTextDecoder && info.modelTypes.isNotEmpty() ->
            "в контейнере нет стандартного текстового декодера LiteRT-LM " +
                "(найдено: ${info.modelTypes.joinToString(", ")}). "
        else -> ""
    }

    /** Сброс памяти по запросу системы: движок держит нативные буферы. */
    fun onTrimMemory(level: Int) {
        if (level >= TRIM_MEMORY_RUNNING_LOW) {
            Log.i(TAG, "onTrimMemory(level=$level) — закрываем движок")
            close()
        }
    }

    companion object {
        private const val TAG = "LiteRtLmEngine"
        private const val TRIM_MEMORY_RUNNING_LOW = 10

        /** Служебные токены чат-шаблона и запас на роли — резерв из контекста. */
        private const val CONTEXT_OVERHEAD_TOKENS = 64
    }
}