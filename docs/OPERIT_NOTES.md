# OPERIT_NOTES — A-lite (E2-2, verified 2026-10-08)

> Clone: `https://github.com/AAswordman/Operit` SHA `dbf71916fae9750cfdc9f9a774f5a0fee56633fb`
> (2026-09-18, release 1.12.2, shallow depth-1 tại `Temp\opencode\operit-check`).
> Tuyệt đối không dùng Operit2 (`e2314ff8`, Rust/Flutter, AGPL — kiến trúc khác).

## 0. Meta
- Path: `app/build.gradle.kts: versionCode=51, versionName="1.12.2", compileSdk=36, minSdk=26, targetSdk=34`
- `gradle/libs.versions.toml: shizuku="13.1.5", mcp-sdk="0.10.0"`
  (`shizuku-api/provider = dev.rikka.shizuku:api/provider:13.1.5`)
- `LICENSE` root: `GNU LESSER GENERAL PUBLIC LICENSE Version 3` (LGPL-3.0) — V-01 đóng 1 phần.

## 1. Tool định nghĩa ở đâu, schema dạng gì? (V-02 một phần)
- `app/src/main/java/com/ai/assistance/operit/core/tools/AIToolHandler.kt:29`
  `class AIToolHandler private constructor(...)` — registry + dispatcher thấp.
- `.../core/tools/ToolResultDataClasses.kt:30` ghi `moved from AITool.kt` → AITool tồn tại (cần mở full để lấy dòng).
- `.../api/chat/llmprovider/AIServiceFactory.kt:219` `object AIServiceFactory` — factory provider.
- Convert chuẩn hãng ở `.../llmprovider/ClaudeProvider.kt:44` `open class ClaudeProvider(` (xem §4).

## 2. Dispatcher/vòng agent + quyền per-tool?
- Test vòng agent tồn tại:
  `app/src/androidTest/.../enhance/ToolExecutionManagerTest.kt:10`
  `app/src/test/.../enhance/ToolExecutionManagerMarkupTest.kt:13`
- Cần mở `ToolExecutionManager.kt` full để xác nhận parallel/serial (E2-3 spot-check).

## 3. Shizuku init/xin quyền/thực thi + UI? (V-03 một phần)
- `.../core/tools/system/ShizukuAuthorizer.kt:15` `class ShizukuAuthorizer {`
  companion: `SHIZUKU_PACKAGE_NAME="moe.shizuku.privileged.api"`, binder/permission listeners,
  `isServiceAvailable`, `cachedConnection: ShizukuConnectionInfo(uid, binder)`.
- `.../core/tools/system/ShizukuInstaller.kt:19` `class ShizukuInstaller {`
- Thực thi: `.../core/tools/system/AndroidShellExecutor` (dùng rộng: `OperitApplication.kt:236`
  `AndroidShellExecutor.setContext(...)`, `DebuggerSystemOperationTools`, `DebuggerFileSystemTools`,
  `PhoneAgent.kt:100-101` check `ShizukuAuthorizer.isShizukuServiceRunning()/hasShizukuPermission()`).
- UI: `ui/main/components/DrawerContent.kt:86,90,94` rẽ nhánh installed/running/granted.
- `app/build.gradle.kts:736-737` `implementation(libs.shizuku.api/provider)`.

## 4. Giữ/sửa/bỏ + Anthropic + Ktor? (V-08/V-09)
- Anthropic đủ tool_use: `ClaudeProvider.kt:379 put("type","tool_use")`,
  `:396-397 tool_result` — V-08 coi như đóng, F chỉ còn test 1 vòng thật.
- Ktor: `app/build.gradle.kts:819 implementation(libs.ktor.client.okhttp)`,
  `:805 // testImplementation(libs.ktor.server.test.host)` (comment),
  có `// NanoHTTPD for local web server + implementation(libs.nanohttpd)` —
  → Operit dùng Ktor **client** + NanoHTTPD làm server → dashboard giữ NanoHTTPD (D-006 giữ). V-09 đóng.

## Verify còn lại (E2-3, Owner/L4)
- [x] V-01 (repo/main/LGPL/active): SHA + LICENSE + version đã xác minh.
- [x] V-04 (Shizuku 13.1.5): `libs.versions.toml` khớp báo cáo.
- [x] V-08/V-09 (tool_use + NanoHTTPD): đã xác minh dòng.
- [x] V-10 (máy test): `adb -s b36d068a shell` → `hello`, model `24069RA21C`, sdk `36`.
- [ ] V-02/V-03 full: Owner spot-check ≥5 điểm (mở AITool.kt, ToolRegistration.kt,
  ToolExecutionManager.kt, ShizukuAuthorizer full, ClaudeProvider tool_choice).
