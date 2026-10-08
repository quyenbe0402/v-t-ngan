package com.vitngan.harness

import com.vitngan.harness.backup.BackupManager
import com.vitngan.harness.skill.Skill
import com.vitngan.harness.ux.NonTechMode
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

/** E6-3 + E7-3 unit, không cần máy. */
class NonTechBackupTest {
    @Test fun promptRules() {
        val p = NonTechMode.systemPrompt()
        assertTrue(p.contains("xác nhận"))
        assertTrue(p.contains("tiếng Việt") || p.contains("Tiếng Việt") || p.contains("người lớn tuổi"))
        assertTrue(NonTechMode.confirmQuestion("tăng cỡ chữ").contains("tăng cỡ chữ"))
        assertTrue(NonTechMode.doneMessage("tăng cỡ chữ").contains("Xong"))
    }

    @Test fun backupRoundTrip() {
        val dir = Files.createTempDirectory("skills").toFile()
        val skills = listOf(Skill("tiktok-control", "d", mapOf("open" to "am start -n x/.A")))
        val json = BackupManager.export(skills)
        assertTrue(json.contains("tiktok-control"))
        assertFalse(json.contains("sk-ant-"))
        val out = Files.createTempDirectory("restore").toFile()
        val names = BackupManager.importTo(json, out)
        assertEquals(listOf("tiktok-control"), names)
        assertTrue(out.resolve("tiktok-control/skill.json").isFile)
        try {
            BackupManager.importTo("{\"format\":99,\"skills\":[]}", out)
            fail("phải từ chối format lạ")
        } catch (_: IllegalArgumentException) {
        }
    }
}
