package io.legado.app.help.update

object RqtReleasePolicy {
    const val REPOSITORY_URL = "https://github.com/duongden/legado-R-qt"
    const val RELEASES_URL = "$REPOSITORY_URL/releases"
    const val API_URL = "https://api.github.com/repos/duongden/legado-R-qt/releases?per_page=20"

    fun isReleaseApk(name: String): Boolean =
        name.startsWith("legado-R-qt", ignoreCase = true) &&
            name.endsWith(".apk", ignoreCase = true) &&
            !name.contains("debug", ignoreCase = true)

    fun compareVersions(left: String, right: String): Int {
        val a = Regex("\\d+").findAll(left).map { it.value.toLongOrNull() ?: 0L }.toList()
        val b = Regex("\\d+").findAll(right).map { it.value.toLongOrNull() ?: 0L }.toList()
        for (i in 0 until maxOf(a.size, b.size)) {
            val result = a.getOrElse(i) { 0L }.compareTo(b.getOrElse(i) { 0L })
            if (result != 0) return result
        }
        return 0
    }
}
