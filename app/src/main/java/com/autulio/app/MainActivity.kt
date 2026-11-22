package com.autulio.app

import android.app.Activity
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Build
import android.net.Uri
import android.util.DisplayMetrics
import android.view.WindowInsets
import android.provider.Settings
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.util.Locale
import java.io.File
import com.autulio.app.access.AccessRole
import com.autulio.app.accessibility.AutoClickAccessibilityService
import com.autulio.app.auth.AuthRepository
import com.autulio.app.auth.AuthenticatedAppHost
import com.autulio.app.script.AutuSyntaxHighlighter
import com.autulio.app.script.ScriptCompiler
import com.autulio.app.script.ScriptTemplate
import com.autulio.app.script.ScriptTemplateCatalog
import com.autulio.app.runtime.ScriptDatabaseStore
import com.autulio.app.runtime.ScriptFileStore
import com.autulio.app.runtime.ScriptSafFileTransfer
import com.autulio.app.runtime.ScreenDisplayGeometry
import com.autulio.app.runtime.ScreenInsets
import com.autulio.app.runtime.ScreenCutoutBounds
import com.autulio.app.runtime.ScreenDisplayGeometryStore

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prepareDisplayGeometryCapture()
        setContent {
            AutuLioAppEntry()
        }
    }

    private fun prepareDisplayGeometryCapture() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode =
                    android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        val decor = window.decorView
        decor.setOnApplyWindowInsetsListener { _, insets ->
            cacheDisplayGeometry(insets)
            insets
        }
        decor.post { decor.requestApplyInsets() }
    }

    @Suppress("DEPRECATION")
    @SuppressLint("NewApi")
    private fun cacheDisplayGeometry(insets: WindowInsets) {
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getRealMetrics(metrics)
        val cutoutKnown = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
        val systemInsets: ScreenInsets
        val cutoutInsets: ScreenInsets

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bars = insets.getInsets(WindowInsets.Type.systemBars())
            val cutout = insets.getInsets(WindowInsets.Type.displayCutout())
            systemInsets = ScreenInsets(bars.left, bars.top, bars.right, bars.bottom)
            cutoutInsets = ScreenInsets(cutout.left, cutout.top, cutout.right, cutout.bottom)
        } else {
            systemInsets = ScreenInsets(
                insets.systemWindowInsetLeft,
                insets.systemWindowInsetTop,
                insets.systemWindowInsetRight,
                insets.systemWindowInsetBottom
            )
            val cutout = if (cutoutKnown) insets.displayCutout else null
            cutoutInsets = if (cutout == null) ScreenInsets() else ScreenInsets(
                cutout.safeInsetLeft,
                cutout.safeInsetTop,
                cutout.safeInsetRight,
                cutout.safeInsetBottom
            )
        }

        val cutout = if (cutoutKnown) insets.displayCutout else null
        val bounds = cutout?.boundingRects.orEmpty().map { rect ->
            ScreenCutoutBounds(rect.left, rect.top, rect.right, rect.bottom)
        }
        ScreenDisplayGeometryStore.publish(
            ScreenDisplayGeometry(
                widthPx = metrics.widthPixels,
                heightPx = metrics.heightPixels,
                systemInsets = systemInsets,
                cutoutSafeInsets = cutoutInsets,
                cutoutDetectionKnown = cutoutKnown,
                cutoutBounds = bounds
            )
        )
    }
}

