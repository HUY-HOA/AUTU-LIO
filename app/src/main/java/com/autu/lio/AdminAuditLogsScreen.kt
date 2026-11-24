package com.autu.lio

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.autu.lio.auth.AuditLogRecord
import com.autu.lio.auth.AuthRepository
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun AdminAuditLogsScreen(
    accessToken: String?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val repository = remember(context) { AuthRepository(context) }
    val scope = rememberCoroutineScope()
    var logs by remember { mutableStateOf<List<AuditLogRecord>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var search by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("all") }

    suspend fun refresh() {
        val token = accessToken
        if (token.isNullOrBlank()) return
        loading = true
        error = ""
        try {
            logs = repository.listAdminAuditLogs(token, 100)
        } catch (exception: Exception) {
            error = localizedErrorMessage(exception, tr("Không tải được nhật ký hệ thống.", "Could not load system activity."))
        } finally {
            loading = false
        }
    }

    LaunchedEffect(accessToken) { refresh() }

    val filteredLogs = logs.filter { item ->
        val category = auditCategory(item.action)
        val matchesFilter = filter == "all" || category == filter
        val haystack = listOf(
            item.action,
            item.actorEmail,
            item.actorDisplayName.orEmpty(),
            item.targetType.orEmpty(),
            item.targetId.orEmpty(),
            item.metadataJson
        ).joinToString(" ").lowercase(Locale.ROOT)
        matchesFilter && (search.isBlank() || haystack.contains(search.trim().lowercase(Locale.ROOT)))
    }

    Column(
        modifier.fillMaxWidth().padding(bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    tr("Hoạt động gần đây", "Recent activity"),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp
                )
                Text(
                    tr("Tối đa 100 sự kiện mới nhất từ máy chủ", "Up to 100 latest events from the server"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
            }
            IconButton(
                onClick = { scope.launch { refresh() } },
                enabled = !loading && !accessToken.isNullOrBlank()
            ) {
                Icon(Icons.Outlined.Refresh, contentDescription = tr("Làm mới", "Refresh"))
            }
        }

        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            placeholder = { Text(tr("Tìm tài khoản, thao tác hoặc mã đối tượng", "Search account, action or target ID")) }
        )

        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            val filters = listOf(
                "all" to tr("Tất cả", "All"),
                "account" to tr("Tài khoản", "Account"),
                "collaboration" to tr("Cộng tác", "Collaboration"),
                "license" to tr("Giấy phép", "Licenses"),
                "device" to tr("Thiết bị", "Devices"),
                "package" to tr("Gói AUTU", "AUTU packages"),
                "other" to tr("Khác", "Other")
            )
            filters.forEach { (key, label) ->
                FilterChip(
                    selected = filter == key,
                    onClick = { filter = key },
                    label = { Text(label) }
                )
            }
        }

        if (accessToken.isNullOrBlank()) {
            CardBox {
                Text(tr("Cần đăng nhập bằng tài khoản chủ sở hữu", "OWNER sign-in required"), fontWeight = FontWeight.SemiBold)
                Text(
                    tr("Nhật ký quản trị chỉ hiển thị cho tài khoản chủ sở hữu đã được máy chủ xác minh.", "Audit logs are available only to an owner account verified by the server."),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }
        } else if (loading && logs.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            if (error.isNotBlank()) {
                CardBox {
                    Text(error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    OutlinedButton(
                        onClick = { scope.launch { refresh() } },
                        enabled = !loading,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(tr("Thử tải lại", "Retry")) }
                }
            } else if (filteredLogs.isEmpty()) {
                EmptyState(
                    Icons.Outlined.History,
                    if (logs.isEmpty()) tr("Chưa có hoạt động được ghi nhận", "No recorded activity yet")
                    else tr("Không tìm thấy kết quả", "No matching activity"),
                    if (logs.isEmpty()) tr("Các sự kiện sẽ xuất hiện sau khi có thao tác trên máy chủ.", "Events will appear after server-side activity occurs.")
                    else tr("Thử đổi từ khóa hoặc bộ lọc.", "Try another search term or filter.")
                )
            } else {
                Text(
                    tr("Hiển thị " + filteredLogs.size + "/" + logs.size + " sự kiện", "Showing " + filteredLogs.size + "/" + logs.size + " events"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
                filteredLogs.forEach { item ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(
                            Modifier.fillMaxWidth().padding(13.dp),
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    auditActionTitle(item.action),
                                    modifier = Modifier.weight(1f),
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp
                                )
                                Text(
                                    formatAuditTime(item.createdAt),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 10.sp
                                )
                            }
                            Text(
                                item.actorDisplayName?.takeIf { it.isNotBlank() }
                                    ?.let { it + " · " + item.actorEmail }
                                    ?: item.actorEmail.ifBlank { tr("Tài khoản hệ thống", "System account") },
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 11.sp
                            )
                            if (!item.targetType.isNullOrBlank() || !item.targetId.isNullOrBlank()) {
                                Text(
                                    listOfNotNull(item.targetType, item.targetId).joinToString(" · "),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp
                                )
                            }
                            if (item.metadataJson != "{}" && item.metadataJson.isNotBlank()) {
                                Text(
                                    item.metadataJson.take(500),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 10.sp,
                                    lineHeight = 14.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun auditCategory(action: String): String = when {
    action.startsWith("auth.") -> "account"
    action.startsWith("collaborator.") || action.startsWith("project.") -> "collaboration"
    action.startsWith("license.") -> "license"
    action.startsWith("device.") -> "device"
    action.startsWith("package.") -> "package"
    else -> "other"
}

private fun auditActionTitle(action: String): String = when (action) {
    "auth.google_sign_in" -> tr("Đăng nhập Google", "Google sign-in")
    "auth.logout" -> tr("Đăng xuất", "Sign out")
    "collaborator.invite_created" -> tr("Tạo lời mời cộng tác", "Create collaborator invitation")
    "collaborator.invite_accepted" -> tr("Chấp nhận lời mời", "Accept invitation")
    "collaborator.invitation_revoked" -> tr("Hủy lời mời", "Revoke invitation")
    "collaborator.role_changed" -> tr("Thay đổi vai trò", "Change collaborator role")
    "collaborator.permissions_updated" -> tr("Cập nhật quyền", "Update permissions")
    "collaborator.access_revoked" -> tr("Thu hồi quyền cộng tác", "Revoke collaborator access")
    "project.created" -> tr("Tạo dự án", "Create project")
    "project.member_added" -> tr("Thêm thành viên dự án", "Add project member")
    "project.member_removed" -> tr("Gỡ thành viên dự án", "Remove project member")
    "project.script_created" -> tr("Tạo AUTU trong dự án", "Create project AUTU")
    "project.script_updated" -> tr("Cập nhật AUTU trong dự án", "Update project AUTU")
    "project.script_deleted" -> tr("Xóa AUTU trong dự án", "Delete project AUTU")
    "project.ui_draft_saved" -> tr("Lưu bản thiết kế", "Save UI design")
    "project.change_submitted" -> tr("Gửi nội dung duyệt", "Submit changes for review")
    "project.submission_reviewed" -> tr("Đánh giá nội dung", "Review submitted changes")
    "license.created" -> tr("Cấp giấy phép", "Issue license")
    "license.updated" -> tr("Cập nhật giấy phép", "Update license")
    "device.revoked_by_owner" -> tr("Chủ sở hữu thu hồi thiết bị", "Owner revokes device")
    "device.registered" -> tr("Đăng ký thiết bị", "Register device")
    "device.removed_by_customer" -> tr("Khách hàng gỡ thiết bị", "Customer removes device")
    "package.published" -> tr("Phát hành gói AUTU", "Publish AUTU package")
    "package.version_published" -> tr("Phát hành phiên bản AUTU", "Publish AUTU version")
    "package.status_changed" -> tr("Thay đổi trạng thái gói AUTU", "Change AUTU package status")
    "package.downloaded" -> tr("Khách hàng tải gói AUTU", "Customer downloads AUTU package")
    else -> tr("Sự kiện khác", "Other event")
}

private fun formatAuditTime(epochSeconds: Long): String {
    if (epochSeconds <= 0L) return "—"
    return SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.forLanguageTag("vi-VN"))
        .format(Date(epochSeconds * 1_000L))
}
