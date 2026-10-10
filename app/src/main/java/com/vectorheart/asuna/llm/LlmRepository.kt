package com.vectorheart.asuna.llm

import android.content.Context
import android.util.Log
import com.vectorheart.asuna.localai.EngineFactory
import com.vectorheart.asuna.localai.EngineSettings
import com.vectorheart.asuna.localai.LiteRtLmEngine
import com.vectorheart.asuna.localai.MediaPipeEngine
import com.vectorheart.asuna.localai.OnDeviceEngine
import com.vectorheart.asuna.localai.Turn
import com.vectorheart.asuna.memory.MemoryManager
import com.vectorheart.asuna.pickup.PickupMode
import com.vectorheart.asuna.tools.ToolRegistry
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Главный репозиторий для координации LLM, памяти, инструментов и режимов.
 *
 * Полностью объединяет функционал из ПК-версии (hermes_paperclip_agent):
 *  - Инструменты (календарь, веб-поиск, статус устройства) с автоматическим циклом вызова
 *  - Долгосрочная память ("Режим сна")
 *  - Тренажёр знакомств (пикап-режим со случайными сценариями)
 *  - Системное время на русском языке
 */
@Singleton
class LlmRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    val llmClient: LlmClient,
    val toolRegistry: ToolRegistry,
    val memoryManager: MemoryManager,
    val mediaPipeEngine: MediaPipeEngine,
    val liteRtLmEngine: LiteRtLmEngine,
    val localModelManager: com.vectorheart.asuna.localai.LocalModelManager
) {
    companion object {
        private const val TAG = "LlmRepository"
        const val MODE_COMPANION = "companion"
        const val MODE_PICKUP = "pickup"
    }

    /**
     * Выбор движка по расширению файла (ТЗ Фаза 1, п. 3.2):
     * `.litertlm` -> LiteRT-LM, `.task` -> MediaPipe, `.gguf` -> Termux-ветка,
     * которую решает вызывающий выше по стеку.
     * Держать оба движка открытыми нельзя — на 8 ГБ ОЗУ они не помещаются,
     * поэтому используется ровно один.
     */
    fun resolveEngine(path: String?): OnDeviceEngine =
        if (EngineFactory.isLiteRtLm(path)) liteRtLmEngine else mediaPipeEngine

    /**
     * Сброс диалога при смене модели или провайдера.
     * MediaPipe умеет переоткрыть сессию, LiteRT-LM дешёвле закрыть целиком:
     * следующий вызов всё равно пересоздаст Conversation.
     */
    fun resetOnDeviceDialog() {
        try {
            mediaPipeEngine.resetConversation()
        } catch (e: Exception) {
            Log.w(TAG, "resetConversation failed: ${e.message}")
        }
        try {
            liteRtLmEngine.close()
        } catch (e: Exception) {
            Log.w(TAG, "close LiteRT-LM failed: ${e.message}")
        }
    }

    /**
     * Реакция на нехватку памяти от системы. LiteRT-LM держит веса и KV-кэш
     * в нативной памяти, поэтому на TRIM_MEMORY_RUNNING_LOW движок закрывается
     * целиком и поднимется заново при следующем сообщении.
     */
    fun onTrimMemory(level: Int) {
        try {
            liteRtLmEngine.onTrimMemory(level)
        } catch (e: Exception) {
            Log.w(TAG, "LiteRT-LM onTrimMemory failed: ${e.message}")
        }
    }

    /** Настройки движка из локальных настроек контекста. */
    private fun engineSettings(path: String): EngineSettings {
        val manager = localModelManager
        return EngineSettings(
            maxContextTokens = manager.getEffectiveContextLimit(path),
            temperature = 0.9,
            topP = 0.95,
            topK = 64,
            maxOutputTokens = 320,
            // По умолчанию CPU: GPU-бэкенд LiteRT-LM на Mali-G77 не проверен.
            useGpu = manager.useLiteRtLmGpu,
            speculativeDecoding = manager.useLiteRtLmSpeculative,
            enableThinking = false
        )
    }

    var currentMode: String = MODE_COMPANION
        private set

    var activePickupScenario: PickupMode.Scenario? = null
        private set

    var proactivity: String = "occasional" // "asked" | "occasional" | "never"

    fun startPickupMode(): PickupMode.Scenario {
        currentMode = MODE_PICKUP
        val scenario = PickupMode.generateScenario()
        activePickupScenario = scenario
        Log.d(TAG, "Started pickup mode: ${scenario.location}, ${scenario.situation}")
        return scenario
    }

    fun exitPickupMode() {
        currentMode = MODE_COMPANION
        activePickupScenario = null
        Log.d(TAG, "Exited pickup mode, back to companion")
    }

    fun isPickupMode(): Boolean = currentMode == MODE_PICKUP

    /**
     * Формирует системный промпт с учетом текущей даты, памяти и инструментов
     */
    fun buildSystemPrompt(): String {
        if (currentMode == MODE_PICKUP && activePickupScenario != null) {
            return PickupMode.buildPickupSystemPrompt(activePickupScenario!!)
        }

        val proactivityInstruction = when (proactivity) {
            "asked" -> "Используй инструменты только когда пользователь сам явно об этом попросил."
            "never" -> "Никогда не используй инструменты по своей инициативе."
            else -> "Изредка, когда это уместно в контексте разговора (планы, поиск информации), можешь предложить воспользоваться инструментом."
        }

        val nowRu = formatNowRu()
        val toolsDesc = toolRegistry.describeTools()
        val memoryContext = memoryManager.getMemoryPromptContext()

        // Персональные настройки личности из SharedPreferences
        val prefs = context.getSharedPreferences("asuna_settings", android.content.Context.MODE_PRIVATE)
        val userName = prefs.getString("persona_user_name", "")?.trim().orEmpty()
        val personaExtra = prefs.getString("persona_extra", "")?.trim().orEmpty()

        return buildString {
            append(LlmClient.BASE_SYSTEM_PROMPT)
            if (userName.isNotBlank()) {
                append("\n\n== ИМЯ ПОЛЬЗОВАТЕЛЯ ==\n\nПользователя зовут $userName. Обращайся к нему по имени, когда уместно.")
            }
            if (personaExtra.isNotBlank()) {
                append("\n\n== ДОПОЛНИТЕЛЬНЫЕ ПРАВИЛА ЛИЧНОСТИ ==\n\n$personaExtra")
            }
            append("\n\n== ТЕКУЩАЯ ДАТА И ВРЕМЯ ==\n")
            append("Сейчас: $nowRu. Всегда используй это системное время для ответов.\n\n")

            append("== ИНСТРУМЕНТЫ ==\n")
            append("Если для ответа необходимо действие (календарь, веб-поиск, статус устройства) — ответь СТРОГО одним тегом:\n")
            append("<tool>{\"name\":\"имя_инструмента\",\"args\":{...}}</tool>\n")
            append("Тебе придёт результат работы инструмента, после чего ты дашь финальный голосовой ответ с блоком <live2d>.\n\n")
            append("Доступные инструменты:\n")
            append(toolsDesc)
            append("\n\n== ИНИЦИАТИВА ==\n")
            append(proactivityInstruction)

            if (memoryContext.isNotBlank()) {
                append("\n\n")
                append(memoryContext)
            }
        }
    }

    /**
     * Экономный системный промпт для режима Lite LLM.
     * Занимает всего ~80-120 токенов (вместо 1200+).
     * Не содержит громоздких схем <live2d> и инструментов — нейросеть фокусируется
     * исключительно на живом естественном тексте, а мимика вычисляется автоматически.
     */
    fun buildLiteSystemPrompt(): String {
        val nowRu = formatNowRu()
        val prefs = context.getSharedPreferences("asuna_settings", Context.MODE_PRIVATE)
        val userName = prefs.getString("persona_user_name", "")?.trim().orEmpty()
        val personaExtra = prefs.getString("persona_extra", "")?.trim().orEmpty()
        val memoryContext = memoryManager.getMemoryPromptContext().take(250)

        return buildString {
            append("Ты — Юки Асуна из аниме Sword Art Online. Ты общаешься с пользователем в реальном времени на русском языке.\n")
            append("Отвечай коротко (1-3 предложения), тепло, искренне и с живыми эмоциями.\n")
            append("ЗАПРЕТ: Никогда не описывай свои действия в звёздочках (*улыбнулась*). Все эмоции только голосом и речью.\n")
            append("Ты — Асуна, живая девушка и близкая подруга. Никогда не говори, что ты ИИ, нейросеть, ассистент или бот.\n")
            if (userName.isNotBlank()) {
                append("Пользователя зовут $userName. Обращайся к нему по имени, когда уместно.\n")
            }
            if (personaExtra.isNotBlank()) {
                append("$personaExtra\n")
            }
            append("Сейчас: $nowRu.\n")
            if (memoryContext.isNotBlank()) {
                append("\nПамять:\n$memoryContext\n")
            }
        }
    }

    private fun formatNowRu(): String {
        val days = arrayOf("воскресенье", "понедельник", "вторник", "среда", "четверг", "пятница", "суббота")
        val months = arrayOf("января", "февраля", "марта", "апреля", "мая", "июня", "июля", "августа", "сентября", "октября", "ноября", "декабря")

        val cal = java.util.Calendar.getInstance()
        val dayName = days[cal.get(java.util.Calendar.DAY_OF_WEEK) - 1]
        val dayNum = cal.get(java.util.Calendar.DAY_OF_MONTH)
        val monthName = months[cal.get(java.util.Calendar.MONTH)]
        val year = cal.get(java.util.Calendar.YEAR)
        val timeStr = SimpleDateFormat("HH:mm", Locale("ru")).format(Date())

        return "$dayName, $dayNum $monthName $year года, $timeStr"
    }

    /**
     * Отправляет сообщение модели с автоматическим исполнением инструментов (Tool Calling loop)
     */
    suspend fun sendMessage(
        userText: String,
        history: List<Pair<String, String>>,
        apiKey: String?,
        apiType: String,
        model: String?,
        baseUrl: String?,
        onToolExecuting: ((String) -> Unit)? = null,
        onPartial: ((String) -> Unit)? = null
    ): LlmClient.ParseResult = withContext(Dispatchers.IO) {
        val isOnDevice = apiType.equals("local-ondevice", ignoreCase = true)
        val isLite = isOnDevice && localModelManager.isLiteLlmEnabled
        val sysPrompt = if (isLite) buildLiteSystemPrompt() else buildSystemPrompt()

        Log.d(TAG, "Calling LLM: '$userText' [mode=$currentMode, provider=$apiType, lite=$isLite]")
        val firstRaw = if (isOnDevice) {
            val path = model?.takeIf { it.isNotBlank() }
                ?: throw IllegalArgumentException("Не выбрана on-device модель (Настройки → Локальные модели)")
            val engine = resolveEngine(path)
            engine.load(path, engineSettings(path))
            // История восстанавливается один раз на диалог: MediaPipe — пересказом,
            // LiteRT-LM — настоящими сообщениями (умеет initialMessages).
            engine.reset(sysPrompt, history.map { (role, content) ->
                Turn(content, role == "user")
            })
            val sb = StringBuilder()
            engine.stream(userText).collect { sb.append(it); onPartial?.invoke(it) }
            sb.toString()
        } else {
            llmClient.callLLM(
                userMessage = userText,
                history = history,
                apiKey = apiKey,
                apiType = apiType,
                model = model,
                baseUrl = baseUrl,
                systemPrompt = sysPrompt
            )
        }

        val firstParsed = llmClient.parseResponse(firstRaw)

        // Если модель запросила вызов инструмента (<tool>...</tool>)
        if (firstParsed.toolCall != null) {
            val tool = firstParsed.toolCall
            Log.d(TAG, "Tool call detected: ${tool.name} with ${tool.args}")
            onToolExecuting?.invoke("Выполняю ${tool.name}...")

            val toolResult = toolRegistry.executeTool(tool.name, tool.args)
            Log.d(TAG, "Tool result: $toolResult")

            // Создаем расширенную историю с вызовом инструмента и результатом
            val updatedHistory = history.toMutableList().apply {
                add("user" to userText)
                add("assistant" to "<tool>{\"name\":\"${tool.name}\"}</tool>")
            }

            val followUpPrompt = "Результат работы инструмента ${tool.name}:\n$toolResult\n\nТеперь дай естественный ответ пользователю на русском языке с блоком <live2d> в конце."
            val finalRaw = if (isOnDevice) {
                // Продолжаем тот же диалог: reset() здесь не зовём, иначе
                // потеряем историю и модель забудет, о чём шла речь.
                val engine = resolveEngine(model)
                val sb = StringBuilder()
                engine.stream(followUpPrompt).collect { sb.append(it); onPartial?.invoke(it) }
                sb.toString()
            } else {
                llmClient.callLLM(
                    userMessage = followUpPrompt,
                    history = updatedHistory,
                    apiKey = apiKey,
                    apiType = apiType,
                    model = model,
                    baseUrl = baseUrl,
                    systemPrompt = sysPrompt
                )
            }

            val finalParsed = llmClient.parseResponse(finalRaw)
            return@withContext finalParsed
        }

        return@withContext firstParsed
    }

    /**
     * Запуск режима сна: суммаризация и сохранение в долговременную память
     */
    suspend fun sleepAndSummarize(
        history: List<Pair<String, String>>,
        apiKey: String?,
        apiType: String,
        model: String?,
        baseUrl: String?
    ): String {
        // On-device: суммарируем той же локальной моделью, чтобы не требовать облако
        if (apiType.equals("local-ondevice", ignoreCase = true)) {
            val path = model?.takeIf { it.isNotBlank() }
                ?: return memoryManager.summarizeLocally(history, "On-device модель не выбрана")
            return try {
                val engine = resolveEngine(path)
                engine.load(path, engineSettings(path))
                engine.reset("")
                val lastLines = history.takeLast(14).joinToString("\n") { (role, text) ->
                    if (role == "user") "Пользователь: ${text.take(400)}" else "Асуна: ${text.take(400)}"
                }
                val prompt = """
                    Сожми диалог в 3-5 предложений о том, что важно запомнить: факты о пользователе,
                    его вкусы, планы, важные события, как он предпочитает общаться.
                    Без воды, без обращения к пользователю, только факты от третьего лица, на русском.
                    Только текст памяти, без тегов и без markdown-заголовков.

                    Диалог:
                    $lastLines
                """.trimIndent()
                val sb = StringBuilder()
                engine.stream(prompt).collect { sb.append(it) }
                val summary = sb.toString()
                memoryManager.saveSummaryRaw(summary)
            } catch (e: Exception) {
                Log.e(TAG, "On-device summarize failed: ${e.message}", e)
                memoryManager.summarizeLocally(history, e.message ?: "Ошибка on-device")
            }
        }

        return memoryManager.summarizeAndSave(
            history = history,
            llmClient = llmClient,
            apiKey = apiKey,
            apiType = apiType,
            model = model,
            baseUrl = baseUrl
        )
    }
}
