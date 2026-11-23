package com.autulio.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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
    if (details == null) return

    var showDetails by remember(details) { mutableStateOf(false) }

    OutlinedButton(onClick = { showDetails = true }) {
        Icon(Icons.Outlined.Info, contentDescription = null)
        Spacer(Modifier.width(6.dp))
        Text(tr("Thông tin", "Information"))
    }

    if (showDetails) {
        GoogleSignInDetailsDialog(
            details = details,
            title = title,
            onDismiss = { showDetails = false }
        )
    }
}

@Composable
internal fun GoogleSignInDetailsDialog(
    details: GoogleSignInDetails?,
    displayName: String? = null,
    email: String? = null,
    title: String = tr("Thông tin tài khoản và thiết bị", "Account and device information"),
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(end = 4.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Text(
                    tr(
                        "Thông tin bên dưới được dùng để quản lý tài khoản, phiên đăng nhập và thiết bị truy cập AUTU-LIO.",
                        "The information below is used to manage the account, sign-in sessions, and devices accessing AUTU-LIO."
                    ),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(tr("Tài khoản", "Account"), fontWeight = FontWeight.Medium, fontSize = 12.sp)
                InfoLine(tr("Tên hiển thị", "Display name"), displayName)
                InfoLine(tr("Địa chỉ Gmail", "Gmail address"), email)

                if (details != null) {
                    Text(tr("Thông tin đăng nhập", "Sign-in information"), fontWeight = FontWeight.Medium, fontSize = 12.sp)
                    InfoLine(tr("Google ID (sub)", "Google ID (sub)"), details.googleSub)
                    InfoLine(tr("Email đã xác minh", "Email verified"), if (details.emailVerified) tr("Đúng", "Yes") else tr("Không", "No"))
                    InfoLine(tr("Tên", "Given name"), details.givenName)
                    InfoLine(tr("Họ", "Family name"), details.familyName)
                    InfoLine(tr("Trang hồ sơ", "Profile page"), details.profileUrl)
                    InfoLine(tr("Ngôn ngữ Google", "Google locale"), details.locale)
                    InfoLine(tr("Miền Google Workspace", "Google Workspace domain"), details.hostedDomain)
                    InfoLine(tr("Thời điểm đăng nhập gần nhất", "Most recent sign-in"), details.signedInAt?.let(::formatGoogleSignInEpoch))
                    InfoLine(tr("Nhãn thiết bị", "Device label"), details.deviceLabel)

                    if (details.deviceInfo?.hasCollectedValues() == true) {
                        Text(
                            tr("Thông tin kỹ thuật thiết bị", "Device technical information"),
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp
                        )
                        DeviceInfoLines(details.deviceInfo)
                    }
                } else {
                    Text(
                        tr("Chưa có dữ liệu đăng nhập bổ sung cho tài khoản này.", "No additional sign-in details are available for this account."),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(tr("Đóng", "Close"))
            }
        }
    )
}

@Composable
private fun DeviceInfoLines(info: SignInDeviceTechnicalInfo?) {
    if (info == null) return
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
private fun SignInDeviceTechnicalInfo.hasCollectedValues(): Boolean =
    listOf(
        manufacturer, model, brand, product, deviceCodeName, supportedAbis,
        androidVersion, appVersionName, localeTag, timeZoneId, networkType, networkStatus
    ).any { !it.isNullOrBlank() } ||
        listOfNotNull(
            sdkInt, appVersionCode, screenWidthPx, screenHeightPx, densityDpi
        ).isNotEmpty() ||
        networkValidated != null

@Composable
private fun InfoLine(label: String, value: String?) {
    val displayValue = value?.takeIf { it.isNotBlank() } ?: return
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
        java.text.DateFormat.SHORT,
        java.util.Locale.forLanguageTag("vi-VN")
    ).format(java.util.Date(value * 1000L))
}
