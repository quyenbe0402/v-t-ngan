# ADR-001 — Chấp nhận Shizuku làm tầng quyền chính

- Ngày: 2026-10-08 | Trạng thái: Accepted
- Quyết định: Shizuku chính, Dadb dự phòng. Đánh đổi setup Wireless Debugging 1 lần, không lên Play.
- Lý do: ADB-level đủ cho 7 tool MVP; dễ bảo trì hơn tự cài ADB; phù hợp non-tech sau onboarding.
- Hệ quả: cần màn hình hướng dẫn Việt (E6-2), xử lý mất binder không crash (NFR-08).
