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
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.util.Locale
import com.autulio.app.access.AccessRole
import com.autulio.app.accessibility.AutoClickAccessibilityService
import com.autulio.app.access.defaultPermissions
import com.autulio.app.auth.AuthRepository
import com.autulio.app.auth.AuthenticatedAppHost
import com.autulio.app.script.ScriptCompiler
import com.autulio.app.script.ScriptTemplate
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
            else -> CustomerApp(
                accessToken = null,
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
                    accessToken = accessToken,
                    onNavigate = { page = it },
                    onCreate = { createDialog = true }
                )
                "scripts" -> ScriptsScreen(
                    Modifier.padding(padding),
                    scripts = scripts,
                    onCreate = { createDialog = true },
                    onOpen = { name ->
                        editorLauncher.launch(ScriptEditorActivity.intent(context, name))
                    }
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
                    onLogout = onLogout,
                    accessToken = accessToken,
                    onOpenTemplate = { template ->
                        val draftName = "Mẫu - " + tr(template.nameVi, template.nameEn)
                        editorLauncher.launch(
                            ScriptEditorActivity.intent(
                                context = context,
                                scriptName = draftName,
                                initialSource = template.source
                            )
                        )
                    }
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
internal fun CustomerApp(accessToken: String?, email: String, onLogout: () -> Unit) {
    var dark by rememberSaveable { mutableStateOf(true) }
    var page by rememberSaveable { mutableStateOf("home") }

    AppTheme(dark) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
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
                "home" -> CustomerHome(
                    modifier = Modifier.padding(padding),
                    email = email,
                    accessToken = accessToken,
                    onScripts = { page = "scripts" },
                    onDevices = { page = "device" }
                )
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
                    onTheme = { dark = !dark },
                    onLogout = onLogout
                )
            }
        }
    }
}

