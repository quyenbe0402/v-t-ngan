package com.vitngan.harness

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.vitngan.harness.agent.AgentCore
import com.vitngan.harness.agent.AnthropicProvider
import com.vitngan.harness.chat.ChatStore
import com.vitngan.harness.chat.HistoryCodec
import com.vitngan.harness.context.TurnBudget
import com.vitngan.harness.chat.SessionMeta
import com.vitngan.harness.chat.StoredMsg
import com.vitngan.harness.dash.AgentLog
import com.vitngan.harness.dash.DashboardService
import com.vitngan.harness.skill.BuiltinSkills
import com.vitngan.harness.skill.SkillManager
import com.vitngan.harness.tool.ToolContext
import com.vitngan.harness.tool.ToolRegistry
import com.vitngan.harness.ux.NonTechMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.io.File

/**
 * App test hoàn chỉnh trên máy (E6): chat agent + duyệt lệnh + skill + dashboard.
 * Key chỉ từ ô nhập/BuildConfig, không log, xóa sau chạy e2e nhanh.
 */
data class ChatMsg(val who: String, val text: String)
data class PendingCall(val name: String, val args: Map<String, String>, val id: String)

class ProbeActivity : ComponentActivity() {

    private val reqCode = 100
    private val logLines = mutableStateListOf<String>()
    private val keyText = mutableStateOf("")
    private val messages = mutableStateListOf<ChatMsg>()
    private val chatInput = mutableStateOf("")
    private val nonTech = mutableStateOf(false)
    private val sending = mutableStateOf(false)
    private val pending = mutableStateListOf<PendingCall>()
    private val skillRepo = mutableStateOf("")
    private val skillNames = mutableStateListOf<String>()
    private val dashOn = mutableStateOf(false)
    private val sessions = mutableStateListOf<SessionMeta>()
    private val currentId = mutableStateOf<String?>(null)
    private val currentTitle = mutableStateOf("Chat mới")

    private val chatStore: ChatStore by lazy { ChatStore(filesDir) }

    private var registry: ToolRegistry? = null
    private var provider: AnthropicProvider? = null
    private var core: AgentCore? = null
    /** Lịch sử API của phiên đang mở — model nhớ ngữ cảnh giữa các lượt gửi. */
    private var currentHistory = mutableListOf<Map<String, Any>>()

