package com.autulio.app.script

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

data class AutuSyntaxLegendItem(
    val label: String,
    val color: Color
)

/**
 * Syntax coloring for AUTU Script. It changes only rendered styles; source text,
 * cursor offsets, selection ranges and copied text remain untouched.
 */
object AutuSyntaxHighlighter : VisualTransformation {
    val keywordColor = Color(0xFFC792EA)
    val commandColor = Color(0xFF82AAFF)
    val functionColor = Color(0xFF80CBC4)
    val typeColor = Color(0xFFFFCB6B)
    val literalColor = Color(0xFFFFB86C)
    val identifierColor = Color(0xFFE0E6F0)
    val stringColor = Color(0xFFC3E88D)
    val numberColor = Color(0xFFF78C6C)
    val commentColor = Color(0xFF7F8799)

    val legendItems = listOf(
        AutuSyntaxLegendItem("Từ khóa", keywordColor),
        AutuSyntaxLegendItem("Lệnh Android", commandColor),
        AutuSyntaxLegendItem("Hàm", functionColor),
        AutuSyntaxLegendItem("Kiểu dữ liệu", typeColor),
        AutuSyntaxLegendItem("Giá trị", literalColor),
        AutuSyntaxLegendItem("Tên biến/hàm", identifierColor),
        AutuSyntaxLegendItem("Chuỗi", stringColor),
        AutuSyntaxLegendItem("Số", numberColor),
        AutuSyntaxLegendItem("Ghi chú", commentColor)
    )

    private val keywords = setOf(
        "BIẾN", "HÀM", "NẾU", "NGƯỢC_LẠI_NẾU", "NGƯỢC_LẠI",
        "LẶP", "LẶP_KHI", "LẶP_ĐẾN_KHI", "LẶP_MỖI", "TRONG",
        "CHỜ_ĐẾN_KHI", "THOÁT_LẶP", "BỎ_QUA_LƯỢT", "TRẢ_VỀ",
        "THỬ", "BẮT_LỖI", "LUÔN_THỰC_HIỆN", "VÀ", "HOẶC", "KHÔNG"
    )

    private val literals = setOf("ĐÚNG", "SAI", "RỖNG")

    private val types = setOf("SỐ", "CHUỖI", "LUẬN_LÝ", "DANH_SÁCH", "BẢN_ĐỒ", "BẤT_KỲ")

    private val androidCommands = setOf(
        "NHẤN", "NHẤN_GIỮ", "NHẤN_ĐA_ĐIỂM", "VUỐT", "VUỐT_ĐA_ĐIỂM",
        "CHỜ", "MỞ_ỨNG_DỤNG", "QUAY_LẠI", "VỀ_MÀN_HÌNH_CHÍNH",
        "MỞ_ỨNG_DỤNG_GẦN_ĐÂY", "MỞ_THANH_THÔNG_BÁO",
        "MỞ_BẢNG_CÀI_ĐẶT_NHANH", "MỞ_HỘP_THOẠI_NGUỒN",
        "KHÓA_MÀN_HÌNH", "BẬT_TẮT_CHIA_MÀN_HÌNH",
        // Legacy spellings remain colored correctly when editing older saved scripts.
        "CHẠM", "CHẠM_ĐA_ĐIỂM"
    )

