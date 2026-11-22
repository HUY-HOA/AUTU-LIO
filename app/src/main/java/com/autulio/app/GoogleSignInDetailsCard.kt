package com.autulio.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.autulio.app.auth.GoogleSignInDetails
import com.autulio.app.auth.SignInDeviceTechnicalInfo

@Composable
internal fun GoogleSignInDetailsCard(
    details: GoogleSignInDetails?,
    title: String = tr("Dữ liệu đăng nhập Google và thiết bị", "Google sign-in and device data")
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            if (details == null) {
                Text(
                    tr("Máy chủ chưa có dữ liệu chi tiết cho tài khoản này.", "The server has no detailed sign-in data for this account."),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                return@Column
            }

            Text(tr("Dữ liệu Google", "Google data"), fontWeight = FontWeight.Medium, fontSize = 12.sp)
            InfoLine(tr("Google ID (sub)", "Google ID (sub)"), details.googleSub)
            InfoLine(tr("Email đã xác minh", "Email verified"), if (details.emailVerified) tr("Đúng", "Yes") else tr("Không", "No"))
            InfoLine(tr("Tên", "Given name"), details.givenName)
            InfoLine(tr("Họ", "Family name"), details.familyName)
            InfoLine(tr("Đường dẫn ảnh hồ sơ", "Profile picture URL"), details.pictureUrl)
            InfoLine(tr("Trang hồ sơ", "Profile page"), details.profileUrl)
            InfoLine(tr("Ngôn ngữ Google", "Google locale"), details.locale)
            InfoLine(tr("Miền Google Workspace", "Google Workspace domain"), details.hostedDomain)
            InfoLine(tr("Thời điểm đăng nhập gần nhất", "Most recent sign-in"), details.signedInAt?.let(::formatGoogleSignInEpoch))
            InfoLine(tr("Nhãn thiết bị", "Device label"), details.deviceLabel)

            Text(
                tr("Thông tin kỹ thuật thiết bị lúc đăng nhập", "Device technical information at sign-in"),
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp
            )
            DeviceInfoLines(details.deviceInfo)
            Text(
                tr(
                    "Trường Google tùy chọn có thể không được trả về cho mọi tài khoản. Không có dữ liệu nghĩa là Google không gửi trường đó trong lần đăng nhập này.",
                    "Optional Google fields are not returned for every account. A missing value means Google did not provide that field in this sign-in."
                ),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DeviceInfoLines(info: SignInDeviceTechnicalInfo?) {
    if (info == null) {
        Text(tr("Chưa có dữ liệu thiết bị.", "No device details are available."), fontSize = 12.sp)
        return
    }
    InfoLine(tr("Nhà sản xuất", "Manufacturer"), info.manufacturer)
    InfoLine(tr("Mẫu máy", "Model"), info.model)
    InfoLine(tr("Thương hiệu thiết bị", "Device brand"), info.brand)
    InfoLine(tr("Sản phẩm", "Product"), info.product)
    InfoLine(tr("Mã thiết bị", "Device codename"), info.deviceCodeName)
    InfoLine(tr("Kiến trúc CPU hỗ trợ", "Supported CPU ABIs"), info.supportedAbis)
    InfoLine(tr("Phiên bản Android", "Android version"), info.androidVersion)
    InfoLine(tr("Android API", "Android API"), info.sdkInt?.toString())
    InfoLine(tr("Phiên bản AUTU-LIO", "AUTU-LIO version"), info.appVersionName)
    InfoLine(tr("Mã phiên bản ứng dụng", "App version code"), info.appVersionCode?.toString())
    InfoLine(tr("Ngôn ngữ thiết bị", "Device locale"), info.localeTag)
    InfoLine(tr("Múi giờ", "Time zone"), info.timeZoneId)
    val resolution = if (info.screenWidthPx != null && info.screenHeightPx != null) {
        "${info.screenWidthPx} × ${info.screenHeightPx} px"
    } else null
    InfoLine(tr("Độ phân giải", "Screen resolution"), resolution)
    InfoLine(tr("Mật độ màn hình", "Screen density"), info.densityDpi?.let { "${it} dpi" })
    InfoLine(tr("Loại kết nối", "Connection type"), info.networkType)
    InfoLine(tr("Trạng thái mạng", "Network status"), info.networkStatus)
    InfoLine(
        tr("Kết nối Internet đã được xác thực", "Internet connection validated"),
        info.networkValidated?.let { if (it) tr("Đúng", "Yes") else tr("Không", "No") }
    )
}

@Composable
private fun InfoLine(label: String, value: String?) {
    val displayValue = value?.takeIf { it.isNotBlank() }
        ?: tr("Không được cung cấp", "Not provided")
    SelectionContainer {
        Text(
            "$label: $displayValue",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}


private fun formatGoogleSignInEpoch(value: Long): String {
    if (value <= 0L) return "—"
    return java.text.DateFormat.getDateTimeInstance(
        java.text.DateFormat.MEDIUM,
        java.text.DateFormat.SHORT
    ).format(java.util.Date(value * 1000L))
}
