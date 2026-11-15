package com.autulio.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.autulio.app.access.AccessPermission
import com.autulio.app.access.AccessRole
import com.autulio.app.access.labelText as permissionLabelText
import com.autulio.app.auth.AuthApiException
import com.autulio.app.auth.AuthRepository
import com.autulio.app.auth.CollaboratorDirectory
import com.autulio.app.auth.CollaboratorRecord
import com.autulio.app.auth.CollaboratorPermissionSettings
import com.autulio.app.auth.CreatedInvitation
import com.autulio.app.auth.PendingInvitation
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@Composable
internal fun CollaboratorsServerContent(
    accessToken: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val repository = remember(context) { AuthRepository(context) }
    val scope = rememberCoroutineScope()
    var directory by remember { mutableStateOf(CollaboratorDirectory(emptyList(), emptyList())) }
    var email by remember { mutableStateOf("") }
    var selectedRole by remember { mutableStateOf(AccessRole.DEVELOPER) }
    var createdInvitation by remember { mutableStateOf<CreatedInvitation?>(null) }
    var revokeUser by remember { mutableStateOf<CollaboratorRecord?>(null) }
    var revokeInvite by remember { mutableStateOf<PendingInvitation?>(null) }
    var roleMenuUserId by remember { mutableStateOf<String?>(null) }
    var permissionTarget by remember { mutableStateOf<CollaboratorRecord?>(null) }
    var permissionOptions by remember { mutableStateOf<Set<AccessPermission>>(emptySet()) }
    var permissionDraft by remember { mutableStateOf<Set<AccessPermission>>(emptySet()) }
    var showInviteForm by rememberSaveable { mutableStateOf(false) }
    var showPermissionGuide by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

    suspend fun refresh() {
        loading = true
        try {
            directory = repository.listCollaborators(accessToken)
            feedback = ""
            isError = false
        } catch (error: Exception) {
            feedback = error.message ?: tr("Không tải được danh sách cộng tác viên.", "Could not load collaborators.")
            isError = true
        } finally {
            loading = false
        }
    }

    LaunchedEffect(accessToken) {
        refresh()
    }

    val normalizedEmail = email.trim()
    val validEmail = android.util.Patterns.EMAIL_ADDRESS.matcher(normalizedEmail).matches()
    val duplicate = directory.collaborators.any { it.email.equals(normalizedEmail, true) } ||
        directory.pendingInvitations.any { it.email.equals(normalizedEmail, true) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(tr("Cộng tác viên", "Collaborators"), fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Text(
                    tr("Mời người cùng tạo AUTU và quản lý quyền", "Invite AUTU design collaborators and manage access"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
            }
            IconButton(onClick = { showPermissionGuide = true }) {
                Icon(Icons.Outlined.Security, contentDescription = tr("Xem hướng dẫn cấp quyền", "View access guide"))
            }
            FilledTonalIconButton(onClick = { showInviteForm = !showInviteForm }) {
                Icon(
                    if (showInviteForm) Icons.Outlined.Close else Icons.Outlined.PersonAdd,
                    contentDescription = if (showInviteForm) tr("Đóng biểu mẫu mời", "Close invitation form") else tr("Thêm cộng tác viên", "Add collaborator")
                )
            }
            IconButton(onClick = { scope.launch { refresh() } }, enabled = !loading) {
                Icon(Icons.Outlined.Refresh, contentDescription = tr("Làm mới", "Refresh"))
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Icon(Icons.Outlined.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
            Text(
                tr(
                    "Chỉ chủ sở hữu mới được cấp, sửa hoặc thu hồi quyền cộng tác.",
                    "Only the owner can grant, change or revoke collaborator access."
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp
            )
        }

        if (showInviteForm) {
            OutlinedTextField(
                value = email,
                onValueChange = { email = it; feedback = "" },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(tr("Địa chỉ Gmail", "Google account email")) },
                placeholder = { Text("name@gmail.com") },
                isError = email.isNotBlank() && !validEmail
            )
            Text(tr("Vai trò được cấp", "Role to assign"), fontWeight = FontWeight.SemiBold)
            collaboratorRoles.forEach { role ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (selectedRole == role) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(
                        1.dp,
                        if (selectedRole == role) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                    ),
                    shape = RoundedCornerShape(12.dp),
                    onClick = { selectedRole = role }
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = selectedRole == role, onClick = { selectedRole = role })
                        Column(Modifier.weight(1f)) {
                            Text(role.labelText(), fontWeight = FontWeight.SemiBold)
                            Text(role.descriptionText(), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                        }
                    }
                }
            }
            Button(
                onClick = {
                    scope.launch {
                        submitting = true
                        feedback = ""
                        try {
                            createdInvitation = repository.createInvitation(
                                accessToken = accessToken,
                                email = normalizedEmail,
                                role = selectedRole,
                                expiresInDays = 7
                            )
                            email = ""
                            showInviteForm = false
                            refresh()
                            feedback = tr("Đã tạo lời mời trên máy chủ. Hãy sao chép mã và gửi cho người được mời.", "Invitation created on the server. Copy the token and share it with the invitee.")
                            isError = false
                        } catch (error: Exception) {
                            feedback = error.message ?: tr("Không tạo được lời mời.", "Could not create invitation.")
                            isError = true
                        } finally {
                            submitting = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = validEmail && !duplicate && !submitting
            ) {
                if (submitting) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Outlined.PersonAdd, contentDescription = null)
                }
                Spacer(Modifier.width(8.dp))
                Text(tr("Tạo lời mời trên máy chủ", "Create server invitation"))
            }

        }

        if (feedback.isNotBlank()) {
            Text(feedback, color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, fontSize = 12.sp)
        }

        createdInvitation?.let { invitation ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(tr("Mã lời mời — chỉ hiển thị lần này", "Invitation token — shown once"), fontWeight = FontWeight.Bold)
                    Text("${invitation.email} · ${invitation.role.labelText()}", fontSize = 12.sp)
                    Text(invitation.invitationToken, fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                manager.setPrimaryClip(ClipData.newPlainText("AUTU-LIO invitation", invitation.invitationToken))
                                feedback = tr("Đã sao chép mã lời mời.", "Invitation token copied.")
                                isError = false
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Outlined.ContentCopy, null)
                            Spacer(Modifier.width(6.dp))
                            Text(tr("Sao chép mã", "Copy token"))
                        }
                        OutlinedButton(onClick = { createdInvitation = null }) {
                            Text(tr("Đóng", "Close"))
                        }
                    }
                    Text(
                        tr("Gửi mã này riêng cho người có Gmail ở trên. Người nhận phải đăng nhập đúng Gmail đó và nhập mã lời mời trong AUTU-LIO để chấp nhận.",
                            "Share this token privately with the Google account shown above. The invitee must sign in with that exact email and enter the token in AUTU-LIO to accept."),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    )
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                tr("Cộng tác viên (${directory.collaborators.size})", "Collaborators (${directory.collaborators.size})"),
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                modifier = Modifier.weight(1f)
            )
            if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        }
        if (directory.collaborators.isEmpty() && !loading) {
            EmptyState(Icons.Outlined.Security, tr("Chưa có cộng tác viên", "No collaborators"), tr("Các tài khoản được mời và chấp nhận sẽ xuất hiện tại đây.", "Invited accounts appear here after they accept."))
        } else {
            directory.collaborators.forEach { collaborator ->
                CollaboratorServerCard(
                    collaborator = collaborator,
                    roleMenuExpanded = roleMenuUserId == collaborator.id,
                    onRoleMenu = { roleMenuUserId = collaborator.id },
                    onRoleMenuDismiss = { roleMenuUserId = null },
                    onSelectRole = { role ->
                        roleMenuUserId = null
                        scope.launch {
                            submitting = true
                            try {
                                repository.updateCollaboratorRole(accessToken, collaborator.id, role)
                                refresh()
                                feedback = tr("Đã cập nhật vai trò.", "Role updated.")
                                isError = false
                            } catch (error: Exception) {
                                feedback = error.message ?: tr("Không đổi được vai trò.", "Could not update role.")
                                isError = true
                            } finally {
                                submitting = false
                            }
                        }
                    },
                    onEditPermissions = {
                        scope.launch {
                            submitting = true
                            try {
                                val settings = repository.getCollaboratorPermissions(accessToken, collaborator.id)
                                permissionOptions = settings.configurablePermissions
                                permissionDraft = settings.permissions
                                permissionTarget = collaborator
                                isError = false
                            } catch (error: Exception) {
                                feedback = error.message ?: tr("Không tải được quyền chi tiết.", "Could not load detailed permissions.")
                                isError = true
                            } finally {
                                submitting = false
                            }
                        }
                    },
                    onRevoke = { revokeUser = collaborator }
                )
            }
        }

        Text(
            tr("Lời mời đang chờ (${directory.pendingInvitations.size})", "Pending invitations (${directory.pendingInvitations.size})"),
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp
        )
        if (directory.pendingInvitations.isEmpty()) {
            Text(tr("Không có lời mời đang chờ.", "No pending invitations."), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        } else {
            directory.pendingInvitations.forEach { invitation ->
                PendingInvitationCard(invitation, onRevoke = { revokeInvite = invitation })
            }
        }
    }

    if (showPermissionGuide) {
        AlertDialog(
            onDismissRequest = { showPermissionGuide = false },
            title = { Text(tr("Quản lý quyền cộng tác", "Collaborator access")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        tr(
                            "Chọn tài khoản trong danh sách, sau đó nhấn “Cấp / xem quyền” để kiểm tra hoặc bật tắt những quyền được phép.",
                            "Choose an account from the list, then tap “Access” to review or enable and disable its allowed permissions."
                        ),
                        fontSize = 13.sp
                    )
                    Text(
                        tr(
                            "Chủ sở hữu luôn giữ quyền quản trị cao nhất. Cộng tác viên không thể tự cấp quyền cho mình.",
                            "The owner always retains the highest administrative access. Collaborators cannot grant permissions to themselves."
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showPermissionGuide = false }) {
                    Text(tr("Đã hiểu", "Got it"))
                }
            }
        )
    }

    permissionTarget?.let { collaborator ->
        AlertDialog(
            onDismissRequest = { permissionTarget = null },
            title = { Text(tr("Quyền chi tiết", "Detailed permissions")) },
            text = {
                Column(
                    Modifier.fillMaxWidth().heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(collaborator.email, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    Text(
                        tr("Chọn các quyền được phép trong vai trò ${collaborator.role.labelText()}. Quyền quản trị chủ sở hữu không thể cấp tại đây.",
                            "Choose the permissions allowed within the ${collaborator.role.labelText()} role. Owner-only administration rights cannot be granted here."),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                    permissionOptions.sortedBy { it.ordinal }.forEach { permission ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = permission in permissionDraft,
                                onCheckedChange = { checked ->
                                    permissionDraft = if (checked) permissionDraft + permission
                                    else permissionDraft - permission
                                }
                            )
                            Text(permission.permissionLabel(), fontSize = 12.sp)
                        }
                    }
                    if (permissionOptions.isEmpty()) {
                        Text(
                            tr("Vai trò này không có quyền quản trị bổ sung để tùy chỉnh.", "This role has no additional administrative permissions to customize."),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = permissionOptions.isNotEmpty() && !submitting,
                    onClick = {
                        val selectedUser = collaborator
                        val selectedPermissions = permissionDraft
                        scope.launch {
                            submitting = true
                            try {
                                repository.updateCollaboratorPermissions(accessToken, selectedUser.id, selectedPermissions)
                                permissionTarget = null
                                refresh()
                                feedback = tr("Đã lưu quyền chi tiết.", "Detailed permissions saved.")
                                isError = false
                            } catch (error: Exception) {
                                feedback = error.message ?: tr("Không lưu được quyền.", "Could not save permissions.")
                                isError = true
                            } finally {
                                submitting = false
                            }
                        }
                    }
                ) { Text(tr("Lưu quyền", "Save permissions")) }
            },
            dismissButton = {
                TextButton(onClick = { permissionTarget = null }) { Text(tr("Đóng", "Close")) }
            }
        )
    }

    revokeUser?.let { collaborator ->
        AlertDialog(
            onDismissRequest = { revokeUser = null },
            title = { Text(tr("Thu hồi quyền cộng tác viên?", "Revoke collaborator access?")) },
            text = { Text(tr("Tài khoản ${collaborator.email} sẽ mất quyền và các phiên hiện tại bị thu hồi.", "Account ${collaborator.email} will lose access and active sessions will be revoked.")) },
            confirmButton = {
                TextButton(onClick = {
                    revokeUser = null
                    scope.launch {
                        submitting = true
                        try {
                            repository.revokeCollaborator(accessToken, collaborator.id)
                            refresh()
                            feedback = tr("Đã thu hồi quyền cộng tác viên.", "Collaborator access revoked.")
                            isError = false
                        } catch (error: Exception) {
                            feedback = error.message ?: tr("Không thu hồi được quyền.", "Could not revoke access.")
                            isError = true
                        } finally {
                            submitting = false
                        }
                    }
                }) { Text(tr("Thu hồi", "Revoke")) }
            },
            dismissButton = { TextButton(onClick = { revokeUser = null }) { Text(tr("Hủy", "Cancel")) } }
        )
    }

    revokeInvite?.let { invitation ->
        AlertDialog(
            onDismissRequest = { revokeInvite = null },
            title = { Text(tr("Hủy lời mời đang chờ?", "Revoke pending invitation?")) },
            text = { Text(tr("Mã lời mời gửi đến ${invitation.email} sẽ không còn sử dụng được.", "The invitation for ${invitation.email} will no longer work.")) },
            confirmButton = {
                TextButton(onClick = {
                    revokeInvite = null
                    scope.launch {
                        submitting = true
                        try {
                            repository.revokeInvitation(accessToken, invitation.id)
                            refresh()
                            feedback = tr("Đã hủy lời mời.", "Invitation revoked.")
                            isError = false
                        } catch (error: Exception) {
                            feedback = error.message ?: tr("Không hủy được lời mời.", "Could not revoke invitation.")
                            isError = true
                        } finally {
                            submitting = false
                        }
                    }
                }) { Text(tr("Hủy lời mời", "Revoke invitation")) }
            },
            dismissButton = { TextButton(onClick = { revokeInvite = null }) { Text(tr("Đóng", "Close")) } }
        )
    }
}

@Composable
private fun CollaboratorServerCard(
    collaborator: CollaboratorRecord,
    roleMenuExpanded: Boolean,
    onRoleMenu: () -> Unit,
    onRoleMenuDismiss: () -> Unit,
    onSelectRole: (AccessRole) -> Unit,
    onEditPermissions: () -> Unit,
    onRevoke: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(collaborator.email, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text(collaborator.displayName.orEmpty().ifBlank { collaborator.role.labelText() }, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "${collaborator.role.labelText()} · ${collaborator.status}",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 11.sp
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box {
                    TextButton(onClick = onRoleMenu) { Text(tr("Đổi vai trò", "Change role")) }
                    DropdownMenu(expanded = roleMenuExpanded, onDismissRequest = onRoleMenuDismiss) {
                        listOf(AccessRole.DEVELOPER, AccessRole.DESIGNER, AccessRole.REVIEWER, AccessRole.CUSTOMER).forEach { role ->
                            DropdownMenuItem(
                                text = { Text(role.labelText()) },
                                onClick = { onSelectRole(role) }
                            )
                        }
                    }
                }
                if (collaborator.role != AccessRole.CUSTOMER) {
                    OutlinedButton(
                        onClick = onEditPermissions,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Icon(Icons.Outlined.Security, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(tr("Cấp / xem quyền", "Access"), fontSize = 11.sp)
                    }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onRevoke) { Text(tr("Thu hồi quyền", "Revoke access"), color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

@Composable
private fun PendingInvitationCard(invitation: PendingInvitation, onRevoke: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(invitation.email, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Text(invitation.role.labelText(), color = MaterialTheme.colorScheme.primary, fontSize = 11.sp)
                Text(
                    tr("Hết hạn: ", "Expires: ") + formatDate(invitation.expiresAt),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp
                )
            }
            TextButton(onClick = onRevoke) { Text(tr("Hủy", "Revoke")) }
        }
    }
}

private fun formatDate(epochSeconds: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(epochSeconds * 1000))

private fun AccessPermission.permissionLabel(): String = when (this) {
    AccessPermission.EDIT_SCRIPTS -> tr("Viết và sửa mã", "Write and edit scripts")
    AccessPermission.TEST_SCRIPTS -> tr("Kiểm thử", "Test scripts")
    AccessPermission.SUBMIT_RELEASE -> tr("Gửi để duyệt", "Submit for review")
    AccessPermission.EDIT_UI_DESIGN -> tr("Chỉnh sửa giao diện", "Edit interface")
    AccessPermission.PREVIEW_UI -> tr("Xem trước giao diện", "Preview interface")
    AccessPermission.REVIEW_RELEASE -> tr("Duyệt hoặc từ chối bản gửi", "Review or reject submissions")
    else -> permissionLabelText()
}

private val collaboratorRoles = listOf(
    AccessRole.DEVELOPER,
    AccessRole.DESIGNER,
    AccessRole.REVIEWER
)

private fun AccessRole.labelText(): String = when (this) {
    AccessRole.OWNER -> tr("Chủ sở hữu", "Owner")
    AccessRole.DEVELOPER -> tr("Nhà phát triển", "Developer")
    AccessRole.DESIGNER -> tr("Nhà thiết kế", "Designer")
    AccessRole.REVIEWER -> tr("Người kiểm duyệt", "Reviewer")
    AccessRole.CUSTOMER -> tr("Người dùng", "Customer")
}

private fun AccessRole.descriptionText(): String = when (this) {
    AccessRole.DEVELOPER -> tr("Viết, sửa và kiểm thử mã kịch bản; gửi để duyệt", "Write, edit and test scripts; submit for review")
    AccessRole.DESIGNER -> tr("Thiết kế giao diện, tài nguyên và xem trước", "Design the interface and assets; preview changes")
    AccessRole.REVIEWER -> tr("Kiểm tra, duyệt hoặc từ chối bản phát hành", "Test, approve or reject releases")
    AccessRole.OWNER -> tr("Toàn quyền quản trị ứng dụng", "Full application administration")
    AccessRole.CUSTOMER -> tr("Chỉ sử dụng kịch bản được cấp phép", "Use licensed published scripts only")
}
