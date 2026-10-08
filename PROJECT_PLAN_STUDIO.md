# PROJECT PLAN — Android Agent Harness ("Vit-Ngan Harness")

| Trường | Giá trị |
|---|---|
| Phiên bản tài liệu | v3.0 (viết lại theo chuẩn studio; thay thế đề cương v1 và PLAN v2 làm nguồn chính) |
| Ngày | 2026-10-08 |
| Chủ dự án (Owner) | Bạn |
| Cố vấn / Reviewer | Claude (tham vấn tại các Consult Gate) |
| Người thực thi độc lập | Claude Code và các agent khác (làn L2) |
| Trạng thái | Pre-production — chưa có dòng code nào; đang chờ G0 |
| Nơi lưu | `docs/PLAN.md` trong repo GitHub |

**Quy ước đọc tài liệu**: mọi con số/tên file/version được gắn thẻ `[V-xx]` là **chưa xác minh** (xem mục 8). Không dùng làm căn cứ cuối cùng cho tới khi thẻ đó được đóng.

---

## 1. Project Charter

### 1.1 Tầm nhìn
Một ứng dụng Android chạy hoàn toàn trên điện thoại, nơi một AI model gọi "tool" để thao tác thật trên máy (cài/gỡ app, đổi cài đặt, chạm/vuốt, đọc file, chạy shell) ở mức quyền ADB qua Shizuku — mạnh hơn các app agent bán trên CH Play vì không bị ràng buộc chính sách của Play.

### 1.2 Vấn đề cần giải
- App agent công khai bị giới hạn quyền; muốn "làm hộ" người lớn tuổi (đổi cỡ chữ, hình nền, cài app) cần quyền hệ thống thật.
- Các framework mạnh (Ghost in the Droid) cần PC làm server → trái mục tiêu "chạy ngay trên máy".

### 1.3 Mục tiêu (Goals) — đo được
| ID | Mục tiêu | Cách đo |
|---|---|---|
| G-1 | Chạy lệnh shell qua Shizuku từ app của mình | `echo hello` trả `code=0 stdout=hello` < 10s trên máy thật |
| G-2 | Vòng agent end-to-end | Chat "chạy echo hello" → model gọi `shell_exec` → kết quả hiện lại trong câu trả lời |
| G-3 | 7 tool MVP hoạt động có xác nhận + log | Bộ acceptance test AT-01…AT-07 xanh trên máy thật |
| G-4 | Có đường lùi khi Shizuku không khả dụng | Dadb fallback chạy được trên cùng bộ tool |
| G-5 | Cập nhật dễ dàng | Mọi thay đổi qua GitHub PR; có bản APK ký trong GitHub Releases |
| G-6 | Người thân non-tech dùng được | Sau onboarding một lần, tự dùng chat đổi được cỡ chữ/hình nền (Track H) |

### 1.4 Ngoài phạm vi (Non-goals) — đã chốt
- Root, đọc/ghi `/data/data` của app khác (chỉ root làm được).
- Phát hành lên CH Play.
- iOS.
- Server/PC làm thành phần bắt buộc khi vận hành.
- Chạy model local, voice, browser nhúng, workflow, bluetooth, FFmpeg, music (các phần này của Operit bị **quarantine** khỏi MVP).

### 1.5 Ràng buộc
- Ngôn ngữ Kotlin; native Android; minSdk 26.
- Quyền hệ thống đến từ Shizuku (chính) và Dadb (dự phòng). Bước bật Wireless Debugging + ghép nối không thể bỏ (cơ chế bảo mật của Android).
- Nền kế thừa Operit AI (LGPL-3.0) → nghĩa vụ giấy phép (mục 11, R05).
- 1 chủ dự án + agent; ước lượng sai số ±50%.

### 1.6 Giả định (cần xác minh, xem mục 8)
A1: Máy test là Redmi 24069RA21C / Android 16 (API 36) `[V-10]`. A2: Shizuku-API 13.1.5 là bản hiện hành `[V-04]`. A3: Operit `main` có cấu trúc như báo cáo A-lite `[V-01..03]`.

---

## 2. Vai trò & phân quyền (RACI rút gọn)

R = làm, A = chịu trách nhiệm cuối, C = được hỏi ý, I = được báo.

| Hoạt động | Owner | Claude (cố vấn) | Claude Code / agent (L2) |
|---|---|---|---|
| Quyết định phạm vi, chốt gate | A | C | I |
| Chấm JEV, ghi decision log | A | R | R (đề xuất) |
| Viết code theo task card | I | C | R |
| Chạy test trên máy thật, đọc log | R/A | C | — |
| Bí mật (keystore, API key), tạo repo, secrets | R/A | — | — (không được chạm) |
| Review PR theo checklist | A | R | R (self-review) |
| Viết tài liệu người dùng cuối | A | C | R |

Nguyên tắc: **agent không bao giờ tự đóng gate**; gate chỉ đóng khi Owner xác nhận sau Consult.

---

## 3. Mô hình vận hành (Operating Model) — các quy trình bắt buộc

### 3.1 Khung chấm điểm quyết định JEV

Mỗi quyết định kỹ thuật/phạm vi được chấm trên 5 tiêu chí (0–1):

| Mã | Tiêu chí | Trọng số | Ý nghĩa |
|---|---|---|---|
| E | Evidence — bằng chứng | 0.30 | Đã xác minh trực tiếp (0.9+), có nguồn chính thức (0.7), chỉ do agent/báo cáo (0.4–0.5), suy đoán (<0.4) |
| F | Feasibility/Fit | 0.25 | Khả thi với ràng buộc 1.5 |
| R | Reversibility | 0.15 | Dễ đổi ý/blast radius nhỏ = cao |
| C | Cost/Effort | 0.15 | Công sức thấp = cao |
| A | Alignment | 0.15 | Phục vụ mục tiêu G-1…G-6 |

**JEV = 0.30E + 0.25F + 0.15R + 0.15C + 0.15A**

| Kết quả | Hành động |
|---|---|
| **> 0.85** | **PASS** — chốt, ghi decision log, không hỏi lại |
| **0.50 – 0.85** | **FLAG** — nêu phương án tối ưu hơn (nếu có); làm tiếp bằng **mặc định** trên nhánh đảo ngược được; gom câu hỏi vào Consult Gate gần nhất |
| **< 0.50** | **REJECT** — không dùng; người đề xuất tự tìm phương án khác và chấm lại |

