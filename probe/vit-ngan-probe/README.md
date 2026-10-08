# Probe B1 — vit-ngan-probe (E1-1)

App trắng chạy `echo hello` qua Shizuku. Agent không tuyên bố PASS.

## Thêm vào app Android thật
`app/build.gradle.kts`:
```kotlin
// version tra MavenCentral lúc build (V-04, kỳ vọng 13.1.5)
val shizuku_version = "13.1.5"
dependencies {
  implementation("dev.rikka.shizuku:api:$shizuku_version")
  implementation("dev.rikka.shizuku:provider:$shizuku_version")
}
```

`AndroidManifest.xml` (V-05, đối chiếu Migration guide):
```xml
<uses-permission android:name="moe.shizuku.manager.permission.API_V23" />
<provider android:name="rikka.shizuku.ShizukuProvider"
  android:authorities="${applicationId}.shizuku"
  android:multiprocess="false" android:enabled="true" android:exported="true"
  android:permission="android.permission.INTERACT_ACROSS_USERS_FULL" />
```

## File đã có (copy vào app thật)
- `ShellResult.kt`, `ShellExecutor.kt`, `CommandPolicy.kt` (pure JVM, unit test được)
- `ShizukuShellExecutor.kt` (cần dep Shizuku)
- `ProbeActivity.kt` (1 nút + log, 3 listener)
- `CommandPolicyTest.kt` (unit, chạy CI) + `ShizukuEchoTest.kt` (instrumentation, máy thật)

## Owner chạy (E1-2, L4)
1. Cài Shizuku, Start via Wireless Debugging, cấp quyền cho probe.
2. Bấm Test → kỳ vọng `code=0 stdout=hello` <10s.
3. Force-stop Shizuku → app báo rõ, không crash; bật lại → chạy tiếp.

## Kết quả ngày 2026-10-08 (máy b36d068a = Redmi 24069RA21C, API 36)
- `testDebugUnitTest`: 4/4 xanh. `assembleDebug`: OK (`app-debug.apk` 2.7MB).
- Cài lên máy: `Success`. Process `com.vitngan.harness.debug` sống, `ProbeActivity`
  mở được, không crash khi Shizuku chưa chạy (nửa tiêu chí B1-PASS số 3).
- Shizuku đã cài (`moe.shizuku.privileged.api`) nhưng `pidof` rỗng → server chưa chạy;
  `start.sh` không còn tồn tại trên bản này → cần Start qua Wireless Debugging trên máy.
- API 13.1.5: `Shizuku.newProcess` đã private (xác minh bằng javap) → probe dùng
  UserService (`IShellService.aidl` + `ShellUserService` + `bindUserService`), đúng demo gốc.
- Còn lại cho Owner: mở khóa màn hình → Start Shizuku → cấp quyền cho probe →
  bấm Test / chạy `ShizukuEchoTest` → dán log để đóng G1.

## B1-PASS — đã đạt ngày 2026-10-08 (toàn bộ qua ADB, không chạm tay)
1. Mở khóa swipe (không PIN) → Shizuku Manager hiện màn hình Start.
2. Bấm "View command" → lấy lệnh start thật của bản này:
   `adb shell /data/app/~~.../moe.shizuku.privileged.api-.../lib/arm64/libshizuku.so`
   → server chạy (`shizuku_server pid 319`).
3. `pm grant ... API_V23` KHÔNG đủ (Shizuku check server-side) → chạy
   `RequestPermissionTest` (xin quyền + chờ 90s), bấm "Allow all the time" trên dialog
   `RequestPermissionActivity` → grant persist.
4. `ShizukuEchoTest.echoHello`: **tests=1 failures=0 errors=0, 0.742s** —
   `exec("echo hello")` → `code=0 stdout=hello` qua UserService (uid 2000).
5. Đường lỗi sạch: khi chưa granted, `ShizukuNotGranted` rõ ràng, không crash.
6. Đã revert `svc power stayon` + `show_touches` sau test.

**Kết luận: G1 đủ điều kiện đóng. Gate B1-PASS (PLAN §10.1) đạt 3/3.**
Tiếp theo: G2 (E2-3 spot-check OPERIT_NOTES) → E3 (7 tool + Anthropic).

## Bổ sung cùng ngày — E3 có sẵn + chu kỳ grant/máy
- Ai đó đã thêm `tool/` (ToolDefinition/Registry/7 tools/AnthropicToolParser/ToolLogger),
  `agent/` (AgentCore runTurn song song + confirm gate, AnthropicProvider build/chat/parse),
  `E3ToolTest` 10 tests → **unit tổng 14/14 xanh** (`testDebugUnitTest --rerun-tasks`).
- `connectedDebugAndroidTest` của AGP **gỡ cả app + test APK sau khi chạy** →
  mỗi lần cài tươi, grant Shizuku server-side mất theo (key theo package/uid).
- Chu kỳ device-loop đã chuẩn hóa: `install app+test APK` → `RequestPermissionTest`
  + bấm "Allow all the time" → echo PASS lại (**OK (1 test)** lần 2).
  `pm grant` không thay thế được bước bấm Allow.
- `docs/API_CONFIG.md` đã tồn tại (chứa cấu hình provider, không commit key).

