package com.vitngan.harness.skill

/**
 * Skill dựng sẵn trong app (D-020). Khác skill GitHub (lệnh tĩnh): đây là RECIPE
 * từng bước dùng tool có sẵn — tổng quát cho mọi app, không vá từng trường hợp.
 */
object BuiltinSkills {

    data class Builtin(val name: String, val description: String, val recipe: String)

    private val INSTALL_BY_NAME = Builtin(
        name = "install-by-name",
        description = "Cài app theo TÊN (không cần biết package).",
        recipe = """
            Khi user yêu cầu cài app mà chỉ cho TÊN (vd "cài game free fire"):
            1. shell_exec: am start -a android.intent.action.VIEW
               -p com.android.vending -d 'market://search?q=<tên>&c=apps'
               (BẮT BUỘC -p com.android.vending: nếu không, MIUI mở nhầm sang
               kho GetApps của Xiaomi. Thay <tên> bằng từ khóa, dấu cách thành +.)
               Đợi 4-5 giây.
            2. ui_dump: đọc danh sách kết quả (tên + bounds từng dòng).
            3. Nếu chưa thấy app đúng: thử từ khóa khác (vd tên tiếng Anh) rồi ui_dump lại.
            4. Thấy đúng app: ui_action tap vào đúng bounds của nó để mở trang chi tiết.
            5. ui_dump trang chi tiết: nếu THẤY nút Install/Cài đặt thì tap (hỏi duyệt
               trước vì cài app là việc nguy hiểm); nếu ui_dump TRỐNG (Play vẽ native,
               đã xác minh) thì DỪNG và báo user tự bấm Install trên màn hình đang mở.
               Nếu chỉ thấy Open/Mở (đã cài) thì báo đã cài, không làm gì thêm.
            6. get_installed_apps với filter tên package để xác minh đã cài xong.
            7. App trả phí / yêu cầu đăng nhập / không tìm thấy: DỪNG và báo rõ, không đoán.
        """.trimIndent()
    )

    private val SHOPEE_SEARCH = Builtin(
        name = "shopee-search",
        description = "Tìm sản phẩm trên Shopee.",
        recipe = """
            Khi user yêu cầu tìm/mua hàng trên Shopee:
            1. shell_exec: am start -a android.intent.action.VIEW
               -d 'https://shopee.vn/search?keyword=<từ_khóa_dấu_+>' (đợi 4-5 giây).
            2. ui_dump: đọc tên + giá sản phẩm hiện ra.
            3. Muốn mở 1 sản phẩm: tap đúng bounds của nó (hỏi duyệt nếu là thao tác mua).
            4. Shopee vẽ bằng canvas nên ui_dump có thể trống — khi đó báo lại những gì
               xác minh được (đã mở đúng trang tìm kiếm) và hỏi user nhìn giúp.
        """.trimIndent()
    )

    fun list(): List<Builtin> = listOf(INSTALL_BY_NAME, SHOPEE_SEARCH)

    /** Chèn vào system prompt để model luôn thấy receta chung. */
    fun promptSection(): String = buildString {
        append("Kỹ năng dựng sẵn (làm ĐÚNG từng bước khi gặp yêu cầu tương ứng):\n")
        for (b in list()) {
            append("- ").append(b.name).append(": ").append(b.description).append("\n")
            append(b.recipe).append("\n")
        }
    }
}
