package com.vectorheart.asuna

import com.vectorheart.asuna.localai.EngineFactory
import com.vectorheart.asuna.localai.MediaPipeEngine
import org.junit.Assert.assertEquals
import org.junit.Test

class OnDeviceContextAndModelTest {

    @Test
    fun `detectModelMaxTokens корректно определяет размер контекста из имени файла`() {
        assertEquals(4096, MediaPipeEngine.detectModelMaxTokens("Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.task"))
        assertEquals(1280, MediaPipeEngine.detectModelMaxTokens("Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv1280.task"))
        assertEquals(1280, MediaPipeEngine.detectModelMaxTokens("Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.task"))
        assertEquals(4096, MediaPipeEngine.detectModelMaxTokens("gemma-4-E2B-it-web.task"))
        assertEquals(4096, MediaPipeEngine.detectModelMaxTokens("Phi-4-mini-instruct_multi-prefill-seq_q8_ekv4096.task"))
        assertEquals(2048, MediaPipeEngine.detectModelMaxTokens("unknown_model.task"))
    }

    @Test
    fun `лимит контекста из имени применяется только к task моделям`() {
        // Регрессия: .litertlm не должен проходить через парсер имён MediaPipe.
        // Парсер выдумывает 2048 для неизвестного имени, и Gemma 4 молча
        // теряла бы большую часть контекста.
        assertEquals(4096, EngineFactory.contextLimitFromName("Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.task"))
        assertEquals(1280, EngineFactory.contextLimitFromName("gemma-3-1b-it_ekv1280.task"))

        // .litertlm: лимита из имени нет — обрезать нечем
        assertEquals(0, EngineFactory.contextLimitFromName("gemma-4-E2B-it.litertlm"))
        assertEquals(0, EngineFactory.contextLimitFromName("Qwen3-0.6B.mediatek.mt6993.litertlm"))

        // .gguf уходит в Termux — тоже без on-device лимита
        assertEquals(0, EngineFactory.contextLimitFromName("qwen2.5-1.5b.gguf"))
        assertEquals(0, EngineFactory.contextLimitFromName(null))
        assertEquals(0, EngineFactory.contextLimitFromName(""))
    }

    @Test
    fun `очистка токена HuggingFace от Bearer и пробелов`() {
        fun sanitize(value: String): String =
            value.trim().removePrefix("Bearer ").trim().replace(Regex("[\\r\\n\\t\\s]"), "")

        assertEquals("hf_abc123XYZ", sanitize("Bearer hf_abc123XYZ\n"))
        assertEquals("hf_test999", sanitize("  hf_test999  \r\n"))
        assertEquals("hf_mytoken", sanitize("Bearer   hf_mytoken  "))
    }
}
