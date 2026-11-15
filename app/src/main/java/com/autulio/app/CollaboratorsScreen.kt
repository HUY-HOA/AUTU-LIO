package com.autulio.app

import android.content.Context
import android.util.Patterns
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.autulio.app.access.AccessRole
import org.json.JSONArray
import org.json.JSONObject

private data class CollaboratorInviteDraft(
    val email: String,
    val role: AccessRole,
    val createdAt: Long
)

@Composable
internal fun CollaboratorsContent(onBack: () -> Unit) {
    val context = LocalContext.current
    val preferences = remember(context) {
        context.getSharedPreferences("autulio_collaborator_invite_drafts", Context.MODE_PRIVATE)
    }
    val invites = remember(preferences) {
        mutableStateListOf<CollaboratorInviteDraft>().apply {
            val stored = runCatching {
                JSONArray(preferences.getString("invites", "[]") ?: "[]")
            }.getOrNull() ?: JSONArray()
            for (index in 0 until stored.length()) {
                val item = stored.optJSONObject(index) ?: continue
                val email = item.optString("email").trim()
                val role = runCatching {
                    AccessRole.valueOf(item.optString("role"))
                }.getOrNull() ?: continue
                if (email.isNotBlank() && role in collaboratorRoles) {
                    add(CollaboratorInviteDraft(email, role, item.optLong("createdAt")))
                }
            }
        }
    }
    var email by rememberSaveable { mutableStateOf("") }
    var selectedRole by rememberSaveable { mutableStateOf(AccessRole.DEVELOPER.name) }
    var feedback by rememberSaveable { mutableStateOf("") }
    val selected = runCatching { AccessRole.valueOf(selectedRole) }.getOrDefault(AccessRole.DEVELOPER)
    val validEmail = Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()
    val duplicate = invites.any { it.email.equals(email.trim(), ignoreCase = true) }

    fun persist() {
        val array = JSONArray()
        invites.forEach {
            array.put(
                JSONObject()
                    .put("email", it.email)
                    .put("role", it.role.name)
                    .put("createdAt", it.createdAt)
            )
        }
        preferences.edit().putString("invites", array.toString()).apply()
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(onClick = onBack) {
            Text(tr("← Quay lại", "← Back"))
        }
        Heading(
            tr("Quản lý cộng tác viên", "Collaborator management"),
            tr("Chuẩn bị lời mời và chọn quyền cho từng Gmail", "Prepare invitations and choose a role for each Google account")
        )
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.PersonAdd, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(tr("Bản nháp trên thiết bị", "Local invitation drafts"), fontWeight = FontWeight.SemiBold)
                }
                Text(
                    tr(
                        "Các Gmail bên dưới chỉ được lưu thành bản nháp trên điện thoại. Chưa gửi lời mời và chưa cấp quyền cho bất kỳ ai vì máy chủ chưa kết nối.",
                        "The Google accounts below are only saved as drafts on this device. No invitation is sent and no access is granted because the server is not connected."
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
            }
        }
        OutlinedTextField(
            value = email,
            onValueChange = { email = it; feedback = "" },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(tr("Địa chỉ Gmail", "Google account email")) },
            placeholder = { Text("name@gmail.com") },
            isError = email.isNotBlank() && !validEmail
        )
        Text(tr("Chọn vai trò", "Choose a role"), fontWeight = FontWeight.SemiBold)
        collaboratorRoles.forEach { role ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (selected == role) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(
                    1.dp,
                    if (selected == role) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                ),
                shape = RoundedCornerShape(12.dp),
                onClick = { selectedRole = role.name }
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = selected == role, onClick = { selectedRole = role.name })
                    Column(Modifier.weight(1f)) {
                        Text(role.labelVi(), fontWeight = FontWeight.SemiBold)
                        Text(role.descriptionVi(), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                    }
                }
            }
        }
        Button(
            onClick = {
                if (!validEmail) {
                    feedback = tr("Vui lòng nhập Gmail hợp lệ.", "Enter a valid Google account email.")
                } else if (duplicate) {
                    feedback = tr("Gmail này đã có trong danh sách bản nháp.", "This email is already in the draft list.")
                } else {
                    invites.add(CollaboratorInviteDraft(email.trim(), selected, System.currentTimeMillis()))
                    persist()
                    email = ""
                    feedback = tr("Đã lưu bản nháp lời mời trên thiết bị.", "Invitation draft saved on this device.")
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = validEmail && !duplicate
        ) {
            Icon(Icons.Outlined.PersonAdd, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(tr("Thêm bản nháp lời mời", "Add invitation draft"))
        }
        if (feedback.isNotBlank()) {
            Text(feedback, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
        }
        Text(
            tr("Danh sách cộng tác viên dự kiến (${invites.size})", "Planned collaborators (${invites.size})"),
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp
        )
        if (invites.isEmpty()) {
            Text(
                tr("Chưa có bản nháp. Thêm Gmail ở trên để chuẩn bị phân quyền.", "No drafts yet. Add a Google account above to prepare its role."),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
        } else {
            invites.toList().forEach { invite ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(invite.email, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text(invite.role.labelVi(), color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                            Text(tr("Chưa gửi · Chưa cấp quyền", "Not sent · No access granted"), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                        }
                        TextButton(onClick = {
                            invites.remove(invite)
                            persist()
                        }) {
                            Text(tr("Xóa", "Remove"))
                        }
                    }
                }
            }
        }
    }
}

private val collaboratorRoles = listOf(
    AccessRole.DEVELOPER,
    AccessRole.DESIGNER,
    AccessRole.REVIEWER
)

private fun AccessRole.labelVi(): String = when (this) {
    AccessRole.DEVELOPER -> "Nhà phát triển"
    AccessRole.DESIGNER -> "Nhà thiết kế"
    AccessRole.REVIEWER -> "Người kiểm duyệt"
    AccessRole.OWNER -> "Chủ sở hữu"
    AccessRole.CUSTOMER -> "Người dùng"
}

private fun AccessRole.descriptionVi(): String = when (this) {
    AccessRole.DEVELOPER -> "Viết, sửa và kiểm thử mã kịch bản; gửi để duyệt"
    AccessRole.DESIGNER -> "Thiết kế giao diện, tài nguyên và xem trước"
    AccessRole.REVIEWER -> "Kiểm tra, duyệt hoặc từ chối bản phát hành"
    AccessRole.OWNER -> "Toàn quyền quản trị ứng dụng"
    AccessRole.CUSTOMER -> "Chỉ sử dụng kịch bản được cấp phép"
}