@Composable
private fun OverviewScreen(
    modifier: Modifier,
    accessToken: String?,
    onNavigate: (String) -> Unit,
    onCreate: () -> Unit
) {
    val context = LocalContext.current
    val repository = remember(context) { AuthRepository(context) }
    val scope = rememberCoroutineScope()
    var customers by remember { mutableStateOf<List<com.autulio.app.auth.CustomerRecord>>(emptyList()) }
    var licenses by remember { mutableStateOf<List<com.autulio.app.auth.LicenseRecord>>(emptyList()) }
    var devices by remember { mutableStateOf<List<com.autulio.app.auth.DeviceRecord>>(emptyList()) }
    var packages by remember { mutableStateOf<List<com.autulio.app.auth.AutuPackageRecord>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    suspend fun refresh() {
        val token = accessToken
        if (token.isNullOrBlank()) return
        loading = true
        error = ""
        try {
            coroutineScope {
                val customersRequest = async { repository.listAdminCustomers(token) }
                val licensesRequest = async { repository.listAdminLicenses(token) }
                val devicesRequest = async { repository.listAdminDevices(token) }
                val packagesRequest = async { repository.listAdminPackages(token) }
                customers = customersRequest.await()
                licenses = licensesRequest.await()
                devices = devicesRequest.await()
                packages = packagesRequest.await()
            }
        } catch (exception: Exception) {
            error = exception.message?.takeIf { it.isNotBlank() }
                ?: tr("Không tải được số liệu tổng quan.", "Could not load dashboard data.")
        } finally {
            loading = false
        }
    }

    LaunchedEffect(accessToken) { refresh() }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(15.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                BrandHeader(admin = true)
                Heading(
                    tr("Tổng quan", "Overview"),
                    tr("Số liệu quản lý được đồng bộ trực tiếp từ máy chủ", "Management figures synchronized directly from the server")
                )
            }
            IconButton(onClick = { scope.launch { refresh() } }, enabled = !loading && !accessToken.isNullOrBlank()) {
                Icon(Icons.Outlined.Refresh, contentDescription = tr("Làm mới", "Refresh"))
            }
        }

        if (accessToken.isNullOrBlank()) {
            CardBox {
                Text(tr("Chưa có phiên đăng nhập thật", "No authenticated session"), fontWeight = FontWeight.SemiBold)
                Text(
                    tr("Số liệu quản lý sẽ được tải sau khi đăng nhập bằng tài khoản chủ sở hữu.", "Management data loads after signing in with the owner account."),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }
        } else {
            if (loading && customers.isEmpty() && licenses.isEmpty() && devices.isEmpty() && packages.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            if (error.isNotBlank()) {
                CardBox {
                    Text(tr("Không thể đồng bộ dữ liệu", "Could not synchronize data"), fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.error)
                    Text(error, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    OutlinedButton(onClick = { scope.launch { refresh() } }, enabled = !loading) {
                        Icon(Icons.Outlined.Refresh, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(tr("Thử lại", "Retry"))
                    }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Metric(tr("Khách hàng", "Customers"), customers.size.toString(), Icons.Outlined.Group, Modifier.weight(1f))
                    Metric(tr("Thiết bị", "Devices"), devices.count { it.status == "ACTIVE" }.toString(), Icons.Outlined.Smartphone, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Metric(tr("Giấy phép hoạt động", "Active licenses"), licenses.count { it.status == "ACTIVE" }.toString(), Icons.Outlined.VerifiedUser, Modifier.weight(1f))
                    Metric(tr("Gói AUTU hoạt động", "Active AUTU packages"), packages.count { it.status == "ACTIVE" }.toString(), Icons.Outlined.Code, Modifier.weight(1f))
                }
                Text(
                    tr("Dữ liệu được tải từ API quản lý. Kéo xuống để xem thêm hoặc nhấn làm mới để đồng bộ lại.", "Data is loaded from management APIs. Scroll down or tap refresh to synchronize again."),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
            }
        }

        Text(tr("Truy cập nhanh", "Quick actions"), fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionTile(tr("Tạo AUTU", "Create AUTU"), Icons.Outlined.Add, Modifier.weight(1f), onCreate)
            ActionTile(tr("Kịch bản", "Scripts"), Icons.Outlined.ListAlt, Modifier.weight(1f)) { onNavigate("scripts") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionTile(tr("Khách hàng", "Customers"), Icons.Outlined.Group, Modifier.weight(1f)) { onNavigate("customers") }
            ActionTile(tr("Thiết bị", "Devices"), Icons.Outlined.Smartphone, Modifier.weight(1f)) { onNavigate("devices") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionTile(tr("Giấy phép", "Licenses"), Icons.Outlined.VerifiedUser, Modifier.weight(1f)) { onNavigate("licenses") }
            ActionTile(tr("Phát hành gói", "Publish packages"), Icons.Outlined.CloudUpload, Modifier.weight(1f)) { onNavigate("packages") }
        }
    }
}

@Composable
private fun ScriptsScreen(
    modifier: Modifier,
    scripts: List<String>,
    onCreate: () -> Unit,
    onOpen: (String) -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = scripts.filter { name -> name.contains(query, ignoreCase = true) }
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
        Text(tr("Bản nháp được lưu trên thiết bị này; chưa đồng bộ máy chủ.", "Drafts are saved on this device and are not synced to a server."), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (filtered.isEmpty()) {
            EmptyState(
                Icons.Outlined.ListAlt,
                tr("Chưa có kịch bản nào", "No scripts yet"),
                tr("Tạo kịch bản đầu tiên để bắt đầu.", "Create your first script to get started.")
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
                    Text(tr("Chạm để mở trình soạn thảo và tiếp tục chỉnh sửa AUTU.", "Tap to open the editor and continue editing this AUTU."), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
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
                "Chọn mẫu có sẵn để bắt đầu, hoặc tự viết và sửa mã nguồn bên dưới. AUTU-LIO Script ${ScriptCompiler.LANGUAGE_VERSION} hỗ trợ lệnh thao tác, biến, biểu thức, điều kiện, vòng lặp và trạng thái thiết bị.",
                "Choose a built-in template, or write and edit your own source below. AUTU-LIO Script ${ScriptCompiler.LANGUAGE_VERSION} supports gestures, variables, expressions, conditions, loops and device state."
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
                    Text("CHẠM_ĐA_ĐIỂM(ĐƯỜNG_ĐIỂM, ms)  ·  " + tr("Chạm đồng thời 2–5 điểm", "Simultaneous 2–5 finger tap"))
                    Text("VUỐT_ĐA_ĐIỂM(CÁC_ĐƯỜNG, ms)  ·  " + tr("Vuốt đồng thời nhiều ngón", "Simultaneous multi-finger paths"))
                    Text(tr("Mỗi đường là danh sách tọa độ X/Y xen kẽ; tối đa 5 ngón, 16 điểm mỗi đường.", "Each path is a flat X/Y coordinate list; maximum 5 fingers and 16 points per path."))
                    Text("CHỜ(ms)  ·  " + tr("Chờ", "Wait"))
                    Text("MỞ_ỨNG_DỤNG(\"tên.gói\")  ·  " + tr("Mở ứng dụng", "Launch an app"))
                    Text("QUAY_LẠI()  ·  " + tr("Thực hiện thao tác quay lại", "Run Android Back action"))
                    Text("VỀ_MÀN_HÌNH_CHÍNH()  ·  " + tr("Về màn hình chính", "Go to the Android home screen"))
                    Text("MỞ_ỨNG_DỤNG_GẦN_ĐÂY()  ·  " + tr("Mở giao diện ứng dụng gần đây", "Open recent apps"))
                    Text("MỞ_THANH_THÔNG_BÁO()  ·  " + tr("Mở thanh thông báo", "Open the notification shade"))
                    Text("MỞ_BẢNG_CÀI_ĐẶT_NHANH()  ·  " + tr("Mở bảng cài đặt nhanh", "Open Quick Settings"))
                    Text("MỞ_HỘP_THOẠI_NGUỒN()  ·  " + tr("Mở hộp thoại nguồn", "Open the power dialog"))
                    Text("KHÓA_MÀN_HÌNH()  ·  " + tr("Khóa màn hình", "Lock the screen"))
                    Text("BẬT_TẮT_CHIA_MÀN_HÌNH()  ·  " + tr("Bật hoặc tắt chế độ chia màn hình", "Toggle split-screen mode"))
                    Text("ĐẶT SO_LAN: SỐ = 3  ·  " + tr("Khai báo biến có kiểu", "Declare a typed variable"))
                    Text("ĐẶT NOI_DUNG: CHUỖI = \"SẴN SÀNG\"")
                    Text("ĐẶT DA_SANG: LUẬN_LÝ = ĐÚNG")
                    Text("NẾU (SO_LAN > 1 VÀ DA_SANG) { ... } NGƯỢC_LẠI { ... }")
                    Text("NGƯỢC_LẠI_NẾU (SO_LAN == 1) { ... }")
                    Text("LẶP(SO_LAN) { ... }")
                    Text("LẶP_KHI (SO_LAN < 10) { ... }")
                    Text("LẶP_ĐẾN_KHI(SO_LAN >= 10) { ... }")
                    Text("ĐẶT DANH_SÁCH: DANH_SÁCH = TẠO_DANH_SÁCH(10, 20, 30)")
                    Text("LẶP_MỖI (PHAN_TU TRONG DANH_SÁCH) { CHẠM(PHAN_TU, 400) }")
                    Text("ĐẶT CAU_HINH: BẢN_ĐỒ = TẠO_BẢN_ĐỒ(\"TEN\", \"AUTU-LIO\", \"SO\", 3)")
                    Text("ĐẶT_GIÁ_TRỊ(CAU_HINH, \"SO\", 4) · LẤY_GIÁ_TRỊ(CAU_HINH, \"SO\")")
                    Text("CÓ_KHÓA(CAU_HINH, \"SO\") · XÓA_KHÓA(CAU_HINH, \"SO\") · DANH_SÁCH_KHÓA(CAU_HINH)")
                    Text("CHỜ_ĐẾN_KHI(ỨNG_DỤNG_ĐANG_MỞ(\"com.example.game\"), 15000, 250)")
                    Text("THOÁT_LẶP  ·  BỎ_QUA_LƯỢT")
                    Text("ĐỊNH_NGHĨA_HÀM TINH_TONG(A: SỐ, B: SỐ): SỐ { TRẢ_VỀ A + B }")
                    Text("ĐẶT KET_QUA: SỐ = TINH_TONG(10, 20)")
                    Text("SỐ(GIA_TRI), CHUỖI(GIA_TRI), LÀM_TRÒN(SO), TUYỆT_ĐỐI(SO), ĐỘ_DÀI(CHUOI)")
                    Text("CHỮ_HOA(CHUOI), CHỮ_THƯỜNG(CHUOI), CẮT_KHOẢNG_TRẮNG(CHUOI)")
                    Text("CHỨA_CHUỖI(A, B), BẮT_ĐẦU_BẰNG(A, B), KẾT_THÚC_BẰNG(A, B)")
                    Text("THAY_THẾ(A, CU, MOI), TÁCH_CHUỖI(CHUOI, PHAN_CACH), NỐI_CHUỖI(DANH_SACH, PHAN_CACH)")
                    Text("KÝ_TỰ_TẠI(CHUOI, VI_TRI), CẮT_CHUỖI(CHUOI, VI_TRI, DO_DAI)")
                    Text("NHỎ_NHẤT(A, B), LỚN_NHẤT(A, B)")
                    Text("TẠO_DANH_SÁCH(...), ĐỘ_DÀI(DANH_SÁCH), LẤY_PHẦN_TỬ(DANH_SÁCH, CHI_SO)")
                    Text("THÊM_PHẦN_TỬ(DANH_SÁCH, GIA_TRI), ĐẶT_PHẦN_TỬ(DANH_SÁCH, CHI_SO, GIA_TRI)")
                    Text("XÓA_PHẦN_TỬ(DANH_SÁCH, CHI_SO), CÓ_PHẦN_TỬ(DANH_SÁCH, GIA_TRI)")
                    Text("PIN(), PHẦN_TRĂM_PIN(), ĐANG_SẠC(), CÓ_MẠNG(), MÀN_HÌNH_SÁNG()")
                    Text("ỨNG_DỤNG_ĐÃ_CÀI(\"tên.gói\"), ỨNG_DỤNG_ĐANG_MỞ(\"tên.gói\")")
                    Text("CHIỀU_RỘNG_MÀN_HÌNH(), CHIỀU_CAO_MÀN_HÌNH(), THỜI_GIAN_MS()")
                    Text("ĐỌC_VĂN_BẢN_MÀN_HÌNH(), MÀN_HÌNH_CHỨA_CHỮ(\"nội dung\"), TÌM_VỊ_TRÍ_CHỮ(\"nội dung\")")
                    Text("ĐỌC_CẤU_TRÚC_VĂN_BẢN_MÀN_HÌNH()")
                    Text(tr("Trả về danh sách khối và dòng OCR kèm tọa độ pixel; mỗi khối có VĂN_BẢN, X, Y, RỘNG, CAO và CÁC_DÒNG.", "Returns OCR blocks and lines with pixel bounds; each block has text, X, Y, width, height and lines."))
                    Text("ĐỌC_VĂN_BẢN_VÙNG(X, Y, RỘNG, CAO), MÀN_HÌNH_CHỨA_CHỮ_TRONG_VÙNG(\"OK\", X, Y, RỘNG, CAO)")
                    Text("TÌM_VỊ_TRÍ_CHỮ_TRONG_VÙNG(\"OK\", X, Y, RỘNG, CAO)")
                    Text("PHÂN_TÍCH_JSON(\"{\\\"A\\\":1}\"), CHUYỂN_JSON(GIÁ_TRỊ), RỖNG")
                    Text("ĐỌC_TỆP(\"settings/a.txt\"), GHI_TỆP(\"settings/a.txt\", NỘI_DUNG)")
                    Text("TỆP_TỒN_TẠI(\"settings/a.txt\"), XÓA_TỆP(\"settings/a.txt\"), TẠO_THƯ_MỤC(\"settings\")")
                    Text("LIỆT_KÊ_TỆP(\"settings\"), ĐỌC_JSON_TỆP(\"settings/a.json\"), GHI_JSON_TỆP(\"settings/a.json\", GIÁ_TRỊ)")
                    Text("GỬI_YÊU_CẦU_HTTP(\"GET\", \"https://api.example.com/status\", RỖNG, TẠO_BẢN_ĐỒ(), 10000)")
                    Text(tr("HTTP chỉ dùng HTTPS; GET/POST/PUT/DELETE/HEAD; tối đa 1 MiB mỗi chiều và timeout 30 giây.", "HTTP uses HTTPS only; GET/POST/PUT/DELETE/HEAD; 1 MiB request/response limit and 30-second timeout."))
                    Text(tr("Tệp lưu trong vùng riêng AUTU-LIO, đường dẫn tương đối, tối đa 1 MiB mỗi tệp.", "Files stay in AUTU-LIO private storage, use relative paths, and are limited to 1 MiB per file."))
                    Text(tr("DANH_SÁCH và BẢN_ĐỒ có thể lồng nhau; giới hạn 8 cấp, 512 giá trị.", "Lists and maps can be nested; limited to 8 levels and 512 values."))
                    Text("NHẬN_DIỆN_NHÃN_ẢNH_MÀN_HÌNH(), MÀN_HÌNH_CHỨA_NHÃN_ẢNH(\"Phone\")")
                    Text("QUÉT_MÃ_QR_MÀN_HÌNH(), QUÉT_MÃ_VẠCH_MÀN_HÌNH()")
                    Text("CHI_TIẾT_MÃ_QR_MÀN_HÌNH(), CHI_TIẾT_MÃ_VẠCH_MÀN_HÌNH()")
                    Text(tr("Trả về giá trị gốc/hiển thị, định dạng, loại nội dung và hộp tọa độ; không tự mở liên kết.", "Returns raw/display value, format, content type and bounds; does not open links automatically."))
                    Text("PHÁT_HIỆN_VẬT_THỂ_MÀN_HÌNH(), MÀN_HÌNH_CÓ_VẬT_THỂ(\"Food\")")
                    Text("TÌM_TỌA_ĐỘ_VẬT_THỂ(\"Food\"), ĐẾM_VẬT_THỂ_MÀN_HÌNH()")
                    Text("MÀU_TẠI(X, Y), MÀN_HÌNH_CÓ_MÀU(ĐỎ, XANH_LÁ, XANH_DƯƠNG, SAI_SỐ)")
                    Text("TÌM_VỊ_TRÍ_MÀU(ĐỎ, XANH_LÁ, XANH_DƯƠNG, SAI_SỐ)")
                    Text("MẬT_ĐỘ_MÀN_HÌNH(), DP_SANG_PIXEL(DP), PIXEL_SANG_DP(PIXEL)")
                    Text("CHIỀU_RỘNG_DP(), CHIỀU_CAO_DP(), TỶ_LỆ_KHUNG_HÌNH()")
                    Text("HƯỚNG_MÀN_HÌNH(), XOAY_MÀN_HÌNH(), GÓC_XOAY_MÀN_HÌNH()")
                    Text("GÓI_ỨNG_DỤNG_ĐANG_MỞ()  ·  " + tr("Tên package foreground từ sự kiện Trợ năng", "Foreground package from Accessibility events"))
                    Text("HÃNG_SẢN_XUẤT(), MẪU_THIẾT_BỊ(), PHIÊN_BẢN_ANDROID(), MỨC_API_ANDROID()")
                    Text("MÀN_HÌNH_BỊ_KHÓA(), THIẾT_BỊ_ĐƯỢC_BẢO_MẬT()")
                    Text(tr("Thông tin model/Android và trạng thái khóa được đọc cục bộ từ Android; không gửi lên máy chủ.", "Device model/Android and keyguard state are read locally from Android; not uploaded to a server."))
                    Text(tr("Hướng màn hình dựa trên chiều rộng/cao pixel thực; xoay trả mã 0–3 và góc 0°/90°/180°/270°.", "Orientation uses real pixel dimensions; rotation returns index 0–3 and 0°/90°/180°/270°."))
                    Text("TỌA_ĐỘ_X_THEO_TỶ_LỆ(TỶ_LỆ), TỌA_ĐỘ_Y_THEO_TỶ_LỆ(TỶ_LỆ)")
                    Text("TỶ_LỆ_X_THEO_TỌA_ĐỘ(X), TỶ_LỆ_Y_THEO_TỌA_ĐỘ(Y)")
                    Text(tr("Tỷ lệ tọa độ nằm trong khoảng 0 đến 1; tọa độ 0 và 1 tương ứng pixel đầu và cuối màn hình.", "Coordinate ratios range from 0 to 1; 0 and 1 map to the first and last screen pixels."))
                    Text("TỶ_LỆ_MÀU_TRONG_VÙNG(ĐỎ, XANH_LÁ, XANH_DƯƠNG, SAI_SỐ, X, Y, RỘNG, CAO)")
                    Text(tr("Phân tích RGB dùng ảnh chụp màn hình tại thiết bị, lấy mẫu có giới hạn; tỷ lệ màu là giá trị ước lượng.", "RGB analysis uses on-device screenshots and bounded sampling; region color percentage is an estimate."))
                    Text(tr("Hộp giới hạn theo pixel; bộ nhận diện ảnh tĩnh trả tối đa 5 vật thể và nhãn nhóm tổng quát.", "Pixel bounding boxes; the static image detector returns up to 5 objects with coarse labels."))
                    Text(tr("OCR vùng trả tọa độ pixel toàn màn hình; nhận diện ảnh trả nhãn tổng quát; quét mã trả nội dung đã giải mã.", "Region OCR returns full-screen pixel coordinates; image labeling returns general labels; barcode scans return decoded values."))
                    Text("TÌM_THÀNH_PHẦN_VĂN_BẢN(\"Đăng nhập\"), NHẤN_THÀNH_PHẦN_VĂN_BẢN(\"Đăng nhập\")")
                    Text("NHẬP_VĂN_BẢN_VÀO_THÀNH_PHẦN(\"Tên\", \"LIO\"), TÌM_TỌA_ĐỘ_THÀNH_PHẦN(\"Tên\")")
                    Text("TÌM_THÀNH_PHẦN_THEO_ID(\"login_button\"), NHẤN_THÀNH_PHẦN_THEO_ID(\"login_button\")")
                    Text("NHẬP_VĂN_BẢN_THEO_ID(\"username\", \"AUTU\"), CUỘN_XUỐNG(), CUỘN_LÊN()")
                    Text("THỬ { ... } BẮT_LỖI (LỖI) { ... } LUÔN_THỰC_HIỆN { ... }")
                    Text(tr("Biến LỖI là BẢN_ĐỒ có khóa MÃ và THÔNG_BÁO. Trợ năng chỉ thao tác phần tử mà ứng dụng đích công khai.", "LỖI is a BẢN_ĐỒ with MÃ and THÔNG_BÁO keys. Accessibility can only act on elements exposed by the target app."))
                    Text(tr("OCR đọc chữ theo yêu cầu; vị trí trả về danh sách [X, Y] tính bằng pixel màn hình.", "OCR reads text on demand; the location function returns [X, Y] in screen pixels."))
                    Text("Toán tử: +  -  *  /  %  ^  ==  !=  >  >=  <  <=  VÀ  HOẶC  KHÔNG")
                    Text(tr("Danh sách chứa tối đa 64 phần tử; LẶP_MỖI duyệt bản sao an toàn. BẢN_ĐỒ chứa tối đa 64 cặp khóa–giá trị, khóa là CHUỖI không rỗng.", "Lists hold up to 64 items; LẶP_MỖI iterates a safe snapshot. BẢN_ĐỒ holds up to 64 key-value pairs with non-empty CHUỖI keys."))
                    Text(tr("Hàm tự định nghĩa phải viết HOA, khai báo ở cấp cao nhất; tối đa 16 tham số và 16 cấp gọi lồng nhau.", "User functions must be uppercase and declared at top level; up to 16 parameters and 16 nested calls."))
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
    onLogout: () -> Unit,
    accessToken: String?,
    onOpenTemplate: (ScriptTemplate) -> Unit
) {
    if (page == "collaborators" || page == "projects") {
        Column(
            modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            BrandHeader(admin = true)
            TextButton(onClick = { onSelect("more") }) { Text(tr("← Quay lại", "← Back")) }
            Heading(
                tr("Chức năng đang tạm hoãn", "Feature temporarily unavailable"),
                tr("AUTU-LIO hiện chỉ sử dụng hai vai trò: Chủ sở hữu và Người dùng AUTU.", "AUTU-LIO currently uses only two roles: Owner and AUTU Customer.")
            )
            Text(
                tr("Mời người viết AUTU và không gian cộng tác sẽ được mở ở giai đoạn sau.", "AUTU writer invitations and shared workspaces will be enabled in a later phase."),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
        }
        return
    }
    if (page == "licenses") {
        LicensesAdminScreen(accessToken = accessToken, modifier = modifier)
        return
    }
    if (page == "packages") {
        AdminPackagesScreen(accessToken = accessToken, modifier = modifier, onBack = { onSelect("more") })
        return
    }
    if (page == "library") {
        AdminTemplateLibraryScreen(
            modifier = modifier,
            onBack = { onSelect("more") },
            onOpenTemplate = onOpenTemplate
        )
        return
    }
    val items = listOf(
        Triple("licenses", tr("Giấy phép", "Licenses"), Icons.Outlined.VerifiedUser),
        Triple("library", tr("Thư viện mẫu AUTU", "AUTU template library"), Icons.Outlined.ListAlt),
        Triple("packages", tr("Gói AUTU", "AUTU packages"), Icons.Outlined.Code),
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
                    "packages" -> tr("Mã hóa, phát hành phiên bản và phân phối AUTU cho khách hàng", "Encrypt, publish and distribute AUTU versions to customers")
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
                "packages" -> AdminPackagesScreen(
                    accessToken = accessToken,
                    modifier = Modifier.fillMaxSize(),
                    onBack = { onSelect("more") }
                )
                "logs" -> AdminAuditLogsScreen(accessToken = accessToken)
                "settings" -> {
                    SettingRow(Icons.Outlined.Palette, tr("Giao diện", "Appearance"), tr("Chuyển đổi sáng và tối", "Switch light and dark mode"), onTheme)
                    SettingRow(Icons.Outlined.Info, tr("Thông tin ứng dụng", "App information"), "AUTU-LIO · v${BuildConfig.VERSION_NAME}")
                    SettingRow(Icons.Outlined.Lock, tr("Bảo mật", "Security"), tr("Vai trò và quyền được xác minh từ máy chủ.", "Roles and permissions are verified by the server."))
                    SettingRow(Icons.Outlined.Logout, tr("Đăng xuất", "Sign out"), tr("Kết thúc phiên đăng nhập trên thiết bị này.", "End the session on this device."), onLogout)
                }
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
private fun CustomerHome(
    modifier: Modifier,
    email: String,
    accessToken: String?,
    onScripts: () -> Unit,
    onDevices: () -> Unit
) {
    val context = LocalContext.current
    val repository = remember(context) { AuthRepository(context) }
    var account by remember { mutableStateOf<com.autulio.app.auth.CustomerAccountOverview?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }

    LaunchedEffect(accessToken) {
        if (!accessToken.isNullOrBlank()) {
            loading = true
            error = ""
            try {
                account = repository.customerAccount(accessToken)
            } catch (exception: Exception) {
                error = exception.message ?: tr("Không tải được trạng thái tài khoản.", "Could not load account status.")
            } finally {
                loading = false
            }
        } else {
            loading = false
        }
    }

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) {
        BrandHeader(admin = false)
        Heading(tr("Xin chào!", "Welcome!"), tr("Trung tâm sử dụng AUTU-LIO", "AUTU-LIO customer center"))
        CardBox {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Outlined.VerifiedUser, null, tint = MaterialTheme.colorScheme.primary)
                Column {
                    Text(tr("Trạng thái giấy phép", "License status"), fontWeight = FontWeight.SemiBold)
                    when {
                        loading -> Text(tr("Đang kiểm tra với máy chủ…", "Checking with the server…"), fontSize = 12.sp)
                        account?.license == null -> Text(tr("Chưa được cấp giấy phép.", "No license has been issued."), color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                        else -> {
                            Text(
                                "${account?.license?.planCode} · ${account?.license?.effectiveStatus}",
                                color = if (account?.canRun == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                tr(
                                    "Thiết bị đã đăng ký: ${account?.activeDeviceCount}/${account?.license?.maxDevices}",
                                    "Registered devices: ${account?.activeDeviceCount}/${account?.license?.maxDevices}"
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        }
                    }
                    if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                }
            }
        }
        Text(tr("Gói AUTU của bạn", "Your AUTU packages"), fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
        Text(
            tr("Chỉ các gói đã phát hành, có giấy phép hợp lệ và được cấp cho thiết bị này mới có thể tải và chạy.",
                "Only published packages allowed by your active license and registered device can be downloaded and run."),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            lineHeight = 18.sp
        )
        Button(onClick = onScripts, modifier = Modifier.fillMaxWidth()) { Text(tr("Mở kho AUTU", "Open AUTU library")) }
        OutlinedButton(onClick = onDevices, modifier = Modifier.fillMaxWidth()) { Text(tr("Thiết bị và giấy phép", "Devices and license")) }
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
    onTheme: () -> Unit,
    onLogout: () -> Unit
) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
        BrandHeader(admin = false)
        Heading(tr("Cài đặt", "Settings"), tr("Tùy chỉnh AUTU-LIO", "Customize AUTU-LIO"))
        SettingRow(Icons.Outlined.Palette, tr("Giao diện", "Appearance"), tr("Chuyển đổi sáng và tối", "Switch light and dark mode"), onTheme)
        SettingRow(Icons.Outlined.Info, tr("Thông tin ứng dụng", "App information"), "AUTU-LIO v${BuildConfig.VERSION_NAME}")
        SettingRow(Icons.Outlined.Lock, tr("Quyền riêng tư", "Privacy"), tr("Vai trò và giấy phép được máy chủ xác minh.", "Roles and licenses are verified by the server."))
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
internal fun BrandHeader(admin: Boolean) {
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
        private const val EXTRA_INITIAL_SOURCE = "com.autulio.app.extra.INITIAL_SOURCE"

        fun intent(
            context: Context,
            scriptName: String,
            namespace: String = "admin",
            allowTest: Boolean = true,
            initialSource: String? = null
        ): Intent =
            Intent(context, ScriptEditorActivity::class.java)
                .putExtra(EXTRA_SCRIPT_NAME, scriptName)
                .putExtra(EXTRA_NAMESPACE, namespace)
                .putExtra(EXTRA_ALLOW_TEST, allowTest)
                .putExtra(EXTRA_INITIAL_SOURCE, initialSource)
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
        val savedCode = initial.contents[scriptName]
        val initialCode = savedCode ?: intent.getStringExtra(EXTRA_INITIAL_SOURCE).orEmpty()
        if (savedCode == null && initialCode.isNotBlank()) {
            store.save(initialNames, initial.contents + (scriptName to initialCode))
        }
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
