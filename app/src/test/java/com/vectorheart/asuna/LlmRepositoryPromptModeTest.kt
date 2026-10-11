package com.vectorheart.asuna

import com.vectorheart.asuna.llm.LlmRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Режим обученной модели: пользователь дообучает модель сам, поэтому системный
 * промпт (личность, формат `<live2d>`, инструменты) не отправляется вообще.
 *
 * Проверяется чистая логика выбора промпта — она должна не только вернуть
 * пустую строку, но и не собирать полный/экономный промпт зря (в них читаются
 * память и инструменты).
 */
class LlmRepositoryPromptModeTest {

    @Test
    fun `режим обученной модели не отправляет промпт и не собирает его`() {
        var fullBuilt = false
        var liteBuilt = false
        val prompt = LlmRepository.selectSystemPrompt(
            trainedMode = true,
            liteMode = false,
            fullPrompt = { fullBuilt = true; "полный промпт" },
            litePrompt = { liteBuilt = true; "экономный промпт" }
        )
        assertEquals("", prompt)
        assertFalse("полный промпт не должен собираться в этом режиме", fullBuilt)
        assertFalse("экономный промпт не должен собираться в этом режиме", liteBuilt)
    }

    @Test
    fun `режим обученной модели важнее lite-режима`() {
        var liteBuilt = false
        val prompt = LlmRepository.selectSystemPrompt(
            trainedMode = true,
            liteMode = true,
            fullPrompt = { "полный промпт" },
            litePrompt = { liteBuilt = true; "экономный промпт" }
        )
        assertEquals("", prompt)
        assertFalse(liteBuilt)
    }

    @Test
    fun `lite-режим берёт экономный промпт`() {
        val prompt = LlmRepository.selectSystemPrompt(
            trainedMode = false,
            liteMode = true,
            fullPrompt = { "полный промпт" },
            litePrompt = { "экономный промпт" }
        )
        assertEquals("экономный промпт", prompt)
    }

    @Test
    fun `обычный режим берёт полный промпт`() {
        val prompt = LlmRepository.selectSystemPrompt(
            trainedMode = false,
            liteMode = false,
            fullPrompt = { "полный промпт" },
            litePrompt = { "экономный промпт" }
        )
        assertEquals("полный промпт", prompt)
    }
}
