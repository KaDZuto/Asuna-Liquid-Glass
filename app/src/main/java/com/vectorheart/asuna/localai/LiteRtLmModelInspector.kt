package com.vectorheart.asuna.localai

/**
 * Разбор контейнера `.litertlm`: какие типы весов внутри.
 *
 * Зачем это в приложении
 * ----------------------
 * `.litertlm` — это контейнер с tflite-подграфами, а веса лежат в буферах
 * flatbuffer с конкретным `TensorType`. Формат **штатно** понимает
 * `INT2` (=19) наряду с `INT4` (=17) и `INT8` (=9): официальные бандлы
 * `litert-community/Ternary-Bonsai-*` разложены именно в `INT2` с кодами
 * `{-1, 0, +1}`, то есть троичные веса `{-a, 0, +a}` (см. `asuna-lora/tools/
 * ternary-litertlm/verify_int2_levels.py`).
 *
 * Раньше приложение не различало эти модели и показывало пользователю только
 * размер файла. Теперь [LiteRtLmModelInspector] читает заголовок и сам
 * tflite-подграф и говорит честно, сколько бит на вес получилось: от этого
 * зависят и качество, и то, сколько ОЗУ модель съест.
 *
 * Формат разобран по исходникам, а не по догадкам:
 *  - `litert_lm_builder/litertlm_core.py`: magic `LITERTLM`, версия 3xuint32
 *    по смещению 8, длина заголовка uint64 по смещению 24, сам flatbuffer с 32.
 *  - `litertlm_header_schema_py_generated.py`: у `LiteRTLMMetaData` поле
 *    `sectionMetadata` — слот 1; у `SectionObject` `items` — слот 0,
 *    `beginOffset` — слот 1 (uint64), `endOffset` — слот 2 (uint64),
 *    `dataType` — слот 3. `AnySectionDataType.TFLiteModel = 3`.
 *  - схема tflite: `Model.subgraphs` — слот 2, `Model.buffers` — слот 4;
 *    `SubGraph.tensors` — слот 0; `Tensor.shape` — слот 0, `Tensor.type` —
 *    слот 1 (uint8), `Tensor.buffer` — слот 2 (uint32); `Buffer.data` — слот 0.
 *
 * Никаких нативных вызовов: парсер работает с `ByteArray` и `RandomAccessFile`,
 * поэтому проверяется обычными JVM-тестами без устройства.
 */
object LiteRtLmModelInspector {

    private const val MAGIC = "LITERTLM"
    private const val HEADER_END_LOCATION = 24
    private const val HEADER_BEGIN = 32
    private const val SECTION_TFLITE_MODEL = 3

    /** Типы, которые реально занимают место на диске: код -> (имя, бит на вес). */
    private val WEIGHT_TYPES: Map<Int, Pair<String, Int>> = mapOf(
        0 to ("FLOAT32" to 32),
        1 to ("FLOAT16" to 16),
        2 to ("INT32" to 32),
        3 to ("UINT8" to 8),
        4 to ("INT64" to 64),
        7 to ("INT16" to 16),
        9 to ("INT8" to 8),
        16 to ("UINT16" to 16),
        17 to ("INT4" to 4),
        18 to ("BFLOAT16" to 16),
        19 to ("INT2" to 2),   // <- троичные {-a, 0, +a}
        20 to ("UINT4" to 4),
        21 to ("FLOAT8_E4M3FN" to 8),
        22 to ("FLOAT8_E5M2" to 8)
    )