    private val permListener =
        Shizuku.OnRequestPermissionResultListener { _, result ->
            addLog(if (result == PackageManager.PERMISSION_GRANTED) "granted" else "denied")
        }

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        if (BuildConfig.EXPLABS_API_KEY.isNotEmpty()) {
            keyText.value = BuildConfig.EXPLABS_API_KEY
        }
        Shizuku.addBinderReceivedListenerSticky {
            addLog("Binder received uid=" + runCatching { Shizuku.getUid() }.getOrNull())
        }
        Shizuku.addBinderDeadListener { addLog("Binder dead") }
        Shizuku.addRequestPermissionResultListener(permListener)
        setContent { ProbeScreen() }
    }

    private fun addLog(s: String) {
        runOnUiThread {
            AgentLog.append(s)
            logLines.add(s.take(500))
            if (logLines.size > 200) logLines.removeAt(0)
        }
    }

    private fun addMsg(who: String, text: String) {
        runOnUiThread {
            messages.add(ChatMsg(who, text.take(2000)))
            if (messages.size > 60) messages.removeAt(0)
            persistChat()
        }
    }

    /** Lưu phiên hiện tại: tin hiển thị + lịch sử API (giữ ngữ cảnh). */
    private fun persistChat() {
        val id = currentId.value ?: return
        try {
            val stored = messages.map { StoredMsg(it.who, it.text) }.toMutableList()
            val hist = HistoryCodec.sanitize(currentHistory).takeLast(30).toMutableList()
            currentHistory = hist
            val s = chatStore.load(id)
            if (s == null) {
                chatStore.save(
                    com.vitngan.harness.chat.StoredSession(
                        id, currentTitle.value, System.currentTimeMillis(), stored, hist
                    )
                )
            } else {
                s.messages.clear()
                s.messages.addAll(stored)
                s.history.clear()
                s.history.addAll(hist)
                s.title = currentTitle.value
                chatStore.save(s)
            }
            refreshSessionList()
        } catch (e: Exception) {
            AgentLog.append("lưu chat lỗi: ${e.message}")
        }
    }

    private fun refreshSessionList() {
        try {
            val list = chatStore.list()
            sessions.clear()
            sessions.addAll(list)
        } catch (e: Exception) {
            AgentLog.append("đọc phiên lỗi: ${e.message}")
        }
    }

    /** Mở phiên đã lưu (giữ lại dòng log đến sớm trước khi nạp). */
    private fun openSession(id: String) {
        persistChat()
        val early = messages.toList()
        val s = chatStore.load(id)
        messages.clear()
        if (s == null) {
            messages.addAll(early)
            return
        }
        // Tin đã lưu (cũ) trước, dòng đến sớm (mới) sau.
        messages.addAll(s.messages.map { ChatMsg(it.who, it.text) })
        messages.addAll(early.filter { m -> messages.none { it.who == m.who && it.text == m.text } })
        while (messages.size > 60) messages.removeAt(0)
        currentHistory = HistoryCodec.sanitize(s.history).takeLast(30).toMutableList()
        currentId.value = s.id
        currentTitle.value = s.title
        refreshSessionList()
    }

    private fun newChat() {
        persistChat()
        messages.clear()
        currentHistory = mutableListOf()
        val s = chatStore.newSession()
        currentId.value = s.id
        currentTitle.value = s.title
        refreshSessionList()
    }

    private fun deleteCurrent() {
        val id = currentId.value ?: return
        chatStore.delete(id)
        messages.clear()
        currentHistory = mutableListOf()
        val next = chatStore.list().firstOrNull()
        if (next == null) {
            val s = chatStore.newSession()
            currentId.value = s.id
            currentTitle.value = s.title
        } else {
            openSession(next.id)
            return
        }
        refreshSessionList()
    }

    private fun checkPermission(): Boolean {
        // Guard: binder chưa nhận (server chết/chưa Start) thì Shizuku.* ném
        // IllegalStateException("binder haven't been received") → crash main (FATAL
        // 10-09 khi chat lúc NO_SERVER). Không bao giờ để ném ra ngoài (NFR-08).
        try {
            if (!Shizuku.pingBinder()) {
                addLog("Shizuku chưa chạy — mở app Shizuku → Start rồi thử lại")
                return false
            }
        } catch (e: Exception) {
            addLog("Shizuku chưa sẵn sàng (${e.message}) — mở Shizuku → Start")
            return false
        }
        try {
            if (Shizuku.isPreV11()) {
                addLog("pre-v11 unsupported"); return false
            }
        } catch (e: Exception) {
            addLog("Không kiểm tra được Shizuku (${e.message})")
            return false
        }
        try {
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) return true
        } catch (e: Exception) {
            addLog("Shizuku chưa sẵn sàng (${e.message}) — mở Shizuku → Start")
            return false
        }
        try {
            if (Shizuku.shouldShowRequestPermissionRationale()) {
                addLog("Đã chọn không hỏi lại → vào Shizuku Manager cấp lại")
                return false
            }
            Shizuku.requestPermission(reqCode)
        } catch (e: Exception) {
            addLog("Shizuku mất kết nối (${e.message}) — mở Shizuku → Start")
        }
        return false
    }

    private fun needStack(): Boolean {
        val key = keyText.value.trim()
        if (key.isEmpty()) {
            addLog("ERR: dán API key vào ô trên trước")
            return false
        }
        if (registry == null) {
            val r = ToolRegistry(ShizukuShellExecutor(this@ProbeActivity))
            registry = r
            provider = AnthropicProvider(apiKey = key, registry = r)
            core = AgentCore(
                r,
                ToolContext(filesDir.absolutePath, System.currentTimeMillis())
            ) { line -> addLog(line) }
        }
        return true
    }

    private suspend fun doEcho() {
        try {
            val r = ShizukuShellExecutor(this@ProbeActivity).exec("echo hello")
            addLog("code=${r.code} stdout=${r.stdout} (${r.ms}ms)")
        } catch (e: Exception) {
            addLog("ERR: ${e.message}")
        }
    }

    /** System prompt: nền + receta skill dựng sẵn (D-020), non-tech thì thêm giọng đơn giản. */
    private fun systemWithSkills(): String {
        val base = if (nonTech.value) NonTechMode.systemPrompt() else AnthropicProvider.DEFAULT_SYSTEM_HINT
        return base + "\n" + BuiltinSkills.promptSection()
    }

    /** Vòng agent đầy đủ, DÙNG lịch sử phiên (model nhớ các lượt trước). */
    private suspend fun agentLoop(userText: String) {
        val p = provider ?: return
        val c = core ?: return
            val system = systemWithSkills()
        // Sanitize trước khi gửi: bỏ tool_use treo từ lần thoát app giữa chừng.
        currentHistory = HistoryCodec.sanitize(currentHistory).takeLast(30).toMutableList()
        currentHistory.add(mapOf("role" to "user", "content" to userText))
        persistChat()
        val history = currentHistory
        for (turn in 0 until 8) {
            val r = withContext(Dispatchers.IO) { p.postMessages(history, system = system) }
            history.add(p.assistantMessage(r.rawJson))
            if (r.stopReason != "tool_use" || r.calls.isEmpty()) {
                addMsg("Bot", r.text.ifBlank { "(rỗng, stop=${r.stopReason})" })
                persistChat()
                return
            }
            addMsg("Bot", "🔧 " + r.calls.joinToString { it.name })
            val steps = c.runTurn(r.calls.map { Triple(it.name, it.input, it.id) })
            val (done, needs) = AgentCore.partition(steps)
            // Mỗi tool_use chỉ có ĐÚNG 1 tool_result (API từ chối trùng id) —
            // đã add done ở đây thì cuối vòng không add lại nữa.
            if (done.isNotEmpty()) history.add(p.toolResultMessage(done))
            if (needs.isNotEmpty()) {
                pending.addAll(needs.map { PendingCall(it.tool, it.args, it.toolUseId) })
                persistChat()
                addMsg("Bot", "Chờ duyệt: " + needs.joinToString { it.tool } + " — bấm Duyệt để chạy.")
                return
            }
        }
        persistChat()
        addMsg("Bot", "(hết 8 vòng, dừng để tránh lặp)")
    }

    private suspend fun onSend() {
        val text = chatInput.value.trim()
        if (text.isEmpty() || sending.value) return
        if (!checkPermission() || !needStack()) return
        if (currentId.value == null) {
            val s = chatStore.newSession()
            currentId.value = s.id
            currentTitle.value = s.title
        }
        if (currentTitle.value == "Chat mới") {
            currentTitle.value = text.take(24)
        }
        sending.value = true
        try {
            chatInput.value = ""
            addMsg("Bạn", text)
            agentLoop(text)
        } catch (e: Exception) {
            addMsg("Bot", "Lỗi: ${(e.message ?: "unknown").take(300)}")
        } finally {
            sending.value = false
        }
    }

    /** Duyệt các lệnh chờ: chạy lại với confirmed=true rồi đi tiếp vòng agent. */
    private suspend fun onApprove() {
        val c = core ?: return
        val p = provider ?: return
        val calls = pending.toList()
        if (calls.isEmpty()) return
        pending.clear()
        sending.value = true
        try {
            val history = HistoryCodec.sanitize(currentHistory).takeLast(30).toMutableList()
            currentHistory = history
            val steps = c.runTurn(calls.map { Triple(it.name, it.args + ("confirmed" to "true"), it.id) })
            history.add(p.toolResultMessage(steps))
            persistChat()
        val system = systemWithSkills()
            for (turn in 0 until 7) {
                // Gửi bản prune (bỏ turn cũ + ảnh cũ) — history gốc giữ nguyên để lưu.
                val r = withContext(Dispatchers.IO) {
                    p.postMessages(TurnBudget.pruneForSend(history), system = system)
                }
                history.add(p.assistantMessage(r.rawJson))
                if (r.stopReason != "tool_use" || r.calls.isEmpty()) {
                    addMsg("Bot", r.text.ifBlank { "(rỗng, stop=${r.stopReason})" })
                    persistChat()
                    return
                }
                addMsg("Bot", "🔧 " + r.calls.joinToString { it.name })
                val s2 = c.runTurn(r.calls.map { Triple(it.name, it.input, it.id) })
                val (done, needs) = AgentCore.partition(s2)
                if (done.isNotEmpty()) history.add(p.toolResultMessage(done))
                if (needs.isNotEmpty()) {
                    pending.addAll(needs.map { PendingCall(it.tool, it.args, it.toolUseId) })
                    persistChat()
                    addMsg("Bot", "Chờ duyệt tiếp: " + needs.joinToString { it.tool })
                    return
                }
            }
            persistChat()
        } catch (e: Exception) {
            addMsg("Bot", "Lỗi: ${(e.message ?: "unknown").take(300)}")
        } finally {
            sending.value = false
        }
    }

    private suspend fun doE2e() {
        if (!checkPermission() || !needStack()) return
        sending.value = true
        try {
            addMsg("Bạn", "chạy echo hello")
            agentLoop("chạy echo hello, trả lời ngắn gọn")
            keyText.value = ""
        } finally {
            sending.value = false
        }
    }

    private suspend fun installSkill() {
        val url = skillRepo.value.trim()
        if (url.isEmpty()) {
            addMsg("Bot", "Nhập URL repo GitHub của skill trước (vd owner/repo).")
            return
        }
        try {
            val m = SkillManager(File(filesDir, "skills"))
            val s = withContext(Dispatchers.IO) { m.installFromGithub(url) }
            addMsg("Bot", "Đã cài skill ${s.name} (${s.commands.size} lệnh).")
            refreshSkills()
        } catch (e: Exception) {
            addMsg("Bot", "Cài skill lỗi: ${(e.message ?: "unknown").take(300)}")
        }
    }

    private fun refreshSkills() {
        try {
            val list = SkillManager(File(filesDir, "skills")).listInstalled()
            skillNames.clear()
            skillNames.addAll(list.map { it.name })
        } catch (e: Exception) {
            addLog("skills lỗi: ${e.message}")
        }
    }

    private suspend fun runSkillCommand(skill: String, cmd: String, shell: String) {
        if (!checkPermission() || !needStack()) return
        val c = core ?: return
        // Lệnh nguy hiểm không chạy trực tiếp (sẽ làm lệch lịch sử tool_use/tool_result
        // → provider 400). Bảo user nhắn vào chat để agent chạy qua luồng duyệt chuẩn.
        if (com.vitngan.harness.CommandPolicy.needsConfirm(shell)) {
            addMsg("Bot", "Lệnh [$cmd] nguy hiểm — nhắn vào chat (vd “chạy lệnh $cmd của skill $skill”), bot sẽ hỏi duyệt trước khi chạy.")
            return
        }
        sending.value = true
        try {
            addMsg("Bạn", "skill $skill/$cmd")
            val s = c.runTurn(listOf(Triple("shell_exec", mapOf("command" to shell), "skill"))).single()
            addMsg("Bot", if (s.result.ok) s.result.output.take(1500) else "Lỗi: " + s.result.error.take(500))
        } finally {
            sending.value = false
        }
    }

    private fun toggleDash() {
        try {
            if (dashOn.value) {
                stopService(android.content.Intent(this, DashboardService::class.java))
                dashOn.value = false
                addMsg("Bot", "Đã tắt dashboard.")
            } else {
                startForegroundService(android.content.Intent(this, DashboardService::class.java))
                dashOn.value = true
                addMsg("Bot", "Dashboard chạy ở http://127.0.0.1:8080 (mở bằng trình duyệt trên máy).")
            }
        } catch (e: Exception) {
            addMsg("Bot", "Dashboard lỗi: ${(e.message ?: "unknown").take(200)}")
        }
    }

    @Composable
    private fun ProbeScreen() {
        val scope = rememberCoroutineScope()
        val guideOpen = remember { mutableStateOf(false) }
        val logOpen = remember { mutableStateOf(false) }
        androidx.compose.runtime.LaunchedEffect(Unit) { refreshSkills() }
        androidx.compose.runtime.LaunchedEffect(Unit) {
            withContext(Dispatchers.IO) {
                val list = chatStore.list()
                val first = list.firstOrNull()
                runOnUiThread {
                    sessions.clear()
                    sessions.addAll(list)
                    if (first != null) openSession(first.id)
                    else {
                        val s = chatStore.newSession()
                        currentId.value = s.id
                        currentTitle.value = s.title
                        refreshSessionList()
                    }
                }
            }
        }
        MaterialTheme {
            val drawerState = rememberDrawerState(DrawerValue.Closed)
            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
                    ModalDrawerSheet {
                        Column(
                            modifier = Modifier.padding(12.dp).verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("Cài đặt", style = MaterialTheme.typography.headlineSmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = {
                                    scope.launch { drawerState.close() }
                                    if (checkPermission()) scope.launch { doEcho() }
                                }) { Text("Test echo") }
                                Button(onClick = {
                                    scope.launch { drawerState.close() }
                                    if (checkPermission()) scope.launch { doE2e() }
                                }) { Text("Test e2e") }
                            }
                            TextField(
                                value = keyText.value,
                                onValueChange = { keyText.value = it },
                                label = { Text("API key (chỉ RAM, không lưu)") },
                                visualTransformation = PasswordVisualTransformation(),
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            Text("Skill", style = MaterialTheme.typography.titleMedium)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextField(
                                    value = skillRepo.value,
                                    onValueChange = { skillRepo.value = it },
                                    label = { Text("owner/repo") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                                Button(onClick = { scope.launch { installSkill() } }) { Text("Cài") }
                            }
                            skillNames.forEach { name ->
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("• $name", modifier = Modifier.weight(1f).padding(top = 12.dp))
                                    OutlinedButton(onClick = {
                                        scope.launch {
                                            val cmds = SkillManager(File(filesDir, "skills")).getCommands(name)
                                            val first = cmds.entries.firstOrNull()
                                            if (first != null) runSkillCommand(name, first.key, first.value)
                                        }
                                    }) { Text("Chạy") }
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { toggleDash() }) {
                                    Text(if (dashOn.value) "Tắt dashboard" else "Bật dashboard")
                                }
                            }
                            TextButton(onClick = { guideOpen.value = !guideOpen.value }) {
                                Text(if (guideOpen.value) "Ẩn hướng dẫn" else "Hướng dẫn Shizuku")
                            }
                            if (guideOpen.value) GuideText()
                            TextButton(onClick = { logOpen.value = !logOpen.value }) {
                                Text(if (logOpen.value) "Ẩn nhật ký kỹ thuật" else "Nhật ký kỹ thuật")
                            }
                            if (logOpen.value) {
                                logLines.takeLast(30).forEach {
                                    Text(it, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().statusBarsPadding().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "VitNgan",
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                        TextButton(onClick = { scope.launch { drawerState.open() } }) {
                            Text("☰", style = MaterialTheme.typography.headlineMedium)
                        }
                    }
                    Text(
                        "Phiên: ${currentTitle.value}",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { newChat() }) { Text("＋ Mới") }
                        OutlinedButton(onClick = { deleteCurrent() }) { Text("Xóa") }
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(sessions) { s ->
                            OutlinedButton(
                                onClick = { if (s.id != currentId.value) openSession(s.id) },
                                enabled = s.id != currentId.value
                            ) { Text(s.title.take(18)) }
                        }
                    }
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(messages) { m ->
                            Text(
                                (if (m.who == "Bạn") "Bạn: " else "Bot: ") + m.text,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                    if (pending.isNotEmpty()) {
                        Button(onClick = { scope.launch { onApprove() } }) {
                            Text("Duyệt (${pending.size})")
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Checkbox(
                            checked = nonTech.value,
                            onCheckedChange = { nonTech.value = it }
                        )
                        Text("Chế độ đơn giản", modifier = Modifier.padding(top = 12.dp))
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TextField(
                            value = chatInput.value,
                            onValueChange = { chatInput.value = it },
                            label = { Text("Nhắn cho agent…") },
                            modifier = Modifier.weight(1f),
                            singleLine = false,
                            maxLines = 3
                        )
                        Button(
                            onClick = { scope.launch { onSend() } },
                            enabled = !sending.value
                        ) { Text("Gửi") }
                    }
                }
            }
        }
    }

    @Composable
    private fun GuideText() {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("1. Mở app Shizuku → Start → mục “Start by connecting to a computer”.")
            Text("2. Bấm “View command”, chạy lệnh hiện ra trên máy tính (làm lại mỗi khi khởi động lại máy).")
            Text("3. Mở probe → Test echo → phải hiện code=0 stdout=hello.")
            Text("4. Lần đầu Test e2e/chat: hiện bảng xin quyền → chọn “Allow all the time”.")
            Text("5. Gỡ/cài lại app thì phải cho phép lại (Android xóa quyền khi gỡ).")
            Text("6. Lệnh nguy hiểm hiện “Chờ duyệt” — bấm Duyệt mới chạy.")
        }
    }

    override fun onDestroy() {
        Shizuku.removeRequestPermissionResultListener(permListener)
        super.onDestroy()
    }
}