**Luật bổ sung (chống tự tin sai)**:
- *Veto*: F < 0.30 hoặc A < 0.30 → REJECT bất kể tổng điểm.
- *Evidence cap*: E < 0.60 → điểm tổng bị chặn trần ở 0.85 (không thể PASS cho tới khi xác minh).
- *Quyết định không đảo ngược được* (ví dụ chọn nền, ký keystore) cần JEV > 0.90.
- Chấm lại khi có bằng chứng mới (đặc biệt khi đóng một thẻ `[V-xx]`).

### 3.2 Chính sách giảm câu hỏi cho Owner (Ask-minimization)
1. **Default-and-proceed**: mỗi câu hỏi mở (OD-xx, mục 6.2) có sẵn mặc định; agent làm tiếp theo mặc định, không chờ.
2. **Gom lô**: chỉ hỏi Owner tại Consult Gate; **tối đa 3 câu/gate**, mỗi câu kèm mặc định + hệ quả nếu đổi.
3. **Không hỏi** những gì đã có trong tài liệu này hoặc tra cứu được (docs, repo).
4. **Chỉ ngắt ngay** khi: sự cố an toàn dữ liệu, cần bí mật/khoá, hoặc thao tác vật lý trên máy (nhóm L4).

### 3.3 Giao thức Consult Gate (tham vấn Claude sau mỗi mục lớn)
**Khi nào**: cuối mỗi Milestone (G0…G7, mục 9). **Không tự nhảy qua gate.**

**Owner mang đến — "Consult Packet"** (template đặt ở `.github/ISSUE_TEMPLATE/consult-packet.md`):
1. Milestone & gate đang xin đóng.
2. Artifacts: link PR/commit, log test thật (không tóm tắt), `OPERIT_NOTES.md` nếu có.
3. Danh sách quyết định FLAG chờ ý kiến (≤3 câu hỏi, mỗi câu có mặc định).
4. Số liệu: task xong/dở, thời gian thực tế vs ước lượng, rủi ro mới.

**Claude trả về — "Executable Answer"** (định dạng cố định):
1. **Verdict**: GO / GO-với-thay-đổi / NO-GO + lý do 1–3 dòng.
2. **Chấm lại JEV** của các quyết định bị ảnh hưởng.
3. **Task cards kế tiếp** (đã điền đủ, dán thẳng vào Claude Code) cho milestone tiếp theo.
4. **Cập nhật** risk register & verification registry.
5. **Câu hỏi còn lại** (≤3, có mặc định).

### 3.4 Mô hình làn việc (Work Lanes)

| Làn | Tên | Điều kiện vào | Ví dụ |
|---|---|---|---|
| **L1** | Song song | Không phụ thuộc kết quả của nhau | B1 spike ‖ A-lite ‖ scaffold repo |
| **L2** | Model độc lập | Task card đủ spec + acceptance tự kiểm được | Viết ShellExecutor, 7 tool, SkillManager, dashboard, CI |
| **L3** | Cần tham vấn trước | Phụ thuộc kết quả chưa biết hoặc phán đoán thiết kế | Nối Tool Registry vào dispatcher Operit, chọn Ktor/NanoHTTPD |
| **L4** | Chỉ Owner | Vật lý/bí mật/quan hệ người thân | Cài Shizuku, chạy test máy thật, keystore, tạo repo, onboarding người thân |

Một task có thể gắn nhiều làn (vd. `L1+L2`). Nhãn GitHub: `lane:L1…L4`.

**Hợp đồng bàn giao L2 (Handoff Contract)**: agent nhận Task Card (mục 12) → làm trên nhánh `feature/<id>` → mở PR kèm: (a) báo cáo theo format cố định, (b) lệnh chạy test, (c) danh sách giả định đã dùng, (d) điều chưa chắc. Gặp *stop condition* trong card thì dừng và báo, không tự suy diễn.

### 3.5 Quy trình GitHub
- **Repo**: `android-agent-harness` (mặc định **private** tới khi tuân thủ LGPL đầy đủ — OD-03).
- **Nhánh**: `main` được bảo vệ (PR bắt buộc, CI xanh, squash merge); `feature/<task-id>-<slug>`; `release/vX.Y`.
- **Commit**: Conventional Commits (`feat:`, `fix:`, `docs:`, `chore:`).
- **Issue**: 1 task = 1 issue, nhãn `epic:E#`, `lane:L#`, `gate:G#`, `type:task|decision|risk|change-request`.
- **Mẫu**: `task-card.md`, `decision-record.md`, `consult-packet.md`.
- **Tài liệu sống**: `docs/PLAN.md`, `docs/DECISIONS.md` (decision log JEV), `docs/adr/ADR-xxx.md`, `docs/OPERIT_NOTES.md`, `docs/RISKS.md`, `docs/USER_GUIDE_VI.md`.
- **CI** (`.github/workflows/build.yml`): Gradle build + unit test + ktlint/detekt mỗi push/PR. Device test chạy thủ công (L4).
- **Bí mật**: keystore & API key không commit; `.gitignore` chặn `*.jks`, `*.keystore`, `local.properties`; secrets dùng GitHub Actions Secrets.
- **Release**: tag SemVer, GitHub Release đính kèm APK đã ký + changelog.
- **`CLAUDE.md`** ở root repo (cho Claude Code): nêu mục tiêu, lệnh build/test, quy ước code, vùng cấm (keystore, secrets), và quy tắc "dừng & báo khi gặp stop condition".

### 3.6 Definition of Ready / Done
**Ready** (task được phép bắt đầu): có ID, lane, phụ thuộc đã xong, acceptance tự kiểm được, JEV các quyết định liên quan không phải REJECT.
**Done** (task): code merge qua PR + CI xanh + acceptance đạt + tài liệu/decision log cập nhật + risk/verification cập nhật nếu có.
**Done** (milestone): mọi task Done + exit criteria của gate đạt + Consult Gate hoàn tất + Owner đóng gate.

### 3.7 Kiểm soát thay đổi
Mọi yêu cầu thêm/đổi phạm vi mở issue `change-request`; chấm JEV; chỉ nhận vào backlog của milestone **kế tiếp**, không chèn vào milestone đang chạy trừ khi nó chặn gate.

---

## 4. Yêu cầu sản phẩm (PRD)

### 4.1 Người dùng
| Persona | Mô tả | Nhu cầu chính |
|---|---|---|
| P1 — Owner/Power user | Bạn, rành kỹ thuật | Quyền rộng, log rõ, tuỳ biến tool/skill |
| P2 — Người thân non-tech | Lớn tuổi, ít rành | Nói yêu cầu bằng tiếng Việt → agent làm hộ, hỏi xác nhận trước việc quan trọng |