    /**
     * Что удалось выяснить о весах модели.
     *
     * @param formatOk `false`, если это вообще не `.litertlm`; тогда остальные
     *   поля не имеют смысла и вызывающий код должен вести себя как раньше.
     * @param weightBytes суммарный размер буферов констант.
     * @param weightCount число весов в этих буферах.
     * @param weightTypes типы весов, от большего числа весов к меньшему.
     */
    data class Info(
        val formatOk: Boolean,
        val majorVersion: Int = 0,
        val minorVersion: Int = 0,
        val patchVersion: Int = 0,
        val tfliteSections: Int = 0,
        val weightBytes: Long = 0L,
        val weightCount: Long = 0L,
        val bitsPerWeight: Float = 0f,
        val tensorCounts: Map<String, Int> = emptyMap(),
        val weightTypes: List<String> = emptyList(),
        val modelTypes: List<String> = emptyList(),
        val backendConstraints: List<String> = emptyList()
    ) {
        /**
         * Модель требует GPU-бэкенд: в её секциях есть `backend_constraint`
         * с «gpu» (например, `gpu_artisan` у веб-сборок Gemma 4). На CPU такие
         * модели не поднимаются вовсе — движок обязан стартовать на GPU.
         */
        val requiresGpu: Boolean
            get() = backendConstraints.any { it.contains("gpu", ignoreCase = true) }

        /** Есть ли обычный текстовый декодер LiteRT-LM (`tf_lite_prefill_decode`). */
        val hasStandardTextDecoder: Boolean
            get() = modelTypes.any { it == "tf_lite_prefill_decode" }

        /**
         * Модель собрана под конкретный чип/NPU: в секциях есть `tf_lite_aux`
         * (так сделана сборка `Qwen3-0.6B.mediatek.mt6993`). Такие файлы
         * запускаются только на «своём» SoC и на другом устройстве падают.
         */
        val vendorSpecific: Boolean
            get() = modelTypes.any { it == "tf_lite_aux" }

        /** Ограничения бэкенда как строка для сообщения об ошибке, напр. «gpu_artisan». */
        val backendSummary: String get() = backendConstraints.joinToString(", ")

        /** Есть ли в модели 2-битные (в т.ч. троичные) веса. */
        val hasInt2Weights: Boolean get() = "INT2" in weightTypes

        /** Есть ли в модели 4-битные веса. */
        val hasInt4Weights: Boolean get() = "INT4" in weightTypes

        /**
         * Самая плотная разрядность среди весов; `0`, если весов не нашлось.
         * Пользователю это и нужно: «2 бита» или «4 бита».
         */
        val smallestWeightBits: Int
            get() = weightTypes.mapNotNull { name ->
                WEIGHT_TYPES.values.firstOrNull { it.first == name }?.second
            }.minOrNull() ?: 0

        /** Короткая строка для интерфейса, например «INT2, INT8 (3.10 бит/вес)». */
        fun summary(): String {
            if (!formatOk) return "не .litertlm"
            if (weightTypes.isEmpty()) return "веса не найдены"
            val types = weightTypes.joinToString(", ")
            // Locale.ROOT обязателен: в русской локали String.format печатает
            // запятую, и сводка перестаёт быть разбираемой.
            return String.format(java.util.Locale.ROOT, "%s (%.2f бит/вес)", types, bitsPerWeight)
        }
    }

    /** Читает инфу о модели. Никогда не бросает — на мусоре вернёт `formatOk = false`. */
    fun inspect(path: String): Info = try {
        java.io.RandomAccessFile(path, "r").use { raf -> read(raf, raf.length()) }
    } catch (_: Exception) {
        Info(formatOk = false)
    }

