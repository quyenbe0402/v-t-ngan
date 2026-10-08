# LGPL CHECKLIST — E7-5 (quyết định public/private OD-03)

> Repo đang PRIVATE. Chỉ public khi tất cả mục ☐ thành ☑.

- [x] `LICENSE` placeholder + `NOTICE` (ghi nguồn AAswordman/Operit LGPL-3.0).
- [x] Không copy nguyên file từ Operit/Shizuku-demo (chỉ học pattern, tự viết lại);
      không còn nghĩa vụ giữ header file-by-file ở hiện tại.
- [x] Thư viện dùng qua Gradle (Shizuku-API MIT, Dadb Apache-2.0, NanoHTTPD,
      coroutines, annotation, JUnit, org.json) — giấy phép tương thích,
      không cần công bố source của chúng.
- [ ] Điền văn bản LGPL-3.0 đầy đủ vào `LICENSE` (hiện placeholder).
- [ ] Nếu sau này copy file từ Operit: giữ header LGPL từng file + ghi vào NOTICE.
- [ ] Quyết định OD-03 (private/public) + OD-04 (applicationId `com.vitngan.harness`
      đã dùng cho bản probe — chốt trước khi ký release đầu tiên).
- [ ] Release APK ký + giết bản debug khỏi máy người thân trước khi phát hành.