### 4.2 Yêu cầu chức năng (MoSCoW)
| ID | Yêu cầu | Ưu tiên | Milestone |
|---|---|---|---|
| FR-01 | Chat với model và vòng gọi tool (tối đa ~10 vòng/lượt) | Must | M2 |
| FR-02 | Thực thi shell qua Shizuku với timeout, bắt stdout/stderr/exit code | Must | M1 |
| FR-03 | Phát hiện trạng thái Shizuku (chưa cài/chưa chạy/chưa cấp quyền) và hướng dẫn | Must | M1 |
| FR-04 | Tool `shell_exec` | Must | M2 |
| FR-05 | Tool `install_app` (APK path/URL) | Must | M2 |
| FR-06 | Tool `set_system_setting` (system/secure/global) + xác minh bằng `settings get` | Must | M2 |
| FR-07 | Tool `get_installed_apps` (lọc, gồm/không gồm app hệ thống) | Must | M2 |
| FR-08 | Tool `ui_action` (tap/swipe/input_text) | Must | M2 |
| FR-09 | Tool `file_op` (read/write) trong whitelist | Must | M2 |
| FR-10 | Tool `screenshot` | Must | M2 |
| FR-11 | Xác nhận trước lệnh nguy hiểm (danh sách ở 5.5) | Must | M2 |
| FR-12 | Log mọi lần gọi tool (xoay vòng, che key) | Must | M2 |
| FR-13 | Provider Anthropic với `tool_use`/`tool_result` | Must | M2 |
| FR-14 | Lưu API key mã hoá | Must | M2 |
| FR-15 | Dadb fallback + tự chọn executor khả dụng | Should | M3 |
| FR-16 | Build debug song song release (`applicationIdSuffix`) | Should | M3 |
| FR-17 | Skill cài từ GitHub (`skill.json` playbook lệnh) | Should | M4 |
| FR-18 | Dashboard nhúng `/status` + trang log | Should | M4 |
| FR-19 | Màn hình hướng dẫn bật Shizuku bằng hình ảnh (tiếng Việt) | Should | M5 |
| FR-20 | Chế độ "non-tech": trả lời ngắn, xác nhận ý định trước khi làm | Should | M5 |
| FR-21 | Backup/restore dữ liệu agent (chat, memory, skill) ra JSON | Could | M6 |
| FR-22 | Tự động ghép nối Wireless Debugging (Kadb) | Could | Sau v1.0 |
| FR-23 | Engine JS cho skill (QuickJS) | Won't (v1) | — |

### 4.3 Yêu cầu phi chức năng
| ID | Yêu cầu |
|---|---|
| NFR-01 | Build tái lập được từ repo sạch (Gradle wrapper, JDK 17), CI xanh |
| NFR-02 | minSdk 26; chạy được trên máy test A1 |
| NFR-03 | Timeout tool: đọc/get 10s, install 120s; không treo UI (IO dispatcher) |
| NFR-04 | Không chạy lệnh tương tác (không TTY) |
| NFR-05 | Log cắt 4KB/lệnh; che `sk-ant-…`, `Bearer …` |
| NFR-06 | Giao diện và thông báo lỗi tiếng Việt |
| NFR-07 | Tuân thủ LGPL-3.0 (giữ header, LICENSE, NOTICE) |
| NFR-08 | Mất Shizuku không làm crash; khởi động lại Shizuku → dùng tiếp, không cài lại app |

---

## 5. Thiết kế kỹ thuật (TDD tóm tắt)

### 5.1 Bối cảnh & thành phần
```
Người dùng ─► UI (Compose) ─► Agent Core (vòng lặp) ─► AI Provider (Anthropic)
                                   │
                                   ▼
                            Tool Registry ──► Policy (whitelist/confirm/log)
                                   │
                                   ▼
                            ShellExecutor (interface)
                            ├─ ShizukuShellExecutor   (chính)
                            ├─ DadbShellExecutor      (dự phòng)
                            └─ FirstAvailableExecutor (chọn & cache)
Phụ trợ: SkillManager (GitHub) · Dashboard (NanoHTTPD, ForegroundService) · Logger
```

### 5.2 Hợp đồng lõi
```kotlin
data class ShellResult(val code: Int, val stdout: String, val stderr: String, val ms: Long)

interface ShellExecutor {
    suspend fun isAvailable(): Boolean
    suspend fun exec(command: String, timeoutMs: Long = 10_000): ShellResult
}
```
Agent Core chỉ phụ thuộc interface này.

### 5.3 Thiết kế B1 (ShizukuShellExecutor) `[V-04]`
- `isAvailable = pingBinder() && !isPreV11() && checkSelfPermission()==GRANTED`.
- `exec`: chỉ gọi API Shizuku sau khi `pingBinder()==true`; `Shizuku.newProcess(arrayOf("sh","-c",cmd), null, null)`; đọc stdout/stderr ở **hai thread riêng** (tránh deadlock); `waitFor` có timeout + `destroy()`.
- Activity thử: `addBinderReceivedListenerSticky`, `addBinderDeadListener`, `addRequestPermissionResultListener`; luồng: binder? → cấp quyền? → `requestPermission(100)` → `exec("echo hello")`.
- `newProcess` được ghi nhận là deprecated `[V-04]` → dùng cho **gate**, sau đó thêm `UserServiceShellExecutor` (AIDL) ở M3 (task E4-4).
- Manifest tối thiểu `[V-05]`:
```xml
<uses-permission android:name="moe.shizuku.manager.permission.API_V23" />
<provider android:name="rikka.shizuku.ShizukuProvider"
  android:authorities="${applicationId}.shizuku"
  android:multiprocess="false" android:enabled="true" android:exported="true"
  android:permission="android.permission.INTERACT_ACROSS_USERS_FULL" />
```
- Ma trận trạng thái: `ping=false` → "Shizuku chưa chạy, mở Shizuku bấm Start"; DENIED + rationale=false → `requestPermission`; DENIED + rationale=true → "Đã chọn không hỏi lại, vào Shizuku cấp lại"; GRANTED → exec.

### 5.4 Danh mục tool MVP (7 tool logic)
Tên theo regex `^[a-zA-Z0-9_-]{1,128}$`. `ToolDefinition(name, description, inputSchema, annotations)` tự định nghĩa theo đặc tả MCP, hàm `toAnthropic()` đổi `inputSchema → input_schema` `[V-07]`.

| Tool | Tham số | Ánh xạ shell | Timeout |
|---|---|---|---|
| `shell_exec` | `command`, `timeout_ms` | `sh -c` | tuỳ chọn |
| `install_app` | `apk_path` hoặc `url` | `curl -L … && pm install -r` (quote `'…'`) | 120s |
| `set_system_setting` | `namespace`, `key`, `value` | `settings put` + `settings get` kiểm lại | 10s |
| `get_installed_apps` | `filter`, `include_system` | `pm list packages [-3]` | 10s |
| `ui_action` | `action`: tap/swipe/input_text + tham số | `input tap|swipe|text` | 10s |
| `file_op` | `op`: read/write, `path`, `content`, `append` | read `cat`; write base64 `echo b64|base64 -d >[>]` | 10s |
| `screenshot` | `path` | `screencap -p /sdcard/vitngan/shot_<ts>.png` | 10s |