    private fun read(raf: java.io.RandomAccessFile, fileLength: Long): Info {
        if (fileLength < HEADER_BEGIN) return Info(formatOk = false)
        val head = ByteArray(HEADER_BEGIN)
        raf.readFully(head)
        if (String(head, 0, 8, Charsets.US_ASCII) != MAGIC) return Info(formatOk = false)

        val major = u32(head, 8)
        val minor = u32(head, 12)
        val patch = u32(head, 16)
        val headerEnd = u64(head, HEADER_END_LOCATION)
        if (headerEnd <= HEADER_BEGIN || headerEnd > fileLength) {
            return Info(false, major, minor, patch)
        }

        val header = ByteArray((headerEnd - HEADER_BEGIN).toInt())
        raf.seek(HEADER_BEGIN.toLong())
        raf.readFully(header)
        val ranges = tfliteSectionRanges(header)
        val sectionMeta = readSectionMeta(header)

        var totalBytes = 0L
        var totalWeights = 0L
        val counts = LinkedHashMap<String, Int>()
        val weightsByType = LinkedHashMap<Int, Long>()

        for ((begin, end) in ranges) {
            if (begin < 0 || end > fileLength || end <= begin) continue
            val bytes = end - begin
            if (bytes > Int.MAX_VALUE.toLong()) continue
            // Секция — это почти весь файл (сотни МБ). Читать её в память нельзя:
            // на телефоне это мгновенный OOM. Поэтому идём по flatbuffer через
            // [WindowedFileSource], который держит одно окно в 64 КБ.
            collectWeights(WindowedFileSource(raf, begin, bytes.toInt())) { type, numel, nbytes ->
                val name = WEIGHT_TYPES[type]?.first ?: "TYPE_$type"
                counts[name] = (counts[name] ?: 0) + 1
                totalBytes += nbytes
                totalWeights += numel
                weightsByType.merge(type, numel, Long::plus)
            }
        }

        val ordered = weightsByType.entries
            .sortedByDescending { it.value }
            .map { WEIGHT_TYPES[it.key]?.first ?: "TYPE_${it.key}" }

        return Info(
            formatOk = true,
            majorVersion = major,
            minorVersion = minor,
            patchVersion = patch,
            tfliteSections = ranges.size,
            weightBytes = totalBytes,
            weightCount = totalWeights,
            bitsPerWeight = if (totalWeights > 0) totalBytes * 8f / totalWeights else 0f,
            tensorCounts = counts,
            weightTypes = ordered,
            modelTypes = sectionMeta.modelTypes,
            backendConstraints = sectionMeta.backendConstraints
        )
    }

    /** Метаданные секций контейнера: тип модели и ограничение по бэкенду. */
    private data class SectionMeta(
        val modelTypes: List<String>,
        val backendConstraints: List<String>
    )

    /**
     * Читает `items` каждой секции заголовка и достаёт пары «ключ → значение».
     *
     * Раскладка сверена с настоящими бандлами (gemma-4, Qwen3, Bonsai), а не
     * выведена из документации: у `SectionObject` поле `items` — слот 0 (вектор
     * таблиц), у элемента `key` — слот 0 (строка), `value` — слот 2 (таблица),
     * а внутри неё значение — слот 0 (строка). Отсюда берутся `model_type` и
     * `backend_constraint` (`gpu_artisan` = только GPU).
     */
    private fun readSectionMeta(header: ByteArray): SectionMeta {
        val fb = FlatBuffer(ArraySource(header))
        val root = fb.root() ?: return SectionMeta(emptyList(), emptyList())
        val sectionMeta = fb.table(root, 1) ?: return SectionMeta(emptyList(), emptyList())
        val sections = fb.vectorStart(sectionMeta, 0) ?: return SectionMeta(emptyList(), emptyList())
        val modelTypes = ArrayList<String>()
        val constraints = ArrayList<String>()
        repeat(fb.vectorLen(sectionMeta, 0)) { i ->
            val sec = fb.tableAt(sections, i) ?: return@repeat
            val items = fb.vectorStart(sec, 0) ?: return@repeat
            repeat(fb.vectorLen(sec, 0)) { j ->
                val item = fb.tableAt(items, j) ?: return@repeat
                val key = fb.string(item, 0) ?: return@repeat
                val valueTable = fb.table(item, 2) ?: return@repeat
                val value = fb.string(valueTable, 0) ?: return@repeat
                when (key) {
                    "model_type" -> modelTypes.add(value)
                    "backend_constraint" -> constraints.add(value)
                }
            }
        }
        return SectionMeta(modelTypes.distinct(), constraints.distinct())
    }

    /** Диапазоны tflite-секций в байтах файла: (begin, end). */
    private fun tfliteSectionRanges(header: ByteArray): List<Pair<Long, Long>> {
        val fb = FlatBuffer(ArraySource(header))
        val meta = fb.root() ?: return emptyList()
        // LiteRTLMMetaData.sectionMetadata — это ТАБЛИЦА (слот 1),
        // а вектор секций лежит в ней: SectionMetadata.objects — слот 0.
        val sectionMeta = fb.table(meta, 1) ?: return emptyList()
        val sections = fb.vectorStart(sectionMeta, 0) ?: return emptyList()
        val out = ArrayList<Pair<Long, Long>>()
        repeat(fb.vectorLen(sectionMeta, 0)) { i ->
            val sec = fb.tableAt(sections, i) ?: return@repeat
            if (fb.ubyte(sec, 3) != SECTION_TFLITE_MODEL) return@repeat
            out.add(fb.ulong(sec, 1) to fb.ulong(sec, 2))
        }
        return out
    }

