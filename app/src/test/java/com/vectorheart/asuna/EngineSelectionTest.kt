package com.vectorheart.asuna

import com.vectorheart.asuna.localai.EngineFactory
import com.vectorheart.asuna.localai.EngineRuntime
import com.vectorheart.asuna.localai.MediaPipeEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Критерий приёмки ТЗ Фаза 1, п. 3.5: «есть юнит-тест выбора движка по расширению».
 *
 * Здесь же — регрессия на реальные имена файлов из каталога HuggingFace,
 * потому что именно они приходят в `LocalModelManager`.
 */
class EngineSelectionTest {

    @Test
    fun `litertlm идёт в LiteRT-LM`() {
        assertEquals(EngineRuntime.LITERT_LM, EngineFactory.runtimeFor("/data/gemma-4-E2B-it.litertlm"))
        assertTrue(EngineFactory.isLiteRtLm("gemma-4-E2B-it.litertlm"))
        assertTrue(EngineFactory.isSupportedOnDevice("bonsai-4b-int2pc-4k-gpu.litertlm"))
    }

    @Test
    fun `task идёт в MediaPipe`() {
        assertEquals(EngineRuntime.MEDIAPIPE_TASK, EngineFactory.runtimeFor("/sdcard/Qwen2.5-1.5B_multi-prefill-seq_q8_ekv4096.task"))
        assertFalse(EngineFactory.isLiteRtLm("something.task"))
        assertTrue(EngineFactory.isSupportedOnDevice("something.task"))
    }

    @Test
    fun `gguf уходит в Termux-ветку`() {
        assertEquals(EngineRuntime.GGUF_TERMUX, EngineFactory.runtimeFor("/sdcard/Download/Ternary-Bonsai-4B-Q2_0_g64.gguf"))
        assertFalse(EngineFactory.isSupportedOnDevice("bonsai.gguf"))
    }

    @Test
    fun `регистр расширения не важен`() {
        assertEquals(EngineRuntime.LITERT_LM, EngineFactory.runtimeFor("MODEL.LiteRTLM"))
        assertEquals(EngineRuntime.MEDIAPIPE_TASK, EngineFactory.runtimeFor("MODEL.TASK"))
        assertEquals(EngineRuntime.GGUF_TERMUX, EngineFactory.runtimeFor("MODEL.GGUF"))
    }

    @Test
    fun `путь без расширения и пустая строка не ломают выбор`() {
        // Фолбэк — MediaPipe, чтобы старые .task продолжали работать.
        assertEquals(EngineRuntime.MEDIAPIPE_TASK, EngineFactory.runtimeFor(null))
        assertEquals(EngineRuntime.MEDIAPIPE_TASK, EngineFactory.runtimeFor(""))
        assertEquals(EngineRuntime.MEDIAPIPE_TASK, EngineFactory.runtimeFor("/data/models/bonsai"))
    }

    @Test
    fun `MediaPipeEngine по-прежнему узнаёт лимит KV-кэша по имени файла`() {
        // Старые .task должны работать как раньше (критерий приёмки ТЗ п. 3.5).
        assertEquals(4096, MediaPipeEngine.detectModelMaxTokens("Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.task"))
        assertEquals(1280, MediaPipeEngine.detectModelMaxTokens("Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv1280.task"))
        assertEquals(4096, MediaPipeEngine.detectModelMaxTokens("gemma-4-E2B-it-web.task"))
        assertEquals(2048, MediaPipeEngine.detectModelMaxTokens("unknown_model.task"))
    }
}