`ToolRegistry.dispatch`: (1) policy/whitelist/confirm → (2) `executor.exec` → (3) bọc kết quả, `isError = code != 0`. Lỗi tool trả `is_error:true` cho model, không ném khỏi vòng lặp.

### 5.5 Baseline an toàn (kế thừa từ PLAN v2)
- **Xác nhận trước**: `pm uninstall|disable`, `rm -rf`, `dd|mkfs|reboot|factory`, `settings put|delete`, `pm install`, `file_op.write`, shell nối >2 lệnh bằng `&& ; |`. Mặc định hỏi (Allow/Ask/Deny, mặc định Ask).
- **Whitelist file**: `^/sdcard/vitngan/` + `filesDir`; chặn `..`, symlink, kiểm canonical path; đọc cắt 4KB.
- **Log**: timestamp, tool, lệnh, code, stdout/stderr (4KB), ms; xoay vòng trong thư mục riêng của app.

### 5.6 Provider Anthropic (Module F) `[V-08]`
`POST /v1/messages`, header `anthropic-version: 2023-06-01`; body: `model`, `max_tokens ≥ 1024`, `tools[{name,description,input_schema}]`, `tool_choice: auto`, `messages`. Khi `stop_reason == tool_use`: thực thi song song các `tool_use`, trả `role:user` với `tool_result{tool_use_id, content, is_error}` **ngay sau** message `tool_use`. Tối đa ~10 vòng. Key trong EncryptedSharedPreferences; timeout 15s kết nối / 120s đọc. **Nếu Operit đã đủ** (parse multi-block, echo tool_result, tool_choice) thì F chỉ còn kiểm thử; thiếu mới viết `AnthropicProvider` cắm vào `AIServiceFactory`.

### 5.7 Dadb (Module B2) `[V-06]`
`dev.mobile:dadb` (2.0.0 theo báo cáo, stable 1.2.10). Gốc **không có `pair()`** → MVP nhập tay `host:port`; nâng cấp Kadb khi cần FR-22. `Dispatchers.IO` + `.use{}` chống rò socket. Khoá ADB RSA-2048 lưu `filesDir/adbkey`; endpoint lưu DataStore. `FirstAvailableExecutor([shizuku, dadb])`, health check `echo ok` khi resume.

### 5.8 Skill (Module D) & Dashboard (Module E)
- Skill: `skill.json {name, description, commands}`; cài qua `GET api.github.com/repos/{o}/{r}/contents/skill.json` (base64) hoặc zip `…/archive/main.zip` (`ZipInputStream`, ≤2MB/entry); validate `name=[a-z0-9-]+`; mỗi command → tool `skill_<name>_<cmd>` đi qua ShellExecutor + confirm/log.
- Dashboard: NanoHTTPD trong ForegroundService; `GET /status {running,lastCmd,lastCode}`, `GET /` hiển thị log; IP lấy qua `WifiManager`. (Nếu Operit đã dùng Ktor Server → giữ Ktor — D-006.)

### 5.9 Sandbox & debug (Module G)
`debug { applicationIdSuffix = ".debug"; isDebuggable = true }`, release minify. `run-as <pkg.debug>` đọc dữ liệu **chính app** từ PC để debug; không phải tính năng in-app; không đụng app khác.

### 5.10 ADR (Architecture Decision Records)
| ADR | Quyết định | Trạng thái |
|---|---|---|
| ADR-001 | Chấp nhận Shizuku làm tầng quyền chính, đánh đổi: phải setup một lần qua Wireless Debugging, không lên Play | Accepted |
| ADR-002 | Không root, không đọc dữ liệu riêng của app khác | Accepted |
| ADR-003 | Tự định nghĩa `ToolDefinition` theo MCP, chưa kéo MCP SDK 0.x | Proposed (chờ V-07) |
| ADR-004 | Tham khảo/copy pattern từ Operit, không build nguyên khối cho spike B1 | Proposed (chờ V-01) |

---

## 6. Sổ quyết định (Decision Log) — chấm theo JEV

### 6.1 Quyết định đã chấm
| ID | Quyết định | E | F | R | C | A | **JEV** | Kết quả |
|---|---|---|---|---|---|---|---|---|
| D-001 | Kotlin làm ngôn ngữ chính | .95 | .95 | .80 | .90 | 1.0 | **0.93** | PASS |
| D-002 | Shizuku là tầng quyền chính | .85 | .85 | .80 | .85 | .95 | **0.86** | PASS |
| D-003 | Operit AI làm nền (fork/tham khảo) | .55 | .80 | .70 | .80 | .90 | **0.73 → trần 0.85** | FLAG — chờ V-01…V-03 |
| D-004 | Dadb nhập tay IP:port làm fallback MVP | .75 | .70 | .90 | .85 | .60 | **0.75** | FLAG — mặc định giữ; xem lại ở G4 |
| D-005 | Tự định nghĩa ToolDefinition, chưa dùng MCP SDK | .75 | .90 | .85 | .85 | .85 | **0.83** | FLAG — thành PASS nếu V-07 đóng |
| D-006 | NanoHTTPD cho dashboard (Ktor nếu Operit đã có) | .80 | .90 | .95 | .90 | .80 | **0.86** | PASS |
| D-007 | Không root / không đọc data app khác | .95 | .90 | .95 | .90 | .85 | **0.92** | PASS |
| D-008 | Spike B1 trước, A-lite song song, gate cứng | .90 | .95 | .95 | .95 | 1.0 | **0.94** | PASS |
| D-009 | GitHub + Releases làm kênh phân phối | .95 | .95 | .90 | .90 | .95 | **0.94** | PASS |
| D-010 | Skill = playbook lệnh JSON, chưa có JS engine | .85 | .90 | .90 | .90 | .70 | **0.86** | PASS |
| D-011 | `newProcess` cho gate, chuyển UserService/AIDL sau | .70 | .85 | .85 | .70 | .85 | **0.78** | FLAG — mặc định giữ |
| D-012 | Gộp tool: `ui_action`, `file_op` (7 tool logic) | .80 | .90 | .90 | .90 | .85 | **0.86** | PASS |