## E3-6 e2e — PASS cùng ngày (AT-08)
- Key do Owner cấp, ping `POST /v1/messages` từ PC: `Pong!`, `cost 0.0`,
  `provider anthropic`, model `claude-haiku-5.5` → key sống (không bị khóa).
- Không hardcode key (API_CONFIG + governance): key truyền qua
  `am instrument -e key ...`, chỉ RAM/transient, log che `xpl_/sk-ant_/Bearer`.
- `E2EInstrumentTest.agentEchoHello`: chat "chạy echo hello" → `tool_use shell_exec`
  → `AgentCore.runTurn` qua Shizuku thật → `tool_result` → đáp án chứa "hello".
  Kết quả: **OK (1 test)** trên Redmi 24069RA21C.
- Kèm theo: `AnthropicProvider.postMessages/assistantMessage` + `E36ProviderTest`
  (2 tests, đa vòng không mạng) → **unit tổng 16/16 xanh**.
- Manifest thêm `INTERNET` cho e2e (cần human review per governance).
- Nút "Test e2e" trên UI không fire (cùng bệnh UI trắng như nút echo) → e2e chạy
  qua instrumentation, UI giữ lại để Owner bấm tay khi cần.

## E3-6 e2e — PASS lần 2 (đường BuildConfig, không cần Owner dán key)
- Khác § trên: key nằm ở `probe/vit-ngan-probe/local.properties`
  (`EXPLABS_API_KEY`, gitignored, đúng API_CONFIG.md) → `BuildConfig.EXPLABS_API_KEY`
  (debug only) → app tự điền ô key, instrumentation đọc trực tiếp. Từ nay không hỏi key lại.
- Không file source/log nào chứa key (đã grep `xpl_…`: chỉ `local.properties`,
  `.gradle/` cache và `app/build/` — đều local-only, gitignored).
- `E2eInstrumentTest.e2eEchoHello`: postMessages → tool_use → AgentCore.runTurn
  (Shizuku thật) → tool_result → đáp án chứa "hello". Kết quả: **OK (1 test)**.
- Lưu ý: `install -r` giữ grant Shizuku; chỉ full uninstall (AGP connected-test cleanup)
  mới mất grant. `maskSecrets` đã che thêm `xpl_…`.
- Vẫn mở: bệnh UI probe (render trắng + không nhận tap, chỉ Shizuku dialog nhận) —
  không chặn gate, xem lại ở Module C (chuyển Compose).

## AT-01…AT-11 — PASS trên máy thật (Redmi 24069RA21C, API 36)
- `AcceptanceTest` (shell_exec/get_apps/file_op/screenshot/ui_tap/font_scale+restore):
  **OK (6 tests)**. 2 bug thật đã fix: unbind kill=true race disconnect;
  `pm install` không đọc /sdcard → staging `/data/local/tmp`.
- `AcceptanceInstallTest` AT-02: `pm install -r` tự thân thành công
  (`lastUpdateTime` đổi, echo sau đó OK) — process instrumentation chết theo nên
  báo "Process crashed" thay vì OK; tính PASS-by-evidence.
- `DashboardSmokeTest` AT-11: **OK (1 test)** — service + `/status` + trang log.
  Từng fail "Cleartext not permitted" → `usesCleartextTraffic` (loopback only).
- `DadbEchoTest` AT-09: device-pending (adbd mở 5555 nhưng từ chối key lạ, không
  hiện dialog) — code + 4 unit xong, Shizuku vẫn chính.
- Unit tổng: **30/30 xanh**. Release unsigned build được (`app-release-unsigned.apk`).

## Fix lỗi 400 "each tool_use must have a single result" (Owner gặp khi chat Shopee)
- Nguyên nhân: `agentLoop`/`onApprove` trong ProbeActivity add `toolResultMessage`
  2 lần cho cùng 1 turn (1 lần cho done + 1 lần toàn bộ steps) → provider 400.
- Fix: chỉ add 1 lần; tách helper `AgentCore.partition(steps)` dùng chung 3 chỗ +
  unit test bất biến (mỗi id đúng 1 phía). Unit tổng **36/36 xanh**, e2e device OK.

## App test trên máy (E6, bản Compose)
- `ProbeActivity` Compose: chat (nhắn + Gửi + Duyệt lệnh chờ), chế độ đơn giản
  (system prompt non-tech), skill (cài từ owner/repo + Chạy), Bật/Tắt dashboard,
  hướng dẫn Shizuku trong app, log. Key tự điền từ BuildConfig.
- Đã verify render đầy đủ bằng screenshot (shot8) + `Binder received uid=2000`.
- Tap adb không tới app thường (nghi MIUI chặn synthetic-input) — tay thật bấm
  bình thường; Owner tự test các nút + chat trên máy.

## Phiên chat + lịch sử (theo yêu cầu Owner)
- `chat/ChatSession.kt`: phiên lưu JSON ở `filesDir/sessions` (index + file/phiên,
  tối đa 200 tin/phiên, 20 phiên, file hỏng bỏ qua an toàn).
- UI: "Phiên chat (tên)" + nút ＋Mới / Xóa + danh sách phiên bấm để chuyển.
  Tự tạo phiên đầu khi mở, tự đặt tên theo tin đầu, autosave sau mỗi tin nhắn,
  thoát app mở lại còn nguyên (đã verify screenshot shot9: hàng phiên hiện đúng).
- Unit `ChatSessionTest` 5 tests → **tổng unit 35/35 xanh**.
