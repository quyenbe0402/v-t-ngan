package com.vitngan.harness.backup

import com.vitngan.harness.skill.Skill
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * E7-3 Backup/restore JSON (FR-21): skills đã cài (+ version). Không chứa key/secret.
 * Pure JVM (org.json) — test được trên CI.
 */
object BackupManager {
    const val FORMAT = 1

    fun export(skills: List<Skill>): String {
        val arr = JSONArray()
        for (s in skills) {
            val o = JSONObject()
            o.put("name", s.name)
            o.put("description", s.description)
            val cmds = JSONObject()
            for ((k, v) in s.commands) cmds.put(k, v)
            o.put("commands", cmds)
            arr.put(o)
        }
        return JSONObject().put("format", FORMAT).put("skills", arr).toString()
    }

    fun importTo(json: String, skillsDir: File): List<String> {
        val root = JSONObject(json)
        require(root.optInt("format", -1) == FORMAT) { "định dạng backup không hỗ trợ" }
        val arr = root.optJSONArray("skills") ?: JSONArray()
        val names = mutableListOf<String>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val name = o.optString("name", "")
            require(name.matches(Regex("^[a-z0-9-]{1,64}$"))) { "tên skill sai: $name" }
            val dir = File(skillsDir, name)
            dir.mkdirs()
            val out = JSONObject()
            out.put("name", name)
            out.put("description", o.optString("description", ""))
            out.put("commands", o.optJSONObject("commands") ?: JSONObject())
            File(dir, "skill.json").writeText(out.toString(), Charsets.UTF_8)
            names.add(name)
        }
        return names
    }
}
