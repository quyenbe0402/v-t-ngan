package com.vitngan.harness

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Fake connection cho unit CI — không cần adbd thật. */
class FakeDadbConnection(
    private val onShell: (String) -> ShellResult = { ShellResult(0, "dadb:$it", "", 1) }
) : DadbConnection {
    var closed = false
    override fun shell(cmd: String): ShellResult = onShell(cmd)
    override fun close() {
        closed = true
    }
}

class DadbTest {
    private val dir = File(System.getProperty("java.io.tmpdir"))

    private fun exec(
        open: (DadbEndpoint, File) -> DadbConnection = { _, _ -> FakeDadbConnection() }
    ) = DadbShellExecutor(DadbEndpoint(), dir, open)

    @Test fun mapsExitCodeAndOutput() = runBlocking {
        val e = exec { _, _ -> FakeDadbConnection { ShellResult(2, "o", "e", 3) } }
        val r = e.exec("anything", 5_000)
        assertEquals(2, r.code)
        assertEquals("o", r.stdout)
        assertEquals("e", r.stderr)
    }

    @Test fun unavailableOnOpenThrow() = runBlocking {
        val e = exec { _, _ -> throw RuntimeException("conn refused") }
        assertFalse(e.isAvailable())
    }

    @Test fun firstAvailableSkipsDown() = runBlocking {
        val down = exec { _, _ -> throw RuntimeException("down") }
        var secondUsed = false
        val up = exec { _, _ ->
            secondUsed = true
            FakeDadbConnection { ShellResult(0, "hello", "", 1) }
        }
        val first = FirstAvailableExecutor(listOf(down, up))
        assertTrue(first.isAvailable())
        val r = first.exec("echo hello", 5_000)
        assertTrue(secondUsed)
        assertTrue(r.stdout.contains("hello"))
    }

    @Test fun allDownThrowsUnavailable() = runBlocking {
        val first = FirstAvailableExecutor(
            listOf(exec { _, _ -> throw RuntimeException("a") })
        )
        assertFalse(first.isAvailable())
        try {
            first.exec("echo hi", 5_000)
            fail("phải ném ShellUnavailable")
        } catch (e: ShellUnavailable) {
            assertTrue(e.message!!.contains("không có executor"))
        }
    }
}
