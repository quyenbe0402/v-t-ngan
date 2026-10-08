# ADR-002 — Không root, không đọc data app khác

- Ngày: 2026-10-08 | Trạng thái: Accepted
- Quyết định: chỉ ADB-level (uid 2000). Không root, không `/data/data` app khác.
- Lý do: ngoài phạm vi, rủi ro brick, khác biệt với app Play.