### 6.2 Quyết định bị loại (REJECT) — ghi lại để khỏi tranh luận lại
| ID | Phương án | Lý do | Cơ chế loại |
|---|---|---|---|
| X-001 | Ghost in the Droid làm nền | Cần PC/server, trái mục tiêu chạy trên máy (A ≈ 0.10) | Veto A<0.30 |
| X-002 | React Native/Expo cho lõi harness | Phải viết native module cho Shizuku/ADB, Expo managed không nhúng được (F ≈ 0.25) | Veto F<0.30 |
| X-003 | Tự nhúng giao thức ADB (SPAKE2/TLS) trong 3 ngày | Công sức lớn, trùng việc Shizuku/Kadb đã làm (C thấp, E thấp) | JEV < 0.50 |
| X-004 | Root để đọc `/data/data` app khác | Ngoài phạm vi, rủi ro brick (A ≈ 0.20) | Veto A<0.30 |

### 6.3 Câu hỏi mở cần mặc định (Owner không phải trả lời ngay)
| ID | Câu hỏi | **Mặc định** | Nếu đổi |
|---|---|---|---|
| OD-01 | Máy test & phiên bản Android | Máy A1 (API 36) | Cập nhật V-10, kiểm lại B1 |
| OD-02 | Model mặc định | Sonnet (tra tên model hiện hành khi build) | Đổi 1 hằng số cấu hình |
| OD-03 | Repo public hay private | **Private** tới khi tuân thủ LGPL xong | Public: hoàn thiện NOTICE/header trước |
| OD-04 | Tên gói (applicationId) | `com.vitngan.harness` | Đổi trước khi ký bản đầu tiên (không đổi được về sau nếu giữ dữ liệu) |

---

## 7. Cấu trúc phân rã công việc (WBS) & Backlog

Ước lượng theo ngày làm việc, lấy từ PLAN v2, sai số ±50%. "Phụ thuộc" ghi mã task.

### E0 — Foundation (M0)
| ID | Task | Làn | Phụ thuộc | Ước | Acceptance |
|---|---|---|---|---|---|
| E0-1 | Tạo repo GitHub, bảo vệ `main`, bật Actions | L4 | — | 0.1 | Repo có branch protection |
| E0-2 | Scaffold cấu trúc thư mục, LICENSE, NOTICE, `.gitignore`, `CLAUDE.md`, issue templates | L2 | E0-1 | 0.3 | PR merge; cây thư mục khớp 3.5 |
| E0-3 | CI `build.yml` (build + unit + ktlint/detekt) | L2 | E0-2 | 0.3 | CI xanh trên PR mẫu |
| E0-4 | Chuẩn bị máy: Shizuku + Wireless Debugging, tắt auth timeout, cho Shizuku chạy nền | L4 | — | 0.5 | `pingBinder` true; uid 2000 xác nhận |
| E0-5 | Nạp tài liệu: PLAN, DECISIONS, RISKS, verification registry | L2 | E0-2 | 0.2 | Có trong `docs/` |

### E1 — Spike B1 (M1) ‖ E2 — A-lite (M1b)
| ID | Task | Làn | Phụ thuộc | Ước | Acceptance |
|---|---|---|---|---|---|
| E1-1 | Dự án probe `vit-ngan-probe`: ShellResult, ShellExecutor, ShizukuShellExecutor, Activity, `ShizukuEchoTest` | L1+L2 | E0-2 | 1 | Biên dịch; có unit test cho parser/timeout |
| E1-2 | Chạy probe trên máy thật, ghi log | L4 | E1-1, E0-4 | 0.5 | Đạt 3 tiêu chí B1-PASS (mục 10.1) |
| E1-3 | Thử lỗi: dừng Shizuku, bật lại | L4 | E1-2 | 0.5 | Không crash; chạy tiếp không cài lại |
| E2-1 | Clone Operit `--recurse-submodules`, ghim tag/commit | L1+L2 | E0-2 | 0.3 | SHA ghi trong `OPERIT_NOTES.md` |
| E2-2 | Trả lời 4 câu A-lite → `OPERIT_NOTES.md` ≤3 trang | L1+L2 | E2-1 | 2.5 | Mỗi khẳng định có đường dẫn file + dòng |
| E2-3 | Owner đối chiếu ngẫu nhiên ≥5 khẳng định với source thật | L4 | E2-2 | 0.5 | Đóng V-01…V-03 hoặc báo sai lệch |

### E3 — MVP lõi (M2)
| ID | Task | Làn | Phụ thuộc | Ước | Acceptance |
|---|---|---|---|---|---|
| E3-1 | Đưa `ShellExecutor` Shizuku-first vào codebase Operit (thay 5 nhánh quyền) | L3→L2 | G1, G2 | 1 | Build; tool Operit cũ vẫn chạy |
| E3-2 | `ToolDefinition` + `toAnthropic()` + test schema | L2 | G2 | 0.5 | Unit test sinh JSON đúng |
| E3-3 | Bảy tool theo 5.4 + unit test ánh xạ/quote/whitelist | L2 | E3-1, E3-2 | 2 | AT-01…AT-07 mô phỏng xanh |
| E3-4 | `ToolRegistry.dispatch` + policy + confirm + log | L2 | E3-3 | 1 | Lệnh nguy hiểm bị chặn chờ xác nhận |
| E3-5 | Xác nhận/viết Provider Anthropic | L3→L2 | V-08 | 0.5–3 | Chat "chạy echo hello" ra `hello` |
| E3-6 | Nghiệm thu e2e G-2 trên máy thật | L4 | E3-4, E3-5 | 0.5 | Log request/response (che key) đính kèm |

### E4 — Củng cố (M3)
| ID | Task | Làn | Phụ thuộc | Ước | Acceptance |
|---|---|---|---|---|---|
| E4-1 | `DadbShellExecutor` + `FirstAvailableExecutor` + màn hình nhập IP:port | L2 | G3 | 3 | Tắt Shizuku vẫn chạy `echo` qua Dadb |
| E4-2 | Build type debug/release, ký debug | L2 | G3 | 0.5 | Cài song song được 2 bản |
| E4-3 | `UserServiceShellExecutor` (AIDL) thay `newProcess` | L2 | E1-3 | 2 | Cùng bộ test B1 xanh |

### E5 — Mở rộng (M4) — E5-1 ‖ E5-2
| ID | Task | Làn | Phụ thuộc | Ước | Acceptance |
|---|---|---|---|---|---|
| E5-1 | `SkillManager` + cài skill mẫu từ GitHub | L2 | G4 | 2 | `install/list/remove` chạy; skill thành tool |
| E5-2 | Dashboard NanoHTTPD (hoặc Ktor theo D-006) | L3→L2 | G4, V-09 | 2 | `/status` JSON + trang log truy cập từ trình duyệt |

