package com.autulio.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.util.Locale
import com.autulio.app.access.AccessRole
import com.autulio.app.accessibility.AutoClickAccessibilityService
import com.autulio.app.access.defaultPermissions
import com.autulio.app.auth.AuthRepository
import com.autulio.app.auth.AuthenticatedAppHost
import com.autulio.app.script.ScriptCompiler
import com.autulio.app.script.ScriptTemplateCatalog

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AutuLioAppEntry()
        }
    }
}

@Composable
private fun AutuLioAppEntry() {
    var previewRole by rememberSaveable { mutableStateOf<String?>(null) }

    if (!BuildConfig.DEBUG) {
        AuthenticatedAppHost()
    } else if (previewRole == null) {
        RolePreviewChooser(onOpenRole = { previewRole = it.name })
    } else {
        BackHandler { previewRole = null }
        val role = runCatching { AccessRole.valueOf(previewRole.orEmpty()) }
            .getOrDefault(AccessRole.CUSTOMER)
        when (role) {
            AccessRole.OWNER -> AdminApp(accessToken = null, onLogout = { previewRole = null })
            AccessRole.CUSTOMER -> CustomerApp(
                accessToken = null,
                email = tr("Tài khoản xem thử", "Preview account"),
                onLogout = { previewRole = null },
                onRoleUpdated = {}
            )
            AccessRole.DEVELOPER,
            AccessRole.DESIGNER,
            AccessRole.REVIEWER -> CollaboratorWorkspace(
                accessToken = "",
                role = role,
                permissions = role.defaultPermissions(),
                email = tr("Tài khoản xem thử", "Preview account"),
                onLogout = { previewRole = null }
            )
        }
    }
}
@Composable
private fun RolePreviewChooser(
    onOpenRole: (AccessRole) -> Unit
) {
    AppTheme(dark = true) {
        Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(13.dp)
            ) {
                BrandHeader(admin = false)
                Heading(
                    tr("Chọn giao diện kiểm thử", "Choose a test view"),
                    tr("Một ứng dụng AUTU-LIO, giao diện được mở theo quyền tài khoản.", "One AUTU-LIO app; the interface is selected by the account role.")
                )
                CardBox {
                    Text(
                        tr("Chế độ xem thử dành cho bản Debug", "Debug-only preview mode"),
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        tr("Các lựa chọn dưới đây chỉ kiểm tra bố cục. Chúng không đăng nhập và không cấp quyền thật.", "These options only preview layouts. They do not sign in or grant real permissions."),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                }
                Button(onClick = { onOpenRole(AccessRole.OWNER) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.AdminPanelSettings, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(tr("Xem thử giao diện Chủ sở hữu", "Preview Owner interface"))
                }
                OutlinedButton(onClick = { onOpenRole(AccessRole.DEVELOPER) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Code, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(tr("Xem thử Nhà phát triển", "Preview Developer"))
                }
                OutlinedButton(onClick = { onOpenRole(AccessRole.DESIGNER) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Palette, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(tr("Xem thử Nhà thiết kế", "Preview Designer"))
                }
                OutlinedButton(onClick = { onOpenRole(AccessRole.REVIEWER) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.FactCheck, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(tr("Xem thử Người kiểm duyệt", "Preview Reviewer"))
                }
                OutlinedButton(onClick = { onOpenRole(AccessRole.CUSTOMER) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Person, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(tr("Xem thử giao diện Người dùng", "Preview Customer interface"))
                }
            }
        }
    }
}
internal fun tr(vi: String, en: String) =
    if (Locale.getDefault().language == "vi") vi else en

@Composable
internal fun AppTheme(dark: Boolean, content: @Composable () -> Unit) {
    val scheme = if (dark) darkColorScheme(
        primary = Color(0xFF4F8CFF),
        secondary = Color(0xFF35C6C8),
        background = Color(0xFF0B1220),
        surface = Color(0xFF131E2E)
    ) else lightColorScheme(
        primary = Color(0xFF2563C7),
        secondary = Color(0xFF087F83),
        background = Color(0xFFF4F7FB),
        surface = Color.White
    )
    MaterialTheme(colorScheme = scheme, content = content)
}

private data class Tab(val key: String, val vi: String, val en: String, val icon: ImageVector)

private val adminTabs = listOf(
    Tab("overview", "Tổng quan", "Overview", Icons.Outlined.Home),
    Tab("scripts", "Kịch bản", "Scripts", Icons.Outlined.ListAlt),
    Tab("customers", "Khách hàng", "Customers", Icons.Outlined.Group),
    Tab("devices", "Thiết bị", "Devices", Icons.Outlined.Smartphone),
    Tab("more", "Thêm", "More", Icons.Outlined.MoreHoriz)
)

private val customerTabs = listOf(
    Tab("home", "Trang chủ", "Home", Icons.Outlined.Home),
    Tab("scripts", "Kịch bản", "Scripts", Icons.Outlined.ListAlt),
    Tab("device", "Thiết bị", "Device", Icons.Outlined.Smartphone),
    Tab("settings", "Cài đặt", "Settings", Icons.Outlined.Settings)
)

@Composable
internal fun AdminApp(accessToken: String?, onLogout: () -> Unit) {
    val context = LocalContext.current
    val draftStore = remember(context) { SecureScriptDraftStore(context) }
    val initialDrafts = remember(draftStore) { draftStore.load() }
    var dark by rememberSaveable { mutableStateOf(true) }
    var page by rememberSaveable { mutableStateOf("overview") }
    var createDialog by remember { mutableStateOf(false) }
    val scripts = remember(initialDrafts) {
        mutableStateListOf<String>().apply { addAll(initialDrafts.names) }
    }
    val draftContents = remember(initialDrafts) {
        mutableStateMapOf<String, String>().apply { putAll(initialDrafts.contents) }
    }
    val host = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val editorLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val updated = draftStore.load()
            scripts.clear()
            scripts.addAll(updated.names)
            draftContents.clear()
            draftContents.putAll(updated.contents)
        }
    }

    AppTheme(dark) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(host) },
            bottomBar = {
                NavigationBar {
                    adminTabs.forEach { tab ->
                        NavigationBarItem(
                            selected = page == tab.key || (page !in adminTabs.map { it.key } && tab.key == "more"),
                            onClick = { page = tab.key },
                            icon = { Icon(tab.icon, contentDescription = tr(tab.vi, tab.en)) },
                            label = { Text(tr(tab.vi, tab.en), fontSize = 10.sp) }
                        )
                    }
                }
            }
        ) { padding ->
            when (page) {
                "overview" -> OverviewScreen(
                    Modifier.padding(padding),
                    onNavigate = { page = it },
                    onCreate = { createDialog = true }
                )
                "scripts" -> ScriptsScreen(
                    Modifier.padding(padding),
                    scripts = scripts,
                    onCreate = { createDialog = true },
                    onOpen = { name ->
                        editorLauncher.launch(ScriptEditorActivity.intent(context, name))
                    },
                    onPermissions = { page = "collaborators" }
                )
                "customers" -> CustomersAdminScreen(
                    accessToken = accessToken,
                    modifier = Modifier.padding(padding)
                )
                "devices" -> DevicesAdminScreen(
                    accessToken = accessToken,
                    modifier = Modifier.padding(padding)
                )
                else -> MoreScreen(
                    Modifier.padding(padding),
                    page,
                    onSelect = { page = it },
                    onTheme = { dark = !dark },
                    onMessage = { scope.launch { host.showSnackbar(it) } },
                    onLogout = onLogout,
                    accessToken = accessToken
                )
            }
        }

        if (createDialog) CreateDialog(
            onDismiss = { createDialog = false },
            onCreate = { name ->
                if (scripts.any { it.equals(name, ignoreCase = true) }) {
                    scope.launch {
                        host.showSnackbar(tr("Tên kịch bản đã tồn tại.", "A script with this name already exists."))
                    }
                } else {
                    scripts.add(name)
                    draftContents[name] = ""
                    draftStore.save(scripts.toList(), draftContents.toMap())
                    page = "scripts"
                    createDialog = false
                    editorLauncher.launch(ScriptEditorActivity.intent(context, name))
                }
            }
        )
    }
}

