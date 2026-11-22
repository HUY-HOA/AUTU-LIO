package com.autulio.app

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.foundation.text.selection.SelectionContainer
import com.autulio.app.accessibility.AutoClickAccessibilityService
import com.autulio.app.auth.AuthRepository
import com.autulio.app.auth.AutuPackageRecord
import com.autulio.app.auth.EncryptedAutuPackageDecryptor
import com.autulio.app.auth.CustomerAccountOverview
import com.autulio.app.auth.CustomerRecord
import com.autulio.app.auth.DeviceRecord
import com.autulio.app.auth.LicenseRecord
import com.autulio.app.auth.SecureDeviceIdentityStore
import com.autulio.app.BuildConfig
import com.autulio.app.script.ScriptCompiler
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
internal fun CustomersAdminScreen(accessToken: String?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val repository = remember(context) { AuthRepository(context) }
    val scope = rememberCoroutineScope()
    var customers by remember { mutableStateOf<List<CustomerRecord>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var target by remember { mutableStateOf<CustomerRecord?>(null) }
    var planCode by remember { mutableStateOf("MONTHLY") }
    var planMenuExpanded by remember { mutableStateOf(false) }
    var durationDays by remember { mutableStateOf("30") }
    var maxDevices by remember { mutableStateOf("1") }
    var saving by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf("") }

    suspend fun refresh() {
        if (accessToken.isNullOrBlank()) return
        loading = true
        error = ""
        try {
            customers = repository.listAdminCustomers(accessToken)
        } catch (exception: Exception) {
            error = localizedErrorMessage(exception, tr("Không tải được danh sách khách hàng.", "Could not load customers."))
        } finally {
            loading = false
        }
    }

    LaunchedEffect(accessToken) { refresh() }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Heading(tr("Khách hàng", "Customers"), tr("Tài khoản thật đã đăng nhập vào AUTU-LIO", "Real accounts that have signed in to AUTU-LIO"))
            }
            IconButton(onClick = { scope.launch { refresh() } }, enabled = !loading && !accessToken.isNullOrBlank()) {
                Icon(Icons.Outlined.Refresh, contentDescription = tr("Làm mới", "Refresh"))
            }
        }
        if (accessToken.isNullOrBlank()) {
            LiveManagementUnavailable()
        } else {
            if (notice.isNotBlank()) Text(notice, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
            if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            if (loading && customers.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else if (customers.isEmpty()) {
                EmptyState(Icons.Outlined.VerifiedUser, tr("Chưa có khách hàng", "No customers yet"), tr("Tài khoản sẽ xuất hiện sau khi người dùng đăng nhập bằng Google.", "Accounts appear after users sign in with Google."))
            } else {
                Text(tr("Tổng tài khoản: ${customers.size}", "Total accounts: ${customers.size}"), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                customers.forEach { customer ->
                    CardBox {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(customer.displayName ?: customer.email, fontWeight = FontWeight.SemiBold)
                                Text(customer.email, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                            }
                            Text(
                                statusText(customer.status),
                                color = if (customer.status == "ACTIVE") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        GoogleSignInDetailsCard(
                            details = customer.googleSignIn,
                            title = tr("Dữ liệu Google và lần đăng nhập gần nhất", "Google data and most recent sign-in")
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            AdminIconValue(Icons.Outlined.Devices, "Thiết bị đã cấp quyền", customer.activeDeviceCount.toString())
                            AdminIconValue(Icons.Outlined.AccessTime, "Đăng nhập gần nhất", formatEpoch(customer.lastLoginAt))
                        }
                        AdminPresenceInfo(customer.presenceSeenAt, customer.appForegroundAt, customer.runtimeState, customer.status == "ACTIVE")
                        if (customer.licenseId != null) {
                            AdminIconValue(
                                Icons.Outlined.VerifiedUser,
                                "Giấy phép",
                                "${customer.planCode?.let(::planText) ?: "—"} · ${customer.licenseStatus?.let(::statusText) ?: "—"}"
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                AdminIconValue(Icons.Outlined.CalendarToday, "Hết hạn", formatEpoch(customer.licenseExpiresAt))
                                AdminIconValue(Icons.Outlined.Devices, "Giới hạn thiết bị", (customer.maxDevices ?: 1).toString())
                            }
                        } else {
                            Text(tr("Chưa có giấy phép sử dụng", "No usage license"), color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                            Button(
                                onClick = {
                                    target = customer
                                    planCode = "MONTHLY"
                                    planMenuExpanded = false
                                    durationDays = "30"
                                    maxDevices = "1"
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = customer.status == "ACTIVE"
                            ) { Text(tr("Cấp giấy phép", "Issue license")) }
                        }
                    }
                }
            }
        }
    }

    target?.let { customer ->
        AlertDialog(
            onDismissRequest = { if (!saving) target = null },
            title = { Text(tr("Cấp giấy phép", "Issue license")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(customer.email, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Box(Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { planMenuExpanded = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Loại gói: ${planText(planCode)}")
                        }
                        DropdownMenu(
                            expanded = planMenuExpanded,
                            onDismissRequest = { planMenuExpanded = false }
                        ) {
                            listOf(
                                "WEEKLY",
                                "MONTHLY",
                                "QUARTERLY",
                                "YEARLY",
                                "LIFETIME",
                                "TRIAL"
                            ).forEach { code ->
                                DropdownMenuItem(
                                    text = { Text(planText(code)) },
                                    onClick = {
                                        planCode = code
                                        planMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                    OutlinedTextField(durationDays, { durationDays = it.filter(Char::isDigit).take(4) }, label = { Text(tr("Thời hạn (ngày)", "Duration (days)")) }, singleLine = true)
                    OutlinedTextField(maxDevices, { maxDevices = it.filter(Char::isDigit).take(2) }, label = { Text(tr("Số thiết bị tối đa", "Maximum devices")) }, singleLine = true)
                    Text(tr("Giấy phép chỉ được áp dụng sau khi máy chủ lưu thành công.", "The license becomes active only after the server confirms it."), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !saving && planCode.matches(Regex("[A-Z0-9_-]{2,32}")) &&
                        (durationDays.toIntOrNull() ?: 0) in 1..3650 &&
                        (maxDevices.toIntOrNull() ?: 0) in 1..20,
                    onClick = {
                        val token = accessToken ?: return@TextButton
                        scope.launch {
                            saving = true
                            try {
                                val expiry = System.currentTimeMillis() / 1000 + durationDays.toLong() * 86400
                                repository.createCustomerLicense(token, customer.userId, planCode, expiry, maxDevices.toInt())
                                notice = tr("Đã cấp giấy phép cho ${customer.email}.", "License issued to ${customer.email}.")
                                target = null
                                refresh()
                            } catch (exception: Exception) {
                                error = localizedErrorMessage(exception, tr("Không cấp được giấy phép.", "Could not issue license."))
                            } finally {
                                saving = false
                            }
                        }
                    }
                ) { Text(if (saving) tr("Đang lưu…", "Saving…") else tr("Xác nhận cấp", "Issue license")) }
            },
            dismissButton = { TextButton(onClick = { target = null }, enabled = !saving) { Text(tr("Hủy", "Cancel")) } }
        )
    }
}

@Composable
internal fun LicensesAdminScreen(accessToken: String?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val repository = remember(context) { AuthRepository(context) }
    val scope = rememberCoroutineScope()
    var licenses by remember { mutableStateOf<List<LicenseRecord>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var notice by remember { mutableStateOf("") }

    suspend fun refresh() {
        if (accessToken.isNullOrBlank()) return
        loading = true
        error = ""
        try {
            licenses = repository.listAdminLicenses(accessToken)
        } catch (exception: Exception) {
            error = localizedErrorMessage(exception, tr("Không tải được giấy phép.", "Could not load licenses."))
        } finally {
            loading = false
        }
    }

    LaunchedEffect(accessToken) { refresh() }

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Heading(tr("Giấy phép", "Licenses"), tr("Cấp, tạm dừng, gia hạn trạng thái hoặc thu hồi", "Issue, pause, change status or revoke access"))
            }
            IconButton(onClick = { scope.launch { refresh() } }, enabled = !loading && !accessToken.isNullOrBlank()) {
                Icon(Icons.Outlined.Refresh, contentDescription = tr("Làm mới", "Refresh"))
            }
        }
        if (accessToken.isNullOrBlank()) LiveManagementUnavailable()
        else {
            if (notice.isNotBlank()) Text(notice, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
            if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            if (loading && licenses.isEmpty()) Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            else if (licenses.isEmpty()) EmptyState(Icons.Outlined.VerifiedUser, tr("Chưa có giấy phép", "No licenses yet"), tr("Chọn khách hàng để cấp giấy phép trong mục Khách hàng.", "Select a customer to issue a license in Customers."))
            else licenses.forEach { license ->
                CardBox {
                    Text(license.email, fontWeight = FontWeight.SemiBold)
                    Text("${planText(license.planCode)} · ${statusText(license.status)}", fontSize = 13.sp)
                    Text(
                        tr("Thời hạn: ${formatEpoch(license.startsAt)} – ${formatEpoch(license.expiresAt)}", "Period: ${formatEpoch(license.startsAt)} – ${formatEpoch(license.expiresAt)}"),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        tr("${license.activeDeviceCount}/${license.maxDevices} thiết bị đang hoạt động", "${license.activeDeviceCount}/${license.maxDevices} active devices"),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = {
                            val token = accessToken ?: return@OutlinedButton
                            scope.launch {
                                try {
                                    val renewedExpiry = maxOf(
                                        license.expiresAt,
                                        System.currentTimeMillis() / 1000
                                    ) + 30L * 86400L
                                    repository.updateCustomerLicense(
                                        token,
                                        license.id,
                                        "ACTIVE",
                                        renewedExpiry,
                                        license.maxDevices
                                    )
                                    notice = tr("Đã gia hạn giấy phép thêm 30 ngày.", "License extended by 30 days.")
                                    refresh()
                                } catch (exception: Exception) {
                                    error = localizedErrorMessage(exception, tr("Không thể gia hạn giấy phép.", "Could not renew the license."))
                                }
                            }
                        },
                        enabled = license.status != "REVOKED",
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(tr("Gia hạn thêm 30 ngày", "Extend by 30 days")) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            modifier = Modifier.weight(1f),
                            enabled = license.status != "REVOKED",
                            onClick = {
                                val token = accessToken ?: return@OutlinedButton
                                scope.launch {
                                    try {
                                        val next = if (license.status == "PAUSED") "ACTIVE" else "PAUSED"
                                        repository.updateCustomerLicense(token, license.id, next, license.expiresAt, license.maxDevices)
                                        notice = tr("Đã cập nhật giấy phép.", "License updated.")
                                        refresh()
                                    } catch (exception: Exception) {
                                        error = localizedErrorMessage(exception, tr("Không thể cập nhật giấy phép.", "Could not update the license."))
                                    }
                                }
                            }
                        ) { Text(if (license.status == "PAUSED") tr("Kích hoạt", "Activate") else tr("Tạm dừng", "Pause")) }
                        Button(
                            modifier = Modifier.weight(1f),
                            enabled = license.status != "REVOKED",
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            onClick = {
                                val token = accessToken ?: return@Button
                                scope.launch {
                                    try {
                                        repository.updateCustomerLicense(token, license.id, "REVOKED", license.expiresAt, license.maxDevices)
                                        notice = tr("Đã thu hồi giấy phép và các thiết bị liên kết.", "License and its enrolled devices were revoked.")
                                        refresh()
                                    } catch (exception: Exception) {
                                        error = localizedErrorMessage(exception, tr("Không thể thu hồi giấy phép.", "Could not revoke the license."))
                                    }
                                }
                            }
                        ) { Text(tr("Thu hồi", "Revoke")) }
                    }
                }
            }
        }
    }
}

@Composable
internal fun DevicesAdminScreen(accessToken: String?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val repository = remember(context) { AuthRepository(context) }
    val scope = rememberCoroutineScope()
    var devices by remember { mutableStateOf<List<DeviceRecord>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var notice by remember { mutableStateOf("") }
    var restoreTarget by remember { mutableStateOf<DeviceRecord?>(null) }
    var restoring by remember { mutableStateOf(false) }

    suspend fun refresh() {
        if (accessToken.isNullOrBlank()) return
        loading = true
        error = ""
        try {
            devices = repository.listAdminDevices(accessToken)
        } catch (exception: Exception) {
            error = localizedErrorMessage(exception, tr("Không tải được thiết bị.", "Could not load devices."))
        } finally {
            loading = false
        }
    }

    LaunchedEffect(accessToken) { refresh() }

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Heading(tr("Thiết bị", "Devices"), tr("Thiết bị đăng ký bằng khóa bảo mật riêng", "Devices registered with their own secure key"))
            }
            IconButton(onClick = { scope.launch { refresh() } }, enabled = !loading && !accessToken.isNullOrBlank()) {
                Icon(Icons.Outlined.Refresh, contentDescription = tr("Làm mới", "Refresh"))
            }
        }
        if (accessToken.isNullOrBlank()) LiveManagementUnavailable()
        else {
            if (notice.isNotBlank()) Text(notice, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
            if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            if (loading && devices.isEmpty()) Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            else if (devices.isEmpty()) EmptyState(Icons.Outlined.Devices, tr("Chưa có thiết bị", "No devices yet"), tr("Thiết bị chỉ xuất hiện sau khi khách hàng đăng nhập và chủ động đăng ký thiết bị.", "Devices appear after a customer signs in and explicitly registers their device."))
            else devices.forEach { device ->
                CardBox {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(device.label.ifBlank { "${device.manufacturer} ${device.model}".trim().ifBlank { device.email } }, fontWeight = FontWeight.SemiBold)
                            Text(device.email, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                        }
                        Text(
                            if (device.status == "ACTIVE") "Đã cấp quyền" else "Đã thu hồi quyền",
                            color = if (device.status == "ACTIVE") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    AdminIconValue(
                        Icons.Outlined.PhoneAndroid,
                        "Thiết bị Android",
                        "${device.manufacturer} ${device.model} · Android ${device.androidVersion}"
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        AdminIconValue(Icons.Outlined.Apps, "Phiên bản AUTU", "AUTU-LIO ${device.appVersion}")
                        AdminIconValue(Icons.Outlined.AccessTime, "Kiểm tra quyền", formatEpoch(device.lastSeenAt))
                    }
                    AdminPresenceInfo(device.presenceSeenAt, device.appForegroundAt, device.runtimeState, device.status == "ACTIVE" && device.customerStatus == "ACTIVE")
                    if (device.status == "ACTIVE") {
                        OutlinedButton(
                            onClick = {
                                val token = accessToken ?: return@OutlinedButton
                                scope.launch {
                                    try {
                                        repository.revokeCustomerDevice(token, device.id)
                                        notice = tr("Đã khóa thiết bị ${device.email}.", "Locked device for ${device.email}.")
                                        refresh()
                                    } catch (exception: Exception) {
                                        error = localizedErrorMessage(exception, tr("Không thể khóa thiết bị.", "Could not lock the device."))
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) { Text(tr("KHÓA THIẾT BỊ", "LOCK DEVICE")) }
                    } else {
                        val blocked = when {
                            device.customerStatus != "ACTIVE" -> tr("Tài khoản khách hàng đang bị khóa. Hãy kích hoạt lại tài khoản trước.", "The customer account is locked. Activate it first.")
                            device.licenseStatus != "ACTIVE" -> tr("Giấy phép chưa hoạt động hoặc đã hết hạn. Hãy cấp/kích hoạt giấy phép còn hiệu lực.", "The license is not active or has expired. Issue/activate a current license.")
                            device.activeDeviceCount >= (device.maxDevices ?: 0) -> tr("Đã hết giới hạn thiết bị. Hãy khóa thiết bị khác hoặc tăng giới hạn giấy phép.", "The device limit is full. Lock another device or increase the license limit.")
                            else -> null
                        }
                        val reasonText = when (device.revokedReason) {
                            "OWNER_REVOKED" -> tr("Chủ ứng dụng đã thu hồi", "Revoked by app owner")
                            "ACCOUNT_REVOKED" -> tr("Tài khoản đã thu hồi", "Account was revoked")
                            "CUSTOMER_REMOVED" -> tr("Khách hàng đã gỡ", "Removed by customer")
                            "LEGACY_REVOKED" -> tr("Thu hồi từ phiên bản cũ", "Revoked by an older version")
                            else -> tr("Không rõ lý do", "Unknown reason")
                        }
                        Text(
                            tr("Lý do khóa: $reasonText", "Lock reason: $reasonText"),
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 11.sp
                        )
                        OutlinedButton(
                            onClick = { restoreTarget = device },
                            enabled = !restoring,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(tr("MỞ LẠI THIẾT BỊ", "UNLOCK DEVICE")) }
                        if (blocked != null) {
                            Text(blocked, fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }

    restoreTarget?.let { device ->
        AlertDialog(
            onDismissRequest = { if (!restoring) restoreTarget = null },
            title = { Text(tr("Mở lại thiết bị?", "Restore device?")) },
            text = {
                val blocked = when {
                    device.customerStatus != "ACTIVE" -> tr("Tài khoản khách hàng đang bị khóa. Hãy kích hoạt lại tài khoản trước.", "The customer account is locked. Activate it first.")
                    device.licenseStatus != "ACTIVE" -> tr("Giấy phép chưa hoạt động hoặc đã hết hạn. Hãy cấp/kích hoạt giấy phép còn hiệu lực.", "The license is not active or has expired. Issue/activate a current license.")
                    device.activeDeviceCount >= (device.maxDevices ?: 0) -> tr("Đã hết giới hạn thiết bị. Hãy khóa thiết bị khác hoặc tăng giới hạn giấy phép.", "The device limit is full. Lock another device or increase the license limit.")
                    else -> null
                }
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("${device.label.ifBlank { device.model }}")
                    if (blocked != null) Text(blocked, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    else Text(tr("Đủ điều kiện mở lại thiết bị.", "Device is eligible for restore."), color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !restoring && device.customerStatus == "ACTIVE" &&
                        device.licenseStatus == "ACTIVE" &&
                        device.activeDeviceCount < (device.maxDevices ?: 0),
                    onClick = {
                        val token = accessToken ?: return@TextButton
                        scope.launch {
                            restoring = true
                            try {
                                repository.updateCustomerDeviceStatus(token, device.id, "ACTIVE")
                                notice = tr("Đã mở lại thiết bị ${device.email}.", "Restored device for ${device.email}.")
                                restoreTarget = null
                                refresh()
                            } catch (exception: Exception) {
                                error = localizedErrorMessage(exception, tr("Không thể mở lại thiết bị.", "Could not restore the device."))
                            } finally {
                                restoring = false
                            }
                        }
                    }
                ) { Text(if (restoring) tr("Đang mở…", "Restoring…") else tr("Mở lại", "Restore")) }
            },
            dismissButton = {
                TextButton(onClick = { restoreTarget = null }, enabled = !restoring) {
                    Text(tr("Hủy", "Cancel"))
                }
            }
        )
    }
}

@Composable
internal fun CustomerLicenseCountdownBar(accessToken: String?) {
    if (accessToken.isNullOrBlank()) return

    val context = LocalContext.current
    val repository = remember(context) { AuthRepository(context) }
    var expiresAt by remember { mutableStateOf<Long?>(null) }
    var licenseStatus by remember { mutableStateOf<String?>(null) }
    var planCode by remember { mutableStateOf<String?>(null) }
    var checked by remember { mutableStateOf(false) }
    var nowEpochSeconds by remember { mutableStateOf(System.currentTimeMillis() / 1000L) }

    LaunchedEffect(accessToken) {
        while (true) {
            try {
                val current = repository.customerAccount(accessToken)
                expiresAt = current.license?.expiresAt
                licenseStatus = current.license?.effectiveStatus
                planCode = current.license?.planCode
            } catch (_: Exception) {
                // Keep the last server-verified expiry visible during temporary network errors.
            } finally {
                checked = true
            }
            delay(5 * 60 * 1000L)
        }
    }

    LaunchedEffect(expiresAt) {
        if (expiresAt == null) return@LaunchedEffect
        while (true) {
            nowEpochSeconds = System.currentTimeMillis() / 1000L
            delay(1000L)
        }
    }

    val expired = expiresAt != null && expiresAt!! <= nowEpochSeconds
    val active = licenseStatus == "ACTIVE" && !expired
    val lifetime = planCode.equals("LIFETIME", ignoreCase = true)
    val hasRemainingTime = checked && expiresAt != null && !expired && !lifetime
    val remainingDays = expiresAt?.let { licenseDaysRemaining(it, nowEpochSeconds) }
    val label = when {
        !checked -> "Đang kiểm tra thời hạn giấy phép…"
        expiresAt == null -> "Chưa được cấp giấy phép"
        lifetime -> "Giấy phép trọn đời"
        expired -> "Giấy phép đã hết hạn"
        active -> "Giấy phép còn: $remainingDays ngày"
        else -> "Giấy phép ${licenseStatus?.let(::statusText) ?: "chưa xác định"}"
    }
    val remainingDetail = expiresAt?.takeIf { hasRemainingTime }?.let { expiry ->
        "Thời gian chính xác: ${formatLicenseRemaining(expiry, nowEpochSeconds)}"
    }
    val labelColor = if (active || (lifetime && licenseStatus == "ACTIVE")) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.error
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Outlined.VerifiedUser,
                contentDescription = null,
                tint = labelColor,
                modifier = Modifier.size(17.dp)
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    label,
                    color = labelColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2
                )
                remainingDetail?.let { detail ->
                    Text(
                        detail,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        maxLines = 2
                    )
                }
            }
        }
    }
}

@Composable
internal fun CustomerDeviceLicenseScreen(
    accessToken: String?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val repository = remember(context) { AuthRepository(context) }
    val identityStore = remember(context) { SecureDeviceIdentityStore(context) }
    val scope = rememberCoroutineScope()
    var account by remember { mutableStateOf<CustomerAccountOverview?>(null) }
    var loading by remember { mutableStateOf(false) }
    var registering by remember { mutableStateOf(false) }
    var showRegistrationConsent by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var notice by remember { mutableStateOf("") }

    suspend fun refresh() {
        if (accessToken.isNullOrBlank()) return
        loading = true
        error = ""
        try {
            account = repository.customerAccount(accessToken)
        } catch (exception: Exception) {
            error = localizedErrorMessage(exception, tr("Không tải được thông tin tài khoản.", "Could not load account details."))
        } finally {
            loading = false
        }
    }

    LaunchedEffect(accessToken) { refresh() }

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Heading(tr("Thiết bị và giấy phép", "Device and license"), tr("Thông tin được xác minh trực tiếp từ máy chủ", "Information verified directly by the server"))
            }
            IconButton(onClick = { scope.launch { refresh() } }, enabled = !loading && !accessToken.isNullOrBlank()) {
                Icon(Icons.Outlined.Refresh, contentDescription = tr("Làm mới", "Refresh"))
            }
        }
        if (accessToken.isNullOrBlank()) {
            LiveManagementUnavailable()
        } else {
            if (loading && account == null) {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
            if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            if (notice.isNotBlank()) Text(notice, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
            account?.let { current ->
                CardBox {
                    Text(current.displayName ?: current.email, fontWeight = FontWeight.SemiBold)
                    Text(current.email, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (current.license == null) {
                        Text(tr("Chưa được cấp giấy phép", "No license has been issued"), color = MaterialTheme.colorScheme.error)
                    } else {
                        Text("${planText(current.license.planCode)} · ${statusText(current.license.effectiveStatus)}", fontWeight = FontWeight.SemiBold)
                        Text(
                            tr("Có hiệu lực: ${formatEpoch(current.license.startsAt)} – ${formatEpoch(current.license.expiresAt)}", "Valid: ${formatEpoch(current.license.startsAt)} – ${formatEpoch(current.license.expiresAt)}"),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            tr("Thiết bị: ${current.activeDeviceCount}/${current.license.maxDevices}", "Devices: ${current.activeDeviceCount}/${current.license.maxDevices}"),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        if (current.canRun) tr("Được phép sử dụng AUTU đã phát hành.", "You are allowed to run published AUTUs.")
                        else tr("Tài khoản hiện chưa đủ điều kiện chạy AUTU.", "This account is not currently eligible to run AUTUs."),
                        color = if (current.canRun) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        fontSize = 12.sp
                    )
                }
                Text(tr("Thiết bị đã đăng ký", "Registered devices"), fontWeight = FontWeight.SemiBold)
                if (current.devices.isEmpty()) {
                    EmptyState(Icons.Outlined.Devices, tr("Chưa có thiết bị", "No registered devices"), tr("Đăng ký thiết bị này để sử dụng theo giới hạn giấy phép.", "Register this device to use AUTU within your license limit."))
                } else current.devices.forEachIndexed { index, device ->
                    CardBox {
                        Text("Thiết bị ${index + 1}", fontWeight = FontWeight.SemiBold)
                        Text(
                            statusText(device.status),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                        if (device.status == "REVOKED") {
                            val revokeMessage = when (device.revokedReason) {
                                "CUSTOMER_REMOVED" -> tr(
                                    "Bạn đã gỡ thiết bị này. Có thể đăng ký lại bằng khóa bảo mật cũ khi giấy phép còn hiệu lực.",
                                    "You removed this device. You can register it again with its existing security key while your license is active."
                                )
                                "OWNER_REVOKED" -> tr(
                                    "Chủ ứng dụng đã thu hồi thiết bị này. Hãy liên hệ chủ ứng dụng để được mở lại.",
                                    "The app owner revoked this device. Contact the owner to restore it."
                                )
                                "ACCOUNT_REVOKED" -> tr(
                                    "Thiết bị bị khóa cùng tài khoản. Sau khi chủ ứng dụng mở lại tài khoản và cấp giấy phép còn hiệu lực, hãy đăng ký lại thiết bị bằng khóa bảo mật hiện có.",
                                    "This device was disabled with the account. After the owner reactivates the account and issues a current license, register it again with its existing security key."
                                )
                                else -> tr(
                                    "Thiết bị đã bị thu hồi từ phiên bản cũ. Chủ ứng dụng cần mở lại thiết bị trong trang quản lý.",
                                    "This device was revoked by an older version. The owner must restore it from device management."
                                )
                            }
                            Text(revokeMessage, color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                        }
                        if (device.status == "ACTIVE") {
                            TextButton(
                                onClick = {
                                    val token = accessToken ?: return@TextButton
                                    scope.launch {
                                        try {
                                            repository.removeOwnDevice(token, device.id)
                                            notice = tr("Đã gỡ thiết bị khỏi tài khoản.", "Device removed from your account.")
                                            refresh()
                                        } catch (exception: Exception) {
                                            error = localizedErrorMessage(exception, tr("Không thể gỡ thiết bị khỏi tài khoản.", "Could not remove the device."))
                                        }
                                    }
                                }
                            ) { Text(tr("Gỡ thiết bị này", "Remove this device")) }
                        }
                    }
                }
                if (current.canRegisterDevice) {
                    Button(onClick = { showRegistrationConsent = true }, modifier = Modifier.fillMaxWidth(), enabled = !registering) {
                        if (registering) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Outlined.Devices, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(tr("Đăng ký thiết bị này", "Register this device"))
                    }
                }
            }
        }
    }

    if (showRegistrationConsent) {
        AlertDialog(
            onDismissRequest = { if (!registering) showRegistrationConsent = false },
            title = { Text(tr("Đăng ký thiết bị", "Register this device")) },
            text = {
                Text(
                    tr(
                        "AUTU-LIO sẽ tạo khóa RSA 3072-bit trong Android Keystore. Khóa riêng không rời thiết bị; khóa công khai dùng để xác minh thiết bị và nhận khóa giải mã gói AUTU được gửi riêng cho thiết bị. Máy chủ nhận khóa công khai cùng thông tin hãng/mẫu máy, phiên bản Android và AUTU-LIO. Khi ứng dụng được mở, AUTU gửi tín hiệu kết nối định kỳ; khi kịch bản đang chạy hoặc tạm dừng ở nền, dịch vụ gửi trạng thái tương ứng. Máy chủ chỉ lưu thời điểm và trạng thái kết nối/chạy; không ghi nội dung màn hình, thao tác cá nhân, vị trí, IMEI hoặc số sê-ri.",
                        "AUTU-LIO will create an RSA 3072-bit key in Android Keystore. The private key stays on this device; the public key verifies this device and lets the server wrap package decryption keys specifically for it. The server receives the public key and device manufacturer/model, Android version and AUTU-LIO version. While the app is open, AUTU sends periodic connection signals; while a script is running or paused in the background, the service reports that state. The server stores only timestamps and connection/runtime states; it does not collect screen contents, personal interactions, location, IMEI or serial number."
                    ),
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !registering,
                    onClick = {
                        val token = accessToken ?: return@TextButton
                        scope.launch {
                            registering = true
                            error = ""
                            try {
                                val identity = identityStore.getOrCreate()
                                val challenge = repository.createCustomerDeviceChallenge(token)
                                val signature = identityStore.signChallenge(challenge.challengeId, challenge.nonce)
                                repository.registerCustomerDevice(
                                    accessToken = token,
                                    publicKeySpki = identity.publicKeySpki,
                                    challengeId = challenge.challengeId,
                                    nonce = challenge.nonce,
                                    signature = signature,
                                    label = listOf(Build.MANUFACTURER, Build.MODEL).filter { it.isNotBlank() }.joinToString(" ").take(100),
                                    manufacturer = Build.MANUFACTURER.orEmpty(),
                                    model = Build.MODEL.orEmpty(),
                                    androidVersion = Build.VERSION.RELEASE.orEmpty(),
                                    appVersion = BuildConfig.VERSION_NAME
                                )
                                notice = tr("Đã đăng ký thiết bị với máy chủ.", "Device registered with the server.")
                                showRegistrationConsent = false
                                refresh()
                            } catch (exception: Exception) {
                                error = localizedErrorMessage(exception, tr("Không đăng ký được thiết bị.", "Could not register this device."))
                            } finally {
                                registering = false
                            }
                        }
                    }
                ) { Text(tr("Đồng ý và đăng ký", "Agree and register")) }
            },
            dismissButton = { TextButton(onClick = { showRegistrationConsent = false }, enabled = !registering) { Text(tr("Hủy", "Cancel")) } }
        )
    }
}

@Composable
internal fun AdminPackagesScreen(
    accessToken: String?,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    initialDraftName: String? = null
) {
    val context = LocalContext.current
    val repository = remember(context) { AuthRepository(context) }
    val draftStore = remember(context) { SecureScriptDraftStore(context) }
    val scope = rememberCoroutineScope()
    var drafts by remember { mutableStateOf(draftStore.load()) }
    var packages by remember { mutableStateOf<List<AutuPackageRecord>>(emptyList()) }
    var selectedDraft by remember(initialDraftName) { mutableStateOf<String?>(initialDraftName) }
    var draftSelectionInitialized by remember(initialDraftName) { mutableStateOf(false) }
    var description by remember { mutableStateOf("") }
    var sourceInfo by remember { mutableStateOf<com.autulio.app.auth.AdminPackageSourceInfo?>(null) }
    var sourceViewMode by remember { mutableStateOf("SOURCE") }
    var inspectingPackageId by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var publishing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var notice by remember { mutableStateOf("") }

    suspend fun refresh() {
        drafts = draftStore.load()
        if (!draftSelectionInitialized) {
            selectedDraft = initialDraftName?.takeIf { it in drafts.names } ?: drafts.names.lastOrNull()
            draftSelectionInitialized = true
        } else if (selectedDraft != null && selectedDraft !in drafts.names) {
            selectedDraft = drafts.names.lastOrNull()
        }
        if (accessToken.isNullOrBlank()) return
        loading = true
        error = ""
        try {
            packages = repository.listAdminPackages(accessToken)
        } catch (exception: Exception) {
            error = localizedErrorMessage(exception, tr("Không tải được danh sách gói.", "Could not load packages."))
        } finally {
            loading = false
        }
    }

    LaunchedEffect(accessToken) { refresh() }

    val selectedCode = selectedDraft?.let { drafts.contents[it] }.orEmpty()
    val matchingPackage = selectedDraft?.let { name ->
        packages.firstOrNull { it.name.equals(name, ignoreCase = true) }
    }
    val actionLabel = if (matchingPackage == null) {
        tr("Phát hành gói AUTU", "Publish AUTU package")
    } else {
        tr("Phát hành phiên bản mới", "Publish new version")
    }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        BrandHeader(admin = true)
        TextButton(onClick = onBack) { Text(tr("← Quay lại", "← Back")) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Heading(
                    tr("Phát hành gói AUTU", "Publish AUTU packages"),
                    tr("Tạo script → kiểm tra AUTU → nén và mã hóa → phát hành. Bản gốc vẫn nằm trong kho nháp riêng của chủ ứng dụng.", "Create script → validate AUTU → compress and encrypt → publish. The original remains in the owner's private draft vault.")
                )
            }
            IconButton(onClick = { scope.launch { refresh() } }, enabled = !loading && !publishing) {
                Icon(Icons.Outlined.Refresh, contentDescription = tr("Làm mới", "Refresh"))
            }
        }
        if (accessToken.isNullOrBlank()) {
            LiveManagementUnavailable()
        } else {
            if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            if (notice.isNotBlank()) Text(notice, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
            Text(tr("Bản nháp đã mã hóa trên thiết bị", "Encrypted drafts on this device"), fontWeight = FontWeight.SemiBold)
            if (drafts.names.isEmpty()) {
                EmptyState(
                    Icons.Outlined.Code,
                    tr("Chưa có bản nháp", "No saved drafts"),
                    tr("Tạo AUTU trong Trình soạn thảo, lưu bản nháp rồi quay lại đây để phát hành.", "Create an AUTU in the editor, save the draft, then return here to publish it.")
                )
            } else {
                drafts.names.forEach { name ->
                    val selected = selectedDraft == name
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable {
                            selectedDraft = name
                            description = ""
                            error = ""
                            notice = ""
                        },
                        colors = CardDefaults.cardColors(
                            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                        )
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(13.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(name, fontWeight = FontWeight.SemiBold)
                                Text(
                                    tr("${drafts.contents[name]?.length ?: 0} ký tự mã nguồn", "${drafts.contents[name]?.length ?: 0} source characters"),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp
                                )
                            }
                            if (selected) Icon(Icons.Outlined.VerifiedUser, null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
            if (selectedDraft != null) {
                CardBox {
                    Text(
                        if (matchingPackage == null) tr("Gói mới: $selectedDraft", "New package: $selectedDraft")
                        else tr("Cập nhật: $selectedDraft · phiên bản ${matchingPackage.latestVersion + 1}", "Update: $selectedDraft · version ${matchingPackage.latestVersion + 1}"),
                        fontWeight = FontWeight.SemiBold
                    )
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it.take(1_000) },
                        label = { Text(tr("Mô tả gói", "Package description")) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 4
                    )
                    Button(
                        onClick = {
                            val token = accessToken ?: return@Button
                            val name = selectedDraft ?: return@Button
                            val source = selectedCode
                            scope.launch {
                                publishing = true
                                error = ""
                                notice = ""
                                try {
                                    val compilation = ScriptCompiler.compile(source)
                                    if (!compilation.isSuccess || compilation.program == null) {
                                        val details = compilation.diagnostics.joinToString("; ") {
                                            "Dòng ${it.line}: ${it.messageVi}"
                                        }
                                        throw IllegalStateException(tr("Mã AUTU chưa hợp lệ: $details", "AUTU source is invalid: $details"))
                                    }
                                    val result = if (matchingPackage == null) {
                                        repository.publishAdminPackage(
                                            token,
                                            name,
                                            description,
                                            compilation.program.languageVersion,
                                            source
                                        )
                                    } else {
                                        repository.publishAdminPackageVersion(
                                            token,
                                            matchingPackage.id,
                                            name,
                                            description.ifBlank { matchingPackage.description },
                                            compilation.program.languageVersion,
                                            source
                                        )
                                    }
                                    notice = tr(
                                        "Đã phát hành $name phiên bản ${result.latestVersion}.",
                                        "Published $name version ${result.latestVersion}."
                                    )
                                    selectedDraft = null
                                    refresh()
                                } catch (exception: Exception) {
                                    error = localizedErrorMessage(exception, tr("Không phát hành được gói AUTU.", "Could not publish this AUTU package."))
                                } finally {
                                    publishing = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !publishing && !loading && error.isBlank() && selectedDraft != null && selectedCode.isNotBlank()
                    ) {
                        if (publishing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Outlined.PlayArrow, null)
                        Spacer(Modifier.width(8.dp))
                        Text(actionLabel)
                    }
                    Text(
                        "Luồng bảo mật: nén GZIP khi có lợi → AES-256-GCM 256-bit với khóa ngẫu nhiên riêng từng phiên bản → RSA-3072 OAEP SHA-1/MGF1-SHA1 bọc khóa AES cho từng thiết bị khi tải; mã nguồn vẫn dùng AES-256-GCM. D1 chỉ lưu ciphertext và phong bì khóa đã mã hóa.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    )
                }
            }
            CardBox {
                Text("Bảo vệ mã nguồn", fontWeight = FontWeight.SemiBold)
                Text(
                    "Khách hàng chỉ nhận gói mã hóa. Máy chủ xác minh giấy phép và thiết bị trước khi bọc khóa riêng cho đúng thiết bị. Bản rõ chỉ được giải mã trong bộ nhớ để biên dịch và chuẩn bị chạy, không ghi thành tệp dùng chung.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
                Text(
                    "Chủ ứng dụng có thể xem lại mã gốc và phong bì mã hóa đã phát hành ở từng gói.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }
            Divider()
            Text(tr("Các gói đã phát hành", "Published packages"), fontWeight = FontWeight.SemiBold)
            if (loading && packages.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(18.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (packages.isEmpty()) {
                Text(tr("Chưa có gói nào được phát hành.", "No packages have been published."), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            } else {
                packages.forEach { item ->
                    CardBox {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(item.name, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "v${item.latestVersion} · Ngôn ngữ AUTU ${item.languageVersion} · ${statusText(item.status)}",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp
                                )
                            }
                            TextButton(
                                enabled = inspectingPackageId != item.id,
                                onClick = {
                                    val token = accessToken ?: return@TextButton
                                    scope.launch {
                                        inspectingPackageId = item.id
                                        error = ""
                                        try {
                                            sourceInfo = repository.getAdminPackageSource(token, item.id)
                                            sourceViewMode = "SOURCE"
                                        } catch (exception: Exception) {
                                            error = localizedErrorMessage(exception, "Không xem được mã nguồn của gói.")
                                        } finally {
                                            inspectingPackageId = null
                                        }
                                    }
                                }
                            ) {
                                Text(if (inspectingPackageId == item.id) "Đang tải…" else "Mã gốc / mã hóa")
                            }
                            TextButton(
                                onClick = {
                                    val token = accessToken ?: return@TextButton
                                    scope.launch {
                                        try {
                                            val newStatus = if (item.status == "ACTIVE") "ARCHIVED" else "ACTIVE"
                                            repository.setAdminPackageStatus(token, item.id, newStatus)
                                            notice = tr("Đã cập nhật trạng thái gói.", "Package status updated.")
                                            refresh()
                                        } catch (exception: Exception) {
                                            error = localizedErrorMessage(exception, tr("Không thể cập nhật trạng thái gói AUTU.", "Could not update package status."))
                                        }
                                    }
                                }
                            ) {
                                Text(if (item.status == "ACTIVE") tr("Lưu trữ", "Archive") else tr("Khôi phục", "Restore"))
                            }
                        }
                        if (item.description.isNotBlank()) Text(item.description, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    }
                }
            }
        }
    }

    sourceInfo?.let { info ->
        AlertDialog(
            onDismissRequest = { sourceInfo = null },
            title = { Text("${info.name} · v${info.version}") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "AUTU ${info.languageVersion} · SHA-256 ${info.sourceSha256.take(16)}…",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = sourceViewMode == "SOURCE",
                            onClick = { sourceViewMode = "SOURCE" },
                            label = { Text("Mã gốc") }
                        )
                        FilterChip(
                            selected = sourceViewMode == "ENCRYPTED",
                            onClick = { sourceViewMode = "ENCRYPTED" },
                            label = { Text("Bản mã hóa") }
                        )
                    }
                    SelectionContainer {
                        Text(
                            if (sourceViewMode == "SOURCE") info.source else info.encryptedJson,
                            modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            lineHeight = 14.sp
                        )
                    }
                    Text(
                        if (sourceViewMode == "SOURCE") "Chỉ chủ ứng dụng được truy xuất mã gốc qua API quản trị."
                        else "Phong bì gồm thuật toán, IV, ciphertext, khóa dữ liệu đã bọc và mã kiểm tra toàn vẹn.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { sourceInfo = null }) { Text("Đóng") }
            }
        )
    }

}

@Composable
internal fun CustomerPackagesScreen(
    accessToken: String?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val repository = remember(context) { AuthRepository(context) }
    val identityStore = remember(context) { SecureDeviceIdentityStore(context) }
    val identity = remember(context) { runCatching { identityStore.getExistingIdentity() }.getOrNull() }
    val scope = rememberCoroutineScope()
    var packages by remember { mutableStateOf<List<com.autulio.app.auth.AutuPackageRecord>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var runningId by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf("") }
    var notice by remember { mutableStateOf("") }

    suspend fun refresh() {
        if (accessToken.isNullOrBlank() || identity == null) return
        loading = true
        error = ""
        try {
            packages = repository.listCustomerPackages(accessToken, identity.fingerprintSha256).packages
        } catch (exception: Exception) {
            error = localizedErrorMessage(exception, tr("Không tải được kho AUTU.", "Could not load the AUTU library."))
        } finally {
            loading = false
        }
    }

    LaunchedEffect(accessToken, identity?.fingerprintSha256) { refresh() }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Heading(
                    tr("Kho AUTU đã phát hành", "Published AUTU library"),
                    tr("Gói được máy chủ xác minh giấy phép và mã hóa riêng cho thiết bị này.", "Packages are license-checked by the server and encrypted for this device.")
                )
            }
            IconButton(onClick = { scope.launch { refresh() } }, enabled = !loading && !accessToken.isNullOrBlank() && identity != null) {
                Icon(Icons.Outlined.Refresh, contentDescription = tr("Làm mới", "Refresh"))
            }
        }
        if (accessToken.isNullOrBlank()) {
            LiveManagementUnavailable()
        } else if (identity == null) {
            CardBox {
                Icon(Icons.Outlined.Devices, null, tint = MaterialTheme.colorScheme.primary)
                Text(tr("Chưa đăng ký thiết bị", "This device is not registered"), fontWeight = FontWeight.SemiBold)
                Text(
                    tr("Hãy mở mục Thiết bị ở thanh điều hướng bên dưới để đăng ký thiết bị trước khi tải gói AUTU. Khóa riêng luôn được giữ trong Android Keystore.",
                        "Use the Devices tab in the bottom navigation to register this device before downloading AUTUs. The private key stays in Android Keystore."),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
            }
        } else {
            if (loading && packages.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            if (error.isNotBlank()) {
                Text(error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                if (error.contains("thiết bị", ignoreCase = true) || error.contains("device", ignoreCase = true)) {
                    Text(
                        tr("Kiểm tra trạng thái đăng ký tại mục Thiết bị ở thanh điều hướng bên dưới.",
                            "Check the registration status in the Devices tab in the bottom navigation."),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
            }
            if (notice.isNotBlank()) {
                CardBox {
                    Text(notice, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                }
            }
            if (!loading && packages.isEmpty() && error.isBlank()) {
                EmptyState(
                    Icons.Outlined.VerifiedUser,
                    tr("Chưa có gói được phát hành", "No published packages yet"),
                    tr("Khi chủ ứng dụng phát hành AUTU cho tài khoản của bạn, gói sẽ xuất hiện ở đây.", "AUTUs published for your account will appear here.")
                )
            }
            packages.forEach { item ->
                CardBox {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(item.name, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                        Surface(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                "v${item.latestVersion}",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    if (item.description.isNotBlank()) {
                        Text(item.description, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    }
                    Text(
                        "Ngôn ngữ AUTU ${item.languageVersion}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                    Button(
                        onClick = {
                            val token = accessToken ?: return@Button
                            val deviceIdentity = identity
                            scope.launch {
                                runningId = item.id
                                error = ""
                                notice = ""
                                var operationStage = "tải gói từ máy chủ"
                                try {
                                    val encryptedPackage = repository.downloadCustomerPackage(
                                        token,
                                        item.id,
                                        deviceIdentity.fingerprintSha256
                                    )
                                    operationStage = "mở khóa và giải mã gói trên thiết bị"
                                    val source = EncryptedAutuPackageDecryptor.decryptSource(identityStore, encryptedPackage)
                                    operationStage = "biên dịch AUTU Script"
                                    val compilation = ScriptCompiler.compile(source)
                                    if (!compilation.isSuccess || compilation.program == null) {
                                        val details = compilation.diagnostics.joinToString("; ") {
                                            "Dòng ${it.line}: ${it.messageVi}"
                                        }
                                        throw IllegalStateException(
                                            tr("Gói AUTU không hợp lệ: $details", "The AUTU package is invalid: $details")
                                        )
                                    }
                                    if (compilation.program.languageVersion != encryptedPackage.languageVersion) {
                                        throw IllegalStateException(
                                            tr("Phiên bản ngôn ngữ của gói không tương thích với ứng dụng.", "The package language version is incompatible with this app.")
                                        )
                                    }
                                    operationStage = "chuẩn bị bộ chạy AUTU"
                                    val service = AutoClickAccessibilityService.current()
                                    if (service == null) {
                                        notice = tr(
                                            "Đã tải và xác minh gói. Hãy bật AUTU-LIO trong Cài đặt > Trợ năng, quay lại đây và nhấn Chuẩn bị chạy lần nữa.",
                                            "The package was downloaded and verified. Enable AUTU-LIO under Settings > Accessibility, return here and tap Prepare to run again."
                                        )
                                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                                    } else if (service.prepareProgram(compilation.program)) {
                                        notice = tr(
                                            "Đã giải mã, kiểm tra toàn vẹn và chuẩn bị AUTU. Chuyển sang ứng dụng mục tiêu rồi nhấn Chạy trên cửa sổ nổi.",
                                            "AUTU was decrypted, integrity-checked and prepared. Switch to the target app and press Run on the floating controller."
                                        )
                                    } else {
                                        throw IllegalStateException(
                                            tr("Không thể chuẩn bị bộ chạy AUTU trên thiết bị.", "Could not prepare the AUTU runner on this device.")
                                        )
                                    }
                                } catch (exception: Exception) {
                                    val localized = localizedErrorMessage(exception, "")
                                    error = localized.ifBlank {
                                        tr(
                                            "Lỗi ở bước $operationStage (${exception.javaClass.simpleName}).",
                                            "Failed while trying to $operationStage (${exception.javaClass.simpleName})."
                                        )
                                    }
                                } finally {
                                    runningId = null
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = runningId == null && !loading
                    ) {
                        if (runningId == item.id) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Outlined.VerifiedUser, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(tr("Tải và chuẩn bị chạy", "Download and prepare"))
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveManagementUnavailable() {
    CardBox {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Outlined.CloudOff, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Column {
                Text(tr("Chưa có phiên đăng nhập thật", "No live authenticated session"), fontWeight = FontWeight.SemiBold)
                Text(
                    tr("Chức năng này cần máy chủ và tài khoản đã được xác minh. Chế độ xem trước không hiển thị dữ liệu mẫu giả.", "This feature requires the server and a verified account. Preview mode does not show fabricated sample data."),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }
        }
    }
}

internal fun licenseDaysRemaining(expiresAtSeconds: Long, nowSeconds: Long): Long {
    val remaining = (expiresAtSeconds - nowSeconds).coerceAtLeast(0L)
    return if (remaining == 0L) 0L else (remaining - 1L) / 86_400L + 1L
}

private fun formatLicenseRemaining(expiresAtSeconds: Long, nowSeconds: Long): String {
    val remaining = (expiresAtSeconds - nowSeconds).coerceAtLeast(0L)
    val days = remaining / 86_400L
    val hours = (remaining % 86_400L) / 3_600L
    val minutes = (remaining % 3_600L) / 60L
    val seconds = remaining % 60L
    val hh = hours.toString().padStart(2, '0')
    val mm = minutes.toString().padStart(2, '0')
    val ss = seconds.toString().padStart(2, '0')
    return when {
        days > 0L -> "${days} ngày ${hh} giờ ${mm} phút ${ss} giây"
        hours > 0L -> "${hours} giờ ${minutes} phút ${ss} giây"
        minutes > 0L -> "${minutes} phút ${ss} giây"
        else -> "${seconds} giây"
    }
}

private fun formatEpoch(value: Long?): String {
    if (value == null || value <= 0L) return "—"
    return java.text.DateFormat.getDateTimeInstance(
        java.text.DateFormat.MEDIUM,
        java.text.DateFormat.SHORT,
        java.util.Locale.forLanguageTag("vi-VN")
    ).format(java.util.Date(value * 1000L))
}