### E6 — Trải nghiệm (M5, Track H)
| ID | Task | Làn | Phụ thuộc | Ước | Acceptance |
|---|---|---|---|---|---|
| E6-1 | Đánh giá & đơn giản hoá UI chat (chữ to, ít nút) | L3→L2 | G5 | 2 | Checklist UI được Owner duyệt |
| E6-2 | Màn hình hướng dẫn bật Shizuku (ảnh, tiếng Việt) | L2 | G5 | 1.5 | Một người không rành làm theo được |
| E6-3 | Chế độ non-tech: prompt hệ thống riêng + xác nhận ý định | L3→L2 | G5 | 1 | Kịch bản mẫu 5 yêu cầu đạt |
| E6-4 | Chụp ảnh/quay hướng dẫn trên máy người thân | L4 | E6-2 | 1 | Bộ ảnh đầy đủ |

### E7 — Phát hành (M6, Track I)
| ID | Task | Làn | Phụ thuộc | Ước | Acceptance |
|---|---|---|---|---|---|
| E7-1 | Tạo keystore, sao lưu an toàn ngoài repo | L4 | G6 | 0.2 | Có bản sao lưu; không nằm trong git |
| E7-2 | Script build/ký/release + changelog | L2 | E7-1 | 1 | APK ký cài được; Release đính kèm APK |
| E7-3 | Backup/restore JSON (FR-21) | L2 | G5 | 1.5 | Xuất/nhập khôi phục đúng chat, memory, skill |
| E7-4 | `USER_GUIDE_VI.md` (không thuật ngữ) | L3→L2 | E6-4 | 1 | Người thân đọc hiểu |
| E7-5 | Hoàn tất LGPL (header, NOTICE, README) và quyết định public/private | L2+L4 | G6 | 0.5 | Checklist LGPL đạt |

---

## 8. Sổ xác minh (Verification Registry)

Các khẳng định từ báo cáo agent **chưa được kiểm chứng trực tiếp**. Mỗi thẻ phải đóng (hoặc sửa) trước gate ghi ở cột cuối.

| ID | Khẳng định | Cách xác minh | Người | Đóng trước |
|---|---|---|---|---|
| V-01 | `AAswordman/Operit` nhánh `main` là Kotlin, LGPL-3.0, còn bảo trì; không dùng `Operit2` | Mở repo, đọc LICENSE, commit gần nhất | L4 | G2 |
| V-02 | Tool/dispatcher nằm ở `AITool.kt`, `ToolRegistration.kt`, `AIToolHandler`, `ToolExecutionManager` | Mở đúng file, kiểm tên lớp/hàm | L4 (spot-check) | G2 |
| V-03 | Shizuku nằm ở `ShizukuAuthorizer.kt`/`ShizukuInstaller.kt`; Provider Claude có `tool_use` | Grep `Shizuku`, `tool_use`, `input_schema` | L2+L4 | G2 |
| V-04 | Shizuku-API 13.1.5; `newProcess` deprecated | Tra Maven Central + README/changelog | L2 | G1 |
| V-05 | Manifest cần `API_V23` + `ShizukuProvider` với permission như 5.3 | Đối chiếu README Shizuku-API (Migration guide) | L2 | G1 |
| V-06 | Dadb 2.0.0/1.2.10, không có `pair()`; Kadb có `pair()`+mDNS và còn sống | Tra repo + release | L2 | G4 |
| V-07 | MCP Kotlin SDK còn 0.x, hay đổi API | Tra repo/release | L2 | G3 |
| V-08 | Định dạng Messages API với tool_use/tool_result như 5.6 | Đối chiếu tài liệu tool-use hiện hành | L2 | G3 |
| V-09 | Operit có sẵn Ktor Server hay không | Grep `ktor` trong `build.gradle.kts` | L2 | G4 |
| V-10 | Máy test, Android version, hành vi Shizuku trên máy đó | Đọc `Settings → About`, chạy B1 | L4 | G1 |

---

## 9. Lịch trình, Milestone & Stage Gate

Mốc theo ngày làm việc từ D0, giả định 1 Owner + agent song song (ước lượng gộp từ mục 7).

```
Tuần-ngày:  D0  D1 D2 D3 D4 | D5 ─ D10 | D11 ─ D14 | D15 ─ D19 | D20 ─ D23 | D24 ─ D26
Làn L4:     [E0-4][E1-2,E1-3][E2-3]            [E3-6]                    [E6-4]  [E7-1]
Làn L2:     [E0-2,E0-3,E0-5]
            ├─ Track 1: E1-1 ───►G1
            └─ Track 2: E2-1,E2-2 ───►G2        [E3-1..E3-5]►G3  [E4-1..3]►G4  [E5-1 ‖ E5-2]►G5  [E6]►G6  [E7]►G7
```

| Gate | Milestone | Điều kiện vào | **Exit criteria (phải đạt)** | Consult Packet gồm |
|---|---|---|---|---|
| **G0** | M0 Foundation | — | Repo + CI xanh + máy sẵn sàng + tài liệu nạp | Link repo, ảnh `pingBinder` |
| **G1** | M1 Spike B1 | G0 | **B1-PASS** (10.1) trên máy thật; V-04, V-05, V-10 đóng | Log `code=0 hello`, kết quả thử dừng/bật Shizuku |
| **G2** | M1b A-lite | G0 | `OPERIT_NOTES.md` có đường dẫn+dòng; Owner đối chiếu ≥5 điểm; V-01…V-03 đóng; D-003 chấm lại | `OPERIT_NOTES.md`, kết quả đối chiếu |
| **G3** | M2 MVP lõi | G1+G2 | **G-2 đạt**; AT-01…AT-07 xanh; confirm + log hoạt động; V-07, V-08 đóng; **tag `v0.1.0`** | Log e2e (che key), báo cáo test |
| **G4** | M3 Củng cố | G3 | Dadb fallback chạy; build debug/release song song; UserService thay `newProcess`; V-06, V-09 đóng; **tag `v0.2.0`** | Log fallback, ảnh 2 app cài song song |
| **G5** | M4 Mở rộng | G4 | Skill mẫu cài từ GitHub chạy được; dashboard truy cập được; **tag `v0.3.0`** | Demo skill, ảnh dashboard |
| **G6** | M5 Trải nghiệm | G5 | Người thân làm theo hướng dẫn bật Shizuku thành công; kịch bản non-tech đạt; **tag `v0.9.0`** | Ảnh/video hướng dẫn, kết quả kịch bản |
| **G7** | M6 Phát hành | G6 | APK ký trong GitHub Release; backup/restore đạt; LGPL checklist đạt; USER_GUIDE xong; **tag `v1.0.0`** | Link Release, checklist LGPL |

