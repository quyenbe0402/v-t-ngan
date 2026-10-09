# CONSULT PACKET — G1 (M1 Spike B1) + G2 (M1b A-lite)

> Owner mang sang Claude để xin Executable Answer. Không tự nhảy gate.
> Ngày: 2026-10-08 (cập nhật 2026-10-09) | Trạng thái xin đóng: G1 + G2 cùng lúc (B1 trước, A-lite song song).
> Commit: `0fac0f1 chore: initial commit — probe Android harness (B1→E6) + docs PLAN v3` (git clean).

## 1. Gate xin đóng
- **G1**: B1-PASS (PLAN §10.1) — 3/3 tiêu chí trên Redmi 24069RA21C (API 36).
- **G2**: `docs/OPERIT_NOTES.md` có file:dòng + đã tự verify trực tiếp (clone SHA
  `dbf71916`, v1.12.2/51, Shizuku 13.1.5, tool_use, NanoHTTPD).

## 2. Artifacts (log thật, không tóm tắt)
- Unit: `:app:testDebugUnitTest` — **51/51 xanh** (ChatSession 8, CommandPolicy 4,
  Context/TurnBudget 4, Dadb 4, Dashboard 2, Provider E36 3, E3Tool 15,
  NonTech/Backup 2, Skill 6, WebFetch 3).
- Device (Redmi 24069RA21C, API 36, uid 2000): `ShizukuEchoTest` OK (0.7s);
  `AcceptanceTest` OK (6 tests: shell/list-apps/file/screenshot/tap/font_scale+restore);
  AT-02 `pm install -r` PASS-by-evidence (`lastUpdateTime` đổi, echo sau đó OK —
  process instrumentation chết theo là bản chất); `E2eInstrumentTest` OK
  (chat → tool_use shell_exec → tool_result → đáp án "hello", model claude-haiku-5.5);
  `DashboardSmokeTest` OK (`/status` + trang log); thêm `E3DeviceTest` 7,
  `ShopeeFlowTest`, `InstallSearchFlowTest`, `VisionProbeTest` (xác minh D-019/D-020).
- APK: `probe/vit-ngan-probe/app/build/outputs/apk/{debug,release}/`
  (debug cài trên máy, release unsigned chờ keystore E7-1).
- Code: `probe/vit-ngan-probe/` — 45 file .kt (23 main + 10 unit + 11 device + AIDL).
  Shell/UserService/AIDL, **9 tools** (`shell_exec`, `install_app`,
  `set_system_setting`, `get_installed_apps`, `ui_action`, `file_op`,
  `screenshot` + `ui_dump` D-019 + `web_fetch` D-021), `AgentCore.runTurn`
  song song + `partition()` fix 400 duplicate tool_result, `AnthropicProvider`,
  Dadb fallback (code+unit, device-pending), `SkillManager` + `BuiltinSkills`
  (install-by-name D-020), Dashboard NanoHTTPD, `TurnBudget` (D-021),
  `NonTechMode`, `BackupManager`, `ChatSession` (phiên JSON), `ProbeActivity`
  Compose (chat + Duyệt lệnh + skill + dashboard + hướng dẫn Shizuku trong app).

## 3. Quyết định FLAG chờ ý kiến (≤3, có mặc định)
1. **D-003 Operit**: fork nguyên khối hay giữ đường "tự dựng + copy pattern"
   (hiện tại)? Mặc định: GIỮ đường hiện tại (probe đã có đủ 9 tool + provider +
   agent loop + test xanh; fork nguyên khối build nặng: submodule + asset zip,
   chỉ arm64). Đổi → tốn ~1 tuần dựng fork.
2. **D-004 Dadb fallback**: giữ Dadb-nhập-tay làm dự phòng (device-pending: adbd
   mở 5555 nhưng từ chối key lạ, không hiện dialog) hay lên Kadb/pair sớm (FR-22)?
   Mặc định: GIỮ Dadb-nhập-tay, Shizuku là đường chính; Kadb để sau v1.0.
3. **D-019→D-021 ghi nhận**: `ui_dump` (Experiential free không chuyển ảnh),
   install-by-name (Play vẽ native → dừng nhờ user bấm Install), TurnBudget +
   `web_fetch` — xin PASS chính thức để đóng G3-phạm-vi (code + unit 51/51 +
   flow thật đã verify bằng mắt người).

## 4. Số liệu
- Task xong: E0-2/E0-3/E0-5, E1-1/E1-2, E2-1/E2-2, E3-2→E3-6, E4-1(code+unit),
  E4-2(build), E4-3 (xong sớm qua UserService D-014), E5-1/E5-2, E6-3(prompt),
  E7-3/E7-4, + D-019/D-020/D-021, Compose + ChatSession (E6-1/E6-2 bước đầu).
- Dở: E0-1 (repo), E3-1 (chờ Q1), E4-1 device, E6-1/2/4 full, E7-1/2/5 cuối.
- Ước lượng PLAN sai số ±50% vẫn đúng; phát hiện tốn nhất: unbind-kill race (D-014),
  pm/sdcard staging (D-016), cleartext loopback (D-018), UI trắng → Compose,
  duplicate tool_result 400, MIUI chặn synthetic-tap, Play native trống ui_dump
  (đều đã fix/ghi nhận, xem DECISIONS D-014→D-021).
- Rủi ro mới: shizuku_server tự chết không cần reboot (gặp 1 lần, start lại
  bằng lệnh cũ là chạy; grant giữ nguyên) → app phải chịu được binder-dead
  (đã có listener + lỗi rõ, NFR-08).

## 5. Muốn Claude trả về (Executable Answer)
1. Verdict GO / GO-sửa / NO-GO cho G1+G2.
2. Chấm lại D-003 (và D-004/D-005 nếu ảnh hưởng) + xác nhận D-019→D-021.
3. Task cards E3-1/E4-1-device/E6 (điền đủ, dán thẳng Claude Code).
4. Cập nhật risk/verification registry.
