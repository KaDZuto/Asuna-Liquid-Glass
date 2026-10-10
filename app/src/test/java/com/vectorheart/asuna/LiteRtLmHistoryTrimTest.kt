package com.vectorheart.asuna

import com.vectorheart.asuna.localai.Turn
import com.vectorheart.asuna.localai.estimateTokens
import com.vectorheart.asuna.localai.fitHistoryToBudget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Обрезка истории под контекстное окно LiteRT-LM.
 *
 * Зачем тест: LiteRT-LM получает настоящие сообщения (весь лог чата), а не
 * пересказ, как MediaPipe. Если обрезка вычислит бюджет неверно, то на реальном
 * телефоне это либо падение по `maxNumTokens`, либо минуты ожидания на prefill
 * — обе ошибки не видны на JVM, поэтому проверяем чистую функцию.
 */
class LiteRtLmHistoryTrimTest {

    /** История из `pairs` пар «вопрос → ответ». */
    private fun history(pairs: Int, len: Int = 300): List<Turn> = buildList {
        repeat(pairs) { i ->
            add(Turn("вопрос $i ".repeat(len / 8), true))
            add(Turn("ответ $i ".repeat(len / 7), false))
        }
    }

    @Test
    fun `короткая история не трогается`() {
        val h = history(3)
        assertEquals(h, fitHistoryToBudget(h, 100_000))
    }

    @Test
    fun `пустая история остаётся пустой`() {
        assertTrue(fitHistoryToBudget(emptyList(), 1000).isEmpty())
    }

    @Test
    fun `нулевой бюджет означает пустую историю, а не падение`() {
        assertTrue(fitHistoryToBudget(history(4), 0).isEmpty())
        assertTrue(fitHistoryToBudget(history(4), -100).isEmpty())
    }

    @Test
    fun `длинная история обрезается и остаётся в бюджете`() {
        val h = history(20)
        val budget = 500
        val kept = fitHistoryToBudget(h, budget)

        assertTrue("история должна была уменьшиться", kept.size < h.size)
        assertTrue("должен остаться хотя бы один вопрос", kept.isNotEmpty())

        val used = kept.sumOf { estimateTokens(it.text) }
        assertTrue("бюджет превышен: $used > $budget", used <= budget)
    }

    @Test
    fun `обрезка идёт с конца - свежие реплики остаются`() {
        val h = history(10)
        val kept = fitHistoryToBudget(h, 400)

        assertEquals("последняя реплика обязана сохраниться", h.last(), kept.last())
        assertTrue("обрезка удаляет начало, а не конец", kept.last().text.contains("9"))
    }

    @Test
    fun `первой остаётся реплика пользователя - не ответ Асуны`() {
        val kept = fitHistoryToBudget(history(12), 600)

        assertTrue(kept.isNotEmpty())
        assertTrue(
            "диалог не должен начинаться с ответа модели, а не с вопроса",
            kept.first().isUser
        )
    }

    @Test
    fun `чётность пар пользователь-модель сохраняется`() {
        val kept = fitHistoryToBudget(history(11), 700)

        assertEquals(0, kept.size % 2)
        // chunked, а не zipWithNext: тот даёт перекрывающиеся пары
        // (вопрос, ответ), (ответ, вопрос) и вторые всегда «неправильные».
        kept.chunked(2).forEach { pair ->
            assertEquals("пара должна состоять из реплики и ответа", 2, pair.size)
            assertTrue(
                "ожидалась пара вопрос→ответ, а получилось ${pair.map { it.isUser }}",
                pair[0].isUser && !pair[1].isUser
            )
        }
    }

    @Test
    fun `одна реплика не влезает - история пустая, а не частичная`() {
        // Реплика ~1200 символов ≈ 460 токенов при 2.6 симв./токен
        val big = listOf(Turn("а".repeat(1200), true))
        assertTrue(fitHistoryToBudget(big, 100).isEmpty())
        assertEquals(big, fitHistoryToBudget(big, 1000))
    }

    @Test
    fun `оценка токенов правдоподобна для русского`() {
        // ~2.6 символа на токен: 260 символов ≈ 100 токенов
        assertEquals(101, estimateTokens("а".repeat(260)))
        assertEquals(1, estimateTokens(""))
        assertTrue(estimateTokens("а".repeat(1000)) > 300)
        assertFalse(estimateTokens("а".repeat(1000)) > 500)
    }
}