**Kịch bản FAIL tại G1** (B1 FAIL sau 2 ngày trên 2 máy: `pingBinder=false`, bị từ chối quyền, hoặc `newProcess` timeout): dừng, nộp Consult Packet, chọn một trong: (a) Dadb lên làm tầng chính, (b) đổi máy, (c) fork Operit nguyên khối. Claude chấm lại D-002/D-004 và đề xuất.

---

## 10. Chiến lược kiểm thử

### 10.1 B1-PASS (tiêu chí gate G1)
1. Nút Test log `code=0 stdout=hello` trong < 10s trên máy thật.
2. `ShizukuEchoTest` (instrumentation) xanh trên máy thật.
3. Dừng Shizuku → app báo lỗi rõ, không crash; bật lại → chạy tiếp, không cài lại.

### 10.2 Tầng kiểm thử
| Tầng | Phạm vi | Chạy ở | Khi nào |
|---|---|---|---|
| Unit (JVM) | Ánh xạ tool→lệnh, quote, whitelist/canonical path, policy xác nhận, schema JSON, parser `pm list` | CI | Mỗi PR |
| Lint/format | ktlint/detekt | CI | Mỗi PR |
| Instrumentation | `ShizukuEchoTest`, thực thi tool trên máy | Máy thật (L4) | Mỗi gate |
| Acceptance | AT-01…AT-07 (mỗi tool một kịch bản), AT-08 (e2e chat), AT-09 (fallback Dadb), AT-10 (skill), AT-11 (dashboard) | Máy thật (L4) | Gate tương ứng |

### 10.3 Acceptance mẫu
- **AT-03** `set_system_setting`: đặt `system font_scale 1.15` → `settings get` trả `1.15` → khôi phục giá trị cũ; có hỏi xác nhận.
- **AT-05** `ui_action`: mở một app, `tap` vào toạ độ biết trước, quan sát kết quả.
- **AT-08** e2e: "chạy echo hello" → model gọi `shell_exec` → câu trả lời chứa `hello`; log có request/response đã che key.

---

## 11. Sổ rủi ro (Risk Register)

Xác suất P và tác động I theo thang 1–5; điểm = P×I.

| ID | Rủi ro | P | I | Điểm | Giảm thiểu | Kích hoạt / dự phòng |
|---|---|---|---|---|---|---|
| R01 | Binder Shizuku không hoạt động/bị từ chối trên máy test | 2 | 5 | 10 | Spike B1 trước toàn bộ; gate cứng | G1 FAIL → (a)(b)(c) ở mục 9 |
| R02 | `newProcess` bị gỡ ở API mới | 3 | 3 | 9 | Dùng cho gate; làm UserService ở E4-3 | Thấy cảnh báo build → bắt đầu E4-3 sớm |
| R03 | Báo cáo A-lite sai/bịa chi tiết | 3 | 4 | 12 | Sổ V-xx; Owner spot-check; evidence cap trong JEV | Sai lệch >2/5 → làm lại A-lite thủ công |
| R04 | Operit build nặng (submodule, asset zip, chỉ arm64) | 4 | 2 | 8 | Spike B1 trên app trắng; sao chép pattern thay vì build nguyên khối | Không build được → giữ chỉ tham khảo, tự dựng khung |
| R05 | Vi phạm LGPL-3.0 khi public | 2 | 4 | 8 | Repo private mặc định; NOTICE + header; checklist E7-5 | Phát hiện thiếu → chuyển private, bổ sung |
| R06 | Dadb thiếu `pair()` → trải nghiệm kém | 4 | 2 | 8 | MVP nhập tay; Kadb sau (FR-22) | Người thân không tự làm được → ưu tiên Shizuku |
| R07 | Trôi phạm vi (thêm tính năng của Operit) | 4 | 3 | 12 | Danh sách quarantine; kiểm soát thay đổi 3.7 | Issue `change-request` bị từ chối nếu không chặn gate |
| R08 | Version thư viện trôi (Shizuku-API, Dadb, MCP SDK) | 3 | 2 | 6 | Ghim version, kiểm lúc build (V-04/06/07) | Build vỡ → sửa theo changelog |
| R09 | Chất lượng đầu ra của agent (L2) không ổn định | 3 | 3 | 9 | Task card có acceptance + stop condition; PR checklist; Consult Gate | PR không đạt → trả về kèm lỗi cụ thể |
| R10 | Shizuku phải bật lại sau khởi động máy (ma sát cho người thân) | 5 | 2 | 10 | Chấp nhận như giới hạn đã biết; màn hình hướng dẫn (E6-2) | Ghi vào USER_GUIDE |
| R11 | Mất keystore → không cập nhật được bản cài sẵn | 2 | 4 | 8 | Sao lưu ngoài repo ngay khi tạo (E7-1) | Mất → phải gỡ cài lại, mất dữ liệu app |

Rà soát risk register tại mỗi Consult Gate; rủi ro điểm ≥ 12 phải có người phụ trách và ngày kiểm tra lại.

---

## 12. Task Cards cho đợt đầu (dán thẳng vào Claude Code)

### 12.0 Mẫu Task Card
```
TASK <ID> — <tên>
Lane: L2 | Gate: G# | Branch: feature/<id>-<slug>
Bối cảnh: <2–4 dòng; trỏ tới mục trong docs/PLAN.md>
Mục tiêu: <1 câu>
Đầu vào: <file/link/phiên bản đã ghim>
Ràng buộc: <công nghệ, vùng cấm: không chạm keystore/secrets, không thêm thư viện ngoài danh sách>
Việc cần làm: <các bước đánh số>
Sản phẩm bàn giao: <file/PR>
Acceptance: <lệnh chạy được + kết quả mong đợi>
Dừng & báo khi: <điều kiện stop>
Báo cáo (bắt buộc, theo format): Đã làm | Lệnh test + kết quả thật | Giả định đã dùng | Điều chưa chắc | Rủi ro phát hiện
```

### 12.1 TASK E0-2 — Scaffold repo
- **Mục tiêu**: tạo khung repo đúng mục 3.5.
- **Làm**: tạo `app/` (module Android Kotlin trống, minSdk 26, JDK 17, Gradle wrapper), `docs/` (rỗng có README), `.github/workflows/`, `.github/ISSUE_TEMPLATE/` (task-card, decision-record, consult-packet), `LICENSE` (LGPL-3.0), `NOTICE` (ghi "Forked/derived from AAswordman/Operit (LGPL-3.0)" — để Owner xác nhận mức độ phái sinh), `.gitignore` (chặn `*.jks`, `*.keystore`, `local.properties`), `CLAUDE.md`, `README.md`.
- **Acceptance**: `./gradlew assembleDebug` chạy được; không có file bí mật.
- **Dừng & báo khi**: không chắc về nghĩa vụ LGPL — không tự kết luận pháp lý.

