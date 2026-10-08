# CONSULT PACKET — G1 (M1 Spike B1) + G2 (M1b A-lite)

> Owner mang sang Claude để xin Executable Answer. Không tự nhảy gate.
> Ngày: 2026-10-08 | Trạng thái xin đóng: G1 + G2 cùng lúc (B1 trước, A-lite song song).

## 1. Gate xin đóng
- **G1**: B1-PASS (PLAN §10.1) — 3/3 tiêu chí trên Redmi 24069RA21C (API 36).
- **G2**: `docs/OPERIT_NOTES.md` có file:dòng + đã tự verify trực tiếp (clone SHA
  `dbf71916`, v1.12.2/51, Shizuku 13.1.5, tool_use, NanoHTTPD).

## 2. Artifacts (log thật, không tóm tắt)
- Unit: `:app:testDebugUnitTest` — 30/30 xanh (policy 4, tools 11, provider 2,
  dadb 4, skill 5, dashboard 2, nontech/backup 2).
- Device: `ShizukuEchoTest` OK (0.7s, uid 2000); `AcceptanceTest` OK (6 tests:
  shell/list-apps/file/screenshot/tap/font_scale+restore); AT-02 `pm install -r`
  tự thân thành công (process chết theo là bản chất); `E2eInstrumentTest` OK
  (chat → tool_use shell_exec → tool_result → đáp án "hello", model claude-haiku-5.5);
  `DashboardSmokeTest` OK (`/status` + trang log).
- APK: `probe/vit-ngan-probe/app/build/outputs/apk/{debug,release}/`
  (debug cài trên máy, release unsigned chờ keystore E7-1).
- Code: `probe/vit-ngan-probe/` (Shell/UserService/AIDL, tool×7, AgentCore,
  AnthropicProvider, Dadb fallback, SkillManager, Dashboard, NonTechMode, BackupManager).

## 3. Quyết định FLAG chờ ý kiến (≤3, có mặc định)
1. **D-003 Operit**: fork nguyên khối hay giữ đường "tự dựng + copy pattern"
   (hiện tại)? Mặc định: GIỮ đường hiện tại (probe đã có đủ 7 tool + provider +
   agent loop + test xanh; fork nguyên khối build nặng: submodule + asset zip,
   chỉ arm64). Đổi → tốn ~1 tuần dựng fork.
2. **D-011 newProcess→UserService**: đã bắt buộc (13.1.5 private, javap) —
   xin ghi nhận, không cần bàn.
3. **E6 UI**: giữ Compose probe hiện tại làm khung app thật hay dựng module
   `:app` mới từ đầu? Mặc định: GIỮ probe, đổi tên package ở E7-5 (OD-04).

## 4. Số liệu
- Task xong: E0-2/E0-3/E0-5, E1-1/E1-2, E2-1/E2-2, E3-2→E3-6, E4-1(code+unit),
  E4-2(build), E4-3 (xong sớm qua UserService), E5-1/E5-2, E6-3(prompt), E7-3/E7-4.
- Dở: E0-1 (repo), E3-1 (chờ Q1), E4-1 device, E6-1/2/4 full, E7-1/2/5 cuối.
- Ước lượng PLAN sai số ±50% vẫn đúng; phát hiện tốn nhất: unbind-kill race,
  pm/sdcard, cleartext, UI trắng (đều đã fix, xem DECISIONS D-014→D-018).
- Rủi ro mới: shizuku_server tự chết không cần reboot (gặp 1 lần, start lại
  bằng lệnh cũ là chạy; grant giữ nguyên) → app phải chịu được binder-dead
  (đã có listener + lỗi rõ, NFR-08).

## 5. Muốn Claude trả về (Executable Answer)
1. Verdict GO / GO-sửa / NO-GO cho G1+G2.
2. Chấm lại D-003 (và D-004/D-005 nếu ảnh hưởng).
3. Task cards E3-1/E4-1-device/E6 (điền đủ, dán thẳng Claude Code).
4. Cập nhật risk/verification registry.
