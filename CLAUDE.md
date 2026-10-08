# Dự án: Android Agent Harness (Vịt-Ngan Harness)

App Android (Kotlin) cho AI gọi tool qua Shizuku; chạy hoàn toàn trên máy.

- Tài liệu chính: `PROJECT_PLAN_STUDIO.md` (v3.0, canonical).
- Đọc trước: `PROJECT_PLAN_STUDIO.md` mục 3, 5 + `docs/DECISIONS.md`.
- Lệnh: `./gradlew assembleDebug` | `./gradlew test` | `./gradlew ktlintCheck`
- Quy ước: Kotlin, Conventional Commits, mỗi task một nhánh `feature/<id>-<slug>`, PR nhỏ.
- Vùng cấm: không đọc/ghi keystore, secrets, `local.properties`; không thêm dependency ngoài danh sách đã duyệt.
- Quy tắc: làm đúng Task Card; gặp "Dừng & báo khi" thì dừng và báo; không tuyên bố PASS cho test cần máy thật; mọi khẳng định về code có sẵn phải kèm file + dòng.
- Báo cáo cuối task theo format: Đã làm | Lệnh test + kết quả | Giả định | Điều chưa chắc | Rủi ro.

Gốc tham khảo: AAswordman/Operit (LGPL-3.0) — xem `NOTICE`.