@Composable
private fun AutuLioAppEntry() {
    val context = LocalContext.current
    var themeMode by remember(context) {
        mutableStateOf(UiThemePreferences.load(context))
    }
    var previewRole by rememberSaveable { mutableStateOf<String?>(null) }
    val systemIsDark = isSystemInDarkTheme()
    val updateThemeMode: (UiThemeMode) -> Unit = { mode ->
        themeMode = mode
        UiThemePreferences.save(context, mode)
    }

    CompositionLocalProvider(
        LocalUiThemeMode provides themeMode,
        LocalOnUiThemeModeChange provides updateThemeMode
    ) {
        AppTheme(dark = themeMode.isDark(systemIsDark)) {
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
    }
}
@Composable
private fun RolePreviewChooser(
    onOpenRole: (AccessRole) -> Unit
) {
    AppTheme(dark = LocalUiThemeMode.current.isDark(isSystemInDarkTheme())) {
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
// AUTU-LIO is Vietnamese-first by product requirement. Keep English strings
// in call sites only as reference/future localization source; never show raw
// English UI labels to customers.
internal fun tr(vi: String, en: String): String = vi

internal fun localizedErrorMessage(error: Throwable?, fallback: String): String {
    val message = error?.message?.trim().orEmpty()
    if (message.isBlank()) return fallback
    val hasVietnamese = message.any { character ->
        "ăâđêôơưáàảãạấầẩẫậắằẳẵặéèẻẽẹếềểễệíìỉĩịóòỏõọốồổỗộớờởỡợúùủũụứừửữựýỳỷỹỵ".contains(
            character.lowercaseChar()
        )
    }
    return if (hasVietnamese) message else fallback
}

internal fun statusText(value: String): String = when (value.trim().uppercase(Locale.ROOT)) {
    "ACTIVE" -> "Đang hoạt động"
    "SUSPENDED" -> "Đã chặn đăng nhập"
    "PAUSED" -> "Giấy phép tạm dừng"
    "REVOKED" -> "Đã thu hồi"
    "EXPIRED" -> "Đã hết hạn"
    "PENDING" -> "Đang chờ"
    "INACTIVE" -> "Không hoạt động"
    "ARCHIVED" -> "Đã lưu trữ"
    "FAILED" -> "Thất bại"
    "COMPLETED" -> "Hoàn tất"
    "CANCELLED" -> "Đã hủy"
    else -> "Trạng thái khác"
}

internal fun planText(value: String): String = when (value.trim().uppercase(Locale.ROOT)) {
    "WEEKLY" -> "Theo tuần"
    "MONTHLY" -> "Theo tháng"
    "QUARTERLY" -> "Theo quý"
    "YEARLY", "ANNUAL" -> "Theo năm"
    "LIFETIME" -> "Trọn đời"
    "TRIAL" -> "Dùng thử"
    else -> "Gói khác"
}

@Composable
internal fun AppTheme(dark: Boolean, content: @Composable () -> Unit) {
    val scheme = if (dark) darkColorScheme(
        primary = Color(0xFF4F8CFF),
        secondary = Color(0xFF35C6C8),
        background = Color.Black,
        surface = Color(0xFF101010),
        surfaceVariant = Color(0xFF191919),
        onBackground = Color(0xFFF2F2F2),
        onSurface = Color(0xFFF2F2F2),
        outline = Color(0xFF555555)
    ) else lightColorScheme(
        primary = Color(0xFF2459A6),
        secondary = Color(0xFF176D70),
        background = Color(0xFFE9E9E4),
        surface = Color(0xFFF2F2ED),
        surfaceVariant = Color(0xFFDEDED7),
        onBackground = Color(0xFF20211F),
        onSurface = Color(0xFF20211F),
        outline = Color(0xFF777971)
    )
    MaterialTheme(colorScheme = scheme, content = content)
}

private data class Tab(val key: String, val vi: String, val en: String, val icon: ImageVector)

private val adminTabs = listOf(
    Tab("overview", "Tổng quan", "Overview", Icons.Outlined.Home),
    Tab("scripts", "Kịch bản", "Scripts", Icons.Outlined.ListAlt),
    Tab("customers", "Khách hàng", "Customers", Icons.Outlined.Group),
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
    var page by rememberSaveable { mutableStateOf("overview") }
    var createDialog by remember { mutableStateOf(false) }
    var publishDraftName by rememberSaveable { mutableStateOf<String?>(null) }
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
            if (result.data?.getBooleanExtra("com.autulio.app.extra.OPEN_PACKAGE_PUBLISH", false) == true) {
                publishDraftName = result.data?.getStringExtra("com.autulio.app.extra.PUBLISH_DRAFT_NAME")
                page = "packages"
            }
        }
    }
    AppTheme(dark = LocalUiThemeMode.current.isDark(isSystemInDarkTheme())) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(host) },
            topBar = {
                GoogleAccountHeader(accessToken = accessToken)
            },
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
                    accessToken = accessToken
                )
                "scripts" -> ScriptsScreen(
                    Modifier.padding(padding),
                    scripts = scripts,
                    onCreate = { createDialog = true },
                    onOpen = { name ->
                        editorLauncher.launch(ScriptEditorActivity.intent(context, name))
                    }
                )
                "customers" -> AccessControlAdminScreen(
                    accessToken = accessToken,
                    modifier = Modifier.padding(padding)
                )
                "devices" -> AccessControlAdminScreen(
                    accessToken = accessToken,
                    modifier = Modifier.padding(padding),
                    initialSection = "devices"
                )
                else -> MoreScreen(
                    Modifier.padding(padding),
                    page,
                    onSelect = { page = it },
                    onLogout = onLogout,
                    accessToken = accessToken,
                    initialDraftName = publishDraftName,
                    onOpenTemplate = { template ->
                        val draftName = "Mẫu - " + tr(template.nameVi, template.nameEn)
                        editorLauncher.launch(
                            ScriptEditorActivity.intent(
                                context = context,
                                scriptName = draftName,
                                initialSource = template.source
                            )
                        )
                    },

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
    var page by rememberSaveable { mutableStateOf("home") }

    AppTheme(dark = LocalUiThemeMode.current.isDark(isSystemInDarkTheme())) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                Column {
                    GoogleAccountHeader(accessToken = accessToken, fallbackEmail = email)
                    CustomerLicenseCountdownBar(accessToken = accessToken)
                }
            },
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
                    accessToken = accessToken
                )
                "scripts" -> CustomerPackagesScreen(
                    accessToken = accessToken,
                    modifier = Modifier.padding(padding)
                )
                "device" -> CustomerDeviceLicenseScreen(
                    accessToken = accessToken,
                    modifier = Modifier.padding(padding)
                )
                else -> SettingsScreen(
                    Modifier.padding(padding),
                    onLogout = onLogout
                )
            }
        }
    }
}

