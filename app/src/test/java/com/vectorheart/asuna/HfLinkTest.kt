package com.vectorheart.asuna

import com.vectorheart.asuna.localai.HfLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Разбор ссылок на модели ([HfLink]). Логика чистая, поэтому проверяется на
 * JVM без Android: приложение должно одинаково принимать и прямую ссылку
 * (`resolve/main`), и страницу файла (`blob/main`), и падать понятной ошибкой
 * на ссылке репозитория без файла.
 */
class HfLinkTest {

    @Test
    fun `blob превращается в resolve`() {
        assertEquals(
            "https://huggingface.co/KaDZuto/Asuna-Gemma4-E2B-lora/resolve/main/model.litertlm",
            HfLink.toDirectUrl("https://huggingface.co/KaDZuto/Asuna-Gemma4-E2B-lora/blob/main/model.litertlm")
        )
    }

    @Test
    fun `прямая resolve-ссылка остаётся как есть`() {
        val url = "https://huggingface.co/litert-community/Qwen3-0.6B/resolve/main/Qwen3-0.6B.litertlm"
        assertEquals(url, HfLink.toDirectUrl(url))
    }

    @Test
    fun `ссылка на репозиторий без файла отклоняется с понятной ошибкой`() {
        val e = assertThrows(IllegalArgumentException::class.java) {
            HfLink.toDirectUrl("https://huggingface.co/Qwen/Qwen3-4B")
        }
        assertTrue(e.message.orEmpty(), e.message.orEmpty().contains("resolve"))
    }

    @Test
    fun `не http-ссылка отклоняется`() {
        assertThrows(IllegalArgumentException::class.java) { HfLink.toDirectUrl("ftp://example.com/model.gguf") }
        assertThrows(IllegalArgumentException::class.java) { HfLink.toDirectUrl("   ") }
    }

    @Test
    fun `имя файла берётся из ссылки и декодируется`() {
        assertEquals(
            "Asuna E2B.litertlm",
            HfLink.fileName("https://huggingface.co/u/r/resolve/main/Asuna%20E2B.litertlm")
        )
        // query и якорь отбрасываются
        assertEquals(
            "model.gguf",
            HfLink.fileName("https://example.com/files/model.gguf?download=1#x")
        )
    }

    @Test
    fun `чужое расширение отклоняется`() {
        val e = assertThrows(IllegalArgumentException::class.java) {
            HfLink.fileName("https://huggingface.co/u/r/resolve/main/weights.safetensors")
        }
        assertTrue(e.message.orEmpty(), e.message.orEmpty().contains(".litertlm"))
    }

    @Test
    fun `заданное вручную имя имеет приоритет`() {
        assertEquals(
            "my-model.task",
            HfLink.fileName("https://huggingface.co/u/r/resolve/main/other.litertlm", "my-model.task")
        )
    }

    @Test
    fun `из имени убирается путь`() {
        // Имя всегда безопасно для записи в каталог моделей: без ../ и слешей.
        assertEquals("evil.litertlm", HfLink.validateName("../../evil.litertlm"))
        assertEquals("model.gguf", HfLink.validateName("a/b\\c/model.gguf"))
    }
}