    /**
     * Обходит tflite flatbuffer и раскладывает константные тензоры по типам.
     *
     * Внутри модели одни и те же веса встречаются в нескольких подграфах
     * (embedder, prefill, decode), поэтому [onTensor] зовётся на каждый
     * экземпляр — как и вендорный `inspect`, чтобы цифры бит/вес были
     * сопоставимы с отчётом по asuna-lora.
     */
    private inline fun collectWeights(
        src: ByteSource,
        onTensor: (type: Int, numel: Long, nbytes: Long) -> Unit
    ) {
        val fb = FlatBuffer(src)
        val root = fb.root() ?: return
        val buffers = fb.vectorStart(root, 4) ?: return
        val nBuffers = fb.vectorLen(root, 4)
        val subgraphs = fb.vectorStart(root, 2) ?: return
        val nSubgraphs = fb.vectorLen(root, 2)
        for (s in 0 until nSubgraphs) {
            val sg = fb.tableAt(subgraphs, s) ?: continue
            val tensors = fb.vectorStart(sg, 0) ?: continue
            val nTensors = fb.vectorLen(sg, 0)
            for (t in 0 until nTensors) {
                val tensor = fb.tableAt(tensors, t) ?: continue
                val type = fb.ubyte(tensor, 1)
                if (WEIGHT_TYPES[type] == null) continue
                val bufferIdx = fb.uint(tensor, 2)
                if (bufferIdx < 0 || bufferIdx >= nBuffers) continue
                val buf = fb.tableAt(buffers, bufferIdx) ?: continue
                if (fb.vectorLen(buf, 0) <= 0) continue
                val nbytes = fb.vectorLen(buf, 0).toLong()

                val shape = fb.vectorStart(tensor, 0)
                var numel = 1L
                if (shape != null) {
                    val dims = fb.vectorLen(tensor, 0)
                    for (d in 0 until dims) {
                        val v = fb.int(shape, d).toLong()
                        if (v <= 0L) { numel = 0L; break }
                        numel *= v
                    }
                }
                if (numel <= 0L) continue
                onTensor(type, numel, nbytes)
            }
        }
    }

    private fun u32(b: ByteArray, off: Int): Int =
        (b[off].toInt() and 0xFF) or
            ((b[off + 1].toInt() and 0xFF) shl 8) or
            ((b[off + 2].toInt() and 0xFF) shl 16) or
            ((b[off + 3].toInt() and 0xFF) shl 24)

    private fun u64(b: ByteArray, off: Int): Long {
        var v = 0L
        for (i in 7 downTo 0) v = (v shl 8) or (b[off + i].toLong() and 0xFF)
        return v
    }

    /**
 * Источник байт для [FlatBuffer]. Нужен, чтобы не тянуть в память секцию
 * целиком: tflite-подграф внутри `.litertlm` — это сотни мегабайт весов.
 */
    interface ByteSource {
        /** Байт по абсолютному смещению; бросает, если смещение вне диапазона. */
        fun byteAt(at: Int): Int

        val length: Int
    }

    /** [ByteSource] поверх `RandomAccessFile` с одним окном в 64 КБ. */
    class WindowedFileSource(
        private val raf: java.io.RandomAccessFile,
        private val base: Long,
        override val length: Int
    ) : ByteSource {
        private val window = ByteArray(minOf(length, WINDOW))
        private var windowStart = -1

        override fun byteAt(at: Int): Int {
            if (at < 0 || at >= length) {
                throw IndexOutOfBoundsException("смещение $at вне [0, $length)")
            }
            if (windowStart < 0 || at < windowStart || at >= windowStart + window.size) {
                val want = minOf(window.size, length - at)
                raf.seek(base + at)
                raf.readFully(window, 0, want)
                windowStart = at
            }
            return window[at - windowStart].toInt() and 0xFF
        }

        private companion object {
            const val WINDOW = 64 * 1024
        }
    }