@Composable
private fun OverviewScreen(
    modifier: Modifier,
    accessToken: String?
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
            error = localizedErrorMessage(exception, tr("Không tải được số liệu tổng quan.", "Could not load dashboard data."))
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
private fun SyntaxHighlightLegend() {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AutuSyntaxHighlighter.legendItems.forEach { item ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("●", color = item.color, fontSize = 10.sp)
                Text(
                    item.label,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun EditorImeQuickKeyBar(
    quickKeys: List<EditorQuickKey>,
    onInsert: (EditorQuickKey) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = RoundedCornerShape(10.dp),
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            quickKeys.forEach { quickKey ->
                OutlinedButton(
                    onClick = { onInsert(quickKey) },
                    modifier = Modifier
                        .heightIn(min = 36.dp)
                        .focusProperties { canFocus = false },
                    contentPadding = PaddingValues(horizontal = 9.dp, vertical = 2.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        quickKey.label,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp
                    )
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
    onPublishDraft: () -> Unit,
    allowPublish: Boolean,
    allowTest: Boolean,
    allowFloatingEditor: Boolean,
    onPrepareRun: (String) -> Unit,
    onOpenFloatingEditor: () -> Unit
) {
    var validationText by rememberSaveable { mutableStateOf("") }
    var validationSucceeded by rememberSaveable { mutableStateOf<Boolean?>(null) }
    var showTemplates by rememberSaveable { mutableStateOf(false) }
    var showHelp by rememberSaveable { mutableStateOf(false) }
    var templateSearch by rememberSaveable { mutableStateOf("") }
    var guideSearch by rememberSaveable { mutableStateOf("") }
    var templateDetailId by rememberSaveable { mutableStateOf<String?>(null) }
    var guideSectionDetailId by rememberSaveable { mutableStateOf<String?>(null) }
    var editorValue by remember {
        mutableStateOf(TextFieldValue(text = code, selection = TextRange(code.length)))
    }
    var showQuickKeys by rememberSaveable { mutableStateOf(true) }
    var editorHasFocus by rememberSaveable { mutableStateOf(false) }
    val editorFocusRequester = remember { FocusRequester() }
    val editorBringIntoViewRequester = remember { BringIntoViewRequester() }
    val editorScrollState = rememberScrollState()
    val keyboardController = LocalSoftwareKeyboardController.current
    val imeVisible = androidx.compose.foundation.layout.WindowInsets.ime.getBottom(LocalDensity.current) > 0

    LaunchedEffect(imeVisible, editorHasFocus) {
        if (imeVisible && editorHasFocus) {
            delay(180)
            editorBringIntoViewRequester.bringIntoView()
        }
    }
    LaunchedEffect(code) {
        if (editorValue.text != code) {
            editorValue = TextFieldValue(text = code, selection = TextRange(code.length))
        }
    }
    val quickKeys = remember {
        listOf(
            EditorQuickKey("_", "_"),
            EditorQuickKey("()", "()", 1),
            EditorQuickKey("{}", "{}", 1),
            EditorQuickKey("\"\"", "\"\"", 1),
            EditorQuickKey("//", "//"),
            EditorQuickKey(",", ","),
            EditorQuickKey(":", ":"),
            EditorQuickKey(";", ";"),
            EditorQuickKey("=", "="),
            EditorQuickKey("==", "=="),
            EditorQuickKey("!=", "!="),
            EditorQuickKey(">=", ">="),
            EditorQuickKey("<=", "<="),
            EditorQuickKey("+", "+"),
            EditorQuickKey("-", "-"),
            EditorQuickKey("*", "*"),
            EditorQuickKey("/", "/"),
            EditorQuickKey("%", "%"),
            EditorQuickKey("^", "^"),
            EditorQuickKey(">", ">"),
            EditorQuickKey("<", "<"),
            EditorQuickKey(".", ".")
        )
    }
    val selectedTemplate = ScriptTemplateCatalog.templates.firstOrNull { it.id == templateDetailId }
    val selectedGuideSection = EditorGuideCatalog.sections
        .firstOrNull { it.id == guideSectionDetailId }
        ?.let { section ->
            val query = guideSearch.trim()
            val categoryMatches = section.title.contains(query, ignoreCase = true) ||
                section.summary.contains(query, ignoreCase = true)
            if (query.isBlank() || categoryMatches) {
                section
            } else {
                section.copy(
                    commands = section.commands.filter { command ->
                        command.syntax.contains(query, ignoreCase = true) ||
                            command.description.contains(query, ignoreCase = true) ||
                            command.example.orEmpty().contains(query, ignoreCase = true)
                    }
                )
            }
        }

    Column(modifier.fillMaxSize().imePadding()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(editorScrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
        TextButton(onClick = onBack) { Text(tr("← Quay lại", "← Back")) }
        Heading(name, tr("Bản nháp · Lưu trên thiết bị", "Draft · Saved on this device"))
        Text(
            tr(
                "Chọn mẫu có sẵn để bắt đầu, hoặc tự viết và sửa mã nguồn bên dưới. ngôn ngữ lệnh AUTU-LIO ${ScriptCompiler.LANGUAGE_VERSION} hỗ trợ lệnh thao tác, biến, biểu thức, điều kiện, vòng lặp và trạng thái thiết bị.",
                "Choose a built-in template, or write and edit your own source below. ngôn ngữ lệnh AUTU-LIO ${ScriptCompiler.LANGUAGE_VERSION} supports gestures, variables, expressions, conditions, loops and device state."
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                tr("Mã nguồn AUTU-LIO", "AUTU-LIO source"),
                modifier = Modifier.weight(1f),
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp
            )
            TextButton(
                onClick = { showQuickKeys = !showQuickKeys },
                contentPadding = PaddingValues(horizontal = 7.dp, vertical = 0.dp)
            ) {
                Text(
                    if (showQuickKeys) tr("Phím tắt: bật", "Keys: on") else tr("Phím tắt: tắt", "Keys: off"),
                    fontSize = 11.sp
                )
            }
        }
        SyntaxHighlightLegend()
        OutlinedTextField(
            value = editorValue,
            onValueChange = {
                editorValue = it
                onCodeChange(it.text)
                validationText = ""
                validationSucceeded = null
            },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 240.dp, max = 360.dp)
                .bringIntoViewRequester(editorBringIntoViewRequester)
                .focusRequester(editorFocusRequester)
                .onFocusChanged { editorHasFocus = it.isFocused },
            minLines = 8,
            textStyle = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                lineHeight = 19.sp
            ),
            visualTransformation = AutuSyntaxHighlighter,
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
        if (allowFloatingEditor) {
            OutlinedButton(
                onClick = onOpenFloatingEditor,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(tr(
                    "Mở cửa sổ nổi để viết khi dùng ứng dụng khác",
                    "Open floating editor over other apps"
                ))
            }
        }
        if (allowPublish) {
            Button(
                onClick = onPublishDraft,
                modifier = Modifier.fillMaxWidth(),
                enabled = code.isNotBlank()
            ) {
                Icon(Icons.Outlined.VerifiedUser, contentDescription = null)
                Spacer(Modifier.width(7.dp))
                Text(tr("Chuyển sang mã hóa & phát hành", "Continue to secure packaging"))
            }
        }
        }
        if (showQuickKeys && imeVisible && editorHasFocus) {
            EditorImeQuickKeyBar(
                quickKeys = quickKeys,
                onInsert = { quickKey ->
                    val updatedValue = insertEditorQuickText(
                        editorValue,
                        quickKey.insertion,
                        quickKey.cursorOffset
                    )
                    editorValue = updatedValue
                    onCodeChange(updatedValue.text)
                    validationText = ""
                    validationSucceeded = null
                    editorFocusRequester.requestFocus()
                    keyboardController?.show()
                }
            )
        }
    }

    if (showTemplates) {
        AlertDialog(
            onDismissRequest = { showTemplates = false },
            title = { Text("Chọn mẫu AUTU có sẵn") },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 480.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = templateSearch,
                        onValueChange = { templateSearch = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Tìm mẫu") },
                        placeholder = { Text("Nhập tên mẫu cần tìm") },
                        leadingIcon = {
                            Icon(Icons.Outlined.Search, contentDescription = "Tìm mẫu")
                        }
                    )
                    val filteredTemplates = ScriptTemplateCatalog.templates.filter { template ->
                        template.nameVi.contains(templateSearch, ignoreCase = true) ||
                            template.descriptionVi.contains(templateSearch, ignoreCase = true)
                    }
                    Column(
                        modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        if (filteredTemplates.isEmpty()) {
                            Text(
                                "Không tìm thấy mẫu phù hợp.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp
                            )
                        }
                        filteredTemplates.forEach { template ->
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                ),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Column(
                                    Modifier.fillMaxWidth().padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(template.nameVi, fontWeight = FontWeight.SemiBold)
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        TextButton(
                                            onClick = { templateDetailId = template.id },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Icon(
                                                Icons.Outlined.Info,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(Modifier.width(4.dp))
                                            Text("Chi tiết")
                                        }
                                        Spacer(Modifier.weight(1f))
                                        Button(
                                            onClick = {
                                                val existingCode = code.trimEnd()
                                                val separator = if (existingCode.isBlank()) "" else "\n\n"
                                                onCodeChange(existingCode + separator + template.source.trim())
                                                validationText = ""
                                                validationSucceeded = null
                                                onMessage("Đã thêm mẫu vào cuối mã. Bạn có thể chọn thêm mẫu khác.")
                                            },
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                        ) {
                                            Icon(
                                                Icons.Outlined.Add,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(Modifier.width(4.dp))
                                            Text("Chèn mẫu")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showTemplates = false
                    templateSearch = ""
                }) {
                    Text("Đóng")
                }
            }
        )
    }

    if (selectedTemplate != null) {
        AlertDialog(
            onDismissRequest = { templateDetailId = null },
            title = { Text(selectedTemplate.nameVi) },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    Text(selectedTemplate.descriptionVi)
                    Text("Mã mẫu", fontWeight = FontWeight.SemiBold)
                    Text(
                        selectedTemplate.source,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                    Text(
                        "Nhấn Chèn mẫu để thêm đoạn mã này vào cuối nội dung đang có. Có thể chọn lại cùng một mẫu hoặc chèn nhiều mẫu liên tiếp.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { templateDetailId = null }) { Text("Đóng") }
            },
            dismissButton = {
                TextButton(onClick = {
                    val existingCode = code.trimEnd()
                    val separator = if (existingCode.isBlank()) "" else "\n\n"
                    onCodeChange(existingCode + separator + selectedTemplate.source.trim())
                    validationText = ""
                    validationSucceeded = null
                    templateDetailId = null
                    onMessage("Đã thêm mẫu vào cuối mã.")
                }) {
                    Text("Chèn mẫu")
                }
            }
        )
    }

    if (showHelp) {
        val normalizedGuideSearch = guideSearch.trim()
        val filteredGuideSections = EditorGuideCatalog.sections.mapNotNull { section ->
            if (normalizedGuideSearch.isBlank()) {
                section
            } else {
                val categoryMatches =
                    section.title.contains(normalizedGuideSearch, ignoreCase = true) ||
                        section.summary.contains(normalizedGuideSearch, ignoreCase = true)
                val matchingCommands = section.commands.filter { command ->
                    command.syntax.contains(normalizedGuideSearch, ignoreCase = true) ||
                        command.description.contains(normalizedGuideSearch, ignoreCase = true) ||
                        command.example.orEmpty().contains(normalizedGuideSearch, ignoreCase = true)
                }
                when {
                    categoryMatches -> section
                    matchingCommands.isNotEmpty() -> section.copy(commands = matchingCommands)
                    else -> null
                }
            }
        }
        AlertDialog(
            onDismissRequest = { showHelp = false },
            title = { Text("Danh mục lệnh AUTU-LIO") },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 500.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Lệnh được chia theo chức năng. Tìm theo tên lệnh hoặc mở một nhóm để xem cú pháp và ví dụ.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = guideSearch,
                        onValueChange = { guideSearch = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Tìm lệnh hoặc nhóm") },
                        placeholder = { Text("Ví dụ: QUAY_LẠI, PIN, JSON, OCR...") },
                        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                        trailingIcon = {
                            if (guideSearch.isNotBlank()) {
                                IconButton(onClick = { guideSearch = "" }) {
                                    Icon(Icons.Outlined.Close, contentDescription = "Xóa tìm kiếm")
                                }
                            }
                        }
                    )
                    Text(
                        if (normalizedGuideSearch.isBlank()) {
                            "${EditorGuideCatalog.sections.size} nhóm chức năng"
                        } else {
                            "${filteredGuideSections.size} nhóm phù hợp"
                        },
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Column(
                        modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        if (filteredGuideSections.isEmpty()) {
                            Text(
                                "Không tìm thấy lệnh phù hợp.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp
                            )
                        }
                        filteredGuideSections.forEach { section ->
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                ),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Row(
                                    Modifier.fillMaxWidth().padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                        Text(section.title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                        Text(
                                            section.summary,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 11.sp,
                                            lineHeight = 15.sp,
                                            maxLines = 2
                                        )
                                        Text(
                                            "${section.commands.size} mục",
                                            color = MaterialTheme.colorScheme.primary,
                                            fontSize = 10.sp
                                        )
                                    }
                                    TextButton(
                                        onClick = { guideSectionDetailId = section.id },
                                        contentPadding = PaddingValues(horizontal = 7.dp, vertical = 4.dp)
                                    ) {
                                        Text("Chi tiết")
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showHelp = false
                    guideSearch = ""
                }) { Text("Đóng") }
            }
        )
    }

    if (selectedGuideSection != null) {
        AlertDialog(
            onDismissRequest = { guideSectionDetailId = null },
            title = { Text(selectedGuideSection.title) },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    Text(selectedGuideSection.summary)
                    selectedGuideSection.commands.forEach { command ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                command.syntax,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp
                            )
                            Text(
                                command.description,
                                fontSize = 12.sp,
                                lineHeight = 17.sp
                            )
                            Text(
                                "Android tối thiểu: " + command.androidRequirement,
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                            if (!command.androidNote.isNullOrBlank()) {
                                Text(
                                    command.androidNote,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp
                                )
                            }
                            if (!command.example.isNullOrBlank()) {
                                Text(
                                    "Ví dụ: " + command.example,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp
                                )
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                    if (selectedGuideSection.notes.isNotEmpty()) {
                        Text("Lưu ý", fontWeight = FontWeight.SemiBold)
                        selectedGuideSection.notes.forEach { note ->
                            Text("• " + note, fontSize = 12.sp, lineHeight = 17.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { guideSectionDetailId = null }) { Text("Đóng") }
            }
        )
    }

}

@Composable
private fun MoreScreen(
    modifier: Modifier,
    page: String,
    onSelect: (String) -> Unit,
    onLogout: () -> Unit,
    accessToken: String?,
    initialDraftName: String? = null,
    onOpenTemplate: (ScriptTemplate) -> Unit
) {
    if (page == "licenses") {
        AccessControlAdminScreen(accessToken = accessToken, modifier = modifier, initialSection = "licenses")
        return
    }
    if (page == "packages") {
        AdminPackagesScreen(
            accessToken = accessToken,
            modifier = modifier,
            onBack = { onSelect("more") },
            initialDraftName = initialDraftName
        )
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
                "logs" -> AdminAuditLogsScreen(accessToken = accessToken)

                "settings" -> {
                    ThemeModeSelector()
                    SettingRow(Icons.Outlined.Info, tr("Thông tin ứng dụng", "App information"), "AUTU-LIO · v${BuildConfig.VERSION_NAME}")
                    SettingRow(Icons.Outlined.Lock, tr("Bảo mật", "Security"), tr("Vai trò được xác minh từ máy chủ; thông tin kỹ thuật thiết bị và kết nối được ghi nhận khi đăng nhập.", "Roles are verified by the server; basic device and network diagnostics are recorded at sign-in."))
                    DatabaseBackupControls()
        ScriptFileControls()
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
    accessToken: String?
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
                error = localizedErrorMessage(exception, tr("Không tải được trạng thái tài khoản.", "Could not load account status."))
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
                                "${account?.license?.planCode?.let(::planText)} · ${account?.license?.effectiveStatus?.let(::statusText)}",
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
private fun ThemeModeSelector() {
    val selectedMode = LocalUiThemeMode.current
    val onModeChange = LocalOnUiThemeModeChange.current
    val options = listOf(
        UiThemeMode.DARK to tr("Tối", "Dark"),
        UiThemeMode.LIGHT to tr("Sáng", "Light"),
        UiThemeMode.SYSTEM to tr("Theo hệ thống", "System")
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(9.dp))
                Column {
                    Text(tr("Giao diện", "Appearance"), fontWeight = FontWeight.SemiBold)
                    Text(
                        tr("Chọn chế độ hiển thị của AUTU-LIO", "Choose how AUTU-LIO looks"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                options.forEach { (mode, label) ->
                    FilterChip(
                        selected = selectedMode == mode,
                        onClick = { onModeChange(mode) },
                        label = { Text(label, fontSize = 12.sp) }
                    )
                }
            }
            Text(
                when (selectedMode) {
                    UiThemeMode.DARK -> tr("Nền đen thuần, giảm ánh sáng nền.", "Pure black background with less screen glare.")
                    UiThemeMode.LIGHT -> tr("Nền sáng dịu, tránh trắng gắt.", "Soft light background without harsh white.")
                    UiThemeMode.SYSTEM -> tr("Tự đổi theo chế độ sáng/tối của Android.", "Automatically follows Android light/dark mode.")
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                lineHeight = 15.sp
            )
        }
    }
}

@Composable
private fun SettingsScreen(
    modifier: Modifier,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
        BrandHeader(admin = false)
        Heading(tr("Cài đặt", "Settings"), tr("Tùy chỉnh AUTU-LIO", "Customize AUTU-LIO"))
        ThemeModeSelector()
        SettingRow(Icons.Outlined.Info, tr("Thông tin ứng dụng", "App information"), "AUTU-LIO v${BuildConfig.VERSION_NAME}")
        SettingRow(Icons.Outlined.Lock, tr("Quyền riêng tư", "Privacy"), tr("Vai trò và giấy phép được máy chủ xác minh.", "Roles and licenses are verified by the server."))
        SettingRow(
            Icons.Outlined.Chat,
            tr("Liên hệ nhà phát triển", "Contact developer"),
            tr("Mở Zalo để liên hệ và nhận hỗ trợ.", "Open Zalo to contact the developer and get support.")
        ) {
            val contactIntent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://zaloapp.com/qr/p/1gl8y41uqztdt?src=qr")
            )
            context.startActivity(contactIntent)
        }
        DatabaseBackupControls()
        ScriptFileControls()
        SettingRow(Icons.Outlined.Logout, tr("Đăng xuất", "Sign out"), tr("Kết thúc phiên đăng nhập trên thiết bị này.", "End the session on this device."), onLogout)
    }
}

@Composable
private fun ScriptFileControls() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val transfer = remember(context) {
        ScriptSafFileTransfer(ScriptFileStore(File(context.filesDir, "autu-script-data")))
    }
    var targetPath by rememberSaveable { mutableStateOf("") }
    var working by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }

    val importDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) scope.launch {
            working = true
            message = ""
            try {
                val imported = withContext(Dispatchers.IO) {
                    val displayName = querySafDisplayName(context, uri)
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        transfer.importFrom(input, displayName)
                    } ?: throw java.io.IOException("Không mở được tệp đã chọn.")
                }
                targetPath = imported.relativePath
                message = tr(
                    "Đã nhập ${imported.bytes} byte vào ${imported.relativePath}.",
                    "Imported ${imported.bytes} bytes to ${imported.relativePath}."
                )
            } catch (error: Exception) {
                message = localizedErrorMessage(error, tr("Không nhập được tệp.", "Could not import the file."))
            } finally {
                working = false
            }
        }
    }

    val exportDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        if (uri != null) scope.launch {
            val path = targetPath.trim()
            working = true
            message = ""
            try {
                val bytes = withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri, "wt")?.use { output ->
                        transfer.exportTo(path, output)
                    } ?: throw java.io.IOException("Không mở được tệp đích.")
                }
                message = tr(
                    "Đã xuất $bytes byte từ $path.",
                    "Exported $bytes bytes from $path."
                )
            } catch (error: Exception) {
                message = localizedErrorMessage(error, tr("Không xuất được tệp.", "Could not export the file."))
            } finally {
                working = false
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(tr("Tệp AUTU Script", "AUTU Script files"), fontWeight = FontWeight.SemiBold)
        Text(
            tr(
                "Nhập tệp từ nơi bạn chọn vào vùng riêng của AUTU, hoặc xuất một tệp trong vùng này ra nơi khác. Giới hạn 1 MiB mỗi tệp.",
                "Import a file from a location you choose into AUTU's private storage, or export a stored file. Each file is limited to 1 MiB."
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            lineHeight = 17.sp
        )
        OutlinedTextField(
            value = targetPath,
            onValueChange = { targetPath = it.take(256) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !working,
            label = { Text(tr("Đường dẫn tệp trong AUTU", "AUTU file path")) },
            placeholder = { Text("nhap/baocao.csv") }
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { importDocument.launch(arrayOf("*/*")) },
                enabled = !working,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Outlined.AttachFile, contentDescription = null)
                Spacer(Modifier.width(5.dp))
                Text(tr("Nhập tệp", "Import"))
            }
            OutlinedButton(
                onClick = {
                    val path = targetPath.trim()
                    if (path.isBlank() || path.endsWith('/')) {
                        message = tr("Nhập đường dẫn tệp cần xuất.", "Enter the file path to export.")
                    } else {
                        exportDocument.launch(path.substringAfterLast('/'))
                    }
                },
                enabled = !working,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Outlined.Share, contentDescription = null)
                Spacer(Modifier.width(5.dp))
                Text(tr("Xuất tệp", "Export"))
            }
        }
        if (working) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (message.isNotBlank()) {
            Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
        }
    }
}

private fun querySafDisplayName(context: Context, uri: Uri): String {
    val queriedName = runCatching {
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (column >= 0 && cursor.moveToFirst()) cursor.getString(column) else null
        }
    }.getOrNull()
    return queriedName?.takeIf { it.isNotBlank() }
        ?: uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
        ?: "tep_nhap.bin"
}

@Composable
private fun DatabaseBackupControls() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var working by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var restoreUri by remember { mutableStateOf<Uri?>(null) }

    val createBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) scope.launch {
            working = true
            message = ""
            try {
                val metadata = withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri, "wt")?.use { output ->
                        val service = AutoClickAccessibilityService.current()
                        if (service != null) {
                            service.exportDatabaseBackup(output)
                        } else {
                            ScriptDatabaseStore(context).use { store ->
                                store.exportBackupTo(output)
                            }
                        }
                    } ?: throw java.io.IOException("Không mở được tệp đích để sao lưu.")
                }
                message = tr(
                    "Sao lưu xong · ${metadata.databaseBytes} byte · schema ${metadata.schemaVersion} · SHA-256 ${metadata.sha256.take(12)}…",
                    "Backup complete · ${metadata.databaseBytes} bytes · schema ${metadata.schemaVersion} · SHA-256 ${metadata.sha256.take(12)}…"
                )
            } catch (error: Exception) {
                message = localizedErrorMessage(error, tr("Không sao lưu được cơ sở dữ liệu.", "Database backup failed."))
            } finally {
                working = false
            }
        }
    }

    val openBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> restoreUri = uri }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(tr("Sao lưu dữ liệu SQLite", "SQLite database backup"), fontWeight = FontWeight.SemiBold)
        Text(
            tr(
                "Lưu bản sao lưu vào nơi bạn chọn. Khi khôi phục, dữ liệu SQLite hiện tại trên thiết bị sẽ được thay thế.",
                "Save a backup to a location you choose. Restoring replaces the current SQLite database on this device."
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            lineHeight = 17.sp
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { createBackup.launch("AUTU-LIO-SQLite-${System.currentTimeMillis()}.autubak") },
                enabled = !working,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Outlined.Save, contentDescription = null)
                Spacer(Modifier.width(5.dp))
                Text(tr("Sao lưu", "Back up"))
            }
            OutlinedButton(
                onClick = { openBackup.launch(arrayOf("*/*")) },
                enabled = !working,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Outlined.Restore, contentDescription = null)
                Spacer(Modifier.width(5.dp))
                Text(tr("Khôi phục", "Restore"))
            }
        }
        if (working) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (message.isNotBlank()) {
            Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
        }
    }

    val selectedRestore = restoreUri
    if (selectedRestore != null) {
        AlertDialog(
            onDismissRequest = { if (!working) restoreUri = null },
            title = { Text(tr("Xác nhận khôi phục", "Confirm restore")) },
            text = {
                Text(
                    tr(
                        "Khôi phục sẽ thay thế cơ sở dữ liệu AUTU-LIO hiện tại bằng bản sao lưu đã chọn. Tệp sẽ được kiểm tra định dạng, tính toàn vẹn và SHA-256 trước khi thay thế. Tiếp tục?",
                        "Restore replaces the current AUTU-LIO database with the selected backup. Format, integrity and SHA-256 are checked before replacement. Continue?"
                    )
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !working,
                    onClick = {
                        restoreUri = null
                        scope.launch {
                            working = true
                            message = ""
                            try {
                                val metadata = withContext(Dispatchers.IO) {
                                    context.contentResolver.openInputStream(selectedRestore)?.use { input ->
                                        val service = AutoClickAccessibilityService.current()
                                        if (service != null) {
                                            service.restoreDatabaseBackup(input)
                                        } else {
                                            ScriptDatabaseStore(context).use { store ->
                                                store.restoreBackupFrom(input)
                                            }
                                        }
                                    } ?: throw java.io.IOException("Không mở được tệp sao lưu.")
                                }
                                message = tr(
                                    "Khôi phục thành công · schema ${metadata.schemaVersion} · ${metadata.databaseBytes} byte.",
                                    "Restore complete · schema ${metadata.schemaVersion} · ${metadata.databaseBytes} bytes."
                                )
                            } catch (error: Exception) {
                                message = localizedErrorMessage(error, tr("Không khôi phục được cơ sở dữ liệu.", "Database restore failed."))
                            } finally {
                                working = false
                            }
                        }
                    }
                ) { Text(tr("Khôi phục", "Restore")) }
            },
            dismissButton = {
                TextButton(onClick = { restoreUri = null }, enabled = !working) {
                    Text(tr("Hủy", "Cancel"))
                }
            }
        )
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
                    Text(if (admin) "BẢNG ĐIỀU HÀNH" else "TỰ ĐỘNG HÓA", fontSize = 10.sp, letterSpacing = 1.1.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        private const val EXTRA_OPEN_PACKAGE_PUBLISH = "com.autulio.app.extra.OPEN_PACKAGE_PUBLISH"
        private const val EXTRA_PUBLISH_DRAFT_NAME = "com.autulio.app.extra.PUBLISH_DRAFT_NAME"

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
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
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

            PreferredAppTheme(this@ScriptEditorActivity) {
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
                        onPublishDraft = {
                            store.save(initialNames, initial.contents + (scriptName to code))
                            setResult(
                                Activity.RESULT_OK,
                                Intent()
                                    .putExtra(EXTRA_OPEN_PACKAGE_PUBLISH, true)
                                    .putExtra(EXTRA_PUBLISH_DRAFT_NAME, scriptName)
                            )
                            finish()
                        },
                        allowPublish = namespace == "admin",
                        allowTest = allowTest,
                        allowFloatingEditor = namespace == "admin" && allowTest,
                        onOpenFloatingEditor = {
                            if (namespace != "admin" || !allowTest) {
                                scope.launch {
                                    host.showSnackbar(
                                        tr("Cửa sổ nổi chỉ dành cho tài khoản chủ sở hữu.", "The floating editor is available to the owner account only.")
                                    )
                                }
                            } else {
                                store.save(initialNames, initial.contents + (scriptName to code))
                                setResult(Activity.RESULT_OK)
                                val service = AutoClickAccessibilityService.current()
                                if (service == null) {
                                    scope.launch {
                                        host.showSnackbar(
                                            tr(
                                                "Hãy bật AUTU-LIO trong Cài đặt > Trợ năng, sau đó quay lại và mở cửa sổ nổi.",
                                                "Enable AUTU-LIO in Settings > Accessibility, then return and open the floating editor."
                                            )
                                        )
                                    }
                                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                                } else if (service.openFloatingEditor(scriptName, code)) {
                                    finish()
                                } else {
                                    scope.launch {
                                        host.showSnackbar(
                                            tr(
                                                "Không mở được cửa sổ nổi. Hãy kiểm tra quyền Trợ năng hoặc dừng kịch bản đang chạy.",
                                                "Could not open the floating editor. Check Accessibility access or stop the running script."
                                            )
                                        )
                                    }
                                }
                            }
                        },
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
                                } else if (service.prepareProgram(compilation.program!!, scriptName)) {
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
