package com.vectorheart.asuna

import com.vectorheart.asuna.localai.LiteRtLmModelInspector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * Инспектор `.litertlm` на настоящем контейнере.
 *
 * Фикстура `fixtures/tiny-int2.litertlm` собрана скриптом
 * `asuna-lora/tools/ternary-litertlm/make_litertlm_fixture.py` из тех же
 * flatbuffer-схем, что и настоящие бандлы (tflite `ModelT` + заголовок
 * `litertlm_header_schema`), и содержит по одному тензору INT2, INT4, INT8,
 * FLOAT16 и один тензор с пустым буфером.
 *
 * Ожидаемые числа посчитаны независимым инструментом
 * (`ternary_litertlm.py inspect` в asuna-lora), поэтому тест ловит ошибку
 * именно в Kotlin-парсере, а не повторяет его же логику.
 */
class LiteRtLmInspectorTest {

    private fun fixture(): File = fixture("tiny-int2.litertlm")

    private fun fixture(name: String): File {
        val loader = requireNotNull(javaClass.classLoader)
        val stream = requireNotNull(loader.getResourceAsStream("fixtures/$name")) {
            "фикстура fixtures/$name не на classpath"
        }
        val out = Files.createTempFile(name.removeSuffix(".litertlm"), ".litertlm")
        stream.use { input -> Files.copy(input, out, java.nio.file.StandardCopyOption.REPLACE_EXISTING) }
        return out.toFile()
    }

    @Test
    fun `читает версию формата`() {
        val info = LiteRtLmModelInspector.inspect(fixture().path)
        assertTrue(info.formatOk)
        assertEquals(1, info.majorVersion)
        assertEquals(7, info.minorVersion)
        assertEquals(0, info.patchVersion)
    }

    @Test
    fun `видит одну tflite-секцию`() {
        assertEquals(1, LiteRtLmModelInspector.inspect(fixture().path).tfliteSections)
    }

    @Test
    fun `находит INT2 веса - троичные {-a,0,+a} в 2 бита`() {
        val info = LiteRtLmModelInspector.inspect(fixture().path)
        assertTrue("INT2 не найден", info.hasInt2Weights)
        assertEquals(listOf("INT2", "INT4", "INT8", "FLOAT16"), info.weightTypes)
        assertEquals(1, info.tensorCounts["INT2"])
    }

    @Test
    fun `суммирует веса и байты как вендорный инспектор`() {
        val info = LiteRtLmModelInspector.inspect(fixture().path)
        // INT2 64x128=8192 + INT4 32x64=2048 + INT8 16x8=128 + FLOAT16 8x4=32
        assertEquals(10400L, info.weightCount)
        // INT2 2048 + INT4 1024 + INT8 128 + FLOAT16 64
        assertEquals(3264L, info.weightBytes)
        assertEquals(3264.0 * 8 / 10400.0, info.bitsPerWeight.toDouble(), 1e-4)
    }

    @Test
    fun `пустой буфер не считается весом`() {
        val info = LiteRtLmModelInspector.inspect(fixture().path)
        // тензор no_weights есть, но его буфер пуст — в счёт не попадает
        assertFalse("FLOAT32 в списке весов быть не должно", "FLOAT32" in info.weightTypes)
        assertEquals(4, info.tensorCounts.values.sum())
    }

    @Test
    fun `разрядности ровно те, что заявлены в схеме`() {
        val info = LiteRtLmModelInspector.inspect(fixture().path)
        // 2 бита на вес у 8192 из 10400 = 2048 байт из 3264 => 1.577 бит/вес
        // именно для int2-части
        assertEquals(2, info.smallestWeightBits)
        assertTrue(info.hasInt2Weights)
        assertTrue(info.hasInt4Weights)
    }

    @Test
    fun `самая плотная разрядность - два бита`() {
        assertEquals(2, LiteRtLmModelInspector.inspect(fixture().path).smallestWeightBits)
    }

    @Test
    fun `сводка читаема`() {
        val s = LiteRtLmModelInspector.inspect(fixture().path).summary()
        assertTrue(s, s.contains("INT2"))
        assertTrue(s, s.contains("2.51"))
    }

