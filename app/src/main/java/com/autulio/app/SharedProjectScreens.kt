package com.autulio.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import org.json.JSONArray
import org.json.JSONObject
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.autulio.app.access.AccessPermission
import com.autulio.app.access.AccessRole
import com.autulio.app.auth.AuthRepository
import com.autulio.app.auth.CollaboratorRecord
import com.autulio.app.auth.ProjectMember
import com.autulio.app.auth.SharedProject
import com.autulio.app.auth.SharedScriptDraft
import com.autulio.app.auth.SharedSubmission
import com.autulio.app.auth.SharedUiDraft
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
internal fun ProjectsAdminScreen(accessToken: String, modifier: Modifier = Modifier, onBack: () -> Unit) {
    val context = LocalContext.current
    val repository = remember(context) { AuthRepository(context) }
    val scope = rememberCoroutineScope()
    var projects by remember { mutableStateOf<List<SharedProject>>(emptyList()) }
    var collaborators by remember { mutableStateOf<List<CollaboratorRecord>>(emptyList()) }
    var members by remember { mutableStateOf<List<ProjectMember>>(emptyList()) }
    var selectedProject by remember { mutableStateOf<SharedProject?>(null) }
    var projectName by rememberSaveable { mutableStateOf("") }
    var projectDescription by rememberSaveable { mutableStateOf("") }
    var menuExpanded by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

    suspend fun refreshProjects() {
        loading = true
        try {
            projects = repository.listProjects(accessToken)
            collaborators = repository.listCollaborators(accessToken).collaborators
                .filter { it.status == "ACTIVE" && it.role in setOf(AccessRole.DEVELOPER, AccessRole.DESIGNER, AccessRole.REVIEWER) }
            selectedProject = projects.firstOrNull { it.id == selectedProject?.id } ?: projects.firstOrNull()
        } catch (error: Exception) {
            message = error.message ?: sharedTr("Không tải được dự án.", "Could not load projects.")
            isError = true
        } finally {
            loading = false
        }
    }

    LaunchedEffect(accessToken) { refreshProjects() }

    LaunchedEffect(selectedProject?.id) {
        val projectId = selectedProject?.id ?: return@LaunchedEffect
        try {
            members = repository.listProjectMembers(accessToken, projectId)
        } catch (error: Exception) {
            members = emptyList()
            message = error.message ?: sharedTr("Không tải được thành viên dự án.", "Could not load project members.")
            isError = true
        }
    }

    MaterialTheme(colorScheme = darkColorScheme(primary = androidx.compose.ui.graphics.Color(0xFF4F8CFF))) {
        Scaffold(modifier = modifier.fillMaxSize(), containerColor = MaterialTheme.colorScheme.background) { padding ->
            Column(
                Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(13.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onBack) { Text(sharedTr("← Quay lại", "← Back")) }
                    Spacer(Modifier.weight(1f))
                    if (loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    IconButton(onClick = { scope.launch { refreshProjects() } }) {
                        Icon(Icons.Outlined.Refresh, contentDescription = sharedTr("Tải lại", "Refresh"))
                    }
                }
                SharedHeading(
                    sharedTr("Dự án cộng tác", "Shared projects"),
                    sharedTr("Tạo không gian làm việc và phân công cộng tác viên.", "Create workspaces and assign collaborators.")
                )
                SharedCard {
                    Text(sharedTr("Tạo dự án mới", "Create a project"), fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = projectName,
                        onValueChange = { projectName = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text(sharedTr("Tên dự án", "Project name")) }
                    )
                    OutlinedTextField(
                        value = projectDescription,
                        onValueChange = { projectDescription = it },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        label = { Text(sharedTr("Mô tả (không bắt buộc)", "Description (optional)")) }
                    )
                    Button(
                        onClick = {
                            scope.launch {
                                saving = true
                                try {
                                    val project = repository.createProject(accessToken, projectName, projectDescription)
                                    projectName = ""
                                    projectDescription = ""
                                    refreshProjects()
                                    selectedProject = project
                                    message = sharedTr("Đã tạo dự án.", "Project created.")
                                    isError = false
                                } catch (error: Exception) {
                                    message = error.message ?: sharedTr("Không tạo được dự án.", "Could not create project.")
                                    isError = true
                                } finally {
                                    saving = false
                                }
                            }
                        },
                        enabled = projectName.trim().length >= 2 && !saving,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Outlined.Add, null)
                        Spacer(Modifier.width(7.dp))
                        Text(sharedTr("Tạo dự án", "Create project"))
                    }
                }
                Text(
                    sharedTr("Dự án hiện có (" + projects.size + ")", "Existing projects (" + projects.size + ")"),
                    fontWeight = FontWeight.SemiBold
                )
                if (projects.isEmpty() && !loading) {
                    SharedEmptyState(
                        sharedTr("Chưa có dự án", "No projects yet"),
                        sharedTr("Tạo dự án đầu tiên để bắt đầu phân công công việc.", "Create a project to start assigning work.")
                    )
                }
                projects.forEach { project ->
                    SharedProjectCard(
                        project = project,
                        selected = project.id == selectedProject?.id,
                        onClick = { selectedProject = project }
                    )
                }
                selectedProject?.let { project ->
                    SharedHeading(sharedTr("Thành viên dự án", "Project members"), project.name)
                    SharedCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(sharedTr("Cộng tác viên được giao", "Assigned collaborators"), fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            Text(members.size.toString(), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                        if (members.isEmpty()) {
                            Text(sharedTr("Chưa có thành viên. Hãy thêm cộng tác viên đã chấp nhận lời mời.", "No members yet. Add collaborators who have accepted their invitations."), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                        }
                        members.forEach { member ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(member.email, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                                    Text(member.role.sharedRoleLabel() + " · " + member.status, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                                }
                                TextButton(
                                    enabled = !saving,
                                    onClick = {
                                        scope.launch {
                                            saving = true
                                            try {
                                                repository.removeProjectMember(accessToken, project.id, member.id)
                                                members = repository.listProjectMembers(accessToken, project.id)
                                                message = sharedTr("Đã gỡ thành viên khỏi dự án.", "Project member removed.")
                                                isError = false
                                            } catch (error: Exception) {
                                                message = error.message ?: sharedTr("Không gỡ được thành viên.", "Could not remove project member.")
                                                isError = true
                                            } finally {
                                                saving = false
                                            }
                                        }
                                    }
                                ) { Text(sharedTr("Gỡ", "Remove"), color = MaterialTheme.colorScheme.error) }
                            }
                        }
                        Box {
                            OutlinedButton(
                                onClick = { menuExpanded = true },
                                enabled = !saving && collaborators.any { candidate -> members.none { it.id == candidate.id } },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Outlined.PersonAdd, null)
                                Spacer(Modifier.width(7.dp))
                                Text(sharedTr("Thêm cộng tác viên vào dự án", "Add collaborator to project"))
                            }
                            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                                collaborators.filter { candidate -> members.none { it.id == candidate.id } }.forEach { candidate ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(candidate.email)
                                                Text(candidate.role.sharedRoleLabel(), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        },
                                        onClick = {
                                            menuExpanded = false
                                            scope.launch {
                                                saving = true
                                                try {
                                                    repository.addProjectMember(accessToken, project.id, candidate.id)
                                                    members = repository.listProjectMembers(accessToken, project.id)
                                                    message = sharedTr("Đã thêm cộng tác viên vào dự án.", "Collaborator added to project.")
                                                    isError = false
                                                } catch (error: Exception) {
                                                    message = error.message ?: sharedTr("Không thêm được cộng tác viên.", "Could not add collaborator.")
                                                    isError = true
                                                } finally {
                                                    saving = false
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
                if (message.isNotBlank()) SharedFeedback(message, isError)
                SharedServerNotice(
                    sharedTr(
                        "Quản lý dự án cần Worker và Google Sign-In được cấu hình. Không có dự án nào được tạo cục bộ thay cho dữ liệu máy chủ.",
                        "Project management requires a configured Worker and Google Sign-In. The app does not create local-only substitutes."
                    )
                )
            }
        }
    }
}

@Composable
internal fun CollaboratorProjectsWorkspace(
    accessToken: String,
    role: AccessRole,
    permissions: Set<AccessPermission>,
    email: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val repository = remember(context) { AuthRepository(context) }
    val scope = rememberCoroutineScope()
    var projects by remember { mutableStateOf<List<SharedProject>>(emptyList()) }
    var selectedProject by remember { mutableStateOf<SharedProject?>(null) }
    var scripts by remember { mutableStateOf<List<SharedScriptDraft>>(emptyList()) }
    var designs by remember { mutableStateOf<List<SharedUiDraft>>(emptyList()) }
    var submissions by remember { mutableStateOf<List<SharedSubmission>>(emptyList()) }
    var selectedScriptId by rememberSaveable { mutableStateOf<String?>(null) }
    var scriptRevision by rememberSaveable { mutableIntStateOf(0) }
    var scriptName by rememberSaveable { mutableStateOf("") }
    var scriptSource by rememberSaveable { mutableStateOf("") }
    var selectedDesignId by rememberSaveable { mutableStateOf<String?>(null) }
    var designRevision by rememberSaveable { mutableIntStateOf(0) }
    var screenKey by rememberSaveable { mutableStateOf("home") }
    var designTitle by rememberSaveable { mutableStateOf("Trang chủ AUTU-LIO") }
    var designSubtitle by rememberSaveable { mutableStateOf("Tự động hóa thao tác trên Android") }
    var designButtonLabel by rememberSaveable { mutableStateOf("Bắt đầu") }
    var designAccent by rememberSaveable { mutableStateOf("#4F8CFF") }
    var showInfoCard by rememberSaveable { mutableStateOf(true) }
    var showScriptList by rememberSaveable { mutableStateOf(true) }
    var reviewNote by rememberSaveable { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

    suspend fun refreshProjects() {
        loading = true
        try {
            projects = repository.listProjects(accessToken)
            selectedProject = projects.firstOrNull { it.id == selectedProject?.id } ?: projects.firstOrNull()
        } catch (error: Exception) {
            message = error.message ?: sharedTr("Không tải được danh sách dự án.", "Could not load projects.")
            isError = true
        } finally {
            loading = false
        }
    }

    LaunchedEffect(accessToken) { refreshProjects() }

    LaunchedEffect(selectedProject?.id, role) {
        selectedScriptId = null
        scriptRevision = 0
        scriptName = ""
        scriptSource = ""
        selectedDesignId = null
        designRevision = 0
        screenKey = "home"
        designTitle = "Trang chủ AUTU-LIO"
        designSubtitle = "Tự động hóa thao tác trên Android"
        designButtonLabel = "Bắt đầu"
        designAccent = "#4F8CFF"
        showInfoCard = true
        showScriptList = true
        reviewNote = ""
        val projectId = selectedProject?.id ?: return@LaunchedEffect
        loading = true
        try {
            when (role) {
                AccessRole.DEVELOPER -> {
                    scripts = repository.listProjectScripts(accessToken, projectId)
                    designs = emptyList()
                    submissions = emptyList()
                }
                AccessRole.DESIGNER -> {
                    designs = repository.listProjectUiDrafts(accessToken, projectId)
                    scripts = emptyList()
                    submissions = emptyList()
                }
                AccessRole.REVIEWER -> {
                    submissions = repository.listProjectSubmissions(accessToken, projectId)
                    scripts = emptyList()
                    designs = emptyList()
                }
                else -> Unit
            }
        } catch (error: Exception) {
            message = error.message ?: sharedTr("Không tải được dữ liệu dự án.", "Could not load project data.")
            isError = true
        } finally {
            loading = false
        }
    }

    MaterialTheme(colorScheme = darkColorScheme(primary = androidx.compose.ui.graphics.Color(0xFF4F8CFF))) {
        Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
            Column(
                Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(13.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onBack) { Text(sharedTr("← Quay lại", "← Back")) }
                    Spacer(Modifier.weight(1f))
                    if (loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    IconButton(onClick = { scope.launch { refreshProjects() } }) {
                        Icon(Icons.Outlined.Refresh, contentDescription = sharedTr("Tải lại", "Refresh"))
                    }
                }
                SharedHeading(
                    when (role) {
                        AccessRole.DEVELOPER -> sharedTr("Dự án nhà phát triển", "Developer projects")
                        AccessRole.DESIGNER -> sharedTr("Dự án thiết kế", "Design projects")
                        else -> sharedTr("Dự án kiểm duyệt", "Review projects")
                    },
                    email
                )
                if (projects.isEmpty() && !loading) {
                    SharedEmptyState(
                        sharedTr("Chưa được giao dự án", "No projects assigned"),
                        sharedTr("Chủ sở hữu cần thêm tài khoản của bạn vào ít nhất một dự án.", "The owner must add your account to at least one project.")
                    )
                }
                projects.forEach { project ->
                    SharedProjectCard(project, project.id == selectedProject?.id) { selectedProject = project }
                }
                selectedProject?.let { project ->
                    SharedHeading(project.name, project.description.ifBlank { sharedTr("Không có mô tả", "No description") })
                    when (role) {
                        AccessRole.DEVELOPER -> {
                            Text(sharedTr("Bản nháp mã dùng chung", "Shared script drafts"), fontWeight = FontWeight.SemiBold)
                            if (scripts.isEmpty()) Text(sharedTr("Chưa có bản nháp trong dự án.", "There are no drafts in this project yet."), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            scripts.forEach { script ->
                                OutlinedButton(
                                    onClick = {
                                        selectedScriptId = script.id
                                        scriptRevision = script.revision
                                        scriptName = script.name
                                        scriptSource = script.source.orEmpty()
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(script.name + " · v" + script.revision)
                                }
                            }
                            if (AccessPermission.EDIT_SCRIPTS in permissions) {
                                Button(onClick = {
                                    selectedScriptId = null
                                    scriptRevision = 0
                                    scriptName = ""
                                    scriptSource = ""
                                    message = ""
                                }, modifier = Modifier.fillMaxWidth()) {
                                    Text(sharedTr("Tạo bản nháp mới", "Create new draft"))
                                }
                                OutlinedTextField(
                                    value = scriptName,
                                    onValueChange = { scriptName = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    label = { Text(sharedTr("Tên kịch bản", "Script name")) }
                                )
                                OutlinedTextField(
                                    value = scriptSource,
                                    onValueChange = { scriptSource = it },
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 230.dp),
                                    minLines = 8,
                                    label = { Text(sharedTr("Mã AUTU-LIO", "AUTU-LIO source")) }
                                )
                                Button(
                                    enabled = !saving && scriptName.isNotBlank(),
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = {
                                        scope.launch {
                                            saving = true
                                            try {
                                                if (selectedScriptId == null) {
                                                    val created = repository.createProjectScript(accessToken, project.id, scriptName, scriptSource)
                                                    selectedScriptId = created.id
                                                    scriptRevision = created.revision
                                                } else {
                                                    scriptRevision = repository.updateProjectScript(
                                                        accessToken, project.id, selectedScriptId!!,
                                                        scriptName, scriptSource, scriptRevision
                                                    )
                                                }
                                                scripts = repository.listProjectScripts(accessToken, project.id)
                                                message = sharedTr("Đã lưu bản nháp chung lên máy chủ.", "Shared draft saved to the server.")
                                                isError = false
                                            } catch (error: Exception) {
                                                message = error.message ?: sharedTr("Không lưu được bản nháp.", "Could not save draft.")
                                                isError = true
                                            } finally {
                                                saving = false
                                            }
                                        }
                                    }
                                ) {
                                    Icon(Icons.Outlined.CloudUpload, null)
                                    Spacer(Modifier.width(7.dp))
                                    Text(sharedTr("Lưu bản nháp dùng chung", "Save shared draft"))
                                }
                                if (selectedScriptId != null && AccessPermission.SUBMIT_RELEASE in permissions) {
                                    OutlinedButton(
                                        enabled = !saving,
                                        modifier = Modifier.fillMaxWidth(),
                                        onClick = {
                                            val id = selectedScriptId ?: return@OutlinedButton
                                            scope.launch {
                                                saving = true
                                                try {
                                                    repository.createProjectSubmission(accessToken, project.id, "SCRIPT", id, scriptRevision)
                                                    message = sharedTr("Đã gửi bản nháp để người kiểm duyệt xem xét.", "Draft submitted for reviewer approval.")
                                                    isError = false
                                                } catch (error: Exception) {
                                                    message = error.message ?: sharedTr("Không gửi được bản duyệt.", "Could not submit the draft.")
                                                    isError = true
                                                } finally {
                                                    saving = false
                                                }
                                            }
                                        }
                                    ) { Text(sharedTr("Gửi bản hiện tại để duyệt", "Submit current revision for review")) }
                                }
                            } else {
                                SharedServerNotice(sharedTr("Quyền sửa mã đã bị chủ sở hữu tắt.", "The owner has disabled script editing for this account."))
                            }
                        }
                        AccessRole.DESIGNER -> {
                            Text(sharedTr("Bản thiết kế UI dùng chung", "Shared UI design drafts"), fontWeight = FontWeight.SemiBold)
                            designs.forEach { design ->
                                OutlinedButton(
                                    onClick = {
                                        selectedDesignId = design.id
                                        screenKey = design.screenKey
                                        designRevision = design.revision
                                        val saved = runCatching { JSONObject(design.designJson) }.getOrNull()
                                        designTitle = saved?.optString("title", "Trang chủ AUTU-LIO") ?: "Trang chủ AUTU-LIO"
                                        designSubtitle = saved?.optString("subtitle", "Tự động hóa thao tác trên Android") ?: "Tự động hóa thao tác trên Android"
                                        designButtonLabel = saved?.optString("buttonLabel", "Bắt đầu") ?: "Bắt đầu"
                                        designAccent = saved?.optString("accent", "#4F8CFF") ?: "#4F8CFF"
                                        val components = saved?.optJSONArray("components")
                                        val componentNames = buildSet {
                                            if (components != null) {
                                                for (index in 0 until components.length()) add(components.optString(index))
                                            }
                                        }
                                        showInfoCard = "info_card" in componentNames
                                        showScriptList = "script_list" in componentNames
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(design.screenKey + " · v" + design.revision)
                                }
                            }
                            if (AccessPermission.EDIT_UI_DESIGN in permissions) {
                                OutlinedTextField(
                                    value = screenKey,
                                    onValueChange = {
                                        screenKey = it
                                        selectedDesignId = null
                                        designRevision = 0
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    label = { Text(sharedTr("Mã màn hình", "Screen key")) }
                                )
                                OutlinedTextField(
                                    value = designTitle,
                                    onValueChange = { designTitle = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    label = { Text(sharedTr("Tiêu đề màn hình", "Screen title")) }
                                )
                                OutlinedTextField(
                                    value = designSubtitle,
                                    onValueChange = { designSubtitle = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    minLines = 2,
                                    label = { Text(sharedTr("Mô tả", "Subtitle")) }
                                )
                                OutlinedTextField(
                                    value = designButtonLabel,
                                    onValueChange = { designButtonLabel = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    label = { Text(sharedTr("Nội dung nút chính", "Primary button label")) }
                                )
                                Text(sharedTr("Màu chủ đạo", "Accent color"), fontWeight = FontWeight.SemiBold)
                                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                    listOf(
                                        "#4F8CFF" to sharedTr("Xanh dương", "Blue"),
                                        "#00A68C" to sharedTr("Xanh lá", "Green"),
                                        "#8B5CF6" to sharedTr("Tím", "Violet"),
                                        "#F59E0B" to sharedTr("Cam", "Amber")
                                    ).forEach { (hex, label) ->
                                        FilterChip(
                                            selected = designAccent == hex,
                                            onClick = { designAccent = hex },
                                            label = { Text(label, fontSize = 11.sp) }
                                        )
                                    }
                                }
                                Text(sharedTr("Thành phần màn hình", "Screen components"), fontWeight = FontWeight.SemiBold)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = showInfoCard, onCheckedChange = { showInfoCard = it })
                                    Text(sharedTr("Thẻ thông tin", "Information card"), fontSize = 12.sp)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = showScriptList, onCheckedChange = { showScriptList = it })
                                    Text(sharedTr("Danh sách kịch bản", "Script list"), fontSize = 12.sp)
                                }
                                val previewAccent = remember(designAccent) {
                                    runCatching { Color(android.graphics.Color.parseColor(designAccent)) }
                                        .getOrDefault(Color(0xFF4F8CFF))
                                }
                                SharedCard {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("AUTU-LIO", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                        Text(sharedTr("XEM TRƯỚC", "PREVIEW"), color = previewAccent, fontSize = 10.sp)
                                    }
                                    Text(designTitle.ifBlank { sharedTr("Tiêu đề màn hình", "Screen title") }, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                                    if (designSubtitle.isNotBlank()) {
                                        Text(designSubtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                                    }
                                    if (showInfoCard) {
                                        Surface(
                                            color = previewAccent.copy(alpha = 0.13f),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Outlined.Info, null, tint = previewAccent)
                                                Spacer(Modifier.width(8.dp))
                                                Text(sharedTr("Thẻ thông tin mẫu", "Sample information card"), fontSize = 12.sp)
                                            }
                                        }
                                    }
                                    if (showScriptList) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Outlined.ListAlt, null, tint = previewAccent)
                                            Spacer(Modifier.width(8.dp))
                                            Text(sharedTr("Danh sách kịch bản", "Script list"), fontWeight = FontWeight.Medium)
                                        }
                                    }
                                    Button(
                                        onClick = {},
                                        enabled = false,
                                        colors = ButtonDefaults.buttonColors(
                                            disabledContainerColor = previewAccent.copy(alpha = 0.35f)
                                        )
                                    ) {
                                        Text(designButtonLabel.ifBlank { sharedTr("Nút chính", "Primary button") })
                                    }
                                }
                                Button(
                                    enabled = !saving && screenKey.isNotBlank(),
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = {
                                        scope.launch {
                                            saving = true
                                            try {
                                                val componentList = buildList {
                                                    if (showInfoCard) add("info_card")
                                                    if (showScriptList) add("script_list")
                                                }
                                                val designPayload = JSONObject()
                                                    .put("layout", "column")
                                                    .put("title", designTitle.trim())
                                                    .put("subtitle", designSubtitle.trim())
                                                    .put("buttonLabel", designButtonLabel.trim())
                                                    .put("accent", designAccent)
                                                    .put("components", JSONArray(componentList))
                                                designRevision = repository.saveProjectUiDraft(
                                                    accessToken, project.id, screenKey, designPayload.toString(), designRevision
                                                )
                                                designs = repository.listProjectUiDrafts(accessToken, project.id)
                                                selectedDesignId = designs.firstOrNull { it.screenKey == screenKey }?.id
                                                message = sharedTr("Đã lưu thiết kế UI dùng chung.", "Shared UI design saved.")
                                                isError = false
                                            } catch (error: Exception) {
                                                message = error.message ?: sharedTr("Không lưu được thiết kế. Kiểm tra JSON hoặc tải lại phiên bản mới.", "Could not save design. Check JSON or reload the latest revision.")
                                                isError = true
                                            } finally {
                                                saving = false
                                            }
                                        }
                                    }
                                ) {
                                    Icon(Icons.Outlined.CloudUpload, null)
                                    Spacer(Modifier.width(7.dp))
                                    Text(sharedTr("Lưu thiết kế lên máy chủ", "Save design to server"))
                                }
                                if (AccessPermission.SUBMIT_RELEASE in permissions && designRevision > 0 && selectedDesignId != null) {
                                    OutlinedButton(
                                        enabled = !saving,
                                        modifier = Modifier.fillMaxWidth(),
                                        onClick = {
                                            scope.launch {
                                                saving = true
                                                try {
                                                    repository.createProjectSubmission(accessToken, project.id, "UI", selectedDesignId!!, designRevision)
                                                    message = sharedTr("Đã gửi thiết kế để duyệt.", "Design submitted for review.")
                                                    isError = false
                                                } catch (error: Exception) {
                                                    message = error.message ?: sharedTr("Không gửi được thiết kế.", "Could not submit design.")
                                                    isError = true
                                                } finally {
                                                    saving = false
                                                }
                                            }
                                        }
                                    ) { Text(sharedTr("Gửi thiết kế để duyệt", "Submit design for review")) }
                                }
                            } else {
                                SharedServerNotice(sharedTr("Quyền chỉnh sửa giao diện đã bị chủ sở hữu tắt.", "The owner has disabled UI design editing."))
                            }
                        }
                        AccessRole.REVIEWER -> {
                            Text(sharedTr("Bản gửi chờ duyệt", "Submissions for review"), fontWeight = FontWeight.SemiBold)
                            if (submissions.isEmpty()) {
                                SharedEmptyState(sharedTr("Chưa có bản gửi", "No submissions"), sharedTr("Các bản gửi từ nhà phát triển hoặc nhà thiết kế sẽ xuất hiện tại đây.", "Developer and designer submissions will appear here."))
                            }
                            submissions.forEach { submission ->
                                SharedSubmissionCard(
                                    submission = submission,
                                    note = reviewNote,
                                    onNoteChange = { reviewNote = it },
                                    enabled = AccessPermission.REVIEW_RELEASE in permissions && !saving,
                                    onDecision = { decision ->
                                        scope.launch {
                                            saving = true
                                            try {
                                                repository.reviewProjectSubmission(accessToken, project.id, submission.id, decision, reviewNote)
                                                submissions = repository.listProjectSubmissions(accessToken, project.id)
                                                message = if (decision == "APPROVED") sharedTr("Đã duyệt bản gửi.", "Submission approved.") else sharedTr("Đã từ chối bản gửi.", "Submission rejected.")
                                                reviewNote = ""
                                                isError = false
                                            } catch (error: Exception) {
                                                message = error.message ?: sharedTr("Không lưu được quyết định duyệt.", "Could not save review decision.")
                                                isError = true
                                            } finally {
                                                saving = false
                                            }
                                        }
                                    }
                                )
                            }
                        }
                        else -> Unit
                    }
                }
                if (message.isNotBlank()) SharedFeedback(message, isError)
                SharedServerNotice(
                    sharedTr(
                        "Thay đổi được lưu lên D1 khi Worker đã triển khai. Kiểm tra phiên bản trước khi ghi nếu có người khác cùng sửa. Việc duyệt chưa tạo APK hoặc package phát hành.",
                        "Changes are stored in D1 after the Worker is deployed. Check the revision before saving when others are editing. Approval does not build an APK or release a package."
                    )
                )
            }
        }
    }
}

@Composable
private fun SharedProjectCard(project: SharedProject, selected: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(13.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(project.name, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                Text(
                    runCatching { AccessRole.valueOf(project.accessRole).sharedRoleLabel() }
                        .getOrDefault(project.accessRole),
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (project.description.isNotBlank()) Text(project.description, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SharedSubmissionCard(
    submission: SharedSubmission,
    note: String,
    onNoteChange: (String) -> Unit,
    enabled: Boolean,
    onDecision: (String) -> Unit
) {
    SharedCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(submission.kind + " · v" + submission.revision, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text(submission.status, color = MaterialTheme.colorScheme.primary, fontSize = 10.sp)
        }
        Text(sharedTr("Người gửi: ", "Submitted by: ") + submission.submitterEmail, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(submission.snapshot.take(1800), fontSize = 11.sp, lineHeight = 15.sp)
        if (submission.status == "PENDING") {
            OutlinedTextField(
                value = note,
                onValueChange = onNoteChange,
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                label = { Text(sharedTr("Ghi chú kiểm duyệt", "Review note")) }
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onDecision("APPROVED") }, enabled = enabled, modifier = Modifier.weight(1f)) {
                    Text(sharedTr("Duyệt", "Approve"))
                }
                OutlinedButton(onClick = { onDecision("REJECTED") }, enabled = enabled, modifier = Modifier.weight(1f)) {
                    Text(sharedTr("Từ chối", "Reject"))
                }
            }
        } else if (submission.reviewNote.isNotBlank()) {
            Text(submission.reviewNote, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SharedCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = RoundedCornerShape(15.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(13.dp), verticalArrangement = Arrangement.spacedBy(9.dp), content = content)
    }
}

@Composable
private fun SharedHeading(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SharedEmptyState(title: String, description: String) {
    SharedCard {
        Icon(Icons.Outlined.FolderOpen, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(27.dp))
        Text(title, fontWeight = FontWeight.SemiBold)
        Text(description, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SharedFeedback(message: String, isError: Boolean) {
    Text(message, color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, fontSize = 12.sp)
}

@Composable
private fun SharedServerNotice(message: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
        Icon(Icons.Outlined.Cloud, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(8.dp))
        Text(message, fontSize = 11.sp, lineHeight = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun AccessRole.sharedRoleLabel(): String = when (this) {
    AccessRole.OWNER -> sharedTr("Chủ sở hữu", "Owner")
    AccessRole.DEVELOPER -> sharedTr("Nhà phát triển", "Developer")
    AccessRole.DESIGNER -> sharedTr("Nhà thiết kế", "Designer")
    AccessRole.REVIEWER -> sharedTr("Người kiểm duyệt", "Reviewer")
    AccessRole.CUSTOMER -> sharedTr("Người dùng", "Customer")
}

private fun sharedTr(vi: String, en: String): String =
    if (Locale.getDefault().language == "vi") vi else en

