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
        val estimate = (text.length / 2.6).toInt()
        return used + estimate
    }

    override suspend fun load(path: String, settings: EngineSettings) = withContext(Dispatchers.Default) {
        // LlmRepository зовёт load() на КАЖДОЕ сообщение. Подъём .litertlm стоит
        // 10–60 секунд и держит гигабайты ОЗУ, поэтому повторный вызов с той же
        // моделью и теми же параметрами обязан быть no-op — иначе диалог с
        // LiteRT-LM был бы невозможен. Смена модели или настроек меняет ключ.
        val key = loadKey(path, settings)
        if (key == loadedKey && isLoaded) {
            this@LiteRtLmEngine.settings = settings
            Log.d(TAG, "LiteRT-LM уже загружена ($path), переиспользуем движок")
            return@withContext
        }

        close()
        this@LiteRtLmEngine.settings = settings

        // Спекулятивное декодирование — до initialize(): флаг читается при старте.
        ExperimentalFlags.enableSpeculativeDecoding = settings.speculativeDecoding

        // Мультимодальность намеренно не включаем: visionBackend/audioBackend
        // не задаём — экономим ОЗУ, которого на Mi 11T и так в обрез.
        val wantGpu = settings.useGpu && !gpuDisabledByFailure
        var created: Engine? = null

        if (wantGpu) {
            try {
                created = newEngine(path, settings, Backend.GPU())
                created.initialize()
            } catch (e: Exception) {
                // GPU на Mali-G77 не проверен: откатываемся на CPU и запоминаем,
                // чтобы не пытаться снова на каждом запуске.
                Log.w(TAG, "GPU backend failed, fallback to CPU: ${e.message}")
                gpuDisabledByFailure = true
                settings.onBackendFallback?.invoke(e.message ?: "GPU-бэкенд недоступен")
                try {
                    created?.close()
                } catch (_: Exception) {
                }
                created = null
            }
        }

        if (created == null) {
            try {
                created = newEngine(path, settings, Backend.CPU())
                created.initialize()
            } catch (e: Exception) {
                Log.e(TAG, "LiteRT-LM load failed: ${e.message}", e)
                throw RuntimeException(
                    "Не удалось загрузить модель .litertlm: ${firstLine(e)}", e
                )
            }
        }

        engine = created
        loadedPath = path
        loadedKey = key
        modelPrecision = LiteRtLmModelInspector.inspect(path).takeIf { it.formatOk }
        Log.d(
            TAG,
            "LiteRT-LM loaded: $path ctx=${settings.maxContextTokens} " +
                "gpu=${created.engineConfig.backend.name} weights=${modelPrecision?.summary() ?: "не прочитаны"}"
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

    override suspend fun reset(systemPrompt: String, history: List<Turn>) = withContext(Dispatchers.Default) {
        val e = engine ?: throw IllegalStateException("Модель не загружена")
        conversation?.close()
        conversation = e.createConversation(
            ConversationConfig(
                // Contents.empty() во внутреннем API (internal), поэтому пустой промпт
                // передаём как Contents.of("")
                systemInstruction = Contents.of(systemPrompt),
                // История кладём настоящими сообщениями, а не пересказом прозой:
                // это экономит токены и сохраняет роли (ТЗ п. 4.4).
                initialMessages = history.map { turn ->
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
        modelPrecision = null
    }

    /** Первая строка исключения — её и показываем пользователю (ТЗ п. 3.5). */
    private fun firstLine(e: Throwable): String =
        (e.message ?: e::class.java.simpleName).lineSequence().first().take(200)

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
    }
}