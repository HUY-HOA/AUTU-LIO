package com.autulio.app

import android.content.Context
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.PhoneAndroid
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
import com.autulio.app.auth.AuthenticatedUser
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
    var ownerUser by remember { mutableStateOf<AuthenticatedUser?>(null) }
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
    var restoreDeviceTarget by remember { mutableStateOf<DeviceRecord?>(null) }
    var restoringDevice by remember { mutableStateOf(false) }
    var createPlan by remember { mutableStateOf("MONTHLY") }
    var createPlanExpanded by remember { mutableStateOf(false) }
    var createDays by remember { mutableStateOf("30") }
    var createDeviceLimit by remember { mutableStateOf("1") }
    var extendDays by remember { mutableStateOf("30") }
    var editDeviceLimit by remember { mutableStateOf("1") }

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
            ownerUser = runCatching { repository.currentUser(token) }.getOrNull()
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
            ownerUser?.let { currentOwner ->
                GoogleSignInDetailsCard(
                    details = currentOwner.googleSignIn,
                    title = "Thông tin đăng nhập Google của chủ ứng dụng"
                )
            }

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
                val statusOptions = when (section) {
                    "customers" -> listOf(
                        "ALL" to "Tất cả",
                        "ACTIVE" to "Đang hoạt động",
                        "SUSPENDED" to "Đã chặn đăng nhập",
                        "REVOKED" to "Đã thu hồi đăng nhập"
                    )
                    "licenses" -> listOf(
                        "ALL" to "Tất cả",
                        "ACTIVE" to "Đang hiệu lực",
                        "PAUSED" to "Tạm dừng",
                        "EXPIRED" to "Hết hạn",
                        "REVOKED" to "Đã thu hồi"
                    )
                    else -> listOf(
                        "ALL" to "Tất cả",
                        "ACTIVE" to "Được cấp quyền",
                        "REVOKED" to "Đã thu hồi thiết bị"
                    )
                }
                statusOptions.forEach { item ->
                    FilterChip(
                        selected = statusFilter == item.first,
                        onClick = { statusFilter = item.first },
                        label = { Text(item.second, fontSize = 11.sp) }
                    )
                }
            }

            Text(
                when (section) {
                    "customers" -> "ĐĂNG NHẬP: điều khiển trạng thái tài khoản hoặc thu hồi phiên hiện tại. Các thao tác này không sửa giấy phép/thiết bị."
                    "licenses" -> "GIẤY PHÉP: chỉ điều khiển quyền tải/chạy AUTU. Tạm dừng, thu hồi hoặc xóa giấy phép không đăng xuất hay khóa tài khoản."
                    else -> "THIẾT BỊ: chỉ thu hồi quyền của thiết bị này; không thay đổi phiên đăng nhập, tài khoản hoặc giấy phép."
                },
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
                                        AdminIconValue(
                                            Icons.Outlined.VerifiedUser,
                                            "Gói và trạng thái giấy phép",
                                            "${planText(license.planCode)} · ${statusText(license.status)}"
                                        )
                                    }
                                    AdminIconValue(
                                        Icons.Outlined.Devices,
                                        "Thiết bị đang được cấp quyền",
                                        "${license.activeDeviceCount}/${license.maxDevices}"
                                    )
                                }
                                AdminIconValue(
                                    Icons.Outlined.AccessTime,
                                    "Thời hạn giấy phép",
                                    accessDate(license.expiresAt)
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
                                    ) { Text("Gia hạn / giới hạn thiết bị", fontSize = 11.sp) }
                                    if (license.status == "ACTIVE" || license.status == "PAUSED") {
                                        TextButton(
                                            onClick = {
                                                val token = accessToken ?: return@TextButton
                                                val next = if (license.status == "ACTIVE") "PAUSED" else "ACTIVE"
                                                pendingConfirmation = AccessAdminConfirmation(
                                                    title = if (next == "PAUSED") "Tạm dừng giấy phép?" else "Kích hoạt lại giấy phép?",
                                                    message = if (next == "PAUSED") "Người dùng vẫn có thể đăng nhập, nhưng không thể tải hoặc chạy các gói AUTU cho đến khi giấy phép được kích hoạt lại." else "Giấy phép được kích hoạt lại. Người dùng có thể tải và chạy AUTU nếu tài khoản, thiết bị và thời hạn đều hợp lệ.",
                                                    confirmLabel = if (next == "PAUSED") "Tạm dừng giấy phép" else "Kích hoạt lại giấy phép",
                                                    destructive = next == "PAUSED"
                                                ) {
                                                    repository.updateCustomerLicense(token, license.id, next, license.expiresAt, license.maxDevices)
                                                    notice = if (next == "PAUSED") "Đã tạm dừng giấy phép." else "Đã kích hoạt lại giấy phép."
                                                }
                                            }
                                        ) { Text(if (license.status == "ACTIVE") "Tạm dừng giấy phép" else "Kích hoạt lại giấy phép", fontSize = 11.sp) }
                                    }
                                }
                                if (license.status != "REVOKED") {
                                    TextButton(
                                        onClick = {
                                            val token = accessToken ?: return@TextButton
                                            pendingConfirmation = AccessAdminConfirmation(
                                                title = "Thu hồi giấy phép này?",
                                                message = "Chỉ giấy phép này bị thu hồi, người dùng không thể tải hoặc chạy AUTU. Tài khoản vẫn đăng nhập được; các phiên hiện tại và thiết bị đã đăng ký không bị thay đổi. Muốn sử dụng lại cần cấp giấy phép mới.",
                                                confirmLabel = "Thu hồi giấy phép"
                                            ) {
                                                repository.updateCustomerLicense(token, license.id, "REVOKED", license.expiresAt, license.maxDevices)
                                                notice = "Đã thu hồi giấy phép; phiên đăng nhập và thiết bị được giữ nguyên."
                                            }
                                        },
                                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                    ) { Text("Thu hồi giấy phép", fontSize = 11.sp) }
                                }
                                TextButton(
                                    onClick = {
                                        val token = accessToken ?: return@TextButton
                                        pendingConfirmation = AccessAdminConfirmation(
                                            title = "Xóa hẳn giấy phép này?",
                                            message = "Chỉ bản ghi giấy phép này sẽ bị xóa vĩnh viễn. Tài khoản và các phiên đăng nhập vẫn giữ nguyên; thiết bị đã đăng ký không bị xóa hoặc thu hồi. Nhật ký thao tác xóa vẫn được lưu để đối soát. Sau khi xóa, người dùng không còn giấy phép này để truy cập AUTU.",
                                            confirmLabel = "Xóa giấy phép"
                                        ) {
                                            repository.deleteCustomerLicense(token, license.id)
                                            notice = "Đã xóa giấy phép; tài khoản, phiên đăng nhập và thiết bị không thay đổi."
                                        }
                                    },
                                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                ) { Text("Xóa giấy phép", fontSize = 11.sp) }
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
                            CardBox {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(device.label.ifBlank { "${device.manufacturer} ${device.model}".trim().ifBlank { device.email } }, fontWeight = FontWeight.SemiBold)
                                        Text(device.email, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Text(
                                        if (device.status == "ACTIVE") "Đã cấp quyền" else "Đã thu hồi quyền",
                                        color = if (device.status == "ACTIVE") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    AdminIconValue(
                                        Icons.Outlined.PhoneAndroid,
                                        "Thiết bị Android",
                                        "${device.manufacturer} ${device.model} · Android ${device.androidVersion}"
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    AdminIconValue(Icons.Outlined.Apps, "Phiên bản AUTU", "AUTU-LIO ${device.appVersion}")
                                    AdminIconValue(Icons.Outlined.AccessTime, "Kiểm tra quyền", accessDate(device.lastSeenAt))
                                }
                                AdminPresenceInfo(device.presenceSeenAt, device.appForegroundAt, device.runtimeState, device.status == "ACTIVE" && device.customerStatus == "ACTIVE")
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    AdminIconValue(Icons.Outlined.Group, "Tài khoản", statusText(device.customerStatus))
                                    AdminIconValue(
                                        Icons.Outlined.VerifiedUser,
                                        "Giấy phép",
                                        device.licenseStatus?.let(::statusText) ?: "Chưa có"
                                    )
                                    AdminIconValue(Icons.Outlined.Devices, "Thiết bị", "${device.activeDeviceCount}/${device.maxDevices ?: 0}")
                                }
                                if (device.status == "REVOKED") {
                                    val reasonText = when (device.revokedReason) {
                                        "OWNER_REVOKED" -> "Chủ ứng dụng đã thu hồi"
                                        "ACCOUNT_REVOKED" -> "Tài khoản đã thu hồi"
                                        "CUSTOMER_REMOVED" -> "Khách hàng tự gỡ"
                                        "LEGACY_REVOKED" -> "Thu hồi từ phiên bản cũ"
                                        else -> "Không rõ lý do"
                                    }
                                    Text("Lý do thu hồi: $reasonText", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                                    OutlinedButton(
                                        onClick = { restoreDeviceTarget = device },
                                        enabled = !restoringDevice,
                                        modifier = Modifier.fillMaxWidth()
                                    ) { Text("CHO PHÉP LẠI THIẾT BỊ") }
                                    deviceRestoreBlocker(device)?.let { blocked ->
                                        Text(
                                            "Chưa thể mở ngay: $blocked",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                } else {
                                    OutlinedButton(
                                        modifier = Modifier.fillMaxWidth(),
                                        onClick = {
                                            val token = accessToken ?: return@OutlinedButton
                                            pendingConfirmation = AccessAdminConfirmation(
                                                title = "Chặn thiết bị này?",
                                                message = "Thiết bị sẽ không còn được phép tải hoặc chạy các gói AUTU. Thông tin đăng ký và khóa bảo mật vẫn được giữ, không bị xóa. Chủ ứng dụng có thể cho phép lại thiết bị khi tài khoản, giấy phép và giới hạn thiết bị hợp lệ.",
                                                confirmLabel = "Chặn thiết bị"
                                            ) {
                                                repository.revokeCustomerDevice(token, device.id)
                                                notice = "Đã chặn thiết bị. Thông tin đăng ký vẫn được giữ."
                                            }
                                        },
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                    ) { Text("CHẶN THIẾT BỊ") }
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
                                    Text(
                                        when (customer.status) {
                                            "ACTIVE" -> "Đang hoạt động"
                                            "SUSPENDED" -> "Đã chặn đăng nhập"
                                            "REVOKED" -> "Đã thu hồi đăng nhập"
                                            else -> statusText(customer.status)
                                        },
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                GoogleSignInDetailsCard(
                                    details = customer.googleSignIn,
                                    title = "Dữ liệu Google và lần đăng nhập gần nhất"
                                )
                                AdminIconValue(
                                    Icons.Outlined.VerifiedUser,
                                    "Giấy phép",
                                    customer.licenseId?.let {
                                        "${customer.planCode?.let(::planText) ?: "Gói sử dụng"} · ${customer.licenseStatus?.let(::statusText) ?: "Chưa rõ"} · ${accessDate(customer.licenseExpiresAt)}"
                                    } ?: "Chưa có"
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    AdminIconValue(Icons.Outlined.Devices, "Số thiết bị", "${customer.activeDeviceCount}/${customer.maxDevices ?: 0}")
                                    AdminIconValue(Icons.Outlined.AccessTime, "Đăng nhập gần nhất", accessDate(customer.lastLoginAt))
                                }
                                AdminPresenceInfo(customer.presenceSeenAt, customer.appForegroundAt, customer.runtimeState, customer.status == "ACTIVE")
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    if ((customer.licenseId == null || customer.licenseStatus == "REVOKED") && customer.status == "ACTIVE") {
                                        OutlinedButton(
                                            onClick = {
                                                createFor = customer
                                                createPlan = "MONTHLY"
                                                createPlanExpanded = false
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
                                            val title = when {
                                                nextStatus == "SUSPENDED" -> "Chặn đăng nhập tài khoản?"
                                                customer.status == "REVOKED" -> "Kích hoạt tài khoản?"
                                                else -> "Cho phép đăng nhập lại?"
                                            }
                                            val message = when {
                                                nextStatus == "SUSPENDED" -> "Người dùng sẽ bị đăng xuất khỏi các phiên hiện tại và không thể đăng nhập lại. Giấy phép và thiết bị đã đăng ký vẫn được giữ nguyên. Bạn có thể cho phép đăng nhập lại sau."
                                                customer.status == "REVOKED" -> "Tài khoản được mở lại để có thể đăng nhập Google. Giấy phép và thiết bị vẫn giữ nguyên trạng thái trước đó; các phiên cũ đã bị thu hồi nên người dùng phải đăng nhập lại."
                                                else -> "Tài khoản được phép đăng nhập lại. Các phiên cũ đã bị chấm dứt nên người dùng cần đăng nhập mới. Giấy phép và thiết bị vẫn được giữ nguyên."
                                            }
                                            pendingConfirmation = AccessAdminConfirmation(
                                                title = title,
                                                message = message,
                                                confirmLabel = when {
                                                    nextStatus == "SUSPENDED" -> "Chặn đăng nhập"
                                                    customer.status == "REVOKED" -> "Kích hoạt tài khoản"
                                                    else -> "Cho phép đăng nhập"
                                                },
                                                destructive = nextStatus == "SUSPENDED"
                                            ) {
                                                repository.updateCustomerStatus(token, customer.userId, nextStatus)
                                                notice = when {
                                                    nextStatus == "SUSPENDED" -> "Đã chặn đăng nhập tài khoản."
                                                    customer.status == "REVOKED" -> "Đã mở lại đăng nhập. Giấy phép và thiết bị không bị thay đổi."
                                                    else -> "Đã cho phép đăng nhập lại."
                                                }
                                            }
                                        }
                                    ) { Text(
                                        when {
                                            customer.status == "ACTIVE" -> "Chặn đăng nhập"
                                            customer.status == "REVOKED" -> "Kích hoạt tài khoản"
                                            else -> "Cho phép đăng nhập"
                                        },
                                        fontSize = 11.sp
                                    ) }
                                    if (customer.status == "ACTIVE") {
                                        TextButton(
                                            onClick = {
                                                val token = accessToken ?: return@TextButton
                                                pendingConfirmation = AccessAdminConfirmation(
                                                    title = "Đăng xuất khỏi tất cả phiên?",
                                                    message = "Chỉ các phiên đăng nhập hiện tại của tài khoản này sẽ bị thu hồi trên mọi thiết bị. Tài khoản vẫn ở trạng thái hoạt động và người dùng có thể đăng nhập Google lại ngay. Giấy phép, trạng thái tài khoản và thiết bị đã đăng ký không bị thay đổi.",
                                                    confirmLabel = "Thu hồi các phiên"
                                                ) {
                                                    val count = repository.revokeCustomerSessions(token, customer.userId)
                                                    notice = "Đã thu hồi $count phiên đăng nhập. Tài khoản, giấy phép và thiết bị vẫn giữ nguyên."
                                                }
                                            },
                                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                        ) { Text("Đăng xuất mọi phiên", fontSize = 11.sp) }
                                    }
                                }
                                if (customer.status != "REVOKED") {
                                    TextButton(
                                        onClick = {
                                            val token = accessToken ?: return@TextButton
                                            pendingConfirmation = AccessAdminConfirmation(
                                                title = "Thu hồi quyền đăng nhập tài khoản?",
                                                message = "Tài khoản sẽ không thể đăng nhập và tất cả phiên hiện tại sẽ bị thu hồi. Chỉ trạng thái tài khoản và phiên đăng nhập thay đổi; mọi giấy phép (kể cả ACTIVE/PAUSED/REVOKED) và đăng ký thiết bị được giữ nguyên, không tự sửa hoặc xóa.",
                                                confirmLabel = "Thu hồi đăng nhập"
                                            ) {
                                                repository.updateCustomerStatus(token, customer.userId, "REVOKED")
                                                notice = "Đã thu hồi quyền đăng nhập. Giấy phép và thiết bị được giữ nguyên."
                                            }
                                        },
                                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                    ) { Text("Thu hồi đăng nhập", fontSize = 11.sp) }
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

    restoreDeviceTarget?.let { device ->
        val blocked = deviceRestoreBlocker(device)
        AlertDialog(
            onDismissRequest = { if (!restoringDevice) restoreDeviceTarget = null },
            title = { Text("Cho phép lại thiết bị?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${device.email} · ${device.label.ifBlank { device.model }}", fontSize = 12.sp)
                    Text("Thiết bị được cấp lại quyền tải và chạy AUTU bằng khóa bảo mật đã đăng ký trước đây. Không tạo đăng ký hoặc khóa bảo mật mới.")
                    if (blocked != null) {
                        Text(
                            "Chưa thể cho phép thiết bị: $blocked",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp
                        )
                    } else {
                        Text(
                            "Thiết bị đủ điều kiện được cho phép lại. Khi khôi phục, thiết bị sẽ được tính vào giới hạn giấy phép.",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 12.sp
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = blocked == null && !restoringDevice,
                    onClick = {
                        val token = accessToken ?: return@Button
                        scope.launch {
                            restoringDevice = true
                            error = ""
                            try {
                                repository.updateCustomerDeviceStatus(token, device.id, "ACTIVE")
                                notice = "Đã cho phép lại thiết bị ${device.email}."
                                restoreDeviceTarget = null
                                refresh()
                            } catch (exception: Exception) {
                                error = localizedErrorMessage(exception, "Không thể cho phép lại thiết bị.")
                            } finally {
                                restoringDevice = false
                            }
                        }
                    }
                ) { Text(if (restoringDevice) "ĐANG CHO PHÉP…" else "CHO PHÉP LẠI THIẾT BỊ") }
            },
            dismissButton = {
                TextButton(
                    onClick = { restoreDeviceTarget = null },
                    enabled = !restoringDevice
                ) { Text("Đóng") }
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
                    Box(Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { createPlanExpanded = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Loại gói: ${planText(createPlan)}")
                        }
                        DropdownMenu(
                            expanded = createPlanExpanded,
                            onDismissRequest = { createPlanExpanded = false }
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
                                        createPlan = code
                                        createPlanExpanded = false
                                    }
                                )
                            }
                        }
                    }
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
        java.text.DateFormat.SHORT,
        java.util.Locale.forLanguageTag("vi-VN")
    ).format(java.util.Date(value * 1000L))
}


private fun deviceRestoreBlocker(device: DeviceRecord): String? = when {
    device.customerStatus != "ACTIVE" -> "Tài khoản đang bị chặn đăng nhập. Hãy cho phép đăng nhập hoặc kích hoạt lại tài khoản trước."
    device.licenseStatus != "ACTIVE" -> "Giấy phép chưa hoạt động hoặc đã hết hạn. Hãy cấp/kích hoạt giấy phép còn hiệu lực."
    device.activeDeviceCount >= (device.maxDevices ?: 0) -> "Đã hết giới hạn thiết bị. Hãy chặn thiết bị khác hoặc tăng giới hạn giấy phép."
    else -> null
}
