package com.vitngan.harness

import org.junit.Assert.*
import org.junit.Test

class CommandPolicyTest {
    @Test fun dangerousNeedsConfirm() {
        assertTrue(CommandPolicy.needsConfirm("pm uninstall com.x"))
        assertTrue(CommandPolicy.needsConfirm("rm -rf /sdcard/x"))
        assertTrue(CommandPolicy.needsConfirm("settings put system font_scale 1.2"))
        assertTrue(CommandPolicy.needsConfirm("pm install -r /sdcard/a.apk"))
        assertFalse(CommandPolicy.needsConfirm("echo hello"))
        assertFalse(CommandPolicy.needsConfirm("pm list packages"))
    }

    @Test fun chainedNeedsConfirm() {
        assertTrue(CommandPolicy.needsConfirm("a && b && c && d"))
        assertFalse(CommandPolicy.needsConfirm("echo a && echo b"))
    }

    @Test fun whitelist() {
        assertTrue(CommandPolicy.isFilePathAllowed("/sdcard/vitngan/a.txt", "/data/user/0/com.vitngan.harness/files"))
        assertTrue(CommandPolicy.isFilePathAllowed("/data/user/0/com.vitngan.harness/files/s.txt", "/data/user/0/com.vitngan.harness/files"))
        assertFalse(CommandPolicy.isFilePathAllowed("/sdcard/Download/a.txt", "/data/user/0/com.vitngan.harness/files"))
        assertFalse(CommandPolicy.isFilePathAllowed("/sdcard/vitngan/../x", "/data/user/0/com.vitngan.harness/files"))
        assertFalse(CommandPolicy.isFilePathAllowed("/data/data/com.other/f", "/data/user/0/com.vitngan.harness/files"))
    }

    @Test fun shellResultTruncate() {
        val r = ShellResult(0, "x".repeat(5000), "", 5)
        assertTrue(r.isOk)
        val t = r.truncatedStdout()
        assertTrue(t.length <= 4096 + 20)
        assertTrue(t.endsWith("[truncated]"))
        assertEquals("x".repeat(100), ShellResult(0, "x".repeat(100), "", 1).truncatedStdout())
    }
}