    private val builtInFunctions = setOf(
        "SỐ", "CHUỖI", "LÀM_TRÒN", "CĂN_BẬC_HAI", "LÀM_TRÒN_XUỐNG", "LÀM_TRÒN_LÊN",
        "SIN", "COS", "TAN", "SỐ_NGẪU_NHIÊN", "TUYỆT_ĐỐI", "ĐỘ_DÀI",
        "CHỮ_HOA", "CHỮ_THƯỜNG", "CẮT_KHOẢNG_TRẮNG", "CHỨA_CHUỖI",
        "BẮT_ĐẦU_BẰNG", "KẾT_THÚC_BẰNG", "THAY_THẾ", "TÁCH_CHUỖI", "NỐI_CHUỖI",
        "KÝ_TỰ_TẠI", "CẮT_CHUỖI", "TÌM_VỊ_TRÍ_TRONG_CHUỖI", "KIỂM_TRA_RỖNG",
        "NHỎ_NHẤT", "LỚN_NHẤT",
        "SẮP_XẾP_DANH_SÁCH", "ĐẢO_NGƯỢC_DANH_SÁCH", "LỌC_DANH_SÁCH",
        "TÌM_VỊ_TRÍ_PHẦN_TỬ_TRONG_DANH_SÁCH",
        "LẤY_PHẦN_TRĂM_PIN", "KIỂM_TRA_ĐANG_SẠC", "KIỂM_TRA_KẾT_NỐI_MẠNG",
        "KIỂM_TRA_MÀN_HÌNH_ĐANG_SÁNG", "KIỂM_TRA_ỨNG_DỤNG_ĐÃ_CÀI",
        "KIỂM_TRA_ỨNG_DỤNG_ĐANG_MỞ",
        "CHIỀU_RỘNG_MÀN_HÌNH", "CHIỀU_CAO_MÀN_HÌNH", "THỜI_GIAN_MS",
        "CHIỀU_RỘNG_VÙNG_AN_TOÀN", "CHIỀU_CAO_VÙNG_AN_TOÀN",
        "LỀ_AN_TOÀN_TRÁI", "LỀ_AN_TOÀN_TRÊN", "LỀ_AN_TOÀN_PHẢI", "LỀ_AN_TOÀN_DƯỚI",
        "CÓ_THỂ_XÁC_ĐỊNH_VÙNG_KHUYẾT_MÀN_HÌNH", "CÓ_VÙNG_KHUYẾT_MÀN_HÌNH",
        "SỐ_VÙNG_KHUYẾT_MÀN_HÌNH", "TỌA_ĐỘ_VÙNG_KHUYẾT_MÀN_HÌNH",
        "GHI_NHẬT_KÝ", "IN", "THÔNG_BÁO", "LẤY_ÂM_LƯỢNG", "LẤY_ĐỘ_SÁNG",
        "RUNG", "ĐẶT_ÂM_LƯỢNG", "ĐẶT_ĐỘ_SÁNG", "CHỤP_ẢNH_MÀN_HÌNH",
        "TẠO_DANH_SÁCH", "LẤY_PHẦN_TỬ_TỪ_DANH_SÁCH",
        "THÊM_PHẦN_TỬ_VÀO_DANH_SÁCH", "ĐẶT_PHẦN_TỬ_TRONG_DANH_SÁCH",
        "XÓA_PHẦN_TỬ_KHỎI_DANH_SÁCH", "KIỂM_TRA_PHẦN_TỬ_TRONG_DANH_SÁCH",
        "TẠO_BẢN_ĐỒ", "LẤY_GIÁ_TRỊ_TỪ_BẢN_ĐỒ", "ĐẶT_GIÁ_TRỊ_TRONG_BẢN_ĐỒ",
        "XÓA_KHÓA_KHỎI_BẢN_ĐỒ", "KIỂM_TRA_KHÓA_TRONG_BẢN_ĐỒ",
        "LẤY_DANH_SÁCH_KHÓA_TỪ_BẢN_ĐỒ",
        "THỜI_GIAN_ISO", "PHÂN_TÍCH_THỜI_GIAN_ISO", "ĐỊNH_DẠNG_THỜI_GIAN",
        "PHÂN_TÍCH_JSON", "CHUYỂN_JSON",
        "ĐỌC_VĂN_BẢN_MÀN_HÌNH", "ĐỌC_CẤU_TRÚC_VĂN_BẢN_MÀN_HÌNH",
        "CHI_TIẾT_MÃ_QR_MÀN_HÌNH", "CHI_TIẾT_MÃ_VẠCH_MÀN_HÌNH",
        "HÃNG_SẢN_XUẤT", "MẪU_THIẾT_BỊ", "PHIÊN_BẢN_ANDROID", "MỨC_API_ANDROID",
        "MÀN_HÌNH_BỊ_KHÓA", "THIẾT_BỊ_ĐƯỢC_BẢO_MẬT",
        "CHIỀU_RỘNG_DP", "CHIỀU_CAO_DP", "TỶ_LỆ_KHUNG_HÌNH", "HƯỚNG_MÀN_HÌNH",
        "XOAY_MÀN_HÌNH", "GÓC_XOAY_MÀN_HÌNH", "GÓI_ỨNG_DỤNG_ĐANG_MỞ", "MẬT_ĐỘ_MÀN_HÌNH",
        "DP_SANG_PIXEL", "PIXEL_SANG_DP", "TỌA_ĐỘ_X_THEO_TỶ_LỆ", "TỌA_ĐỘ_Y_THEO_TỶ_LỆ",
        "TỶ_LỆ_X_THEO_TỌA_ĐỘ", "TỶ_LỆ_Y_THEO_TỌA_ĐỘ",
        "TỌA_ĐỘ_X_VÙNG_AN_TOÀN", "TỌA_ĐỘ_Y_VÙNG_AN_TOÀN",
        "TỶ_LỆ_X_VÙNG_AN_TOÀN", "TỶ_LỆ_Y_VÙNG_AN_TOÀN",
        "TỌA_ĐỘ_TỪ_HƯỚNG_GỐC", "TỌA_ĐỘ_VỀ_HƯỚNG_GỐC",
        "KIỂM_TRA_CHỮ_TRÊN_MÀN_HÌNH", "TÌM_VỊ_TRÍ_CHỮ",
        "TÌM_THÀNH_PHẦN_VĂN_BẢN", "NHẤN_THÀNH_PHẦN_VĂN_BẢN",
        "NHẬP_VĂN_BẢN_VÀO_THÀNH_PHẦN", "TÌM_TỌA_ĐỘ_THÀNH_PHẦN",
        "TÌM_THÀNH_PHẦN_THEO_ID", "NHẤN_THÀNH_PHẦN_THEO_ID",
        "NHẬP_VĂN_BẢN_THEO_ID", "CUỘN_XUỐNG", "CUỘN_LÊN",
        "ĐỌC_VĂN_BẢN_TRONG_VÙNG_MÀN_HÌNH", "KIỂM_TRA_CHỮ_TRONG_VÙNG_MÀN_HÌNH",
        "TÌM_VỊ_TRÍ_CHỮ_TRONG_VÙNG_MÀN_HÌNH", "NHẬN_DIỆN_NHÃN_ẢNH_MÀN_HÌNH",
        "KIỂM_TRA_NHÃN_ẢNH_TRÊN_MÀN_HÌNH", "QUÉT_MÃ_QR_MÀN_HÌNH",
        "QUÉT_MÃ_VẠCH_MÀN_HÌNH", "PHÁT_HIỆN_VẬT_THỂ_MÀN_HÌNH",
        "KIỂM_TRA_VẬT_THỂ_TRÊN_MÀN_HÌNH", "TÌM_TỌA_ĐỘ_VẬT_THỂ",
        "ĐẾM_VẬT_THỂ_MÀN_HÌNH", "LẤY_MÀU_TẠI_TỌA_ĐỘ_MÀN_HÌNH",
        "KIỂM_TRA_MÀU_TRÊN_MÀN_HÌNH", "TÌM_VỊ_TRÍ_MÀU_TRÊN_MÀN_HÌNH",
        "TÍNH_TỶ_LỆ_MÀU_TRONG_VÙNG_MÀN_HÌNH",
        "ĐỌC_TỆP", "GHI_TỆP", "KIỂM_TRA_TỆP_TỒN_TẠI", "XÓA_TỆP",
        "TẠO_THƯ_MỤC", "LIỆT_KÊ_TỆP", "ĐỌC_JSON_TỆP", "GHI_JSON_TỆP",
        "PHÂN_TÍCH_CSV", "CHUYỂN_CSV", "GỬI_YÊU_CẦU_HTTP",
        "TẢI_LÊN_TỆP_HTTP", "TẢI_XUỐNG_TỆP_HTTP", "CHỌN_TỆP", "LƯU_TỆP",
        "SQL_THỰC_THI", "SQL_TRUY_VẤN", "DI_TRÚ_CƠ_SỞ_DỮ_LIỆU",
        "PHIÊN_BẢN_CƠ_SỞ_DỮ_LIỆU", "KIỂM_TRA_TOÀN_VẸN_CƠ_SỞ_DỮ_LIỆU",
        "HIỂN_THỊ_CHỮ", "VẼ_KHUNG", "VẼ_HÌNH_TRÒN", "VẼ_ĐƯỜNG",
        "VẼ_MŨI_TÊN", "XÓA_ĐÁNH_DẤU_MÀN_HÌNH",
        // Legacy spellings stay recognizable in existing saved scripts.
        "PIN", "PHẦN_TRĂM_PIN", "ĐANG_SẠC", "CÓ_MẠNG", "MÀN_HÌNH_SÁNG",
        "ỨNG_DỤNG_ĐÃ_CÀI", "ỨNG_DỤNG_ĐANG_MỞ", "ÂM_LƯỢNG", "ĐỘ_SÁNG",
        "MÀN_HÌNH_CHỨA_CHỮ", "MÀN_HÌNH_CHỨA_CHỮ_TRONG_VÙNG",
        "MÀN_HÌNH_CHỨA_NHÃN_ẢNH", "MÀN_HÌNH_CÓ_VẬT_THỂ", "MÀN_HÌNH_CÓ_MÀU",
        "TÌM_VỊ_TRÍ", "ĐỌC_VĂN_BẢN_VÙNG", "TÌM_VỊ_TRÍ_CHỮ_TRONG_VÙNG",
        "MÀU_TẠI", "TÌM_VỊ_TRÍ_MÀU", "TỶ_LỆ_MÀU_TRONG_VÙNG",
        "LẤY_PHẦN_TỬ", "THÊM_PHẦN_TỬ", "ĐẶT_PHẦN_TỬ", "XÓA_PHẦN_TỬ",
        "CÓ_PHẦN_TỬ", "TÌM_PHẦN_TỬ", "SẮP_XẾP", "ĐẢO_NGƯỢC", "LỌC",
        "LẤY_GIÁ_TRỊ", "ĐẶT_GIÁ_TRỊ", "XÓA_KHÓA", "CÓ_KHÓA", "DANH_SÁCH_KHÓA",
        "LƯU_ẢNH_MÀN_HÌNH", "TỆP_TỒN_TẠI", "GỬI_TỆP_HTTP", "TẢI_TỆP_HTTP"
    )