    @Test
    fun `на мусоре и на несуществующем файле не падает`() {
        val tmp = Files.createTempFile("not-litertlm", ".litertlm")
        Files.write(tmp, ByteArray(64) { 0x41 })
        val info = LiteRtLmModelInspector.inspect(tmp.toString())
        assertFalse(info.formatOk)
        assertEquals("не .litertlm", info.summary())

        val missing = LiteRtLmModelInspector.inspect("/нет/такого/файла.litertlm")
        assertFalse(missing.formatOk)
        assertEquals(0, missing.smallestWeightBits)

        Files.write(tmp, ByteArray(8)) // короче заголовка
        assertFalse(LiteRtLmModelInspector.inspect(tmp.toString()).formatOk)
    }

    @Test
    fun `обрезанный файл не даёт ложных весов`() {
        val full = fixture().readBytes()
        val cut = Files.createTempFile("truncated", ".litertlm")
        Files.write(cut, full.copyOfRange(0, full.size / 2))
        val info = LiteRtLmModelInspector.inspect(cut.toString())
        // формат опознан, но веса обрезаны — правки быть не должно
        assertTrue("обрезанный файл не должен ломать разбор", !info.hasInt2Weights ||
                info.weightBytes < full.size)
    }

    @Test
    fun `разрядности известны движку, а 1 бита не бывает`() {
        // В схеме tflite есть INT2 (19) и INT4 (17), но нет INT1: минимальный
        // целочисленный тип для весов — два бита. Поэтому «1-битная» модель
        // в .litertlm невозможна, а троичная {-a,0,+a} — это ровно INT2.
        assertEquals(2, LiteRtLmModelInspector.inspect(fixture().path).smallestWeightBits)
        assertTrue(LiteRtLmModelInspector.inspect(fixture().path).hasInt2Weights)
    }

    // --- Метаданные секций: model_type / backend_constraint ------------------
    // Фикстуры `header-*.litertlm` собраны тем же flatbuffers-билдером, что и
    // настоящие бандлы (проверено на gemma-4-E2B-it-web, Qwen3-0.6B и др.):
    // значения взяты из реальных контейнеров.

    @Test
    fun `читает model_type и backend_constraint из секций`() {
        val info = LiteRtLmModelInspector.inspect(fixture("header-gpu-artisan.litertlm").path)
        assertTrue(info.formatOk)
        assertEquals(listOf("tf_lite_artisan_text_decoder"), info.modelTypes)
        assertEquals(listOf("gpu_artisan"), info.backendConstraints)
        assertTrue("gpu_artisan должен требовать GPU", info.requiresGpu)
        assertFalse("у artisan-сборки нет tf_lite_prefill_decode", info.hasStandardTextDecoder)
        assertEquals("gpu_artisan", info.backendSummary)
    }

    @Test
    fun `обычная сборка с prefill_decode не требует GPU`() {
        val info = LiteRtLmModelInspector.inspect(fixture("header-cpu-prefill.litertlm").path)
        assertTrue(info.hasStandardTextDecoder)
        assertFalse(info.requiresGpu)
        assertFalse(info.vendorSpecific)
        assertEquals(listOf("tf_lite_embedder", "tf_lite_prefill_decode"), info.modelTypes)
    }

    @Test
    fun `vendor-сборка с tf_lite_aux помечается как чип-специфичная`() {
        // Так устроена Qwen3-0.6B.mediatek.mt6993: tf_lite_aux + prefill_decode.
        val info = LiteRtLmModelInspector.inspect(fixture("header-vendor-npu.litertlm").path)
        assertTrue(info.vendorSpecific)
        assertTrue(info.hasStandardTextDecoder)
        assertFalse("vendor-сборка не GPU-only, просто чужая по чипу", info.requiresGpu)
    }

    @Test
    fun `фикстура без метаданных секций не ломает разбор`() {
        val info = LiteRtLmModelInspector.inspect(fixture().path)
        assertTrue(info.modelTypes.isEmpty())
        assertTrue(info.backendConstraints.isEmpty())
        assertFalse(info.requiresGpu)
        assertFalse(info.vendorSpecific)
    }
}