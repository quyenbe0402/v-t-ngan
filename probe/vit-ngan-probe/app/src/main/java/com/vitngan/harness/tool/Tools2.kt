package com.vitngan.harness.tool

import com.vitngan.harness.CommandPolicy
import com.vitngan.harness.ShellExecutor

/**
 * E3-2 fix khớp PLAN §5.4 (ten + anh xa shell):
 * install_app(apk_path|url, 120s) | set_system_setting(namespace,key,value + get kiem lai)
 * ui_action(action tap/swipe/input_text + params) | file_op(read/write, path, content, append)
 * screenshot(path -> screencap -p /sdcard/vitngan/shot_<ts>.png)
 */

/** Nguồn APK cho phép: whitelist file chung + staging chuẩn /data/local/tmp
 * (pm install không mở được file trên /sdcard ở Android mới). */
internal fun isInstallSourceAllowed(path: String, filesDir: String): Boolean =
    CommandPolicy.isFilePathAllowed(path, filesDir) || path.startsWith("/data/local/tmp/")

/** install_app — curl -L + pm install -r, quote '...', timeout 120s, confirm. */
class InstallAppTool(private val exec: ShellExecutor) : AgentTool {    override val definition = ToolDefinition(
        name = "install_app",
        description = "Cai APK (curl -L + pm install -r, 120s, co xac nhan)",
        inputSchema = mapOf("apk_path" to "string", "url" to "string"),
        requiresConfirm = true
    )
    override suspend fun execute(args: Map<String, String>, ctx: ToolContext): ToolResult {
        val apk = args["apk_path"] ?: args["url"] ?: return ToolResult.fail("thieu apk_path|url")
        val local: String
        if (apk.startsWith("http")) {
            // Staging ở /data/local/tmp (pm install không đọc được /sdcard trên Android mới)
            val dest = "/data/local/tmp/vitngan-dl_" + ctx.timestampMs + ".apk"
            val dl = sh(exec, "curl -L '$apk' -o '$dest'", 120_000)
            if (dl.code != 0) return ToolResult.fail("tai APK loi: " + dl.stderr.take(500))
            local = dest
        } else {
            if (!isInstallSourceAllowed(apk, ctx.filesDir))
                return ToolResult.fail("path ngoai whitelist: $apk")
            local = apk
        }
        return try {
            okOut(sh(exec, "pm install -r '$local'", 120_000))
        } catch (e: SecurityException) {
            ToolResult.needConfirm(e.message ?: local)
        } catch (e: Exception) {
            ToolResult.fail(e.message ?: "install_app failed")
        }
    }
}

/** set_system_setting — settings put + settings get kiem lai, confirm. */
class SetSystemSettingTool(private val exec: ShellExecutor) : AgentTool {
    override val definition = ToolDefinition(
        name = "set_system_setting",
        description = "settings put + get kiem lai (co xac nhan)",
        inputSchema = mapOf("namespace" to "string", "key" to "string", "value" to "string"),
        required = listOf("namespace", "key", "value"),
        requiresConfirm = true
    )
    override suspend fun execute(args: Map<String, String>, ctx: ToolContext): ToolResult {
        val ns = args["namespace"] ?: return ToolResult.fail("thieu namespace")
        val k = args["key"] ?: return ToolResult.fail("thieu key")
        val v = args["value"] ?: return ToolResult.fail("thieu value")
        return try {
            val put = sh(exec, "settings put $ns $k $v")
            if (put.code != 0) return ToolResult.fail("put loi: " + put.stderr.take(500))
            okOut(sh(exec, "settings get $ns $k"))
        } catch (e: SecurityException) {
            ToolResult.needConfirm(e.message ?: "$ns $k")
        } catch (e: Exception) {
            ToolResult.fail(e.message ?: "set_system_setting failed")
        }
    }
}


