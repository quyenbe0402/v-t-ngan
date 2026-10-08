package com.vitngan.harness

/**
 * Policy thuần JVM (test được trên CI, không cần máy):
 * - dangerous: cần confirm trước khi exec (PLAN §5.5)
 * - file whitelist: chỉ ^/sdcard/vitngan/ + filesDir (truyền filesDir vào để test)
 */
object CommandPolicy {
    private val dangerous = listOf(
        Regex("""\bpm\s+(uninstall|disable)\b"""),
        Regex("""\brm\s+-rf?\b"""),
        Regex("""\b(dd|mkfs|reboot|fastboot)\b"""),
        Regex("""\bfactory\b"""),
        Regex("""\bsettings\s+(put|delete)\b"""),
        Regex("""\bpm\s+install\b""")
    )

    fun needsConfirm(cmd: String): Boolean {
        if (dangerous.any { it.containsMatchIn(cmd) }) return true
        val joins = Regex("""(&&|;\s*|\|\|)""").findAll(cmd).count()
        return joins > 2
    }

    fun isFilePathAllowed(path: String, filesDir: String): Boolean {
        val norm = path.replace('\\', '/')
        if (norm.contains("..")) return false
        if (norm.startsWith("/sdcard/vitngan/")) return true
        val fd = filesDir.trimEnd('/') + "/"
        if (norm.startsWith(fd)) return true
        return false
    }
}
