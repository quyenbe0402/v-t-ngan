# HƯỚNG DẪN SỬ DỤNG — Vịt-Ngan Harness (cho người thân)

> Con/cháu đọc phần "Cài đặt 1 lần" để làm hộ. Người thân chỉ cần đọc "Dùng hàng ngày".

## Dùng hàng ngày (cho bác)
1. Mở app **VitNgan Probe**.
2. Nói hoặc gõ yêu cầu, ví dụ: "tăng cỡ chữ lên", "đổi hình nền", "cài app Zalo".
3. App sẽ hỏi lại 1 câu xác nhận (ví dụ: "Bác muốn con tăng cỡ chữ, đúng không ạ?").
   Trả lời **đồng ý** thì app mới làm.
4. Xong app báo "Xong rồi ạ". Nếu báo "không làm được", nhờ con/cháu xem giúp.

## Việc app KHÔNG bao giờ tự làm
- Gỡ app, xóa file, khôi phục gốc (factory reset).
- Đọc tin nhắn, ảnh riêng của bác.
- Làm gì khác ngoài đúng câu bác vừa đồng ý.

## Nếu máy vừa khởi động lại
Shizuku (quyền hệ thống của app) phải bật lại sau mỗi lần tắt/mở máy:
1. Mở app **Shizuku** → bấm **Start** (lần đầu làm theo hướng dẫn ghép nối, xem dưới).
2. Mở lại VitNgan Probe dùng bình thường.

## Cài đặt 1 lần (con/cháu làm)
1. Trên điện thoại: Cài đặt → Giới thiệu → chạm 7 lần vào "Số bản dựng" để mở
   Tùy chọn nhà phát triển → bật **Gỡ lỗi USB** và **Gỡ lỗi không dây**.
2. Cắm cáp vào máy tính, chạy `adb devices` thấy máy → cài 2 file APK (debug).
3. Mở app **Shizuku** → **Start** → chọn **Start by connecting to a computer**
   → **View command** → chạy lệnh hiện ra trên máy tính.
4. Mở VitNgan Probe → bấm **Test echo** → hiện `code=0 stdout=hello` là được.
5. Bấm **Test e2e** lần đầu: khi hiện bảng xin quyền Shizuku, chọn
   **"Allow all the time"** (chỉ hỏi 1 lần duy nhất).
6. Nhập API key vào ô key (chỉ 1 lần, app không gửi key đi đâu ngoài máy chủ AI).

## Khi gặp lỗi
| Hiện tượng | Cách xử lý |
|---|---|
| `Shizuku chưa chạy` | Mở Shizuku → Start (sau khởi động lại máy) |
| `Chưa cấp quyền` | Mở Shizuku → Authorized apps → cho phép VitNgan |
| Đáp án không chứa kết quả | Kiểm tra mạng (4G/WiFi), thử lại 1 lần |
| Sau cài lại app phải cấp quyền lại | Bình thường (Android xóa quyền khi gỡ app) — làm lại bước 5 |

## An toàn
- Mọi lệnh nguy hiểm đều hỏi trước khi chạy.
- Mọi lệnh đã chạy đều ghi log (xem ở Dashboard `/status` khi cần).
- Key và dữ liệu chỉ nằm trên máy, không gửi cho ai khác ngoài máy chủ AI đã chọn.