/** ui_action — action tap/swipe/input_text + params (§5.4). */
class UiActionTool(private val exec: ShellExecutor) : AgentTool {
    override val definition = ToolDefinition(
        name = "ui_action",
        description = "input tap|swipe|text",
        inputSchema = mapOf(
            "action" to "string", "x" to "string", "y" to "string",
            "x2" to "string", "y2" to "string", "duration_ms" to "string",
            "text" to "string"
        ),
        required = listOf("action")
    )
    override suspend fun execute(args: Map<String, String>, ctx: ToolContext): ToolResult {
        val cmd = try {
            buildCommand(args)
        } catch (e: Exception) {
            return ToolResult.fail("action khong ho tro: " + args["action"])
        }
        return try {
            okOut(sh(exec, cmd))
        } catch (e: SecurityException) {
            ToolResult.needConfirm(e.message ?: cmd)
        } catch (e: Exception) {
            ToolResult.fail(e.message ?: "ui_action failed")
        }
    }

    companion object {
        fun buildCommand(args: Map<String, String>): String = when (args["action"]) {
            "tap" -> "input tap " + args["x"] + " " + args["y"]
            "swipe" -> "input swipe " + args["x"] + " " + args["y"] + " " + args["x2"] + " " + args["y2"] + " " + (args["duration_ms"] ?: args["ms"] ?: "300")
            "input_text" -> "input text '" + (args["text"] ?: "") + "'"
            else -> throw IllegalArgumentException("action khong ho tro")
        }
    }
}

/**
 * file_op — read/write (path, content, append).
 * read: cat; write: base64 echo | base64 -d >[>>]; can ../symlink/canonical (§5.5).
 */
class FileOpTool(private val exec: ShellExecutor) : AgentTool {
    override val definition = ToolDefinition(
        name = "file_op",
        description = "read (cat) / write base64 (echo b64|base64 -d)",
        inputSchema = mapOf(
            "op" to "string", "path" to "string",
            "content" to "string", "append" to "string"
        ),
        required = listOf("op", "path")
    )
    override suspend fun execute(args: Map<String, String>, ctx: ToolContext): ToolResult {
        val op = args["op"] ?: return ToolResult.fail("thieu op")
        val path = args["path"] ?: return ToolResult.fail("thieu path")
        if (!CommandPolicy.isFilePathAllowed(path, ctx.filesDir))
            return ToolResult.fail("path ngoai whitelist: $path")
        if (path.contains("..")) return ToolResult.fail("chan .. trong path")
        return try {
            when (op) {
                "read" -> okOut(sh(exec, "cat '$path'"))
                "write" -> {
                    val content = args["content"] ?: ""
                    val append = args["append"] == "true"
                    val redir = if (append) ">>" else ">"
                    val b64 = try {
                        android.util.Base64.encodeToString(
                            content.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP
                        )
                    } catch (_: RuntimeException) {
                        // Unit JVM: android.util.Base64 là stub — fallback JDK để test logic
                        java.util.Base64.getEncoder().encodeToString(content.toByteArray(Charsets.UTF_8))
                    }
                    // Chia nhỏ base64 để tránh giới hạn độ dài lệnh binder
                    val chunks = b64.chunked(4000)
                    var code = 0
                    var err = ""
                    for ((i, c) in chunks.withIndex()) {
                        val op2 = if (i == 0) redir else ">>"
                        val r = sh(exec, "echo '$c' | base64 -d $op2 '$path'")
                        if (r.code != 0) { code = r.code; err = r.stderr; break }
                    }
                    if (code == 0) ToolResult.ok("wrote ${content.length} chars -> $path")
                    else ToolResult.fail("write loi code=$code $err")
                }
                else -> ToolResult.fail("op khong ho tro: $op (dung read|write)")
            }
        } catch (e: SecurityException) {
            ToolResult.needConfirm(e.message ?: path)
        } catch (e: Exception) {
            ToolResult.fail(e.message ?: "file_op failed")
        }
    }
}

