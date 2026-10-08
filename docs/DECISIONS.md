# DECISIONS — Decision Log (JEV)

Nguồn: `PROJECT_PLAN_STUDIO.md` §6. Ghi thêm ở đây khi chấm lại.

## D-001 — Kotlin PASS 0.93
## D-002 — Shizuku chính PASS 0.86
## D-003 — Operit nền FLAG (chờ V-01..V-03)
## D-004 — Dadb nhập tay FLAG
## D-005 — ToolDefinition tự định nghĩa FLAG (chờ V-07)
## D-006 — NanoHTTPD (Ktor nếu Operit có) PASS 0.86
## D-007 — Không root PASS 0.92
## D-008 — B1 trước + A-lite song song PASS 0.94
## D-009 — GitHub + Releases PASS 0.94
## D-010 — Skill playbook JSON PASS 0.86
## D-011 — newProcess cho gate FLAG
## D-012 — Gộp ui_action/file_op PASS 0.86
## D-013 — Model tham vấn claude-haiku-5.5 via Experiential Free PASS
Ngày 2026-10-08 | Đề xuất: Owner | Trạng thái PASS
Dùng `claude-haiku-5.5` qua `https://api.experientiallabs.ai/v1` cho tham vấn Consult Gate.
Free đã xác minh live (`cost 0.0`, `default_effort medium`, không giữ budget số).
Chi tiết: `docs/API_CONFIG.md`. Key do Owner giữ L4, không commit.

## D-014 — UserService thay newProcess (13.1.5) PASS
Ngày 2026-10-08 | Xác minh: javap (`newProcess` private) + demo gốc + echo PASS.
`bindUserService` + AIDL riêng là đường duy nhất; unbind KHÔNG kill (kill=true race
disconnect vào lần bind sau — AT-05/06/07 từng fail, fix xong 6/6 xanh).

## D-015 — Gate xác nhận ở AgentCore, không double-gate ở executor PASS
Ngày 2026-10-08 | `ShizukuShellExecutor` chỉ thực thi; `requiresConfirm + confirmed`
nằm ở AgentCore + whitelist từng tool (nếu không, lệnh đã duyệt vẫn bị chặn).

## D-016 — Staging APK ở /data/local/tmp PASS
Ngày 2026-10-08 | `pm install` không mở được file trên /sdcard (Android mới) —
AT-02 fail "Can't open file" 2 lần; chuyển staging + whitelist, curl-dl cũng về đây.

## D-017 — Key qua BuildConfig debug (local.properties gitignored) PASS
Ngày 2026-10-08 | Theo API_CONFIG.md (local/Env, không hardcode/commit). Release dùng
EncryptedSharedPreferences (chưa làm — E7). `maskSecrets` che thêm `xpl_…`.

## D-018 — Cleartext loopback cho Dashboard PASS (ghi nhận, chờ review chung E7)
Ngày 2026-10-08 | `usesCleartextTraffic=true` vì targetSdk 37 chặn HTTP 127.0.0.1
(AT-11 fail "Cleartext not permitted", fix xong OK). Chỉ phục vụ dashboard nội bộ;
gateway ngoài vẫn HTTPS. Có thể siết lại bằng Network Security Config sau.

## Ghi nhận vận hành (không phải quyết định)
- `connectedDebugAndroidTest` gỡ app+test APK sau chạy → grant Shizuku mất theo;
  device-loop chuẩn: install → Allow (1 lần) → chạy bằng `am instrument` (không gỡ).
- `pm grant` không thay thế Allow của Shizuku (server check riêng).
- Dadb device-pending: adbd TCP mở (5555) nhưng từ chối key lạ, không hiện dialog
  auth → fallback code + unit xong, Shizuku vẫn là đường chính.
- Probe UI render trắng + không nhận tap (chỉ Shizuku dialog nhận) — cosmetic,
  e2e/AT chạy qua instrumentation; xem lại ở Module C.
- UPDATE: View thường không vẽ trên Redmi test → đã chuyển ProbeActivity sang
  Compose, render đẹp (có screenshot shot5/shot6), thêm mục hướng dẫn Shizuku
  trong app (E6-1/E6-2 bước đầu) + statusBarsPadding.
- Tap adb vẫn không tới app thường (nghi MIUI chặn synthetic-input với app bên
  thứ 3) — tay thật bấm bình thường; verify qua instrumentation, không phụ thuộc tap.
- shizuku_server tự chết không cần reboot (gặp 1 lần sau 8 ngày uptime) — start
  lại bằng lệnh libshizuku.so cũ là chạy, grant giữ nguyên (lưu ở manager DB).
- `docs/CONSULT_G1G2.md` đã soạn Consult Packet xin đóng G1+G2 (chờ Owner mang
  sang Claude lấy Executable Answer + task cards E3-1/E4-device/E6).
- Fix 400 duplicate tool_result (Owner gặp khi chat Shopee 2026-10-08):
  `AgentCore.partition()` + unit bất biến, unit 36/36, e2e device OK.
- D-019 ui_dump PASS: route Experiential free không chuyển ảnh tới model (đã chứng
  minh: ảnh 19KB đính kèm đúng chuẩn mà model vẫn bảo "không xem được") → thêm
  tool `ui_dump` (chữ + bounds, retry 3 lần + fallback focus), registry 8 tools,
  hint mặc định dạy deep link + ui_dump. Shopee flow thật: deep link → DRE results
  (xác minh bằng mắt người qua screenshot: Closeup 66.999₫, P/S 150.509₫, Eucryl
  70.400₫…). Unit 42/42.
- D-020 install-by-name PASS (giải pháp chung, không vá từng app): skill dựng sẵn
  search→đọc→chạm→cài→kiểm (market://search + ui_dump). Flow Free Fire thật:
  mở đúng trang kết quả Play (xác minh bằng mắt: Free Fire x NARUTO, 4.3★, 124M
  reviews, nút Install). 2 bài học: (1) MIUI phải pin -p com.android.vending nếu
  không mở nhầm GetApps; (2) Play vẽ native nên ui_dump trống → recipe dừng và
  nhờ user bấm Install, rồi verify bằng get_installed_apps. Unit 44/44.
- D-021 context + web (tham khảo deepseek-harness, Firecrawl/webcrawl MCP):
  TurnBudget (bỏ turn cũ nguyên vẹn + bỏ ảnh cũ, giữ cặp use/result) đã đấu vào
  vòng chat; tool web_fetch (SSRF guard + html-to-text, trang JS-nặng báo rõ).
  Unit 51/51.
