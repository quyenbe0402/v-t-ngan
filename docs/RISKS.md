# RISKS — Risk Register (rút gọn từ PLAN §11)

| ID | Rủi ro | P×I | Giảm thiểu |
|---|---|---|---|
| R01 | Binder Shizuku fail | 2×5=10 | Spike B1 trước, gate cứng G1 |
| R02 | newProcess bị gỡ | 3×3=9 | Gate dùng tạm, E4-3 UserService sau |
| R03 | A-lite bịa chi tiết | 3×4=12 | V-xx + Owner spot-check ≥5 điểm |
| R04 | Operit build nặng | 4×2=8 | App trắng + copy pattern |
| R05 | Vi phạm LGPL | 2×4=8 | Private mặc định, checklist E7-5 |
| R06 | Dadb thiếu pair() | 4×2=8 | Nhập tay MVP, Kadb sau |
| R07 | Trôi phạm vi | 4×3=12 | Quarantine + change-request |
| R08 | Version trôi | 3×2=6 | Ghim version lúc build |
| R09 | Agent L2 thiếu ổn định | 3×3=9 | Task card acceptance + stop condition |
| R10 | Shizuku bật lại sau reboot | 5×2=10 | Hướng dẫn E6-2, ghi USER_GUIDE |
| R11 | Mất keystore | 2×4=8 | Sao lưu ngoài repo (E7-1) |

Rà soát mỗi Consult Gate. Điểm ≥12 phải có người + ngày kiểm tra lại.
