package com.autulio.app

import android.content.Context
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.autulio.app.auth.AuthRepository
import com.autulio.app.auth.CustomerRecord
import com.autulio.app.auth.DeviceRecord
import com.autulio.app.auth.LicenseRecord
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

private data class AccessAdminConfirmation(
    val title: String,
    val message: String,
    val confirmLabel: String,
    val destructive: Boolean = true,
    val execute: suspend () -> Unit
)

@Composable
internal fun AccessControlAdminScreen(
    accessToken: String?,
    modifier: Modifier = Modifier,
    initialSection: String = "customers"
) {
    val context = LocalContext.current
    val repository = remember(context) { AuthRepository(context) }
    val scope = rememberCoroutineScope()
    var section by remember(initialSection) { mutableStateOf(initialSection) }
    var customers by remember { mutableStateOf<List<CustomerRecord>>(emptyList()) }
    var licenses by remember { mutableStateOf<List<LicenseRecord>>(emptyList()) }
    var devices by remember { mutableStateOf<List<DeviceRecord>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var notice by remember { mutableStateOf("") }
    var search by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf("ALL") }
    var pendingConfirmation by remember { mutableStateOf<AccessAdminConfirmation?>(null) }
    var createFor by remember { mutableStateOf<CustomerRecord?>(null) }
    var editLicense by remember { mutableStateOf<LicenseRecord?>(null) }
    var createPlan by remember { mutableStateOf("MONTHLY") }
    var createDays by remember { mutableStateOf("30") }
    var createDeviceLimit by remember { mutableStateOf("1") }
    var extendDays by remember { mutableStateOf("30") }
    var editDeviceLimit by remember { mutableStateOf("1") }
    var customerInfo by remember { mutableStateOf<CustomerRecord?>(null) }
    var deviceInfo by remember { mutableStateOf<DeviceRecord?>(null) }

    suspend fun refresh() {
        val token = accessToken ?: return
        loading = true
        error = ""
        try {
            val result = coroutineScope {
                val customerRequest = async { repository.listAdminCustomers(token) }
                val licenseRequest = async { repository.listAdminLicenses(token) }
                val deviceRequest = async { repository.listAdminDevices(token) }
                Triple(customerRequest.await(), licenseRequest.await(), deviceRequest.await())
            }
            customers = result.first
            licenses = result.second
            devices = result.third
        } catch (exception: Exception) {
            error = localizedErrorMessage(exception, "Không tải được dữ liệu quản lý.")
        } finally {
            loading = false
        }
    }

    LaunchedEffect(accessToken) { refresh() }

    fun matchesSearch(vararg values: String?): Boolean {
        val needle = search.trim()
        return needle.isBlank() || values.filterNotNull().any { it.contains(needle, ignoreCase = true) }
    }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("Quản lý truy cập", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("Khách hàng · Giấy phép · Thiết bị", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
            IconButton(onClick = { scope.launch { refresh() } }, enabled = !loading && !accessToken.isNullOrBlank()) {
                Icon(Icons.Outlined.Refresh, contentDescription = "Làm mới")
            }
        }

        if (accessToken.isNullOrBlank()) {
            EmptyState(Icons.Outlined.VerifiedUser, "Chưa có phiên quản trị", "Đăng nhập bằng tài khoản chủ sở hữu để quản lý khách hàng, giấy phép và thiết bị.")
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                listOf(
                    "customers" to "Khách hàng",
                    "licenses" to "Giấy phép",
                    "devices" to "Thiết bị"
                ).forEach { item ->
                    FilterChip(
                        selected = section == item.first,
                        onClick = {
                            section = item.first
                            statusFilter = "ALL"
                        },
                        label = { Text(item.second) }
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                AccessMetric("Khách hàng", customers.size.toString(), Modifier.weight(1f))
                AccessMetric("Giấy phép", licenses.count { it.status == "ACTIVE" }.toString(), Modifier.weight(1f))
                AccessMetric("Thiết bị", devices.count { it.status == "ACTIVE" }.toString(), Modifier.weight(1f))
            }

            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Tìm tên, email hoặc thiết bị") }
            )

            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(
                    "ALL" to "Tất cả",
                    "ACTIVE" to "Hoạt động",
                    "SUSPENDED" to "Tạm khóa",
                    "PAUSED" to "Tạm dừng",
                    "EXPIRED" to "Hết hạn",
                    "REVOKED" to "Thu hồi"
                ).forEach { item ->
                    FilterChip(
                        selected = statusFilter == item.first,
                        onClick = { statusFilter = item.first },
                        label = { Text(item.second, fontSize = 11.sp) }
                    )
                }
            }

            if (notice.isNotBlank()) {
                Text(notice, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
            }
            if (error.isNotBlank()) {
                Text(error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            }
            if (loading && customers.isEmpty() && licenses.isEmpty() && devices.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                when (section) {
                    "licenses" -> {
                        val filtered = licenses.filter { license ->
                            (statusFilter == "ALL" || license.status == statusFilter) &&
                                matchesSearch(license.email, license.planCode, license.status)
                        }
                        if (filtered.isEmpty()) {
                            EmptyState(Icons.Outlined.VerifiedUser, "Không có giấy phép phù hợp", "Thử đổi bộ lọc hoặc tìm kiếm. Có thể cấp giấy phép tại hồ sơ khách hàng.")
                        } else filtered.forEach { license ->
                            CardBox {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(license.email, fontWeight = FontWeight.SemiBold)
                                        Text("${planText(license.planCode)} · ${statusText(license.status)}", fontSize = 12.sp)
                                    }
                                    Text("${license.activeDeviceCount}/${license.maxDevices}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                                Text(
                                    "Hết hạn: ${accessDate(license.expiresAt)}",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    OutlinedButton(
                                        modifier = Modifier.weight(1f),
                                        enabled = license.status != "REVOKED",
                                        onClick = {
                                            editLicense = license
                                            extendDays = "30"
                                            editDeviceLimit = license.maxDevices.toString()
                                        }
                                    ) { Text("Gia hạn / thiết bị", fontSize = 11.sp) }
                                    if (license.status == "ACTIVE" || license.status == "PAUSED") {
                                        TextButton(
                                            onClick = {
                                                val token = accessToken ?: return@TextButton
                                                val next = if (license.status == "ACTIVE") "PAUSED" else "ACTIVE"
                                                pendingConfirmation = AccessAdminConfirmation(
                                                    title = if (next == "PAUSED") "Tạm dừng giấy phép?" else "Mở lại giấy phép?",
                                                    message = if (next == "PAUSED") "Khách hàng sẽ không chạy được AUTU cho đến khi giấy phép được mở lại." else "Khách hàng sẽ được dùng lại giấy phép nếu tài khoản và thời hạn vẫn hợp lệ.",
                                                    confirmLabel = if (next == "PAUSED") "Tạm dừng" else "Mở lại"
                                                ) {
                                                    repository.updateCustomerLicense(token, license.id, next, license.expiresAt, license.maxDevices)
                                                    notice = "Đã cập nhật trạng thái giấy phép."
                                                }
                                            }
                                        ) { Text(if (license.status == "ACTIVE") "Tạm dừng" else "Mở lại", fontSize = 11.sp) }
                                    }
                                }
                                if (license.status != "REVOKED") {
                                    TextButton(
                                        onClick = {
                                            val token = accessToken ?: return@TextButton
                                            pendingConfirmation = AccessAdminConfirmation(
                                                title = "Thu hồi giấy phép?",
                                                message = "Quyền chạy AUTU sẽ bị chấm dứt nhưng các đăng ký thiết bị và khóa bảo mật được giữ nguyên. Khi cấp giấy phép mới, những thiết bị đã đăng ký có thể tiếp tục sử dụng nếu vẫn trong giới hạn.",
                                                confirmLabel = "Thu hồi giấy phép"
                                            ) {
                                                repository.updateCustomerLicense(token, license.id, "REVOKED", license.expiresAt, license.maxDevices)
                                                notice = "Đã thu hồi giấy phép."
                                            }
                                        },
                                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                    ) { Text("Thu hồi giấy phép", fontSize = 11.sp) }
                                }
                            }
                        }
                    }
                    "devices" -> {
                        val filtered = devices.filter { device ->
                            (statusFilter == "ALL" || device.status == statusFilter) &&
                                matchesSearch(device.email, device.label, device.manufacturer, device.model, device.appVersion)
                        }
                        if (filtered.isEmpty()) {
                            EmptyState(Icons.Outlined.Devices, "Không có thiết bị phù hợp", "Thiết bị chỉ xuất hiện sau khi khách hàng đăng ký thiết bị bằng khóa bảo mật.")
                        } else filtered.forEach { device ->
                            val deviceName = device.label.ifBlank {
                                listOf(device.manufacturer, device.model)
                                    .filter { it.isNotBlank() }
                                    .joinToString(" ")
                                    .ifBlank { "Thiết bị Android" }
                            }
                            val canRestore = device.customerStatus == "ACTIVE" &&
                                device.licenseStatus == "ACTIVE" &&
                                device.activeDeviceCount < (device.maxDevices ?: 0)
                            CardBox {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(deviceName, fontWeight = FontWeight.SemiBold)
                                        Text(device.email, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Text(statusText(device.status), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                }
                                Text(
                                    "Đang dùng ${device.activeDeviceCount}/${device.maxDevices ?: "—"} khe thiết bị",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TextButton(onClick = { deviceInfo = device }) {
                                        Icon(Icons.Outlined.Info, contentDescription = null)
                                        Text("Thông tin")
                                    }
                                    if (device.status == "REVOKED") {
                                        OutlinedButton(
                                            enabled = canRestore,
                                            onClick = {
                                                val token = accessToken ?: return@OutlinedButton
                                                pendingConfirmation = AccessAdminConfirmation(
                                                    title = "Cho phép lại thiết bị?",
                                                    message = "Thiết bị sẽ được kích hoạt lại bằng khóa bảo mật đã đăng ký trước đây. Cần tài khoản đang hoạt động, giấy phép còn hiệu lực và còn chỗ trong giới hạn thiết bị.",
                                                    confirmLabel = "Cho phép lại",
                                                    destructive = false
                                                ) {
                                                    repository.updateCustomerDeviceStatus(token, device.id, "ACTIVE")
                                                    notice = "Đã cho phép thiết bị sử dụng lại."
                                                }
                                            }
                                        ) { Text("Cho phép lại", fontSize = 11.sp) }
                                        if (!canRestore) {
                                            val blocked = when {
                                                device.customerStatus != "ACTIVE" -> "Cần kích hoạt tài khoản trước"
                                                device.licenseStatus != "ACTIVE" -> "Cần giấy phép còn hiệu lực"
                                                else -> "Đã đủ số thiết bị; gỡ thiết bị khác hoặc tăng giới hạn"
                                            }
                                            Text(blocked, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    } else {
                                        TextButton(
                                            onClick = {
                                                val token = accessToken ?: return@TextButton
                                                pendingConfirmation = AccessAdminConfirmation(
                                                    title = "Gỡ quyền sử dụng thiết bị?",
                                                    message = "Thiết bị sẽ không chạy AUTU và một khe trong giới hạn sẽ được giải phóng. Hồ sơ được giữ trong lịch sử kiểm soát; thao tác này không xóa vật lý dữ liệu thiết bị.",
                                                    confirmLabel = "Gỡ thiết bị"
                                                ) {
                                                    repository.revokeCustomerDevice(token, device.id)
                                                    notice = "Đã gỡ quyền sử dụng thiết bị."
                                                }
                                            },
                                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                        ) { Text("Gỡ thiết bị", fontSize = 11.sp) }
                                    }
                                }
                            }
                        }
                    }
                    else -> {
                        val filtered = customers.filter { customer ->
                            (statusFilter == "ALL" || customer.status == statusFilter) &&
                                matchesSearch(customer.email, customer.displayName, customer.status, customer.planCode)
                        }
                        if (filtered.isEmpty()) {
                            EmptyState(Icons.Outlined.Group, "Không có khách hàng phù hợp", "Khách hàng sẽ xuất hiện sau khi đăng nhập bằng Google. Có thể đổi bộ lọc hoặc nội dung tìm kiếm.")
                        } else filtered.forEach { customer ->
                            CardBox {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(customer.displayName ?: customer.email, fontWeight = FontWeight.SemiBold)
                                        Text(customer.email, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Text(statusText(customer.status), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                }
                                Text(
                                    customer.licenseId?.let {
                                        "${customer.planCode?.let(::planText) ?: "Gói sử dụng"} · ${customer.licenseStatus?.let(::statusText) ?: "Chưa rõ"} · Hết hạn ${accessDate(customer.licenseExpiresAt)}"
                                    } ?: "Chưa có giấy phép sử dụng",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    "Thiết bị: ${customer.activeDeviceCount}/${customer.maxDevices ?: 0} · Đăng nhập gần nhất: ${accessDate(customer.lastLoginAt)}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    TextButton(onClick = { customerInfo = customer }) {
                                        Icon(Icons.Outlined.Info, contentDescription = null)
                                        Text("Thông tin")
                                    }
                                    if ((customer.licenseId == null || customer.licenseStatus == "REVOKED") && customer.status == "ACTIVE") {
                                        OutlinedButton(
                                            onClick = {
                                                createFor = customer
                                                createPlan = "MONTHLY"
                                                createDays = "30"
                                                createDeviceLimit = "1"
                                            }
                                        ) { Text("Cấp giấy phép", fontSize = 11.sp) }
                                    } else {
                                        OutlinedButton(
                                            enabled = customer.licenseId != null && customer.licenseStatus != "REVOKED",
                                            onClick = {
                                                val license = licenses.firstOrNull { it.id == customer.licenseId }
                                                if (license != null) {
                                                    editLicense = license
                                                    extendDays = "30"
                                                    editDeviceLimit = license.maxDevices.toString()
                                                    section = "licenses"
                                                }
                                            }
                                        ) { Text("Giấy phép", fontSize = 11.sp) }
                                    }
                                    val nextStatus = if (customer.status == "ACTIVE") "SUSPENDED" else "ACTIVE"
                                    TextButton(
                                        onClick = {
                                            val token = accessToken ?: return@TextButton
                                            val title = if (nextStatus == "SUSPENDED") "Tạm khóa khách hàng?" else "Mở khóa khách hàng?"
                                            val message = if (nextStatus == "SUSPENDED") {
                                                "Các phiên đăng nhập hiện tại sẽ bị chấm dứt. Giấy phép và thiết bị vẫn được giữ, nhưng tài khoản không thể sử dụng khi đang khóa."
                                            } else if (customer.status == "REVOKED") {
                                                "Tài khoản được kích hoạt lại để đăng nhập. Hãy cấp giấy phép mới; sau đó có thể mở lại các thiết bị đã thu hồi trong mục Thiết bị nếu còn giới hạn."
                                            } else {
                                                "Khách hàng có thể đăng nhập lại. Phiên cũ đã bị chấm dứt nên cần đăng nhập mới."
                                            }
                                            pendingConfirmation = AccessAdminConfirmation(
                                                title = title,
                                                message = message,
                                                confirmLabel = if (nextStatus == "SUSPENDED") "Tạm khóa" else "Kích hoạt"
                                            ) {
                                                repository.updateCustomerStatus(token, customer.userId, nextStatus)
                                                notice = if (nextStatus == "SUSPENDED") "Đã tạm khóa khách hàng." else "Đã kích hoạt tài khoản."
                                            }
                                        }
                                    ) { Text(if (customer.status == "ACTIVE") "Khóa" else "Mở", fontSize = 11.sp) }
                                }
                                if (customer.status != "REVOKED") {
                                    TextButton(
                                        onClick = {
                                            val token = accessToken ?: return@TextButton
                                            pendingConfirmation = AccessAdminConfirmation(
                                                title = "Xóa / thu hồi tài khoản?",
                                                message = "Tài khoản sẽ bị đánh dấu đã thu hồi; mọi phiên đăng nhập, giấy phép và thiết bị đang hoạt động sẽ bị thu hồi ngay. Nhật ký và dữ liệu đối soát được giữ lại; đây là xóa quyền truy cập, không phải xóa vật lý hồ sơ.",
                                                confirmLabel = "Xóa và thu hồi"
                                            ) {
                                                repository.updateCustomerStatus(token, customer.userId, "REVOKED")
                                                notice = "Đã thu hồi tài khoản và quyền truy cập liên quan."
                                            }
                                        },
                                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                    ) { Text("Xóa / thu hồi tài khoản", fontSize = 11.sp) }
                                }
                            }
                        }
                    }
                }
            }
            if (loading) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }
    }

    customerInfo?.let { customer ->
        AlertDialog(
            onDismissRequest = { customerInfo = null },
            title = { Text(customer.displayName ?: customer.email) },
            text = {
                Column(
                    Modifier.fillMaxWidth().heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AccessInfoLine("Email", customer.email)
                    AccessInfoLine("Trạng thái tài khoản", statusText(customer.status))
                    AccessInfoLine("Tạo tài khoản", customer.createdAt.takeIf { it > 0L }?.let { accessDate(it) })
                    AccessInfoLine("Đăng nhập gần nhất", customer.lastLoginAt?.takeIf { it > 0L }?.let { accessDate(it) })
                    AccessInfoLine(
                        "Giấy phép",
                        customer.licenseId?.let {
                            "${customer.planCode?.let(::planText) ?: "Giấy phép"} · ${customer.licenseStatus?.let(::statusText) ?: "Chưa rõ"}"
                        }
                    )
                    AccessInfoLine("Hết hạn", customer.licenseExpiresAt?.takeIf { it > 0L }?.let { accessDate(it) })
                    AccessInfoLine(
                        "Thiết bị đang hoạt động",
                        "${customer.activeDeviceCount}/${customer.maxDevices ?: "—"}"
                    )
                    val customerDevices = devices.filter { it.userId == customer.userId }
                    Text("Thiết bị đã liên kết (${customerDevices.size})", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    customerDevices.forEach { device ->
                        val name = device.label.ifBlank {
                            listOf(device.manufacturer, device.model).filter { it.isNotBlank() }.joinToString(" ")
                                .ifBlank { "Thiết bị Android" }
                        }
                        Text(
                            "$name · ${statusText(device.status)}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    customer.googleSignIn?.let { GoogleSignInDetailsContent(it) }
                }
            },
            confirmButton = { TextButton(onClick = { customerInfo = null }) { Text("Đóng") } }
        )
    }

    deviceInfo?.let { device ->
        val reason = when (device.revokedReason) {
            "OWNER_REVOKED" -> "Chủ ứng dụng đã gỡ quyền"
            "ACCOUNT_REVOKED" -> "Tài khoản đã bị thu hồi"
            "CUSTOMER_REMOVED" -> "Khách hàng tự gỡ thiết bị"
            "LEGACY_REVOKED" -> "Trạng thái thu hồi từ phiên bản cũ"
            else -> null
        }
        AlertDialog(
            onDismissRequest = { deviceInfo = null },
            title = { Text(device.label.ifBlank { listOf(device.manufacturer, device.model).filter { it.isNotBlank() }.joinToString(" ").ifBlank { "Thiết bị Android" } }) },
            text = {
                Column(
                    Modifier.fillMaxWidth().heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AccessInfoLine("Tài khoản", device.email)
                    AccessInfoLine("Trạng thái thiết bị", statusText(device.status))
                    AccessInfoLine("Nhà sản xuất", device.manufacturer)
                    AccessInfoLine("Mẫu máy", device.model)
                    AccessInfoLine("Phiên bản Android", device.androidVersion)
                    AccessInfoLine("Phiên bản AUTU-LIO", device.appVersion)
                    AccessInfoLine("Đăng ký lúc", device.registeredAt.takeIf { it > 0L }?.let { accessDate(it) })
                    AccessInfoLine("Hoạt động gần nhất", device.lastSeenAt.takeIf { it > 0L }?.let { accessDate(it) })
                    AccessInfoLine("Gỡ lúc", device.revokedAt?.takeIf { it > 0L }?.let { accessDate(it) })
                    AccessInfoLine("Lý do gỡ", reason)
                    AccessInfoLine("Trạng thái tài khoản", statusText(device.customerStatus))
                    AccessInfoLine("Trạng thái giấy phép", device.licenseStatus?.let(::statusText))
                    AccessInfoLine("Số thiết bị đang hoạt động", "${device.activeDeviceCount}/${device.maxDevices ?: "—"}")
                }
            },
            confirmButton = { TextButton(onClick = { deviceInfo = null }) { Text("Đóng") } }
        )
    }

    pendingConfirmation?.let { confirmation ->
        AlertDialog(
            onDismissRequest = { if (!saving) pendingConfirmation = null },
            title = { Text(confirmation.title) },
            text = { Text(confirmation.message, fontSize = 13.sp) },
            confirmButton = {
                TextButton(
                    enabled = !saving,
                    onClick = {
                        val action = pendingConfirmation ?: return@TextButton
                        scope.launch {
                            saving = true
                            try {
                                action.execute()
                                pendingConfirmation = null
                                refresh()
                            } catch (exception: Exception) {
                                error = localizedErrorMessage(exception, "Không thực hiện được thao tác.")
                            } finally {
                                saving = false
                            }
                        }
                    },
                    colors = if (confirmation.destructive) ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error) else ButtonDefaults.textButtonColors()
                ) { Text(if (saving) "Đang xử lý…" else confirmation.confirmLabel) }
            },
            dismissButton = {
                TextButton(onClick = { pendingConfirmation = null }, enabled = !saving) { Text("Hủy") }
            }
        )
    }

    createFor?.let { customer ->
        AlertDialog(
            onDismissRequest = { if (!saving) createFor = null },
            title = { Text("Cấp giấy phép") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text(customer.email, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(createPlan, { createPlan = it.uppercase().take(32) }, label = { Text("Mã gói") }, singleLine = true)
                    OutlinedTextField(createDays, { createDays = it.filter(Char::isDigit).take(4) }, label = { Text("Thời hạn (ngày)") }, singleLine = true)
                    OutlinedTextField(createDeviceLimit, { createDeviceLimit = it.filter(Char::isDigit).take(2) }, label = { Text("Số thiết bị tối đa") }, singleLine = true)
                    Text("Giấy phép chỉ có hiệu lực sau khi máy chủ lưu thành công.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !saving && createPlan.matches(Regex("[A-Z0-9_-]{2,32}")) &&
                        (createDays.toIntOrNull() ?: 0) in 1..3650 &&
                        (createDeviceLimit.toIntOrNull() ?: 0) in 1..20,
                    onClick = {
                        val token = accessToken ?: return@TextButton
                        scope.launch {
                            saving = true
                            try {
                                val expiry = System.currentTimeMillis() / 1000 + createDays.toLong() * 86400L
                                repository.createCustomerLicense(token, customer.userId, createPlan, expiry, createDeviceLimit.toInt())
                                createFor = null
                                notice = "Đã cấp giấy phép cho ${customer.email}."
                                refresh()
                            } catch (exception: Exception) {
                                error = localizedErrorMessage(exception, "Không cấp được giấy phép.")
                            } finally {
                                saving = false
                            }
                        }
                    }
                ) { Text(if (saving) "Đang lưu…" else "Xác nhận cấp") }
            },
            dismissButton = { TextButton(onClick = { createFor = null }, enabled = !saving) { Text("Hủy") } }
        )
    }

    editLicense?.let { license ->
        AlertDialog(
            onDismissRequest = { if (!saving) editLicense = null },
            title = { Text("Gia hạn và giới hạn thiết bị") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("${license.email} · ${planText(license.planCode)}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Hạn hiện tại: ${accessDate(license.expiresAt)}", fontSize = 12.sp)
                    OutlinedTextField(extendDays, { extendDays = it.filter(Char::isDigit).take(4) }, label = { Text("Gia hạn thêm (ngày)") }, singleLine = true)
                    OutlinedTextField(editDeviceLimit, { editDeviceLimit = it.filter(Char::isDigit).take(2) }, label = { Text("Giới hạn thiết bị (1–20)") }, singleLine = true)
                    Text("Gia hạn tính từ ngày hết hạn hiện tại; nếu đã hết hạn thì tính từ hôm nay. Tăng giới hạn không tự đăng ký thêm thiết bị.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !saving &&
                        (extendDays.toIntOrNull() ?: -1) in 0..3650 &&
                        (editDeviceLimit.toIntOrNull() ?: 0) in 1..20 &&
                        (license.expiresAt > System.currentTimeMillis() / 1000 || (extendDays.toIntOrNull() ?: 0) > 0),
                    onClick = {
                        val token = accessToken ?: return@TextButton
                        scope.launch {
                            saving = true
                            try {
                                val days = extendDays.toLong()
                                val newExpiry = maxOf(license.expiresAt, System.currentTimeMillis() / 1000) + days * 86400L
                                val nextStatus = if (license.status == "PAUSED") "PAUSED" else "ACTIVE"
                                repository.updateCustomerLicense(token, license.id, nextStatus, newExpiry, editDeviceLimit.toInt())
                                editLicense = null
                                notice = "Đã cập nhật giấy phép."
                                refresh()
                            } catch (exception: Exception) {
                                error = localizedErrorMessage(exception, "Không cập nhật được giấy phép.")
                            } finally {
                                saving = false
                            }
                        }
                    }
                ) { Text(if (saving) "Đang lưu…" else "Lưu thay đổi") }
            },
            dismissButton = { TextButton(onClick = { editLicense = null }, enabled = !saving) { Text("Hủy") } }
        )
    }
}

@Composable
private fun AccessMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 9.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(value, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun accessDate(value: Long?): String {
    if (value == null || value <= 0L) return "Chưa có"
    return java.text.DateFormat.getDateTimeInstance(
        java.text.DateFormat.SHORT,
        java.text.DateFormat.SHORT
    ).format(java.util.Date(value * 1000L))
}


@Composable
private fun AccessInfoLine(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Text(
        "$label: $value",
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
