package com.autulio.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
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
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.util.Locale
import com.autulio.app.access.AccessPermission
import com.autulio.app.access.AccessRole
import com.autulio.app.access.hasAutomationAccessibilityPermission
import com.autulio.app.accessibility.AutoClickAccessibilityService
import com.autulio.app.auth.AuthApiException
import com.autulio.app.auth.AuthRepository
import com.autulio.app.auth.SecureSessionStore
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
    AuthenticatedAppHost()
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
    var pendingEditorIntent by remember { mutableStateOf<Intent?>(null) }
    val permissionSettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        val pending = pendingEditorIntent
        pendingEditorIntent = null
        if (pending != null) {
            if (hasAutomationAccessibilityPermission(context)) {
                editorLauncher.launch(pending)
            } else {
                scope.launch {
                    host.showSnackbar(tr("Cần bật quyền Trợ năng để tạo hoặc sửa kịch bản.", "Enable Accessibility access before creating or editing scripts."))
                }
            }
        }
    }
    fun launchProtectedEditor(intent: Intent) {
        if (hasAutomationAccessibilityPermission(context)) {
            editorLauncher.launch(intent)
        } else {
            pendingEditorIntent = intent
            permissionSettingsLauncher.launch(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
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
                    onCreate = {
                        if (hasAutomationAccessibilityPermission(context)) createDialog = true
                        else permissionSettingsLauncher.launch(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    }
                )
                "scripts" -> ScriptsScreen(
                    Modifier.padding(padding),
                    scripts = scripts,
                    onCreate = { createDialog = true },
                    onOpen = { name ->
                        launchProtectedEditor(ScriptEditorActivity.intent(context, name))
                    },
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
                    accessToken = accessToken,
                    onOpenTemplate = { template ->
                        val draftName = "Mẫu - " + tr(template.nameVi, template.nameEn)
                        launchProtectedEditor(
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
                if (!hasAutomationAccessibilityPermission(context)) {
                    createDialog = false
                    permissionSettingsLauncher.launch(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                } else if (scripts.any { it.equals(name, ignoreCase = true) }) {
                    scope.launch {
                        host.showSnackbar(tr("Tên kịch bản đã tồn tại.", "A script with this name already exists."))
                    }
                } else {
                    scripts.add(name)
                    draftContents[name] = ""
                    draftStore.save(scripts.toList(), draftContents.toMap())
                    page = "scripts"
                    createDialog = false
                    launchProtectedEditor(ScriptEditorActivity.intent(context, name))
                }
            }
        )
    }
}

@Composable
internal fun CustomerApp(accessToken: String?, email: String) {
    var showDeviceSetup by rememberSaveable { mutableStateOf(false) }
    var deviceRegistrationVersion by rememberSaveable { mutableStateOf(0) }

    AppTheme(dark = true) {
        Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
            CustomerPackagesScreen(
                accessToken = accessToken,
                modifier = Modifier.padding(padding),
                onOpenDevices = { showDeviceSetup = true },
                deviceRegistrationVersion = deviceRegistrationVersion
            )
        }
    }

    if (showDeviceSetup) {
        Dialog(onDismissRequest = { showDeviceSetup = false }) {
            Surface(
                modifier = Modifier.fillMaxWidth().heightIn(max = 680.dp),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                CustomerDeviceLicenseScreen(
                    accessToken = accessToken,
                    modifier = Modifier.fillMaxWidth(),
                    onRegistered = {
                        deviceRegistrationVersion += 1
                        showDeviceSetup = false
                    }
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
                    Text("CHỜ(ms)  ·  " + tr("Chờ", "Wait"))
                    Text("MỞ_ỨNG_DỤNG(\"tên.gói\")  ·  " + tr("Mở ứng dụng", "Launch an app"))
                    Text("ĐẶT ten = 3  ·  " + tr("Khai báo biến", "Declare a variable"))
                    Text("NẾU (ten > 1) { ... } NGƯỢC_LẠI { ... }")
                    Text("LẶP(ten) { ... }  ·  " + tr("Lặp theo số lần", "Repeat a fixed count"))
                    Text("LẶP_KHI (ten < 10) { ... }  ·  " + tr("Lặp khi điều kiện đúng", "Repeat while a condition is true"))
                    Text("LẶP_ĐẾN_KHI (ten >= 10) { ... }  ·  " + tr("Lặp đến khi điều kiện đúng", "Repeat until a condition is true"))
                    Text("CHỜ_ĐẾN_KHI(điều_kiện, thời_hạn_ms, chu_kỳ_ms)")
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
    accessToken: String?,
    onOpenTemplate: (ScriptTemplate) -> Unit
) {
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
                    "licenses" -> tr("Cấp, gia hạn và thu hồi quyền sử dụng", "Issue, renew and revoke access")
                    "library" -> tr("Quản lý hàm và thành phần dùng chung", "Manage shared functions and components")
                    "packages" -> tr("Mã hóa, phát hành phiên bản và phân phối AUTU cho khách hàng", "Encrypt, publish and distribute AUTU versions to customers")
                    "release" -> tr("Quản lý phiên bản và phân phối", "Manage versions and distribution")
                    "logs" -> tr("Theo dõi sự kiện và lỗi hệ thống", "Review system events and errors")
                    else -> tr("Tùy chỉnh ứng dụng quản trị", "Customize the admin application")
                }
            )
            when (page) {
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
                "release" -> {
                    CardBox {
                        Text(tr("Quy trình phát hành", "Release workflow"), fontWeight = FontWeight.SemiBold)
                        Text(tr("Kiểm tra · Tăng phiên bản · Ghi chú · Chọn kênh · Xác nhận · Phân phối", "Validate · Version · Notes · Channel · Confirm · Distribute"), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    }
                    EmptyState(Icons.Outlined.PlayArrow, tr("Chưa có bản phát hành", "No releases yet"), tr("Chỉ phát hành khi gói đã được kiểm tra và ký xác thực.", "A package can only be released after validation and signing."))
                }
                "logs" -> AdminAuditLogsScreen(accessToken = accessToken)
                "settings" -> {
                    SettingRow(Icons.Outlined.Palette, tr("Giao diện", "Appearance"), tr("Chuyển đổi sáng và tối", "Switch light and dark mode"), onTheme)
                    SettingRow(Icons.Outlined.Info, tr("Thông tin ứng dụng", "App information"), "AUTU-LIO · 0.1.0")
                    SettingRow(Icons.Outlined.Lock, tr("Bảo mật", "Security"), tr("Vai trò và quyền được xác minh từ máy chủ.", "Roles and permissions are verified by the server."))
                    SettingRow(Icons.Outlined.Logout, tr("Đăng xuất", "Sign out"), tr("Kết thúc phiên đăng nhập trên thiết bị này.", "End the session on this device."), onLogout)
                }
            }
            if (page !in setOf("packages", "logs")) {
                OutlinedButton(
                    onClick = { onMessage(tr("Máy chủ chưa được kết nối. Thao tác chưa thể lưu dữ liệu thật.", "The server is not connected. This action cannot save live data yet.")) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(tr("Kiểm tra kết nối máy chủ", "Check server connection"))
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
        private const val EXTRA_INITIAL_SOURCE = "com.autulio.app.extra.INITIAL_SOURCE"

        fun intent(
            context: Context,
            scriptName: String,
            namespace: String = "admin",
            initialSource: String? = null
        ): Intent =
            Intent(context, ScriptEditorActivity::class.java)
                .putExtra(EXTRA_SCRIPT_NAME, scriptName)
                .putExtra(EXTRA_NAMESPACE, namespace)
                .putExtra(EXTRA_INITIAL_SOURCE, initialSource)

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)

        val scriptName = intent.getStringExtra(EXTRA_SCRIPT_NAME)?.trim().orEmpty()
        val namespace = intent.getStringExtra(EXTRA_NAMESPACE) ?: "admin"
        if (scriptName.isBlank() || namespace != "admin") {
            finish()
            return
        }
        if (!hasAutomationAccessibilityPermission(this)) {
            Toast.makeText(this, tr("Cần cấp quyền Trợ năng trước khi chỉnh sửa hoặc chạy script.", "Accessibility access is required before editing or running scripts."), Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            finish()
            return
        }

        lifecycleScope.launch {
            try {
                val accessToken = SecureSessionStore(this@ScriptEditorActivity).read()
                if (accessToken.isNullOrBlank()) {
                    denyEditorAccess()
                    return@launch
                }
                val user = AuthRepository(this@ScriptEditorActivity).currentUser(accessToken)
                val roleAllowed = namespace == "admin" && user.role == AccessRole.OWNER
                val canEdit = AccessPermission.EDIT_SCRIPTS in user.permissions
                if (!roleAllowed || !canEdit) {
                    denyEditorAccess()
                    return@launch
                }
                val service = AutoClickAccessibilityService.current()
                if (service == null || !service.showFloatingWindowForEditing(scriptName)) {
                    Toast.makeText(
                        this@ScriptEditorActivity,
                        tr("Không mở được cửa sổ nổi. Hãy bật lại AUTU-LIO trong Cài đặt > Trợ năng.", "Could not open the floating window. Enable AUTU-LIO again in Settings > Accessibility.")
                    , Toast.LENGTH_LONG).show()
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    finish()
                    return@launch
                }
                showEditor(
                    scriptName = scriptName,
                    namespace = namespace,
                    allowTest = AccessPermission.TEST_SCRIPTS in user.permissions
                )
            } catch (error: Exception) {
                if (error is AuthApiException && error.statusCode in 401..403) {
                    SecureSessionStore(this@ScriptEditorActivity).clear()
                }
                denyEditorAccess()
            }
        }
    }

    private fun denyEditorAccess() {
        Toast.makeText(
            this,
            tr("Không thể xác minh quyền sửa mã nguồn. Vui lòng đăng nhập lại hoặc liên hệ Chủ sở hữu.", "Script editing permission could not be verified. Sign in again or contact the Owner."),
            Toast.LENGTH_LONG
        ).show()
        finish()
    }

    private fun showEditor(scriptName: String, namespace: String, allowTest: Boolean) {
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
                            if (!hasAutomationAccessibilityPermission(this@ScriptEditorActivity)) {
                                scope.launch {
                                    host.showSnackbar(tr("Quyền Trợ năng đã bị tắt. Hãy cấp lại quyền trước khi chạy script.", "Accessibility access was disabled. Grant it again before running scripts."))
                                }
                                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                                return@EditorScreen
                            }
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
                                } else if (service.prepareProgram(compilation.program!!, scriptName, namespace)) {
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

    private var accessibilityRegrantRequested = false

    override fun onResume() {
        super.onResume()
        if (!hasAutomationAccessibilityPermission(this) && !isFinishing && !accessibilityRegrantRequested) {
            accessibilityRegrantRequested = true
            Toast.makeText(
                this,
                tr("Quyền Trợ năng đã bị tắt. AUTU-LIO yêu cầu cấp lại trước khi tiếp tục chỉnh sửa.", "Accessibility access was disabled. AUTU-LIO requires it again before editing can continue."),
                Toast.LENGTH_LONG
            ).show()
            finish()
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        } else if (hasAutomationAccessibilityPermission(this)) {
            accessibilityRegrantRequested = false
        }
    }

    override fun onDestroy() {
        if (isFinishing) AutoClickAccessibilityService.current()?.finishEditing()
        super.onDestroy()
    }

}