/** screenshot — screencap -p /sdcard/vitngan/shot_<ts>.png (§5.4). */
class ScreenshotTool(private val exec: ShellExecutor) : AgentTool {    override val definition = ToolDefinition(
        name = "screenshot",
        description = "Chup man hinh (screencap -p)",
        inputSchema = mapOf("path" to "string")
    )
    override suspend fun execute(args: Map<String, String>, ctx: ToolContext): ToolResult {
        val path = args["path"] ?: "/sdcard/vitngan/shot_" + ctx.timestampMs + ".png"
        if (!CommandPolicy.isFilePathAllowed(path, ctx.filesDir))
            return ToolResult.fail("path ngoai whitelist: $path")
        return try {
            val r = sh(exec, "screencap -p '$path'")
            if (r.code != 0) return ToolResult.fail("screencap loi: " + r.stderr.take(500))
            // Xác minh file thật để lượt sau đính kèm ảnh cho model nhìn.
            val ok = sh(exec, "test -f '$path' && echo EXISTS")
            if (!ok.stdout.contains("EXISTS")) return ToolResult.fail("không thấy file ảnh: $path")
            ToolResult.ok(path).also { it.imagePath = path }
        } catch (e: Exception) {
            ToolResult.fail(e.message ?: "screenshot failed")
        }
    }
}

/**
 * ui_dump — đọc màn hình bằng chữ + tọa độ (D-019).
 * Route free không chuyển ảnh tới model (flow Shopee chứng minh), nên đây là
 * "mắt" chính của agent: uiautomator dump rồi lọc node có chữ, tối đa 80 dòng.
 */
class UiDumpTool(private val exec: ShellExecutor) : AgentTool {
    override val definition = ToolDefinition(
        name = "ui_dump",
        description = "Đọc chữ + tọa độ trên màn hình (dùng thay mắt khi cần tap đúng chỗ)",
        inputSchema = mapOf("max_nodes" to "string")
    )

    override suspend fun execute(args: Map<String, String>, ctx: ToolContext): ToolResult {
        val max = args["max_nodes"]?.toIntOrNull()?.coerceIn(10, 150) ?: 80
        return try {
            // App động (animation/load liên tục) làm uiautomator HẾT idle → thử lại
            // vài lần; vẫn fail thì trả focus hiện tại để model còn biết đang ở đâu.
            var dump = sh(exec, "uiautomator dump /sdcard/vitngan/ui.xml")
            var tries = 1
            while (dump.code != 0 && tries < 3) {
                sh(exec, "sleep 2")
                dump = sh(exec, "uiautomator dump /sdcard/vitngan/ui.xml")
                tries++
            }
            if (dump.code != 0) {
                val focus = sh(exec, "dumpsys window | grep -i mCurrentFocus")
                return ToolResult.ok(
                    "(không dump được màn hình — app đang động; focus: " +
                        focus.stdout.trim().take(300) + ")"
                )
            }
            val out = sh(
                exec,
                "grep -o 'text=\"[^\"]\\+\"[^>]*bounds=\"\\[[0-9,]*\\]\\[[0-9,]*\\]\"' " +
                    "/sdcard/vitngan/ui.xml | grep -v 'text=\"\"' | head -" + max
            )
            val text = out.stdout.trim()
            if (text.isEmpty()) ToolResult.ok("(màn hình không có chữ nào)")
            else ToolResult.ok(text.take(6000))
        } catch (e: Exception) {
            ToolResult.fail(e.message ?: "ui_dump failed")
        }
    }

    companion object {
        /** Tọa độ tâm từ chuỗi bounds "[l,t][r,b]" — dùng để tap theo ui_dump. */
        fun center(bounds: String): Pair<Int, Int>? {
            val m = Regex("""\[(\d+),(\d+)\]\[(\d+),(\d+)\]""").find(bounds) ?: return null
            val (l, t, r, b) = m.destructured
            return (l.toInt() + r.toInt()) / 2 to (t.toInt() + b.toInt()) / 2
        }
    }
}