    /** [ByteSource] поверх готового массива — для заголовка и для тестов. */
    class ArraySource(private val buf: ByteArray) : ByteSource {
        override val length: Int get() = buf.size
        override fun byteAt(at: Int): Int {
            if (at < 0 || at >= buf.size) {
                throw IndexOutOfBoundsException("смещение $at вне [0, ${buf.size})")
            }
            return buf[at].toInt() and 0xFF
        }
    }

    /**
     * Минимальный читатель flatbuffers: таблицы, векторы, скаляры.
     *
     * Раскладка: в начале буфера uoffset на корневую таблицу; в таблице
     * int32 `soffset` назад к vtable; в vtable u16-размер, u16-размер объекта,
     * дальше по 2 байта на слот поля (`0` — поле отсутствует).
     *
     * [vectorStart] возвращает позицию **первого элемента** вектора, а
     * [tableAt]/[int] уже индексируют от неё.
     */
    class FlatBuffer(private val src: ByteSource) {

        private fun u8(at: Int): Int = src.byteAt(at)

        private fun short(at: Int): Int =
            (u8(at) or (u8(at + 1) shl 8)).toShort().toInt()

        private fun int(at: Int): Int = (short(at) and 0xFFFF) or (short(at + 2) shl 16)

        private fun long(at: Int): Long =
            (int(at).toLong() and 0xFFFFFFFFL) or
                ((int(at + 4).toLong() and 0xFFFFFFFFL) shl 32)

        /** Позиция корневой таблицы. */
        fun root(): Int? {
            val n = int(0)
            return if (n == 0) null else n
        }

        private fun fieldOffset(tablePos: Int, slot: Int): Int {
            val vt = tablePos - int(tablePos)
            val off = (vt + 4 + slot * 2).let {
                (u8(it) or (u8(it + 1) shl 8))
            }
            return if (off == 0) 0 else tablePos + off
        }

        private fun vectorFieldPos(tablePos: Int, slot: Int): Int {
            val p = fieldOffset(tablePos, slot)
            return if (p == 0) 0 else p + int(p)
        }

        /** Позиция первого элемента вектора в поле `slot`. */
        fun vectorStart(tablePos: Int, slot: Int): Int? {
            val p = vectorFieldPos(tablePos, slot)
            return if (p == 0) null else p + 4
        }

        /** Длина вектора в поле `slot`. */
        fun vectorLen(tablePos: Int, slot: Int): Int {
            val p = vectorFieldPos(tablePos, slot)
            return if (p == 0) 0 else int(p)
        }

        /** Таблица по uoffset внутри вектора, начало которого — [start]. */
        fun tableAt(start: Int, index: Int): Int? {
            val p = start + index * 4
            val n = int(p)
            return if (n == 0) null else p + n
        }

        /**
         * Таблица, на которую указывает поле `slot` (не вектор!).
         * Возвращает позицию самой таблицы.
         */
        fun table(tablePos: Int, slot: Int): Int? {
            val p = fieldOffset(tablePos, slot)
            return if (p == 0) null else p + int(p)
        }

        /** Элемент вектора int32 (`Tensor.shape` — это вектор int, не int64). */
        fun int(vectorStart: Int, index: Int): Int = int(vectorStart + index * 4)

        fun ubyte(tablePos: Int, slot: Int): Int {
            val p = fieldOffset(tablePos, slot)
            return if (p == 0) 0 else u8(p)
        }

        fun uint(tablePos: Int, slot: Int): Int {
            val p = fieldOffset(tablePos, slot)
            return if (p == 0) 0 else int(p)
        }

        fun ulong(tablePos: Int, slot: Int): Long {
            val p = fieldOffset(tablePos, slot)
            return if (p == 0) 0L else long(p)
        }

        /**
         * Строка в поле `slot`. FlatBuffers хранит строку как вектор байт с
         * u32-длиной впереди, поэтому сначала идём по uoffset поля, затем
         * читаем длину и символы. `null`, если поля нет или длина абсурдна.
         */
        fun string(tablePos: Int, slot: Int): String? {
            val p = fieldOffset(tablePos, slot)
            if (p == 0) return null
            val start = p + int(p)
            val len = int(start)
            if (len <= 0 || len > 4096) return null
            val sb = StringBuilder(len)
            for (i in 0 until len) sb.append(u8(start + 4 + i).toChar())
            return sb.toString()
        }
    }
}