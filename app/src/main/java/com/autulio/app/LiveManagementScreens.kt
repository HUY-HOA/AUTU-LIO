package com.autulio.app

import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Devices
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
import com.autulio.app.auth.AuthRepository
import com.autulio.app.auth.CustomerAccountOverview
import com.autulio.app.auth.CustomerRecord
import com.autulio.app.auth.DeviceRecord
import com.autulio.app.auth.LicenseRecord
import com.autulio.app.auth.SecureDeviceIdentityStore
import com.autulio.app.BuildConfig
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
            error = exception.message ?: tr("Không tải được danh sách khách hàng.", "Could not load customers.")
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
                                if (customer.status == "ACTIVE") tr("Hoạt động", "Active") else customer.status,
                                color = if (customer.status == "ACTIVE") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Text(
                            tr("Thiết bị đang dùng: ${customer.activeDeviceCount}", "Active devices: ${customer.activeDeviceCount}"),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (customer.licenseId != null) {
                            Text(
                                "${customer.planCode ?: "—"} · ${customer.licenseStatus ?: "—"}",
                                fontWeight = FontWeight.Medium,
                                fontSize = 13.sp
                            )
                            Text(
                                tr("Hết hạn: ${formatEpoch(customer.licenseExpiresAt)} · Giới hạn: ${customer.maxDevices ?: 1} thiết bị", "Expires: ${formatEpoch(customer.licenseExpiresAt)} · Limit: ${customer.maxDevices ?: 1} devices"),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp
                            )
                        } else {
                            Text(tr("Chưa có giấy phép sử dụng", "No usage license"), color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                            Button(
                                onClick = {
                                    target = customer
                                    planCode = "MONTHLY"
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
                    OutlinedTextField(planCode, { planCode = it.uppercase() }, label = { Text(tr("Mã gói", "Plan code")) }, singleLine = true)
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
                                error = exception.message ?: tr("Không cấp được giấy phép.", "Could not issue license.")
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
            error = exception.message ?: tr("Không tải được giấy phép.", "Could not load licenses.")
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
                    Text("${license.planCode} · ${license.status}", fontSize = 13.sp)
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
                                    error = exception.message.orEmpty()
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
                                        error = exception.message.orEmpty()
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
                                        error = exception.message.orEmpty()
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

    suspend fun refresh() {
        if (accessToken.isNullOrBlank()) return
        loading = true
        error = ""
        try {
            devices = repository.listAdminDevices(accessToken)
        } catch (exception: Exception) {
            error = exception.message ?: tr("Không tải được thiết bị.", "Could not load devices.")
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
                            device.status,
                            color = if (device.status == "ACTIVE") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Text("${device.manufacturer} ${device.model} · Android ${device.androidVersion}", fontSize = 12.sp)
                    Text(
                        tr("AUTU-LIO ${device.appVersion} · Hoạt động: ${formatEpoch(device.lastSeenAt)}", "AUTU-LIO ${device.appVersion} · Last seen: ${formatEpoch(device.lastSeenAt)}"),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (device.status == "ACTIVE") {
                        OutlinedButton(
                            onClick = {
                                val token = accessToken ?: return@OutlinedButton
                                scope.launch {
                                    try {
                                        repository.revokeCustomerDevice(token, device.id)
                                        notice = tr("Đã thu hồi thiết bị ${device.email}.", "Revoked device for ${device.email}.")
                                        refresh()
                                    } catch (exception: Exception) {
                                        error = exception.message.orEmpty()
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) { Text(tr("Thu hồi thiết bị", "Revoke device")) }
                    }
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
            error = exception.message ?: tr("Không tải được thông tin tài khoản.", "Could not load account details.")
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
                        Text("${current.license.planCode} · ${current.license.effectiveStatus}", fontWeight = FontWeight.SemiBold)
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
                } else current.devices.forEach { device ->
                    CardBox {
                        Text(device.label.ifBlank { "${device.manufacturer} ${device.model}".trim() }, fontWeight = FontWeight.SemiBold)
                        Text("${device.manufacturer} ${device.model}", fontSize = 12.sp)
                        Text(
                            "${device.status} · ${tr("Lần cuối", "Last seen")}: ${formatEpoch(device.lastSeenAt)}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
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
                                            error = exception.message.orEmpty()
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
                        "AUTU-LIO sẽ tạo khóa RSA 3072-bit trong Android Keystore. Khóa riêng không rời thiết bị; chỉ khóa công khai và thông tin model/phiên bản Android/AUTU-LIO được gửi đến máy chủ để quản lý giới hạn thiết bị. Không gửi IMEI hoặc số sê-ri.",
                        "AUTU-LIO will create an RSA 3072-bit key in Android Keystore. The private key stays on this device; only the public key and device model/Android/AUTU-LIO version are sent to the server for device limits. No IMEI or serial number is collected."
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
                                    oaepHash = identity.oaepHash,
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
                                error = exception.message ?: tr("Không đăng ký được thiết bị.", "Could not register this device.")
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

private fun formatEpoch(value: Long?): String {
    if (value == null || value <= 0L) return "—"
    return java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT)
        .format(java.util.Date(value * 1000L))
}
