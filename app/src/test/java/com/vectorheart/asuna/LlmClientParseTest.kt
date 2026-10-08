package com.vectorheart.asuna

import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import com.vectorheart.asuna.llm.LlmClient

class LlmClientParseTest {

    private val client = LlmClient(OkHttpClient())

    @Test
    fun `обычный ответ с live2d — display очищен, action распарсен`() {
        val raw = """Привет, Кирто! <live2d>{"expression": "F_FUN", "motion_group": "idle", "motion_index": 1}</live2d>"""
        val result = client.parseResponse(raw)
        assertEquals("Привет, Кирто!", result.display)
        assertEquals("F_FUN", result.action.expression)
        assertEquals("idle", result.action.motion_group)
        assertEquals(1, result.action.motion_index)
        assertNull(result.toolCall)
    }

    @Test
    fun `незакрытый live2d — тег и JSON скрыты из display, action подбирается по смыслу`() {
        val raw = """Я рада тебя видеть. <live2d>{"expression": "F_FUN""""
        val result = client.parseResponse(raw)
        assertEquals("Я рада тебя видеть.", result.display)
        assertTrue(result.action.expression in listOf("F_WARM_SMILE", "F_FUN_SMILE", "F_FUN"))
        assertNull(result.toolCall)
    }

    @Test
    fun `think блок вырезается из display`() {
        val raw = "Ответ пользователю. <think>скрытые рассуждения модели</think> Ещё фраза."
        val result = client.parseResponse(raw)
        assertEquals("Ответ пользователю.  Ещё фраза.".replace("  ", " ").trim(), result.display.replace("  ", " "))
        assertTrue(!result.display.contains("think"))
        assertTrue(!result.display.contains("скрытые"))
    }

    @Test
    fun `незакрытый think — хвост обрезается`() {
        val raw = "Видимый текст. <think>недописанные мысли модели"
        val result = client.parseResponse(raw)
        assertEquals("Видимый текст.", result.display)
    }

    @Test
    fun `tool блок парсится в toolCall и скрывается из display`() {
        val raw = """Сейчас посмотрю календарь. <tool>{"name": "calendar_list", "args": {"days": 7}}</tool>"""
        val result = client.parseResponse(raw)
        assertEquals("Сейчас посмотрю календарь.", result.display)
        assertNotNull(result.toolCall)
        assertEquals("calendar_list", result.toolCall!!.name)
        assertEquals(7, result.toolCall!!.args["days"]?.toString()?.toDoubleOrNull()?.toInt())
    }

    @Test
    fun `незакрытый tool блок скрывается, toolCall null`() {
        val raw = "Проверяю... <tool>{\"name\":"
        val result = client.parseResponse(raw)
        assertEquals("Проверяю...", result.display)
        assertNull(result.toolCall)
    }

    @Test
    fun `reasoning превью — think и reasoning не попадают в display`() {
        val raw = """<think>Думаю над ответом про погоду в Токио...</think>Сегодня в Токио солнечно. <live2d>{"expression":"F_FUN_SMILE"}</live2d>"""
        val result = client.parseResponse(raw)
        assertEquals("Сегодня в Токио солнечно.", result.display)
        assertTrue(!result.display.contains("Думаю"))
        assertEquals("F_FUN_SMILE", result.action.expression)
    }

    @Test
    fun `битый JSON внутри live2d — не падает, action по умолчанию`() {
        val raw = "Текст <live2d>{не json вообще}</live2d> конец"
        val result = client.parseResponse(raw)
        assertEquals("Текст  конец".replace("  ", " ").trim(), result.display.replace("  ", " "))
        assertEquals("F_NOMAL", result.action.expression)
    }

    @Test
    fun `tool с битым JSON — toolCall null, без краша`() {
        val raw = "Текст <tool>{not valid}</tool>"
        val result = client.parseResponse(raw)
        assertNull(result.toolCall)
        assertEquals("Текст", result.display)
    }

    /**
     * Формат, на котором обучена адаптированная модель: одно поле expression,
     * без motion/head/body. Такое обязано разбираться без потерь.
     */
    @Test
    fun `обучающий формат — только expression, дефолты остальных полей`() {
        val raw = """Ну наконец-то. Ты же весь день сидел над этим проектом, правда? <live2d>{"expression":"F_TEASING"}</live2d>"""
        val result = client.parseResponse(raw)
        assertEquals(
            "Ну наконец-то. Ты же весь день сидел над этим проектом, правда?",
            result.display
        )
        assertEquals("F_TEASING", result.action.expression)
        // остальные поля — дефолты Action, приложение их не меняет
        assertEquals("idle", result.action.motion_group)
        assertEquals(0, result.action.motion_index)
        assertEquals(0f, result.action.head_x)
    }

    /**
     * Регрессия: список выражений в системном промпте и в риге должен совпадать.
     * Раньше промпт предлагал F_FUN_WARM, которого в приложении нет, и не
     * упоминал 10 выражений, которые риг умеет — модель просила бы лицо,
     * которого не существует, и осталась бы с молчаливым лицом.
     */
    @Test
    fun `в системном промпте нет выражений, которых не умеет приложение`() {
        val prompt = LlmClient.BASE_SYSTEM_PROMPT
        val rigKnows = listOf(
            "F_NOMAL", "F_FUN", "F_FUN_HANIKAMI", "F_FUN_MAX", "F_FUN_SMILE",
            "F_SAD", "F_SAD_CUTE", "F_SURPRISE", "F_SHOCKED", "F_ANGRY", "F_SLEEP",
            "F_SHY", "F_ADORE", "F_CURIOUS", "F_TEASING", "F_PLAYFUL",
            "F_POUTY", "F_GIGGLE", "F_WARM_SMILE"
        )
        val mentioned = Regex("F_[A-Z_]+").findAll(prompt).map { it.value }.toSet()
        assertEquals(
            "в промпте есть выражения, которых нет в риге",
            emptySet<String>(),
            mentioned - rigKnows.toSet()
        )
        for (e in rigKnows) {
            assertTrue("выражение $e не упомянуто в промпте", prompt.contains(e))
        }
    }

    /**
     * Промпт просит одно поле expression. Если туда вернётся требование
     * заполнять motion/head/body, обученная модель начнёт это выдумывать.
     */
    @Test
    fun `промпт требует только поле expression`() {
        val prompt = LlmClient.BASE_SYSTEM_PROMPT
        assertTrue(prompt.contains("""{"expression":"F_WARM_SMILE"}"""))
        for (field in listOf("motion_group\":", "head_x\":", "body_angle\":")) {
            assertTrue(
                "промпт не должен требовать поле $field",
                !prompt.contains("\"$field")
            )
        }
    }

    /** Звёздочки запрещены полностью: промпт не должен показывать пример с ними. */
    @Test
    fun `в системном промпте нет примеров описаний в звёздочках`() {
        assertTrue(
            "в промпте есть пример описания действия в звёздочках",
            !LlmClient.BASE_SYSTEM_PROMPT.contains("*улыб")
        )
    }
}
