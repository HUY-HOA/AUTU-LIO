package com.autulio.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.autulio.app.auth.GoogleSignInDetails
import com.autulio.app.auth.SignInDeviceTechnicalInfo

@Composable
internal fun GoogleSignInDetailsButton(
    details: GoogleSignInDetails?,
    title: String = tr("Thông tin đăng nhập và thiết bị", "Sign-in and device information")
) {
    if (details == null) return
    var showDetails by remember { mutableStateOf(false) }

    TextButton(onClick = { showDetails = true }) {
        Icon(Icons.Outlined.Info, contentDescription = null)
        Text(tr("Thông tin", "Info"))
    }

    if (showDetails) {
        AlertDialog(
            onDismissRequest = { showDetails = false },
            title = { Text(title) },
            text = {
                Column(
                    Modifier.fillMaxWidth().heightIn(max = 460.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    GoogleSignInDetailsContent(details)
                }
            },
            confirmButton = {
                TextButton(onClick = { showDetails = false }) { Text(tr("Đóng", "Close")) }
            }
        )
    }
}

@Composable
private fun GoogleSignInDetailsContent(details: GoogleSignInDetails) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(tr("Thông tin Google", "Google information"), fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, fontSize = 13.sp)
            InfoLine(tr("Email đã xác minh", "Email verified"), if (details.emailVerified) tr("Đúng", "Yes") else tr("Không", "No"))
            InfoLine(
                tr("Tên hiển thị từ Google", "Google given name"),
                listOfNotNull(details.givenName, details.familyName).joinToString(" ").takeIf { it.isNotBlank() }
            )
            InfoLine(tr("Thời điểm đăng nhập gần nhất", "Most recent sign-in"), details.signedInAt?.takeIf { it > 0L }?.let(::formatGoogleSignInEpoch))
            InfoLine(tr("Nhãn thiết bị lúc đăng nhập", "Device label at sign-in"), details.deviceLabel)

            DeviceInfoLines(details.deviceInfo)
        }
    }
}

@Composable
private fun DeviceInfoLines(info: SignInDeviceTechnicalInfo?) {
    if (info == null) return
    val fields = listOf(
        tr("Nhà sản xuất", "Manufacturer") to info.manufacturer,
        tr("Mẫu máy", "Model") to info.model,
        tr("Thương hiệu thiết bị", "Device brand") to info.brand,
        tr("Sản phẩm", "Product") to info.product,
        tr("Mã thiết bị", "Device codename") to info.deviceCodeName,
        tr("Kiến trúc CPU hỗ trợ", "Supported CPU ABIs") to info.supportedAbis,
        tr("Phiên bản Android", "Android version") to info.androidVersion,
        tr("Android API", "Android API") to info.sdkInt?.toString(),
        tr("Phiên bản AUTU-LIO", "AUTU-LIO version") to info.appVersionName,
        tr("Mã phiên bản ứng dụng", "App version code") to info.appVersionCode?.toString(),
        tr("Ngôn ngữ thiết bị", "Device locale") to info.localeTag,
        tr("Múi giờ", "Time zone") to info.timeZoneId,
        tr("Độ phân giải", "Screen resolution") to if (info.screenWidthPx != null && info.screenHeightPx != null) "\${info.screenWidthPx} × \${info.screenHeightPx} px" else null,
        tr("Mật độ màn hình", "Screen density") to info.densityDpi?.let { "\${it} dpi" },
        tr("Loại kết nối", "Connection type") to info.networkType,
        tr("Trạng thái mạng", "Network status") to info.networkStatus,
        tr("Internet đã được xác thực", "Internet validated") to info.networkValidated?.let { if (it) tr("Đúng", "Yes") else tr("Không", "No") }
    ).filter { !it.second.isNullOrBlank() }

    if (fields.isEmpty()) return
    Spacer(Modifier.height(4.dp))
    Text(tr("Thông tin kỹ thuật có ghi nhận", "Recorded technical information"), fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, fontSize = 13.sp)
    fields.forEach { (label, value) -> InfoLine(label, value) }
}

@Composable
private fun InfoLine(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    SelectionContainer {
        Text(
            "$label: $value",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatGoogleSignInEpoch(value: Long): String {
    if (value <= 0L) return ""
    return java.text.DateFormat.getDateTimeInstance(
        java.text.DateFormat.MEDIUM,
        java.text.DateFormat.SHORT
    ).format(java.util.Date(value * 1000L))
}