    override fun filter(text: AnnotatedString): TransformedText {
        val source = text.text
        val builder = AnnotatedString.Builder()
        builder.append(source)

        var index = 0
        while (index < source.length) {
            when {
                source[index] == '/' && source.getOrNull(index + 1) == '/' -> {
                    val start = index
                    while (index < source.length && source[index] != '\n' && source[index] != '\r') {
                        index++
                    }
                    addStyle(builder, commentColor, start, index)
                }

                source[index] == '"' -> {
                    val start = index
                    index++
                    while (index < source.length) {
                        when (source[index]) {
                            '\\' -> index = (index + 2).coerceAtMost(source.length)
                            '"' -> {
                                index++
                                break
                            }
                            '\n', '\r' -> break
                            else -> index++
                        }
                    }
                    addStyle(builder, stringColor, start, index)
                }

                source[index].isDigit() -> {
                    val start = index
                    while (index < source.length && source[index].isDigit()) index++
                    if (index + 1 < source.length && source[index] == '.' && source[index + 1].isDigit()) {
                        index++
                        while (index < source.length && source[index].isDigit()) index++
                    }
                    addStyle(builder, numberColor, start, index)
                }

                source[index].isLetter() || source[index] == '_' -> {
                    val start = index
                    while (index < source.length && (source[index].isLetterOrDigit() || source[index] == '_')) {
                        index++
                    }
                    val token = source.substring(start, index)
                    val isCall = nextNonWhitespace(source, index) == '('
                    val color = when {
                        token in keywords -> keywordColor
                        token in literals -> literalColor
                        token in androidCommands -> commandColor
                        token in types && !isCall -> typeColor
                        token in builtInFunctions && isCall -> functionColor
                        isCall -> functionColor
                        else -> identifierColor
                    }
                    addStyle(builder, color, start, index)
                }

                else -> index++
            }
        }

        return TransformedText(builder.toAnnotatedString(), OffsetMapping.Identity)
    }

    private fun nextNonWhitespace(source: String, start: Int): Char? {
        var index = start
        while (index < source.length && source[index].isWhitespace()) index++
        return source.getOrNull(index)
    }

    private fun addStyle(builder: AnnotatedString.Builder, color: Color, start: Int, end: Int) {
        if (start < end) {
            builder.addStyle(SpanStyle(color = color), start, end)
        }
    }
}
