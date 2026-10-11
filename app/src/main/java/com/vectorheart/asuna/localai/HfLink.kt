package com.vectorheart.asuna.localai

/**
 * Разбор ссылок на модели: превращает ссылку Hugging Face в **прямую**
 * (`…/resolve/main/файл`) и достаёт имя файла.
 *
 * Зачем отдельный объект: приложение качает модель напрямую по `downloadUrl`,
 * а пользователь копирует из браузера что попало — страницу репозитория
 * (`…/tree/main`), страницу файла (`…/blob/main/x.litertlm`) или уже готовую
 * прямую ссылку. Ошибку в этом месте легко сделать незаметной, поэтому логика
 * вынесена в чистую функцию и покрыта JVM-тестами без устройства.
 */
object HfLink {

    /** Расширения, которые открывает приложение. */
    private val ALLOWED_EXTENSIONS = listOf(".litertlm", ".task", ".gguf")

    /**
     * Приводит ссылку к прямой.
     *
     * - `https://huggingface.co/<repo>/blob/main/<файл>` → `…/resolve/main/<файл>`;
     * - `…/resolve/main/<файл>` возвращается как есть;
     * - на прочих ссылках (страница репозитория без файла) бросает понятную ошибку.
     */
    fun toDirectUrl(raw: String): String {
        val url = raw.trim()
        require(url.isNotEmpty()) { "Ссылка пустая" }
        require(url.startsWith("http://") || url.startsWith("https://")) {
            "Ссылка должна начинаться с http:// или https://"
        }
        val resolved = url.replace("/blob/", "/resolve/")
        if (!resolved.contains("/resolve/")) {
            throw IllegalArgumentException(
                "Это не прямая ссылка на файл. Откройте нужный файл на Hugging Face " +
                    "и скопируйте ссылку на скачивание (…/resolve/main/имя) — " +
                    "или укажите полный URL файла."
            )
        }
        return resolved
    }

    /** Имя файла из ссылки, либо [override], если он задан. */
    fun fileName(url: String, override: String? = null): String {
        val given = override?.trim().orEmpty()
        if (given.isNotEmpty()) return validateName(given)
        val base = url.substringBefore('?').substringBefore('#').substringAfterLast('/')
        val decoded = try {
            java.net.URLDecoder.decode(base, "UTF-8")
        } catch (_: Exception) {
            base
        }
        require(decoded.isNotBlank()) { "Не удалось определить имя файла из ссылки — задайте его вручную" }
        return validateName(decoded)
    }

    /**
     * Проверяет имя файла и заодно убирает любой путь: результат всегда
     * безопасен для записи в каталог моделей (без `/`, `\`, `..`).
     */
    fun validateName(name: String): String {
        val clean = name.substringAfterLast('/').substringAfterLast('\\').trim()
        require(clean.isNotBlank() && clean != "..") { "Недопустимое имя файла" }
        require(ALLOWED_EXTENSIONS.any { clean.endsWith(it, ignoreCase = true) }) {
            "Поддерживаются только .litertlm, .task и .gguf (получено: $clean)"
        }
        return clean
    }
}
