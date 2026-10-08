package com.vitngan.harness.skill

import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

/**
 * E5-1 Skill playbook JSON (PLAN §5.8). MVP: skill = 1 file skill.json,
 * không JS engine. Cài từ GitHub qua API contents hoặc zip fallback.
 */
data class Skill(val name: String, val description: String, val commands: Map<String, String>)

interface SkillFetcher {
    suspend fun fetchFile(owner: String, repo: String, path: String): String?
    suspend fun fetchZip(owner: String, repo: String, ref: String): ByteArray?
}

class UrlSkillFetcher(
    private val connectTimeoutMs: Int = 15_000,
    private val readTimeoutMs: Int = 30_000
) : SkillFetcher {

    private fun get(url: String): ByteArray {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "vitngan-harness")
        }
        val code = conn.responseCode
        if (code !in 200..299) throw RuntimeException("github $code: $url")
        return conn.inputStream.use { it.readBytes() }
    }

    override suspend fun fetchFile(owner: String, repo: String, path: String): String? {
        val raw = try {
            get("https://api.github.com/repos/$owner/$repo/contents/$path")
        } catch (_: Exception) {
            return null
        }
        val obj = JSONObject(raw.toString(Charsets.UTF_8))
        if (obj.optString("type") != "file") return null
        val b64 = obj.optString("content", "").filterNot { it.isWhitespace() }
        if (b64.isEmpty()) return null
        return try {
            String(android.util.Base64.decode(b64, android.util.Base64.DEFAULT), Charsets.UTF_8)
        } catch (_: RuntimeException) {
            // Unit JVM: android.util.Base64 là stub
            String(java.util.Base64.getMimeDecoder().decode(b64), Charsets.UTF_8)
        }
    }

    override suspend fun fetchZip(owner: String, repo: String, ref: String): ByteArray {
        return get("https://github.com/$owner/$repo/archive/refs/heads/$ref.zip")
    }
}

class SkillManager(
    private val skillsDir: File,
    private val fetch: SkillFetcher = UrlSkillFetcher()
) {
    companion object {
        private val NAME_RE = Regex("^[a-z0-9-]{1,64}$")
        private const val MAX_ENTRY_BYTES = 2 * 1024 * 1024
    }

    data class RepoRef(val owner: String, val repo: String)

    fun parseRepoRef(repoUrl: String): RepoRef {
        val m = Regex("(?:github\\.com/)?([A-Za-z0-9_.-]+)/([A-Za-z0-9_.-]+)").find(repoUrl.trim())
            ?: throw IllegalArgumentException("repo URL không hiểu: $repoUrl")
        return RepoRef(m.groupValues[1], m.groupValues[2].removeSuffix(".git"))
    }

    fun parseSkill(name: String, json: String): Skill {
        val o = JSONObject(json)
        val n = o.optString("name", name)
        require(n.matches(NAME_RE)) { "tên skill sai: $n" }
        val desc = o.optString("description", "")
        val cmds = o.optJSONObject("commands")
            ?: throw IllegalArgumentException("thiếu commands")
        require(cmds.length() > 0) { "commands rỗng" }
        val map = mutableMapOf<String, String>()
        val keys = cmds.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            val v = cmds.optString(k, "")
            require(v.isNotBlank()) { "lệnh rỗng: $k" }
            map[k] = v
        }
        return Skill(n, desc, map)
    }

    suspend fun installFromGithub(
        repoUrl: String, path: String = "skill.json", ref: String = "main"
    ): Skill {
        val (owner, repo) = parseRepoRef(repoUrl).let { it.owner to it.repo }
        val direct = try {
            fetch.fetchFile(owner, repo, path)
        } catch (_: Exception) {
            null
        }
        val json: String
        val skillName: String
        if (direct != null) {
            skillName = path.substringAfterLast("/").removeSuffix(".json")
            json = direct
        } else {
            val zip = fetch.fetchZip(owner, repo, ref)
                ?: throw IllegalArgumentException("tải zip lỗi: $owner/$repo")
            val found = findSkillInZip(zip, path.substringAfterLast("/"))
                ?: throw IllegalArgumentException("không thấy skill.json trong zip $owner/$repo")
            skillName = found.first
            json = found.second
        }
        val skill = parseSkill(skillName, json)
        val dir = File(skillsDir, skill.name)
        dir.mkdirs()
        File(dir, "skill.json").writeText(json, Charsets.UTF_8)
        return skill
    }

    private fun findSkillInZip(zip: ByteArray, fileName: String): Pair<String, String>? {
        ZipInputStream(ByteArrayInputStream(zip)).use { zin ->
            var e = zin.nextEntry
            while (e != null) {
                if (!e.isDirectory && e.name.endsWith(fileName)) {
                    val out = ByteArrayOutputStreamCapped(MAX_ENTRY_BYTES)
                    val buf = ByteArray(8192)
                    var n = zin.read(buf)
                    while (n >= 0) {
                        out.write(buf, 0, n)
                        n = zin.read(buf)
                    }
                    val name = e.name.substringAfterLast("/").removeSuffix(".json")
                    return name to out.toString(Charsets.UTF_8)
                }
                zin.closeEntry()
                e = zin.nextEntry
            }
        }
        return null
    }

    fun listInstalled(): List<Skill> =
        (skillsDir.listFiles() ?: emptyArray())
            .filter { it.isDirectory && File(it, "skill.json").isFile }
            .mapNotNull { runCatching { parseSkill(it.name, File(it, "skill.json").readText()) }.getOrNull() }
            .sortedBy { it.name }

    fun remove(skillName: String): Boolean =
        File(skillsDir, skillName).takeIf { it.isDirectory }?.deleteRecursively() ?: false

    fun getCommands(skillName: String): Map<String, String> =
        listInstalled().firstOrNull { it.name == skillName }?.commands ?: emptyMap()

    private class ByteArrayOutputStreamCapped(private val cap: Int) : java.io.ByteArrayOutputStream() {
        @Synchronized
        override fun write(b: ByteArray, off: Int, len: Int) {
            if (count + len > cap) throw IllegalArgumentException("entry zip quá lớn (>$cap)")
            super.write(b, off, len)
        }
    }
}
