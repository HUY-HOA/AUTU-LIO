package com.autulio.app

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.autulio.app.access.AccessPermission
import com.autulio.app.access.AccessRole
import com.autulio.app.access.allows
import com.autulio.app.access.labelText

@Composable
internal fun CollaboratorWorkspace(
    role: AccessRole,
    permissions: Set<AccessPermission>,
    email: String,
    onLogout: () -> Unit
) {
    when (role) {
        AccessRole.DEVELOPER -> DeveloperWorkspace(email, permissions, onLogout)
        AccessRole.DESIGNER -> DesignerWorkspace(email, permissions, onLogout)
        AccessRole.REVIEWER -> ReviewerWorkspace(email, permissions, onLogout)
        else -> CustomerAccessFallback(email, onLogout)
    }
}

@Composable
private fun DeveloperWorkspace(
    email: String,
    permissions: Set<AccessPermission>,
    onLogout: () -> Unit
) {
    val canEditScripts = AccessPermission.EDIT_SCRIPTS in permissions
    val canTestScripts = AccessPermission.TEST_SCRIPTS in permissions
    val canSubmitRelease = AccessPermission.SUBMIT_RELEASE in permissions
    val context = LocalContext.current
    val preferences = remember(context) {
        context.getSharedPreferences("autulio_developer_drafts", Context.MODE_PRIVATE)
    }
    var name by rememberSaveable {
        mutableStateOf(preferences.getString("draft_name", "") ?: "")
    }
    var code by rememberSaveable {
        mutableStateOf(preferences.getString("draft_code", "") ?: "")
    }
    var message by rememberSaveable { mutableStateOf("") }

    AppTheme(dark = true) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                WorkspaceHeader(
                    title = tr("Không gian nhà phát triển", "Developer workspace"),
                    email = email,
                    onLogout = onLogout
                )
            }
        ) { padding ->
            Column(
                Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(13.dp)
            ) {
                Heading(
                    tr("Trình soạn thảo kịch bản", "Script editor"),
                    tr("Bạn có thể viết và kiểm thử bản nháp trong phạm vi được giao.", "Write and test drafts within your assigned scope.")
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; message = "" },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(tr("Tên kịch bản", "Script name")) },
                    enabled = canEditScripts
                )
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it; message = "" },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp),
                    label = { Text(tr("Mã kịch bản AUTU-LIO", "AUTU-LIO script source")) },
                    placeholder = { Text(tr("Nhập mã kịch bản tại đây…", "Write the script here…")) },
                    enabled = canEditScripts
                )
                Button(
                    onClick = {
                        preferences.edit()
                            .putString("draft_name", name.trim())
                            .putString("draft_code", code)
                            .apply()
                        message = tr(
                            "Đã lưu bản nháp trên thiết bị. Chưa đồng bộ máy chủ hoặc gửi duyệt.",
                            "Draft saved on this device. It has not been synced or submitted for review."
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = canEditScripts && name.isNotBlank() && code.isNotBlank()
                ) {
                    Icon(Icons.Outlined.Save, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(tr("Lưu bản nháp", "Save draft"))
                }
                if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                WorkspaceNotice(
                    tr(
                        "Quyền nhà phát triển không bao gồm quản lý cộng tác viên, giấy phép, khóa ký hoặc phát hành chính thức. Bản nháp hiện chỉ lưu cục bộ.",
                        "Developer access does not include collaborator management, licenses, signing keys or final publishing. Drafts are currently local only."
                    )
                )
                CardBox {
                    Text(tr("Quyền đã cấp", "Granted permissions"), fontWeight = FontWeight.SemiBold)
                    Text(
                        permissions.filter { it in setOf(AccessPermission.EDIT_SCRIPTS, AccessPermission.TEST_SCRIPTS, AccessPermission.SUBMIT_RELEASE) }
                            .joinToString(" · ") { it.labelText() }
                            .ifBlank { tr("Chưa được cấp quyền thao tác kịch bản.", "No script permissions have been granted.") },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                    if (!canEditScripts) {
                        Text(tr("Bạn chỉ có thể xem màn hình; quyền sửa mã hiện đã bị tắt.", "You can only view this screen; script editing is disabled."), color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }
                    if (canTestScripts || canSubmitRelease) {
                        Text(
                            listOfNotNull(
                                if (canTestScripts) tr("Có quyền kiểm thử", "Testing allowed") else null,
                                if (canSubmitRelease) tr("Có quyền gửi duyệt", "Submission allowed") else null
                            ).joinToString(" · "),
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DesignerWorkspace(
    email: String,
    permissions: Set<AccessPermission>,
    onLogout: () -> Unit
) {
    val canEditUi = AccessPermission.EDIT_UI_DESIGN in permissions
    val canPreviewUi = AccessPermission.PREVIEW_UI in permissions
    var accentIndex by rememberSaveable { mutableIntStateOf(0) }
    val accents = listOf(
        Color(0xFF4F8CFF),
        Color(0xFF00A68C),
        Color(0xFF8B5CF6)
    )
    val accent = accents[accentIndex]

    AppTheme(dark = true) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                WorkspaceHeader(
                    title = tr("Không gian nhà thiết kế", "Designer workspace"),
                    email = email,
                    onLogout = onLogout
                )
            }
        ) { padding ->
            Column(
                Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Heading(
                    tr("Bản xem trước giao diện", "Interface preview"),
                    tr("Thiết kế bố cục và màu sắc trong khu vực được giao.", "Preview layout and color choices within your assigned scope.")
                )
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(color = accent.copy(alpha = 0.16f), shape = RoundedCornerShape(10.dp)) {
                                Icon(Icons.Outlined.TouchApp, null, tint = accent, modifier = Modifier.padding(10.dp))
                            }
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text("AUTU-LIO", fontWeight = FontWeight.Bold)
                                Text(tr("Tự động hóa thao tác", "Automation workspace"), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                            }
                        }
                        Text(tr("Kịch bản của bạn", "Your scripts"), fontWeight = FontWeight.SemiBold)
                        Surface(
                            color = accent.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.PlayCircle, null, tint = accent)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(tr("Kịch bản mẫu", "Sample script"), fontWeight = FontWeight.SemiBold)
                                    Text(tr("Bản xem trước — chưa phát hành", "Preview — not published"), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Icon(Icons.Outlined.ChevronRight, null)
                            }
                        }
                        Button(onClick = {}, enabled = false, colors = ButtonDefaults.buttonColors(disabledContainerColor = accent.copy(alpha = 0.25f))) {
                            Text(tr("Nút xem trước", "Preview button"))
                        }
                    }
                }
                Text(tr("Chọn màu nhấn", "Choose accent color"), fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    accents.forEachIndexed { index, color ->
                        FilterChip(
                            selected = accentIndex == index,
                            onClick = { accentIndex = index },
                            enabled = canEditUi,
                            label = { Text(listOf("Blue", "Green", "Violet")[index]) },
                            leadingIcon = { Surface(Modifier.size(12.dp), color = color, shape = RoundedCornerShape(50)) {} }
                        )
                    }
                }
                WorkspaceNotice(
                    tr(
                        "Thay đổi hiện chỉ là bản xem trước cục bộ. Vai trò nhà thiết kế không được xem hoặc sửa mã kịch bản, giấy phép hay phát hành.",
                        "Changes are local previews only. Designer access cannot view or edit script source, licenses or releases."
                    )
                )
                CardBox {
                    Text(tr("Quyền đã cấp", "Granted permissions"), fontWeight = FontWeight.SemiBold)
                    Text(
                        permissions.filter { it in setOf(AccessPermission.EDIT_UI_DESIGN, AccessPermission.PREVIEW_UI) }
                            .joinToString(" · ") { it.labelText() }
                            .ifBlank { tr("Chưa được cấp quyền thiết kế hoặc xem trước.", "No design or preview permissions have been granted.") },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                    if (!canEditUi) Text(tr("Quyền chỉnh sửa giao diện đã bị tắt.", "Interface editing is disabled."), color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    if (!canPreviewUi) Text(tr("Quyền xem trước giao diện đã bị tắt.", "Interface preview is disabled."), color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun ReviewerWorkspace(
    email: String,
    permissions: Set<AccessPermission>,
    onLogout: () -> Unit
) {
    val canTest = AccessPermission.TEST_SCRIPTS in permissions
    val canReview = AccessPermission.REVIEW_RELEASE in permissions
    AppTheme(dark = true) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                WorkspaceHeader(
                    title = tr("Không gian kiểm duyệt", "Review workspace"),
                    email = email,
                    onLogout = onLogout
                )
            }
        ) { padding ->
            Column(
                Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(13.dp)
            ) {
                Heading(
                    tr("Kiểm tra bản gửi duyệt", "Review submissions"),
                    tr("Bạn chỉ xem và đánh giá các bản được gửi vào hàng chờ.", "Review and evaluate submissions assigned to the review queue.")
                )
                WorkspaceNotice(
                    tr(
                        "Hiện chưa có bản phát hành nào được gửi duyệt. Khi kết nối máy chủ, các bản gửi duyệt sẽ xuất hiện ở đây.",
                        "There are no release submissions waiting for review. They will appear here after the server is connected."
                    )
                )
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text(tr("Quyền kiểm duyệt", "Review permissions"), fontWeight = FontWeight.SemiBold)
                        Text(tr("Xem mã được giao · Chạy kiểm thử · Duyệt hoặc từ chối đề xuất", "View assigned code · Run tests · Approve or reject proposals"), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(tr("Không có quyền phát hành cuối cùng hoặc quản lý tài khoản.", "No final publishing or account administration rights."), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                CardBox {
                    Text(tr("Quyền đã cấp", "Granted permissions"), fontWeight = FontWeight.SemiBold)
                    Text(
                        permissions.filter { it in setOf(AccessPermission.TEST_SCRIPTS, AccessPermission.REVIEW_RELEASE) }
                            .joinToString(" · ") { it.labelText() }
                            .ifBlank { tr("Chưa được cấp quyền kiểm thử hoặc duyệt.", "No testing or review permissions have been granted.") },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                    if (!canTest) Text(tr("Quyền kiểm thử đã bị tắt.", "Testing is disabled."), color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    if (!canReview) Text(tr("Quyền duyệt bản gửi đã bị tắt.", "Review permission is disabled."), color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun CustomerAccessFallback(email: String, onLogout: () -> Unit) {
    AppTheme(dark = true) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = { WorkspaceHeader(tr("Tài khoản", "Account"), email, onLogout) }
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
                Heading(tr("Quyền tài khoản không phù hợp", "Account role unavailable"), tr("Đăng xuất rồi đăng nhập lại hoặc liên hệ chủ ứng dụng.", "Sign out and sign in again or contact the app owner."))
            }
        }
    }
}

@Composable
private fun WorkspaceHeader(title: String, email: String, onLogout: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text(email, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
            }
            TextButton(onClick = onLogout) {
                Icon(Icons.Outlined.Logout, contentDescription = null)
                Spacer(Modifier.width(5.dp))
                Text(tr("Đăng xuất", "Sign out"))
            }
        }
    }
}

@Composable
private fun WorkspaceNotice(message: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Outlined.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(9.dp))
            Text(message, fontSize = 12.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