### 12.2 TASK E1-1 — Probe Shizuku (B1-min)
- **Mục tiêu**: app trắng `vit-ngan-probe` chạy `echo hello` qua Shizuku.
- **Đầu vào**: mục 5.2, 5.3, 10.1; version lấy từ Maven Central lúc build (V-04), đối chiếu README Shizuku-API (V-05).
- **Ràng buộc**: chỉ dùng `dev.rikka.shizuku:api` + `:provider`; không thêm thư viện khác; không dùng hidden API.
- **Làm**: `ShellResult`, `ShellExecutor`, `ShizukuShellExecutor` (exec với 2 thread đọc stream, timeout 10s + `destroy`), lỗi `ShizukuNotAvailable/NotGranted`; Activity một nút Test + vùng log, đăng ký 3 listener, theo ma trận trạng thái 5.3; `ShizukuEchoTest`; unit test cho phần không phụ thuộc Shizuku (timeout, gộp stream).
- **Acceptance**: build sạch; unit test xanh; ghi rõ cách Owner chạy instrumentation và đọc log.
- **Dừng & báo khi**: API trong tài liệu khác với phiên bản thư viện thật; manifest/permission cho lỗi build.
- **Lưu ý**: agent **không** tuyên bố "PASS" — chỉ Owner (E1-2) xác nhận trên máy thật.

### 12.3 TASK E2-2 — A-lite: đọc Operit
- **Mục tiêu**: `docs/OPERIT_NOTES.md` ≤3 trang trả lời đúng 4 câu.
- **Đầu vào**: clone `AAswordman/Operit` nhánh `main` với `--recurse-submodules`, ghim SHA (E2-1). Tuyệt đối không dùng `Operit2`.
- **Câu cần trả lời**: (1) Tool được định nghĩa ở đâu, schema dạng gì? (2) Dispatcher/vòng agent nằm ở đâu, quyền theo từng tool xử lý ra sao? (3) Shizuku được khởi tạo/xin quyền/thực thi ở đâu, có UI hướng dẫn không? (4) Tool nào giữ/sửa/bỏ cho 7 tool MVP; provider Anthropic đã đủ `tool_use` chưa; repo có dùng Ktor không.
- **Ràng buộc**: **mỗi khẳng định phải kèm đường dẫn file + số dòng + trích ≤10 dòng**; khẳng định không tìm thấy bằng chứng phải ghi "KHÔNG XÁC MINH". Không sửa code.
- **Acceptance**: Owner đối chiếu ngẫu nhiên ≥5 khẳng định đều khớp (E2-3).
- **Dừng & báo khi**: repo không clone/build được hoặc cấu trúc khác hẳn mô tả ở mục 8.

### 12.4 Thẻ kế tiếp
Các thẻ cho E3 trở đi do Claude phát hành trong **Executable Answer** tại G1/G2, vì phụ thuộc kết quả thật của B1 và A-lite — không viết trước để tránh dựa trên giả định.

---

## 13. Việc cần làm ngay (7 ngày đầu)

| Thứ tự | Việc | Làn | Ghi chú |
|---|---|---|---|
| 1 | Tạo repo GitHub (private), bật bảo vệ `main` | L4 | E0-1 |
| 2 | Giao E0-2, E0-3, E0-5 cho Claude Code | L2 | Dùng thẻ 12.1 |
| 3 | Cài Shizuku + Wireless Debugging, tắt auth timeout, cho Shizuku chạy nền | L4 | E0-4 — làm song song với bước 2 |
| 4 | Giao E1-1 (thẻ 12.2) và E2-1/E2-2 (thẻ 12.3) cùng lúc | L1+L2 | Hai track song song |
| 5 | Chạy probe trên máy, thử dừng/bật Shizuku | L4 | E1-2, E1-3 |
| 6 | Đối chiếu ≥5 khẳng định A-lite với source | L4 | E2-3 |
| 7 | **Consult Gate G1+G2**: mang log B1 và `OPERIT_NOTES.md` | Owner ↔ Claude | Nhận Executable Answer + thẻ E3 |

---

## 14. Phụ lục

### 14.1 Mẫu `CLAUDE.md` (đặt ở root repo)
```
# Dự án: Android Agent Harness
Mục tiêu: app Android (Kotlin) cho AI gọi tool qua Shizuku; chạy hoàn toàn trên máy.
Đọc trước: docs/PLAN.md (mục 3, 5), docs/DECISIONS.md.
Lệnh: ./gradlew assembleDebug | ./gradlew test | ./gradlew ktlintCheck
Quy ước: Kotlin, Conventional Commits, mỗi task một nhánh feature/<id>-<slug>, PR nhỏ.
Vùng cấm: không đọc/ghi keystore, secrets, local.properties; không thêm dependency ngoài danh sách đã duyệt.
Quy tắc: làm đúng Task Card; gặp "Dừng & báo khi" thì dừng và báo; không tuyên bố PASS cho test cần máy thật; mọi khẳng định về code có sẵn phải kèm file + dòng.
Báo cáo cuối task theo format: Đã làm | Lệnh test + kết quả | Giả định | Điều chưa chắc | Rủi ro.
```

### 14.2 Mẫu Decision Record (`docs/DECISIONS.md`)
```
## D-xxx — <tên quyết định>
Ngày | Người đề xuất | Trạng thái (PASS/FLAG/REJECT)
Phương án đã xét: A, B, C
Chấm: E=… F=… R=… C=… A=…  → JEV=…  (veto/cap nếu có)
Quyết định & lý do | Mặc định nếu FLAG | Điều kiện chấm lại
```

### 14.3 Tài liệu tham chiếu (cần kiểm tra bản hiện hành)
- Shizuku-API: https://github.com/RikkaApps/Shizuku-API
- Dadb: https://github.com/mobile-dev-inc/dadb · Kadb: https://github.com/flyfishxu/Kadb
- Operit AI: https://github.com/AAswordman/Operit
- Model Context Protocol: https://modelcontextprotocol.io
- Anthropic tool use: https://docs.claude.com/en/docs/build-with-claude/tool-use

### 14.4 Thuật ngữ
**Gate**: cổng chuyển giai đoạn, chỉ Owner đóng. **Consult Packet / Executable Answer**: gói đầu vào/đầu ra của buổi tham vấn. **Lane**: làn công việc L1–L4. **JEV**: khung chấm điểm quyết định. **FLAG/PASS/REJECT**: kết quả chấm. **Quarantine**: tính năng của Operit bị loại khỏi MVP.
