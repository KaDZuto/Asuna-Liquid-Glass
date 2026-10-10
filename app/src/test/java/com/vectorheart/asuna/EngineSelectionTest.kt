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

    @Test
    fun `litertlm не должен попадать в парсер имён MediaPipe`() {
        // В имени .litertlm нет маркера _ekvNNNN — это конструкция MediaPipe.
        // Парсер для неизвестного имени возвращает 2048 «по умолчанию», то есть
        // выдумывает лимит, которого у .litertlm нет. Поэтому LocalModelManager
        // отсекает .litertlm ДО вызова парсера (modelContextLimit).
        //
        // Здесь фиксируем, что парсер на таких именах выдаёт именно выдумку,
        // и что формат при этом остаётся разным:
        assertEquals(4096, MediaPipeEngine.detectModelMaxTokens("gemma-4-E2B-it.litertlm"))
        assertEquals(2048, MediaPipeEngine.detectModelMaxTokens("Qwen3-0.6B.mediatek.mt6993.litertlm"))
        // Регрессия на маршрутизацию: .litertlm обязан уходить в LiteRT-LM,
        // иначе обрезка по имени применится не к тому движку.
        assertTrue(EngineFactory.isLiteRtLm("gemma-4-E2B-it.litertlm"))
        assertTrue(EngineFactory.isLiteRtLm("Qwen3-0.6B.mediatek.mt6993.litertlm"))
        assertEquals(
            EngineRuntime.MEDIAPIPE_TASK,
            EngineFactory.runtimeFor("gemma-4-E2B-it-web.task")
        )
    }

    @Test
    fun `реальный файл с magic bytes LITERTLM распознаётся даже без расширения`() {
        val loader = requireNotNull(javaClass.classLoader)
        val stream = requireNotNull(loader.getResourceAsStream("fixtures/tiny-int2.litertlm"))
        val tempWithoutExt = java.nio.file.Files.createTempFile("bonsai-no-ext", "").toFile()
        tempWithoutExt.deleteOnExit()
        stream.use { input -> tempWithoutExt.outputStream().use { input.copyTo(it) } }

        assertEquals(EngineRuntime.LITERT_LM, EngineFactory.runtimeFor(tempWithoutExt.absolutePath))
        assertTrue(EngineFactory.isLiteRtLm(tempWithoutExt.absolutePath))
    }

    @Test
    fun `реальный файл ZIP таски распознаётся по заголовку PK`() {
        val tempZip = java.nio.file.Files.createTempFile("custom-task", ".bin").toFile()
        tempZip.deleteOnExit()
        tempZip.writeBytes(byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x00, 0x00, 0x00, 0x00))

        assertEquals(EngineRuntime.MEDIAPIPE_TASK, EngineFactory.runtimeFor(tempZip.absolutePath))
        assertFalse(EngineFactory.isLiteRtLm(tempZip.absolutePath))
    }

    @Test
    fun `реальный файл GGUF распознаётся по заголовку GGUF`() {
        val tempGguf = java.nio.file.Files.createTempFile("custom-gguf", ".bin").toFile()
        tempGguf.deleteOnExit()
        tempGguf.writeBytes(byteArrayOf('G'.code.toByte(), 'G'.code.toByte(), 'U'.code.toByte(), 'F'.code.toByte()))

        assertEquals(EngineRuntime.GGUF_TERMUX, EngineFactory.runtimeFor(tempGguf.absolutePath))
        assertFalse(EngineFactory.isSupportedOnDevice(tempGguf.absolutePath))
    }
}