@Composable
internal fun CustomerApp(accessToken: String?, email: String, onLogout: () -> Unit, onRoleUpdated: () -> Unit) {
    var dark by rememberSaveable { mutableStateOf(true) }
    var page by rememberSaveable { mutableStateOf("home") }
    var scriptDetail by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val authRepository = remember(context) { AuthRepository(context) }
    val host = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    AppTheme(dark) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(host) },
            bottomBar = {
                NavigationBar {
                    customerTabs.forEach { tab ->
                        NavigationBarItem(
                            selected = page == tab.key,
                            onClick = { page = tab.key },
                            icon = { Icon(tab.icon, contentDescription = tr(tab.vi, tab.en)) },
                            label = { Text(tr(tab.vi, tab.en), fontSize = 10.sp) }
                        )
                    }
                }
            }
        ) { padding ->
            when (page) {
                "home" -> CustomerHome(Modifier.padding(padding), email = email, onScripts = { page = "scripts" })
                "scripts" -> CustomerPackagesScreen(
                    accessToken = accessToken,
                    modifier = Modifier.padding(padding),
                    onOpenDevices = { page = "device" }
                )
                "device" -> CustomerDeviceLicenseScreen(
                    accessToken = accessToken,
                    modifier = Modifier.padding(padding)
                )
                else -> SettingsScreen(
                    Modifier.padding(padding),
                    accessToken = accessToken,
                    onTheme = { dark = !dark },
                    onLogout = onLogout,
                    onAcceptInvitation = { invitationToken ->
                        if (accessToken.isNullOrBlank()) {
                            scope.launch { host.showSnackbar(tr("Chế độ xem thử không nhận lời mời thật.", "Preview mode cannot accept real invitations.")) }
                        } else {
                            scope.launch {
                                try {
                                    authRepository.acceptInvitation(accessToken, invitationToken)
                                    host.showSnackbar(tr("Đã chấp nhận lời mời. Đang cập nhật quyền tài khoản…", "Invitation accepted. Refreshing account access…"))
                                    onRoleUpdated()
                                } catch (error: Exception) {
                                    host.showSnackbar(error.message ?: tr("Không chấp nhận được lời mời.", "Could not accept invitation."))
                                }
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun OverviewScreen(modifier: Modifier, onNavigate: (String) -> Unit, onCreate: () -> Unit) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(15.dp)
    ) {
        BrandHeader(admin = true)
        Heading(tr("Tổng quan", "Overview"), tr("Quản lý ứng dụng và hoạt động hệ thống", "Manage the app and system activity"))
        CardBox {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Outlined.CloudOff, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Column {
                    Text(tr("Chưa kết nối máy chủ", "Server not connected"), fontWeight = FontWeight.SemiBold)
                    Text(tr("Bản thử nghiệm giao diện, chưa có dữ liệu thật.", "UI prototype, no live data yet."), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Metric(tr("Khách hàng", "Customers"), "—", Icons.Outlined.Group, Modifier.weight(1f))
            Metric(tr("Thiết bị", "Devices"), "—", Icons.Outlined.Smartphone, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Metric(tr("Giấy phép còn hạn", "Active licenses"), "—", Icons.Outlined.VerifiedUser, Modifier.weight(1f))
            Metric(tr("Kịch bản phát hành", "Published scripts"), "—", Icons.Outlined.Code, Modifier.weight(1f))
        }
        Text(tr("Truy cập nhanh", "Quick actions"), fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionTile(tr("Tạo kịch bản", "Create script"), Icons.Outlined.Add, Modifier.weight(1f), onCreate)
            ActionTile(tr("Kịch bản", "Scripts"), Icons.Outlined.ListAlt, Modifier.weight(1f)) { onNavigate("scripts") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionTile(tr("Khách hàng", "Customers"), Icons.Outlined.Group, Modifier.weight(1f)) { onNavigate("customers") }
            ActionTile(tr("Thiết bị", "Devices"), Icons.Outlined.Smartphone, Modifier.weight(1f)) { onNavigate("devices") }
        }
        Text(tr("Hoạt động gần đây", "Recent activity"), fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
        EmptyState(Icons.Outlined.Info, tr("Chưa có hoạt động", "No activity yet"), tr("Nhật ký sẽ xuất hiện khi máy chủ được kết nối.", "Activity will appear after the server is connected."))
        Text(tr("Prototype giao diện · Chưa kết nối máy chủ", "UI prototype · Server not connected"), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
    }
}

@Composable
private fun ScriptsScreen(
    modifier: Modifier,
    scripts: List<String>,
    onCreate: () -> Unit,
    onOpen: (String) -> Unit,
    onPermissions: () -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("all") }
    val filtered = scripts.filter { name ->
        val matchesQuery = name.contains(query, ignoreCase = true)
        val matchesStatus = filter == "all" || filter == "draft"
        matchesQuery && matchesStatus
    }
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        BrandHeader(admin = true)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text("AUTU", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text(
                    tr("Quản lý và xây dựng các AUTU của bạn", "Manage and build your AUTUs"),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            OutlinedButton(
                onClick = onPermissions,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                modifier = Modifier.height(42.dp)
            ) {
                Icon(Icons.Outlined.Security, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(5.dp))
                Text(tr("Quyền", "Access"), fontSize = 12.sp)
            }
            FilledIconButton(
                onClick = onCreate,
                modifier = Modifier.size(46.dp)
            ) {
                Icon(
                    Icons.Outlined.Add,
                    contentDescription = tr("Thêm AUTU", "Add AUTU"),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            placeholder = { Text(tr("Tìm kiếm kịch bản", "Search scripts")) }
        )
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("all" to tr("Tất cả", "All"), "draft" to tr("Bản nháp", "Draft"), "published" to tr("Đã phát hành", "Published")).forEach {
                FilterChip(filter == it.first, onClick = { filter = it.first }, label = { Text(it.second) })
            }
        }
        Text(tr("Bản nháp được lưu trên thiết bị này; chưa đồng bộ máy chủ.", "Drafts are saved on this device and are not synced to a server."), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (filtered.isEmpty()) {
            EmptyState(
                Icons.Outlined.ListAlt,
                if (filter == "published") tr("Chưa có kịch bản phát hành", "No published scripts") else tr("Chưa có kịch bản nào", "No scripts yet"),
                if (filter == "published") tr("Các kịch bản đã phát hành sẽ xuất hiện tại đây sau khi được kết nối.", "Published scripts will appear here after the service is connected.") else tr("Tạo kịch bản đầu tiên để bắt đầu.", "Create your first script to get started.")
            )
        } else filtered.forEach { name ->
            Card(
                modifier = Modifier.fillMaxWidth().clickable { onOpen(name) },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(name, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                        Text(tr("Bản nháp", "Draft"), color = MaterialTheme.colorScheme.primary, fontSize = 11.sp)
                    }
                    Text(tr("Chạm để mở trình soạn thảo prototype.", "Tap to open the editor prototype."), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun EditorScreen(
    name: String,
    code: String,
    onCodeChange: (String) -> Unit,
    modifier: Modifier,
    onBack: () -> Unit,
    onMessage: (String) -> Unit,
    onSaveDraft: () -> Unit,
    allowTest: Boolean,
    onPrepareRun: (String) -> Unit
) {
    var validationText by rememberSaveable { mutableStateOf("") }
    var validationSucceeded by rememberSaveable { mutableStateOf<Boolean?>(null) }
    var showTemplates by rememberSaveable { mutableStateOf(false) }
    var showHelp by rememberSaveable { mutableStateOf(false) }

    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        TextButton(onClick = onBack) { Text(tr("← Quay lại", "← Back")) }
        Heading(name, tr("Bản nháp · Lưu trên thiết bị", "Draft · Saved on this device"))
        Text(
            tr(
                "Chọn mẫu có sẵn để bắt đầu, hoặc tự viết và sửa mã nguồn bên dưới. AUTU-LIO Script 0.1 hỗ trợ tap, longPress, swipe, wait và repeat.",
                "Choose a built-in template, or write and edit your own source below. AUTU-LIO Script 0.1 supports tap, longPress, swipe, wait and repeat."
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            lineHeight = 18.sp
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { showTemplates = true },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Outlined.LibraryBooks, contentDescription = null)
                Spacer(Modifier.width(5.dp))
                Text(tr("Mẫu có sẵn", "Templates"))
            }
            OutlinedButton(
                onClick = { showHelp = true },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Outlined.HelpOutline, contentDescription = null)
                Spacer(Modifier.width(5.dp))
                Text(tr("Hướng dẫn", "Guide"))
            }
        }
        Text(
            tr("Mã nguồn AUTU-LIO · Bạn có thể tự nhập hoặc chỉnh sửa", "AUTU-LIO source · Write or edit your own code"),
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp
        )
        OutlinedTextField(
            value = code,
            onValueChange = {
                onCodeChange(it)
                validationText = ""
                validationSucceeded = null
            },
            modifier = Modifier.fillMaxWidth().weight(1f),
            minLines = 8,
            label = { Text(tr("Mã nguồn", "Source code")) },
            placeholder = { Text(tr("Nhập mã kịch bản hoặc chọn một mẫu...", "Enter a script or choose a template...")) }
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = {
                    val result = ScriptCompiler.compile(code)
                    validationSucceeded = result.isSuccess
                    validationText = if (result.isSuccess) {
                        val program = result.program!!
                        tr(
                            "Mã hợp lệ · AUTU-LIO " + program.languageVersion +
                                "\nSố lệnh sau khi mở rộng: " + program.expandedInstructionCount +
                                "\nTổng thời lượng tĩnh: " + program.estimatedDurationMs + " ms",
                            "Valid source · AUTU-LIO " + program.languageVersion +
                                "\nExpanded instructions: " + program.expandedInstructionCount +
                                "\nStatic duration: " + program.estimatedDurationMs + " ms"
                        )
                    } else {
                        result.diagnostics.joinToString("\n") { diagnostic ->
                            tr(
                                "Dòng " + diagnostic.line + ", cột " + diagnostic.column + ": " + diagnostic.messageVi,
                                "Line " + diagnostic.line + ", column " + diagnostic.column + ": " + diagnostic.messageEn
                            )
                        }
                    }
                },
                modifier = Modifier.weight(1f)
            ) { Text(tr("Kiểm tra mã", "Check code")) }
            if (allowTest) {
                Button(
                    onClick = { onPrepareRun(code) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Outlined.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(5.dp))
                    Text(tr("Chuẩn bị chạy", "Prepare run"))
                }
            }
        }
        if (validationText.isNotBlank()) {
            Text(
                validationText,
                color = if (validationSucceeded == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                fontSize = 12.sp,
                lineHeight = 18.sp
            )
        }
        OutlinedButton(onClick = onSaveDraft, modifier = Modifier.fillMaxWidth()) {
            Text(tr("Lưu bản nháp trên thiết bị", "Save draft on this device"))
        }
    }

    if (showTemplates) {
        AlertDialog(
            onDismissRequest = { showTemplates = false },
            title = { Text(tr("Chọn mẫu AUTU có sẵn", "Choose an AUTU template")) },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 430.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ScriptTemplateCatalog.templates.forEach { template ->
                        Card(
                            modifier = Modifier.fillMaxWidth().clickable {
                                onCodeChange(template.source)
                                validationText = ""
                                validationSucceeded = null
                                showTemplates = false
                            },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(
                                Modifier.fillMaxWidth().padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Text(
                                    tr(template.nameVi, template.nameEn),
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    tr(template.descriptionVi, template.descriptionEn),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 12.sp
                                )
                                Text(
                                    template.source,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp
                                )
                                Text(
                                    tr("Chạm để dùng mẫu này", "Tap to use this template"),
                                    color = MaterialTheme.colorScheme.secondary,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTemplates = false }) {
                    Text(tr("Đóng", "Close"))
                }
            }
        )
    }

    if (showHelp) {
        AlertDialog(
            onDismissRequest = { showHelp = false },
            title = { Text(tr("Hướng dẫn tạo và chạy AUTU", "How to create and run an AUTU")) },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    Text(
                        tr("1. Chọn mẫu có sẵn hoặc tự viết mã. Khi chọn mẫu, toàn bộ mã mẫu được đưa vào ô soạn thảo và bạn có thể sửa tiếp.", "1. Choose a template or write code yourself. A selected template is inserted into the editor and remains fully editable.")
                    )
                    Text(
                        tr("2. Sửa tọa độ X, Y theo vị trí trên màn hình điện thoại. Tọa độ tính bằng pixel; cần kiểm tra lại khi đổi độ phân giải hoặc xoay màn hình.", "2. Adjust X and Y to match positions on the phone screen. Coordinates are pixels and should be checked after changing resolution or rotating the device.")
                    )
                    Text(tr("Các lệnh đang hỗ trợ", "Supported commands"), fontWeight = FontWeight.SemiBold)
                    Text("CHẠM(x, y)  ·  " + tr("Chạm một lần", "Single tap"))
                    Text("NHẤN_GIỮ(x, y, ms)  ·  " + tr("Nhấn giữ", "Long press"))
                    Text("VUỐT(x1, y1, x2, y2, ms)  ·  " + tr("Vuốt", "Swipe"))
                    Text("CHỜ(ms)  ·  " + tr("Chờ", "Wait"))
                    Text("MỞ_ỨNG_DỤNG(\"tên.gói\")  ·  " + tr("Mở ứng dụng", "Launch an app"))
                    Text("ĐẶT ten = 3  ·  " + tr("Khai báo biến", "Declare a variable"))
                    Text("NẾU (ten > 1) { ... } NGƯỢC_LẠI { ... }")
                    Text("LẶP(ten) { ... }  ·  " + tr("Lặp theo số lần", "Repeat a fixed count"))
                    Text("LẶP_KHI (ten < 10) { ... }  ·  " + tr("Lặp theo điều kiện", "Repeat while a condition is true"))
                    Text("PIN(), ĐANG_SẠC(), CÓ_MẠNG(), MÀN_HÌNH_SÁNG()")
                    Text(
                        tr("3. Nhấn Kiểm tra mã để tìm lỗi cú pháp. Chỉ khi mã hợp lệ mới chuẩn bị được cửa sổ chạy.", "3. Tap Check code to find syntax errors. Only valid code can be prepared for running.")
                    )
                    Text(
                        tr("4. Bật quyền Trợ năng cho AUTU-LIO khi Android yêu cầu. Đây là quyền do bạn tự cấp trong Cài đặt Android.", "4. Enable AUTU-LIO Accessibility when Android asks. You grant this permission yourself in Android Settings.")
                    )
                    Text(
                        tr("5. Khi cửa sổ chạy xuất hiện, chuyển sang ứng dụng mục tiêu rồi nhấn Chạy trên cửa sổ nổi. Có thể Tạm dừng, Tiếp tục, Dừng hoặc thu nhỏ bất cứ lúc nào.", "5. When the runner appears, switch to the target app and press Run on the floating window. You can pause, resume, stop or collapse it.")
                    )
                    Text(
                        tr("Biến, phép tính, điều kiện, vòng lặp và các hàm trạng thái thiết bị đang được hỗ trợ. Quét ảnh/OCR và các hàm Android nâng cao chỉ hiển thị khi đã được tích hợp và cấp quyền phù hợp. Luôn kiểm tra tọa độ và tên gói trước khi chạy.", "Variables, expressions, conditions, loops and device-state functions are supported. Image/OCR and advanced Android functions are only available when integrated and properly authorized. Verify coordinates and package names before running.")
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showHelp = false }) {
                    Text(tr("Đã hiểu", "Got it"))
                }
            }
        )
    }
}

@Composable
private fun MoreScreen(
    modifier: Modifier,
    page: String,
    onSelect: (String) -> Unit,
    onTheme: () -> Unit,
    onMessage: (String) -> Unit,
    onLogout: () -> Unit,
    accessToken: String?
) {
    if (page == "licenses") {
        LicensesAdminScreen(accessToken = accessToken, modifier = modifier)
        return
    }
    if (page == "projects") {
        if (!accessToken.isNullOrBlank()) {
            ProjectsAdminScreen(accessToken, modifier = modifier, onBack = { onSelect("more") })
        } else {
            Column(modifier.fillMaxSize().padding(16.dp)) {
                TextButton(onClick = { onSelect("more") }) { Text(tr("← Quay lại", "← Back")) }
                EmptyState(Icons.Outlined.CloudOff, tr("Chưa có phiên đăng nhập", "No signed-in session"), tr("Đăng nhập lại để quản lý dự án.", "Sign in again to manage projects."))
            }
        }
        return
    }
    val items = listOf(
        Triple("collaborators", tr("Cộng tác viên", "Collaborators"), Icons.Outlined.PersonAdd),
        Triple("projects", tr("Dự án cộng tác", "Shared projects"), Icons.Outlined.FolderOpen),
        Triple("licenses", tr("Giấy phép", "Licenses"), Icons.Outlined.VerifiedUser),
        Triple("library", tr("Thư viện", "Libraries"), Icons.Outlined.ListAlt),
        Triple("release", tr("Phát hành", "Releases"), Icons.Outlined.PlayArrow),
        Triple("logs", tr("Nhật ký", "Logs"), Icons.Outlined.Info),
        Triple("settings", tr("Cài đặt", "Settings"), Icons.Outlined.Settings)
    )
    val selected = items.firstOrNull { it.first == page }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        BrandHeader(admin = true)
        if (page == "more") {
            Heading(tr("Quản lý khác", "More management"), tr("Công cụ quản trị AUTU-LIO", "AUTU-LIO administration tools"))
            items.forEach { item ->
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onSelect(item.first) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(item.third, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Text(item.second, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
                        Icon(Icons.Outlined.ChevronRight, null)
                    }
                }
            }
            OutlinedButton(onClick = onTheme, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Palette, null)
                Spacer(Modifier.width(8.dp))
                Text(tr("Chuyển giao diện sáng / tối", "Toggle light / dark mode"))
            }
        } else {
            TextButton(onClick = { onSelect("more") }) {
                Text(tr("← Quay lại", "← Back"))
            }
            Heading(
                selected?.second ?: tr("Quản lý", "Management"),
                when (page) {
                    "collaborators" -> tr("Mời và phân quyền nhà phát triển, nhà thiết kế, người kiểm duyệt", "Invite and assign developers, designers and reviewers")
                    "licenses" -> tr("Cấp, gia hạn và thu hồi quyền sử dụng", "Issue, renew and revoke access")
                    "library" -> tr("Quản lý hàm và thành phần dùng chung", "Manage shared functions and components")
                    "release" -> tr("Quản lý phiên bản và phân phối", "Manage versions and distribution")
                    "logs" -> tr("Theo dõi sự kiện và lỗi hệ thống", "Review system events and errors")
                    else -> tr("Tùy chỉnh ứng dụng quản trị", "Customize the admin application")
                }
            )
            when (page) {
                "collaborators" -> CollaboratorsContent(accessToken = accessToken, onBack = { onSelect("more") })
                "licenses" -> {
                    CardBox {
                        Text(tr("Thông tin giấy phép", "License information"), fontWeight = FontWeight.SemiBold)
                        Text(tr("Khách hàng · Gói sử dụng · Thời hạn · Giới hạn thiết bị · Trạng thái", "Customer · Plan · Duration · Device limit · Status"), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    }
                    EmptyState(Icons.Outlined.VerifiedUser, tr("Chưa có giấy phép", "No licenses yet"), tr("Dữ liệu giấy phép sẽ xuất hiện sau khi kết nối máy chủ.", "License data will appear after the server is connected."))
                }
                "library" -> {
                    CardBox {
                        Text(tr("Thư viện dùng chung", "Shared libraries"), fontWeight = FontWeight.SemiBold)
                        Text(tr("Phiên bản · Hàm · Tương thích runtime · Kịch bản phụ thuộc", "Version · Functions · Runtime compatibility · Script dependencies"), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    }
                    EmptyState(Icons.Outlined.ListAlt, tr("Chưa có thư viện", "No libraries yet"), tr("Thư viện dùng chung sẽ xuất hiện sau khi bộ máy ngôn ngữ được triển khai.", "Shared libraries will appear after the language runtime is implemented."))
                }
                "release" -> {
                    CardBox {
                        Text(tr("Quy trình phát hành", "Release workflow"), fontWeight = FontWeight.SemiBold)
                        Text(tr("Kiểm tra · Tăng phiên bản · Ghi chú · Chọn kênh · Xác nhận · Phân phối", "Validate · Version · Notes · Channel · Confirm · Distribute"), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    }
                    EmptyState(Icons.Outlined.PlayArrow, tr("Chưa có bản phát hành", "No releases yet"), tr("Chỉ phát hành khi gói đã được kiểm tra và ký xác thực.", "A package can only be released after validation and signing."))
                }
                "logs" -> {
                    CardBox {
                        Text(tr("Nhật ký hệ thống", "System logs"), fontWeight = FontWeight.SemiBold)
                        Text(tr("Thời gian · Loại sự kiện · Mức độ · Thiết bị · Kết quả", "Time · Event type · Severity · Device · Result"), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    }
                    EmptyState(Icons.Outlined.Info, tr("Chưa có nhật ký", "No logs yet"), tr("Nhật ký chỉ xuất hiện sau khi các thành phần thực tế được kết nối.", "Logs will appear after live components are connected."))
                }
                "settings" -> {
                    SettingRow(Icons.Outlined.Palette, tr("Giao diện", "Appearance"), tr("Chuyển đổi sáng và tối", "Switch light and dark mode"), onTheme)
                    SettingRow(Icons.Outlined.Info, tr("Thông tin ứng dụng", "App information"), "AUTU-LIO · 0.1.0")
                    SettingRow(Icons.Outlined.Lock, tr("Bảo mật", "Security"), tr("Vai trò và quyền được xác minh từ máy chủ.", "Roles and permissions are verified by the server."))
                    SettingRow(Icons.Outlined.Logout, tr("Đăng xuất", "Sign out"), tr("Kết thúc phiên đăng nhập trên thiết bị này.", "End the session on this device."), onLogout)
                }
            }
            OutlinedButton(
                onClick = { onMessage(tr("Máy chủ chưa được kết nối. Thao tác chưa thể lưu dữ liệu thật.", "The server is not connected. This action cannot save live data yet.")) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(tr("Kiểm tra kết nối máy chủ", "Check server connection"))
            }
        }
    }
}

@Composable
private fun AdminModulePage(
    modifier: Modifier,
    title: String,
    subtitle: String,
    icon: ImageVector,
    details: List<String>,
    onBack: () -> Unit
) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        BrandHeader(admin = true)
        TextButton(onClick = onBack) { Text(tr("← Quay lại", "← Back")) }
        Heading(title, subtitle)
        CardBox {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Outlined.CloudOff, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Column {
                    Text(tr("Chưa kết nối máy chủ", "Server not connected"), fontWeight = FontWeight.SemiBold)
                    Text(tr("Đây là giao diện prototype; chưa có dữ liệu khách hàng hoặc thiết bị thật.", "This is a UI prototype; no live customer or device data is available."), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
            }
        }
        Text(tr("Thông tin sẽ quản lý", "Information managed"), fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
        details.forEach { detail ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Text(detail, fontSize = 13.sp)
                }
            }
        }
        EmptyState(icon, tr("Chưa có dữ liệu", "No data yet"), tr("Dữ liệu thật sẽ hiển thị sau khi tích hợp xác thực và máy chủ.", "Live data will appear after authentication and server integration."))
    }
}

@Composable
private fun CustomerHome(modifier: Modifier, email: String, onScripts: () -> Unit) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) {
        BrandHeader(admin = false)
        Heading(tr("Xin chào!", "Welcome!"), tr("Sẵn sàng sử dụng AUTU-LIO", "Ready to use AUTU-LIO"))
        CardBox {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Outlined.VerifiedUser, null, tint = MaterialTheme.colorScheme.primary)
                Column {
                    Text(tr("Giấy phép chưa kết nối", "License not connected"), fontWeight = FontWeight.SemiBold)
                    Text(tr("Đăng nhập để xác minh khi dịch vụ được tích hợp.", "Sign in to verify after the service is integrated."), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Text(tr("Kịch bản của bạn", "Your scripts"), fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
        EmptyState(Icons.Outlined.ListAlt, tr("Chưa có kịch bản", "No scripts available"), tr("Kịch bản được chủ ứng dụng phát hành sẽ xuất hiện tại đây.", "Scripts published by the app owner will appear here."))
        Button(onClick = onScripts, modifier = Modifier.fillMaxWidth()) { Text(tr("Xem kịch bản", "View scripts")) }
        Text(email, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CustomerScriptsScreen(modifier: Modifier, onOpen: () -> Unit) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        BrandHeader(admin = false)
        Heading(tr("Kịch bản", "Scripts"), tr("Các kịch bản do chủ ứng dụng cung cấp", "Scripts provided by the app owner"))
        Card(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                Modifier.fillMaxWidth().padding(15.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        tr("MẪU XEM TRƯỚC · KHÔNG PHẢI GÓI THẬT", "PREVIEW SAMPLE · NOT A REAL PACKAGE"),
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                        color = MaterialTheme.colorScheme.secondary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("Kịch bản minh họa", "Sample script"), modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                    Icon(Icons.Outlined.ChevronRight, contentDescription = null)
                }
                Text(
                    tr("Màn hình mẫu để kiểm tra luồng xem chi tiết và các nút điều khiển. Không chứa thao tác tự động hóa thật.",
                        "A sample screen to preview the details and control flow. It contains no real automation actions."),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
                OutlinedButton(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
                    Text(tr("Xem chi tiết mẫu", "View sample details"))
                }
            }
        }
        CardBox {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Outlined.CloudOff, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Column {
                    Text(tr("Kho kịch bản chưa kết nối", "Script library not connected"), fontWeight = FontWeight.SemiBold)
                    Text(
                        tr("Kịch bản thật sẽ xuất hiện sau khi đăng nhập và đồng bộ giấy phép.",
                            "Real scripts will appear after sign-in and license synchronization."),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun CustomerScriptDetail(
    modifier: Modifier,
    onBack: () -> Unit,
    onPreviewAction: () -> Unit
) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        BrandHeader(admin = false)
        TextButton(onClick = onBack) { Text(tr("← Quay lại danh sách", "← Back to scripts")) }
        Heading(tr("Kịch bản minh họa", "Sample script"), tr("Chi tiết bản xem trước", "Preview details"))
        CardBox {
            Surface(
                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.14f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    tr("CHỈ XEM TRƯỚC", "PREVIEW ONLY"),
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp
                )
            }
            Text(tr("Mô tả", "Description"), fontWeight = FontWeight.SemiBold)
            Text(
                tr("Đây là nội dung minh họa để kiểm tra bố cục. Chưa có package được phát hành hoặc tải về thiết bị.",
                    "This is sample content for layout testing. No released package has been downloaded to this device."),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                lineHeight = 18.sp
            )
        }
        CardBox {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Icon(Icons.Outlined.CloudOff, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Column {
                    Text(tr("Chưa sẵn sàng chạy", "Not ready to run"), fontWeight = FontWeight.SemiBold)
                    Text(
                        tr("Runtime AutoClick và quyền trợ năng chưa được tích hợp.", "The AutoClick runtime and accessibility service are not integrated."),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
            }
        }
        Text(tr("Bảng điều khiển", "Controls"), fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
        Button(onClick = onPreviewAction, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.PlayArrow, null)
            Spacer(Modifier.width(8.dp))
            Text(tr("Thử nút chạy (không thực thi)", "Preview Run button (no execution)"))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            OutlinedButton(onClick = onPreviewAction, modifier = Modifier.weight(1f)) {
                Icon(Icons.Outlined.Pause, null)
                Spacer(Modifier.width(5.dp))
                Text(tr("Tạm dừng mẫu", "Pause sample"))
            }
            OutlinedButton(onClick = onPreviewAction, modifier = Modifier.weight(1f)) {
                Icon(Icons.Outlined.Stop, null)
                Spacer(Modifier.width(5.dp))
                Text(tr("Dừng mẫu", "Stop sample"))
            }
        }
        Text(
            tr("Các nút trên chỉ kiểm tra giao diện và thông báo. Chúng không chạm, vuốt hoặc điều khiển ứng dụng khác.",
                "These buttons only test the interface and messages. They do not tap, swipe, or control other apps."),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            lineHeight = 16.sp
        )
    }
}

@Composable
private fun CustomerDeviceLicensePage(modifier: Modifier) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        BrandHeader(admin = false)
        Heading(tr("Thiết bị và giấy phép", "Device and license"), tr("Thông tin đăng ký của tài khoản", "Account registration information"))
        CardBox {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Outlined.CloudOff, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Column {
                    Text(tr("Chưa đăng nhập thật", "Not signed in"), fontWeight = FontWeight.SemiBold)
                    Text(
                        tr("Đây là chế độ xem trước. Chưa có dữ liệu thiết bị hoặc giấy phép được truy xuất.",
                            "This is preview mode. No device or license data has been retrieved."),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
            }
        }
        Text(tr("Thiết bị hiện tại", "Current device"), fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
        SettingRow(Icons.Outlined.Smartphone, tr("Thông tin thiết bị", "Device information"), tr("Chỉ hiển thị sau khi đăng nhập và đồng ý cung cấp dữ liệu cần thiết.", "Shown after sign-in and consent to necessary data."))
        Text(tr("Giấy phép", "License"), fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
        EmptyState(
            Icons.Outlined.VerifiedUser,
            tr("Chưa có thông tin giấy phép", "No license information"),
            tr("Gói sử dụng, thời hạn và giới hạn thiết bị sẽ hiển thị sau khi máy chủ xác minh tài khoản.",
                "Plan, duration and device limit will appear after the server verifies the account.")
        )
    }
}

@Composable
private fun SettingsScreen(
    modifier: Modifier,
    accessToken: String?,
    onTheme: () -> Unit,
    onLogout: () -> Unit,
    onAcceptInvitation: (String) -> Unit
) {
    var invitationToken by rememberSaveable { mutableStateOf("") }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
        BrandHeader(admin = false)
        Heading(tr("Cài đặt", "Settings"), tr("Tùy chỉnh AUTU-LIO", "Customize AUTU-LIO"))
        SettingRow(Icons.Outlined.Palette, tr("Giao diện", "Appearance"), tr("Chuyển đổi sáng và tối", "Switch light and dark mode"), onTheme)
        SettingRow(Icons.Outlined.Info, tr("Thông tin ứng dụng", "App information"), "AUTU-LIO v${BuildConfig.VERSION_NAME}")
        SettingRow(Icons.Outlined.Lock, tr("Quyền riêng tư", "Privacy"), tr("Vai trò và giấy phép được máy chủ xác minh.", "Roles and licenses are verified by the server."))
        CardBox {
            Text(tr("Bạn có mã lời mời cộng tác viên?", "Have a collaborator invitation token?"), fontWeight = FontWeight.SemiBold)
            Text(
                tr("Đăng nhập bằng đúng Gmail đã nhận lời mời, sau đó nhập mã bên dưới để chấp nhận và nhận vai trò.",
                    "Sign in with the exact Google account that received the invitation, then enter its token below to accept the assigned role."),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                lineHeight = 18.sp
            )
            OutlinedTextField(
                value = invitationToken,
                onValueChange = { invitationToken = it.trim() },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(tr("Mã lời mời", "Invitation token")) },
                minLines = 2
            )
            Button(
                onClick = { onAcceptInvitation(invitationToken.trim()) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !accessToken.isNullOrBlank() && invitationToken.isNotBlank()
            ) {
                Text(tr("Chấp nhận lời mời", "Accept invitation"))
            }
        }
        SettingRow(Icons.Outlined.Logout, tr("Đăng xuất", "Sign out"), tr("Kết thúc phiên đăng nhập trên thiết bị này.", "End the session on this device."), onLogout)
    }
}

@Composable
private fun SettingRow(icon: ImageVector, title: String, detail: String, onClick: (() -> Unit)? = null) {
    Card(
        modifier = Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(detail, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (onClick != null) Icon(Icons.Outlined.ChevronRight, null)
        }
    }
}

@Composable
private fun EmptyPage(modifier: Modifier, title: String, subtitle: String, icon: ImageVector) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) {
        BrandHeader(admin = false)
        Heading(title, subtitle)
        EmptyState(icon, tr("Chưa có dữ liệu", "No data yet"), tr("Thông tin sẽ xuất hiện sau khi đăng nhập và kết nối dịch vụ.", "Information will appear after sign-in and service connection."))
    }
}

@Composable
private fun BrandHeader(admin: Boolean) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)) {
                Icon(Icons.Outlined.Code, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(10.dp).size(22.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("AUTU-LIO", fontWeight = FontWeight.Bold, fontSize = 17.sp, letterSpacing = 0.5.sp)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(if (admin) "ADMIN STUDIO" else "AUTOCLICK", fontSize = 10.sp, letterSpacing = 1.1.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("·", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("v${BuildConfig.VERSION_NAME}", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
internal fun Heading(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(subtitle, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun CardBox(content: @Composable ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), content = content)
    }
}

@Composable
private fun Metric(title: String, value: String, icon: ImageVector, modifier: Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(13.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(21.dp))
            Text(value, fontWeight = FontWeight.Bold, fontSize = 25.sp)
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            Text(tr("Chưa có dữ liệu", "No data yet"), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ActionTile(title: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Text(title, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
internal fun EmptyState(icon: ImageVector, title: String, message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 25.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
            Text(title, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, lineHeight = 18.sp, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun CreateDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Tạo kịch bản", "Create script")) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text(tr("Tên kịch bản", "Script name")) }
            )
        },
        confirmButton = {
            Button(onClick = { onCreate(name.trim()) }, enabled = name.isNotBlank()) {
                Text(tr("Tạo bản nháp", "Create draft"))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Hủy", "Cancel")) } }
    )
}


class ScriptEditorActivity : ComponentActivity() {
    companion object {
        private const val EXTRA_SCRIPT_NAME = "com.autulio.app.extra.SCRIPT_NAME"
        private const val EXTRA_NAMESPACE = "com.autulio.app.extra.SCRIPT_NAMESPACE"
        private const val EXTRA_ALLOW_TEST = "com.autulio.app.extra.ALLOW_TEST"

        fun intent(
            context: Context,
            scriptName: String,
            namespace: String = "admin",
            allowTest: Boolean = true
        ): Intent =
            Intent(context, ScriptEditorActivity::class.java)
                .putExtra(EXTRA_SCRIPT_NAME, scriptName)
                .putExtra(EXTRA_NAMESPACE, namespace)
                .putExtra(EXTRA_ALLOW_TEST, allowTest)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scriptName = intent.getStringExtra(EXTRA_SCRIPT_NAME)?.trim().orEmpty()
        val namespace = intent.getStringExtra(EXTRA_NAMESPACE) ?: "admin"
        val allowTest = intent.getBooleanExtra(EXTRA_ALLOW_TEST, false)
        if (scriptName.isBlank() || namespace !in setOf("admin", "developer")) {
            finish()
            return
        }

        val store = SecureScriptDraftStore(this, namespace)
        val initial = store.load()
        val initialNames = (initial.names + scriptName).distinctBy { it.lowercase(Locale.ROOT) }
        val initialCode = initial.contents[scriptName].orEmpty()
        setContent {
            var code by remember { mutableStateOf(initialCode) }
            val host = remember { SnackbarHostState() }
            val scope = rememberCoroutineScope()

            AppTheme(dark = true) {
                Scaffold(
                    containerColor = MaterialTheme.colorScheme.background,
                    snackbarHost = { SnackbarHost(host) }
                ) { padding ->
                    EditorScreen(
                        name = scriptName,
                        code = code,
                        onCodeChange = { updatedCode ->
                            code = updatedCode
                            store.save(initialNames, initial.contents + (scriptName to updatedCode))
                            setResult(Activity.RESULT_OK)
                        },
                        modifier = Modifier.padding(padding),
                        onBack = {
                            setResult(Activity.RESULT_OK)
                            finish()
                        },
                        onMessage = { message ->
                            scope.launch { host.showSnackbar(message) }
                        },
                        onSaveDraft = {
                            store.save(initialNames, initial.contents + (scriptName to code))
                            setResult(Activity.RESULT_OK)
                            scope.launch {
                                host.showSnackbar(
                                    tr("Đã lưu bản nháp được mã hóa trên thiết bị.", "Encrypted draft saved on this device.")
                                )
                            }
                        },
                        allowTest = allowTest,
                        onPrepareRun = { source ->
                            if (!allowTest) return@EditorScreen
                            store.save(initialNames, initial.contents + (scriptName to source))
                            setResult(Activity.RESULT_OK)
                            val compilation = ScriptCompiler.compile(source)
                            if (!compilation.isSuccess) {
                                val message = compilation.diagnostics.joinToString("\n") { diagnostic ->
                                    tr(
                                        "Dòng ${diagnostic.line}, cột ${diagnostic.column}: ${diagnostic.messageVi}",
                                        "Line ${diagnostic.line}, column ${diagnostic.column}: ${diagnostic.messageEn}"
                                    )
                                }
                                scope.launch { host.showSnackbar(message) }
                            } else {
                                val service = AutoClickAccessibilityService.current()
                                if (service == null) {
                                    scope.launch {
                                        host.showSnackbar(
                                            tr(
                                                "Hãy bật AUTU-LIO trong Cài đặt > Trợ năng, sau đó quay lại chuẩn bị chạy.",
                                                "Enable AUTU-LIO in Settings > Accessibility, then return to prepare the runner."
                                            )
                                        )
                                    }
                                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                                } else if (service.prepareProgram(compilation.program!!)) {
                                    scope.launch {
                                        host.showSnackbar(
                                            tr(
                                                "Cửa sổ chạy đã sẵn sàng. Mở ứng dụng mục tiêu rồi nhấn Chạy trên cửa sổ nổi.",
                                                "Runner is ready. Open the target app, then press Run in the floating window."
                                            )
                                        )
                                    }
                                    finish()
                                } else {
                                    scope.launch {
                                        host.showSnackbar(
                                            tr("Không mở được cửa sổ chạy. Hãy kiểm tra quyền Trợ năng hoặc dừng kịch bản đang chạy.", "The runner window could not be opened. Check Accessibility access or stop the current script.")
                                        )
                                    }
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}
