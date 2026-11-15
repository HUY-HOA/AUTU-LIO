package com.autulio.app

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
    accessToken: String,
    role: AccessRole,
    permissions: Set<AccessPermission>,
    email: String,
    onLogout: () -> Unit
) {
    var showSharedProjects by rememberSaveable { mutableStateOf(false) }
    if (showSharedProjects) {
        CollaboratorProjectsWorkspace(
            accessToken = accessToken,
            role = role,
            permissions = permissions,
            email = email,
            onBack = { showSharedProjects = false }
        )
        return
    }
    when (role) {
        AccessRole.DEVELOPER -> DeveloperWorkspace(email, permissions, onLogout) { showSharedProjects = true }
        AccessRole.DESIGNER -> DesignerWorkspace(email, permissions, onLogout) { showSharedProjects = true }
        AccessRole.REVIEWER -> ReviewerWorkspace(email, permissions, onLogout) { showSharedProjects = true }
        else -> CustomerAccessFallback(email, onLogout)
    }
}

@Composable
private fun DeveloperWorkspace(
    email: String,
    permissions: Set<AccessPermission>,
    onLogout: () -> Unit,
    onOpenProjects: () -> Unit
) {
    val canEditScripts = AccessPermission.EDIT_SCRIPTS in permissions
    val canTestScripts = AccessPermission.TEST_SCRIPTS in permissions
    val canSubmitRelease = AccessPermission.SUBMIT_RELEASE in permissions
    val context = LocalContext.current
    val draftStore = remember(context) { SecureScriptDraftStore(context, "developer") }
    val initialDrafts = remember(draftStore) { draftStore.load() }
    var draftNames by remember(draftStore) { mutableStateOf(initialDrafts.names) }
    var name by rememberSaveable {
        mutableStateOf(initialDrafts.names.firstOrNull().orEmpty())
    }
    var message by rememberSaveable { mutableStateOf("") }
    val editorLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        val refreshed = draftStore.load()
        draftNames = refreshed.names
        if (name.isBlank() && refreshed.names.isNotEmpty()) {
            name = refreshed.names.first()
        }
        message = tr(
            "Đã cập nhật danh sách bản nháp được mã hóa trên thiết bị.",
            "Encrypted local draft list has been refreshed."
        )
    }

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
                    tr("Kịch bản của nhà phát triển", "Developer scripts"),
                    tr("Mã nguồn được chỉnh sửa trong cửa sổ trình soạn thảo riêng.", "Source code is edited in a separate editor window.")
                )
                OutlinedButton(onClick = onOpenProjects, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.FolderOpen, null)
                    Spacer(Modifier.width(7.dp))
                    Text(tr("Mở dự án cộng tác", "Open shared projects"))
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; message = "" },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(tr("Tên kịch bản", "Script name")) },
                    enabled = canEditScripts
                )
                if (draftNames.isNotEmpty()) {
                    Text(
                        tr("Bản nháp đã lưu", "Saved drafts"),
                        fontWeight = FontWeight.SemiBold
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        draftNames.forEach { draftName ->
                            OutlinedButton(
                                onClick = { name = draftName; message = "" },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = canEditScripts
                            ) {
                                Icon(Icons.Outlined.Code, null)
                                Spacer(Modifier.width(8.dp))
                                Text(draftName)
                            }
                        }
                    }
                } else {
                    WorkspaceNotice(
                        tr("Chưa có bản nháp. Nhập tên rồi mở trình soạn thảo riêng để tạo kịch bản.", "No drafts yet. Enter a name and open the separate editor to create a script.")
                    )
                }
                Button(
                    onClick = {
                        val cleanName = name.trim()
                        if (cleanName.isNotEmpty()) {
                            val current = draftStore.load()
                            val names = (current.names + cleanName)
                                .distinctBy { it.lowercase(java.util.Locale.ROOT) }
                            draftStore.save(names, current.contents + (cleanName to current.contents[cleanName].orEmpty()))
                            draftNames = names
                            editorLauncher.launch(
                                ScriptEditorActivity.intent(
                                    context = context,
                                    scriptName = cleanName,
                                    namespace = "developer",
                                    allowTest = canTestScripts
                                )
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = canEditScripts && name.isNotBlank()
                ) {
                    Icon(Icons.Outlined.Edit, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(tr("Mở trình soạn thảo riêng", "Open separate script editor"))
                }
                if (message.isNotBlank()) {
                    Text(message, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                }
                WorkspaceNotice(
                    tr(
                        "Bản nháp nhà phát triển được mã hóa và lưu riêng, không dùng chung kho bản nháp của Chủ sở hữu. Lưu cục bộ chưa có nghĩa là đã đồng bộ hoặc gửi duyệt.",
                        "Developer drafts are encrypted and stored separately from Owner drafts. Local saving does not mean the draft has been synced or submitted for review."
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
                    if (canTestScripts) {
                        Text(
                            tr("Bạn được phép kiểm thử kịch bản.", "You are allowed to test scripts."),
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 11.sp
                        )
                    }
                    if (canSubmitRelease) {
                        Text(
                            tr("Bạn được phép gửi bản kịch bản để duyệt.", "You are allowed to submit scripts for review."),
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
    onLogout: () -> Unit,
    onOpenProjects: () -> Unit
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
                OutlinedButton(onClick = onOpenProjects, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.FolderOpen, null)
                    Spacer(Modifier.width(7.dp))
                    Text(tr("Mở dự án thiết kế", "Open design projects"))
                }
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
                        permissions.filter { it in setOf(AccessPermission.EDIT_UI_DESIGN, AccessPermission.PREVIEW_UI, AccessPermission.SUBMIT_RELEASE) }
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
    onLogout: () -> Unit,
    onOpenProjects: () -> Unit
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
                OutlinedButton(onClick = onOpenProjects, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.FolderOpen, null)
                    Spacer(Modifier.width(7.dp))
                    Text(tr("Mở hàng đợi dự án", "Open project review queue"))
                }
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
