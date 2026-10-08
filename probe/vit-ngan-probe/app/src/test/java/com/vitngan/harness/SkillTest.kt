package com.vitngan.harness

import com.vitngan.harness.skill.Skill
import com.vitngan.harness.skill.SkillFetcher
import com.vitngan.harness.skill.SkillManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** E5-1 unit: fetcher giả, thư mục tạm — không chạm mạng. */
class SkillTest {
    private fun tmpDir(): File = Files.createTempDirectory("skills").toFile()

    private val sample = """
        {"name":"tiktok-control","description":"Dieu khien TikTok",
         "commands":{"open_tiktok":"am start -n com.x/.Act","scroll":"input swipe 500 1500 500 500"}}
    """.trimIndent()

    private class FakeFetch(val file: String? = null, val zip: ByteArray? = null) : SkillFetcher {
        override suspend fun fetchFile(owner: String, repo: String, path: String): String? = file
        override suspend fun fetchZip(owner: String, repo: String, ref: String): ByteArray =
            zip ?: throw RuntimeException("no zip")
    }

    private fun zipWithSkill(entry: String, content: String): ByteArray {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { z ->
            z.putNextEntry(ZipEntry("repo-main/$entry"))
            z.write(content.toByteArray(Charsets.UTF_8))
            z.closeEntry()
        }
        return bos.toByteArray()
    }

    @Test fun installViaApiAndListRemove() = runBlocking {
        val m = SkillManager(tmpDir(), FakeFetch(file = sample))
        val s = m.installFromGithub("owner/repo")
        assertEquals("tiktok-control", s.name)
        assertEquals(2, s.commands.size)
        assertEquals(1, m.listInstalled().size)
        assertEquals(2, m.getCommands("tiktok-control").size)
        assertTrue(m.remove("tiktok-control"))
        assertTrue(m.listInstalled().isEmpty())
        assertFalse(m.remove("tiktok-control"))
    }

    @Test fun installViaZipFallback() = runBlocking {
        val m = SkillManager(tmpDir(), FakeFetch(zip = zipWithSkill("skill.json", sample)))
        val s = m.installFromGithub("https://github.com/o/r.git")
        assertEquals("tiktok-control", s.name)
    }

    @Test fun rejectsBadNameAndEmpty() = runBlocking {
        val m = SkillManager(tmpDir(), FakeFetch(file = """{"name":"BAD NAME!","commands":{"a":"b"}}"""))
        try {
            m.installFromGithub("o/r")
            fail("phải từ chối tên sai")
        } catch (_: IllegalArgumentException) {
        }
        val m2 = SkillManager(tmpDir(), FakeFetch(file = """{"name":"ok","commands":{}}"""))
        try {
            m2.installFromGithub("o/r")
            fail("phải từ chối commands rỗng")
        } catch (_: IllegalArgumentException) {
        }
    }

    @Test fun parseRepoRefForms() {
        val m = SkillManager(tmpDir(), FakeFetch())
        assertEquals("o", m.parseRepoRef("o/r").owner)
        assertEquals("r", m.parseRepoRef("https://github.com/o/r.git").repo)
        assertEquals("NetLops", m.parseRepoRef("https://github.com/NetLops/Operit").owner)
    }

    @Test fun skillDataClass() {
        val s = Skill("a-b", "d", mapOf("x" to "echo hi"))
        assertEquals("a-b", s.name)
    }

    @Test fun builtinInstallByName() {
        val all = com.vitngan.harness.skill.BuiltinSkills.list()
        assertTrue(all.any { it.name == "install-by-name" })
        val install = all.first { it.name == "install-by-name" }
        assertTrue(install.recipe.contains("market://search?q="))
        assertTrue(install.recipe.contains("ui_dump"))
        assertTrue(install.recipe.contains("get_installed_apps"))
        val section = com.vitngan.harness.skill.BuiltinSkills.promptSection()
        assertTrue(section.contains("install-by-name"))
        assertTrue(section.contains("shopee-search"))
    }
}
