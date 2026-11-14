package com.autulio.app

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.text.selection.SelectionContainer
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
import com.autulio.app.access.AccessRole
import com.autulio.app.auth.AutuLioApi
import com.autulio.app.auth.Collaborator
import com.autulio.app.auth.CollaboratorInvitation
import com.autulio.app.auth.GoogleAuthRepository
import com.autulio.app.auth.SecureSessionStore
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.util.Locale

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
        AuthenticatedApp()
    } else if (previewRole == null) {
        RolePreviewChooser(
            onOpenAdmin = { previewRole = "admin" },
            onOpenCustomer = { previewRole = "customer" },
            onOpenLiveLogin = { previewRole = "live" }
        )
    } else {
        BackHandler { previewRole = null }
        when (previewRole) {
            "admin" -> AdminApp()
            "customer" -> CustomerApp()
            else -> AuthenticatedApp()
        }
    }
}

@Composable
private fun AuthenticatedApp() {
    val context = LocalContext.current
    val activity = context.findActivity()
    val scope = rememberCoroutineScope()
    var roleName by rememberSaveable { mutableStateOf<String?>(null) }
    var accountId by rememberSaveable { mutableStateOf<String?>(null) }
    var checkingSession by rememberSaveable { mutableStateOf(true) }
    var signingIn by rememberSaveable { mutableStateOf(false) }
    var loginError by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        checkingSession = true
        val token = SecureSessionStore.read(context)
        if (!token.isNullOrBlank()) {
            val user = runCatching { AutuLioApi.currentUser(token) }.getOrNull()
            if (user != null) {
                roleName = user.role
                accountId = user.id
            } else {
                SecureSessionStore.clear(context)
            }
        }
        checkingSession = false
    }

    val logout: () -> Unit = {
        scope.launch {
            val token = SecureSessionStore.read(context)
            if (!token.isNullOrBlank()) AutuLioApi.logout(token)
            SecureSessionStore.clear(context)
            roleName = null
            accountId = null
            loginError = null
        }
    }

    when {
        checkingSession -> AuthLoadingScreen()
        roleName == null -> AuthLoginScreen(
            busy = signingIn,
            errorMessage = loginError,
            onSignIn = { invitationCode ->
                scope.launch {
                    if (activity == null) {
                        loginError = tr("Không xác định được màn hình đăng nhập.", "Could not access the sign-in activity.")
                    } else {
                        signingIn = true
                        loginError = null
                        try {
                            val session = GoogleAuthRepository(activity).signIn(invitationCode)
                            SecureSessionStore.save(context, session.sessionToken)
                            roleName = session.user.role
                            accountId = session.user.id
                        } catch (error: Exception) {
                            loginError = error.message ?: tr("Đăng nhập thất bại. Vui lòng thử lại.", "Sign-in failed. Please try again.")
                        } finally {
                            signingIn = false
                        }
                    }
                }
            }
        )
        roleName == AccessRole.CUSTOMER.name -> CustomerApp(
            initiallySignedIn = true,
            onLogout = logout
        )
        else -> {
            val role = runCatching { AccessRole.valueOf(roleName.orEmpty()) }.getOrNull()
            if (role == null) {
                AuthLoginScreen(
                    busy = false,
                    errorMessage = tr("Vai trò tài khoản không hợp lệ. Vui lòng đăng nhập lại.", "Invalid account role. Please sign in again."),
                    onSignIn = { invitationCode ->
                        scope.launch {
                            if (activity != null) {
                                signingIn = true
                                loginError = null
                                try {
                                    val session = GoogleAuthRepository(activity).signIn(invitationCode)
                                    SecureSessionStore.save(context, session.sessionToken)
                                    roleName = session.user.role
                                    accountId = session.user.id
                                } catch (error: Exception) {
                                    loginError = error.message ?: tr("Đăng nhập thất bại.", "Sign-in failed.")
                                } finally {
                                    signingIn = false
                                }
                            }
                        }
                    }
                )
            } else {
                AdminApp(role = role, accountId = accountId.orEmpty(), onLogout = logout)
            }
        }
    }
}

@Composable
private fun AuthLoginScreen(
    busy: Boolean,
    errorMessage: String?,
    onSignIn: (String?) -> Unit
) {
    var invitationCode by rememberSaveable { mutableStateOf("") }
    AppTheme(dark = true) {
        Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(15.dp)
            ) {
                Spacer(Modifier.height(20.dp))
                BrandHeader(admin = false)
                Spacer(Modifier.height(12.dp))
                Icon(Icons.Outlined.Code, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(52.dp))
                Heading(
                    tr("Đăng nhập AUTU-LIO", "Sign in to AUTU-LIO"),
                    tr("Dùng Google để xác minh tài khoản và quyền sử dụng.", "Use Google to verify your account and access role.")
                )
                CardBox {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            tr("Nếu bạn được mời làm cộng tác viên, nhập mã mời ở đây. Người dùng thông thường có thể để trống.",
                                "If you were invited as a collaborator, enter the invitation code here. Regular users can leave it blank."),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            lineHeight = 18.sp
                        )
                        OutlinedTextField(
                            value = invitationCode,
                            onValueChange = { invitationCode = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(tr("Mã mời (không bắt buộc)", "Invitation code (optional)")) },
                            singleLine = true
                        )
                        Button(
                            onClick = { onSignIn(invitationCode.trim().ifBlank { null }) },
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (busy) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                            } else {
                                Icon(Icons.Outlined.Person, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(tr("Đăng nhập bằng Google", "Continue with Google"))
                        }
                        if (!errorMessage.isNullOrBlank()) {
                            Text(errorMessage, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                        }
                    }
                }
                Text(
                    tr("AUTU-LIO không dùng Firebase. Quyền tài khoản được xác minh trên máy chủ.",
                        "AUTU-LIO does not use Firebase. Account roles are verified by the server."),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun AuthLoadingScreen() {
    AppTheme(dark = true) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator()
                Text(tr("Đang kiểm tra phiên đăng nhập...", "Checking your sign-in session..."), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun RolePreviewChooser(
    onOpenAdmin: () -> Unit,
    onOpenCustomer: () -> Unit,
    onOpenLiveLogin: () -> Unit
) {
    AppTheme(dark = true) {
        Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
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
                        tr("Các lựa chọn xem thử chỉ giúp kiểm tra bố cục. Chúng không đăng nhập, không cấp quyền Admin và không thay thế xác minh quyền trên máy chủ.",
                            "Preview choices only test layouts. They do not sign in, grant Admin access, or replace server-side authorization."),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                }
                Button(onClick = onOpenAdmin, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Code, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(tr("Xem thử giao diện Admin", "Preview Admin interface"))
                }
                OutlinedButton(onClick = onOpenCustomer, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Person, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(tr("Xem thử giao diện người dùng", "Preview customer interface"))
                }
                OutlinedButton(onClick = onOpenLiveLogin, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Login, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(tr("Thử đăng nhập Google thật", "Test real Google sign-in"))
                }
                Text(
                    tr("Đăng nhập thật chỉ hoạt động khi Google Web Client ID và API Cloudflare đã được cấu hình.",
                        "Real sign-in works only after the Google Web Client ID and Cloudflare API are configured."),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
                Text(
                    tr("Bản phát hành sẽ chỉ mở quyền Admin sau khi Google đăng nhập và máy chủ xác minh vai trò.",
                        "The release build will grant Admin access only after Google sign-in and server-side role verification."),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun tr(vi: String, en: String) =
    if (Locale.getDefault().language == "vi") vi else en

@Composable
private fun AppTheme(dark: Boolean, content: @Composable () -> Unit) {
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

private val designTab = Tab("design", "Thiết kế", "Design", Icons.Outlined.Palette)
private val reviewTab = Tab("review", "Duyệt", "Review", Icons.Outlined.VerifiedUser)
private val accountTab = Tab("account", "Tài khoản", "Account", Icons.Outlined.AccountCircle)

private val customerTabs = listOf(
    Tab("home", "Trang chủ", "Home", Icons.Outlined.Home),
    Tab("scripts", "Kịch bản", "Scripts", Icons.Outlined.ListAlt),
    Tab("device", "Thiết bị", "Device", Icons.Outlined.Smartphone),
    Tab("settings", "Cài đặt", "Settings", Icons.Outlined.Settings)
)

@Composable
private fun AdminApp(
    role: AccessRole = AccessRole.OWNER,
    accountId: String = "debug-preview",
    onLogout: () -> Unit = {}
) {
    val context = LocalContext.current
    val preferences = remember(context) {
        context.getSharedPreferences("autulio_admin_drafts_" + accountId, Context.MODE_PRIVATE)
    }
    var dark by rememberSaveable { mutableStateOf(true) }
    var page by rememberSaveable { mutableStateOf("overview") }
    var editor by rememberSaveable { mutableStateOf<String?>(null) }
    var createDialog by remember { mutableStateOf(false) }
    val scripts = remember(preferences) {
        mutableStateListOf<String>().apply {
            val saved = runCatching {
                JSONArray(preferences.getString("scripts", "[]") ?: "[]")
            }.getOrNull()
            if (saved != null) {
                for (index in 0 until saved.length()) {
                    val name = saved.optString(index).trim()
                    if (name.isNotEmpty() && none { it.equals(name, ignoreCase = true) }) add(name)
                }
            }
        }
    }
    val draftContents = remember(preferences) {
        mutableStateMapOf<String, String>().apply {
            preferences.all.forEach { (key, value) ->
                if (key.startsWith("draft:") && value is String) {
                    put(key.removePrefix("draft:"), value)
                }
            }
        }
    }
    val host = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val visibleTabs = when (role) {
        AccessRole.OWNER -> adminTabs
        AccessRole.DEVELOPER -> listOf(adminTabs[0], adminTabs[1], accountTab)
        AccessRole.DESIGNER -> listOf(adminTabs[0], designTab, accountTab)
        AccessRole.REVIEWER -> listOf(adminTabs[0], reviewTab, accountTab)
        AccessRole.CUSTOMER -> listOf(accountTab)
    }

    AppTheme(dark) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(host) },
            bottomBar = {
                if (editor == null) NavigationBar {
                    visibleTabs.forEach { tab ->
                        NavigationBarItem(
                            selected = page == tab.key || (page !in visibleTabs.map { it.key } && tab.key == if (role == AccessRole.OWNER) "more" else "account"),
                            onClick = { page = tab.key },
                            icon = { Icon(tab.icon, contentDescription = tr(tab.vi, tab.en)) },
                            label = { Text(tr(tab.vi, tab.en), fontSize = 10.sp) }
                        )
                    }
                }
            }
        ) { padding ->
            if (editor != null) {
                val editingName = editor.orEmpty()
                EditorScreen(
                    name = editingName,
                    code = draftContents[editingName].orEmpty(),
                    readOnly = role == AccessRole.REVIEWER,
                    onCodeChange = {
                        draftContents[editingName] = it
                        preferences.edit().putString("draft:$editingName", it).apply()
                    },
                    modifier = Modifier.padding(padding),
                    onBack = { editor = null },
                    onMessage = { scope.launch { host.showSnackbar(it) } },
                    onSaveDraft = {
                        preferences.edit()
                            .putString("scripts", JSONArray(scripts).toString())
                            .putString("draft:$editingName", draftContents[editingName].orEmpty())
                            .apply()
                        scope.launch {
                            host.showSnackbar(tr("Đã lưu bản nháp trên thiết bị này. Chưa đồng bộ máy chủ.", "Draft saved on this device. It has not been synced to a server."))
                        }
                    }
                )
            } else when (page) {
                "overview" -> OverviewScreen(
                    Modifier.padding(padding),
                    onNavigate = { page = it },
                    onCreate = { if (role == AccessRole.OWNER || role == AccessRole.DEVELOPER) createDialog = true },
                    canCreate = role == AccessRole.OWNER || role == AccessRole.DEVELOPER,
                    canManageCustomers = role == AccessRole.OWNER
                )
                "scripts" -> ScriptsScreen(
                    Modifier.padding(padding),
                    scripts = scripts,
                    onCreate = { createDialog = true },
                    onOpen = { editor = it },
                    canCreate = role == AccessRole.OWNER || role == AccessRole.DEVELOPER
                )
                "customers" -> AdminModulePage(
                    Modifier.padding(padding),
                    title = tr("Khách hàng", "Customers"),
                    subtitle = tr("Quản lý tài khoản và trạng thái khách hàng", "Manage customer accounts and status"),
                    icon = Icons.Outlined.Group,
                    details = listOf(
                        tr("Hồ sơ và email tài khoản", "Customer profiles and account email"),
                        tr("Số thiết bị đã liên kết", "Number of linked devices"),
                        tr("Loại giấy phép và ngày hết hạn", "License type and expiry date"),
                        tr("Lần hoạt động gần nhất", "Last active time")
                    ),
                    onBack = { page = "overview" }
                )
                "devices" -> AdminModulePage(
                    Modifier.padding(padding),
                    title = tr("Thiết bị", "Devices"),
                    subtitle = tr("Theo dõi thiết bị đã đăng ký", "Review registered devices"),
                    icon = Icons.Outlined.Smartphone,
                    details = listOf(
                        tr("Hãng và model thiết bị", "Device manufacturer and model"),
                        tr("Phiên bản Android và AUTU-LIO", "Android and AUTU-LIO versions"),
                        tr("Tài khoản sở hữu thiết bị", "Device owner account"),
                        tr("Ngày đăng ký và hoạt động gần nhất", "Registration and last active time")
                    ),
                    onBack = { page = "overview" }
                )
                "design" -> AdminModulePage(
                    Modifier.padding(padding),
                    title = tr("Thiết kế giao diện", "Interface design"),
                    subtitle = tr("Khu vực thiết kế dành cho cộng tác viên được phân quyền", "Design workspace for authorized collaborators"),
                    icon = Icons.Outlined.Palette,
                    details = listOf(
                        tr("Thiết kế bố cục màn hình Auto Click", "Design AutoClick screen layouts"),
                        tr("Điều chỉnh màu sắc, biểu tượng và kiểu chữ", "Adjust colors, icons and typography"),
                        tr("Xem trước thay đổi trước khi gửi duyệt", "Preview changes before submitting for review"),
                        tr("Không có quyền sửa mã kịch bản hoặc phát hành", "Cannot edit scripts or publish releases")
                    ),
                    onBack = { page = "overview" }
                )
                "review" -> AdminModulePage(
                    Modifier.padding(padding),
                    title = tr("Kiểm duyệt", "Review"),
                    subtitle = tr("Kiểm tra bản thay đổi trước khi chủ sở hữu phát hành", "Review changes before the owner publishes"),
                    icon = Icons.Outlined.VerifiedUser,
                    details = listOf(
                        tr("Kiểm tra kịch bản và giao diện gửi duyệt", "Check submitted scripts and interface changes"),
                        tr("Ghi nhận kết quả kiểm thử", "Record test results"),
                        tr("Duyệt hoặc từ chối yêu cầu phát hành", "Approve or reject release requests"),
                        tr("Không tự phát hành hoặc thay đổi quyền tài khoản", "Cannot publish or change account permissions")
                    ),
                    onBack = { page = "overview" }
                )
                "account" -> AdminAccountPage(role = role, onLogout = onLogout, modifier = Modifier.padding(padding))
                else -> MoreScreen(
                    Modifier.padding(padding),
                    page,
                    onSelect = { page = it },
                    onTheme = { dark = !dark },
                    onMessage = { scope.launch { host.showSnackbar(it) } },
                    sessionToken = SecureSessionStore.read(context).orEmpty(),
                    onLogout = onLogout
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
                    preferences.edit()
                        .putString("scripts", JSONArray(scripts).toString())
                        .putString("draft:$name", "")
                        .apply()
                    editor = name
                    page = "scripts"
                    createDialog = false
                }
            }
        )
    }
}

@Composable
private fun CustomerApp(
    initiallySignedIn: Boolean = false,
    onLogout: () -> Unit = {}
) {
    var dark by rememberSaveable { mutableStateOf(true) }
    var page by rememberSaveable { mutableStateOf("home") }
    var previewEntered by rememberSaveable { mutableStateOf(initiallySignedIn) }
    var scriptDetail by rememberSaveable { mutableStateOf(false) }
    val host = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    AppTheme(dark) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(host) },
            bottomBar = {
                if (previewEntered) NavigationBar {
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
            if (!previewEntered) {
                CustomerLoginPage(
                    Modifier.padding(padding),
                    onGoogle = {
                        scope.launch {
                            host.showSnackbar(tr("Google OAuth chưa được tích hợp trong bản prototype.", "Google OAuth is not integrated in this prototype."))
                        }
                    },
                    onPreview = { previewEntered = true }
                )
            } else when (page) {
                "home" -> CustomerHome(Modifier.padding(padding), onScripts = { page = "scripts" }, authenticated = initiallySignedIn)
                "scripts" -> if (scriptDetail) {
                    CustomerScriptDetail(
                        Modifier.padding(padding),
                        onBack = { scriptDetail = false },
                        onPreviewAction = {
                            scope.launch {
                                host.showSnackbar(tr("Đây chỉ là bản xem trước. AUTU-LIO chưa thực hiện thao tác trên màn hình.", "This is preview only. AUTU-LIO did not perform any screen actions."))
                            }
                        }
                    )
                } else {
                    CustomerScriptsScreen(
                        Modifier.padding(padding),
                        onOpen = { scriptDetail = true }
                    )
                }
                "device" -> CustomerDeviceLicensePage(Modifier.padding(padding), signedIn = initiallySignedIn)
                else -> SettingsScreen(Modifier.padding(padding), onTheme = { dark = !dark }, onLogout = onLogout)
            }
        }
    }
}

@Composable
private fun CustomerLoginPage(
    modifier: Modifier,
    onGoogle: () -> Unit,
    onPreview: () -> Unit
) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(Modifier.height(24.dp))
        BrandHeader(admin = false)
        Spacer(Modifier.height(24.dp))
        Icon(
            Icons.Outlined.Code,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(54.dp)
        )
        Heading(
            tr("Chào mừng đến với AUTU-LIO", "Welcome to AUTU-LIO"),
            tr("Đăng nhập để sử dụng các kịch bản được cấp phép.", "Sign in to use your licensed scripts.")
        )
        CardBox {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onGoogle, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Person, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(tr("Đăng nhập bằng Google", "Continue with Google"))
                }
                OutlinedButton(onClick = onPreview, modifier = Modifier.fillMaxWidth()) {
                    Text(tr("Xem trước giao diện", "Preview the interface"))
                }
                Text(
                    tr("Đăng nhập Google chưa hoạt động trong bản thử nghiệm này. Bạn có thể vào chế độ xem trước để kiểm tra giao diện.",
                        "Google sign-in is not active in this prototype. Use preview mode to explore the interface."),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
        Text(
            tr("Bằng cách tiếp tục, bạn sẽ xem được Điều khoản và Chính sách quyền riêng tư khi tính năng tài khoản được tích hợp.",
                "Terms and Privacy information will be shown when account features are integrated."),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun OverviewScreen(
    modifier: Modifier,
    onNavigate: (String) -> Unit,
    onCreate: () -> Unit,
    canCreate: Boolean = true,
    canManageCustomers: Boolean = true
) {
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
        if (canCreate || canManageCustomers) {
            Text(tr("Truy cập nhanh", "Quick actions"), fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
        }
        if (canCreate) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionTile(tr("Tạo kịch bản", "Create script"), Icons.Outlined.Add, Modifier.weight(1f), onCreate)
                ActionTile(tr("Kịch bản", "Scripts"), Icons.Outlined.ListAlt, Modifier.weight(1f)) { onNavigate("scripts") }
            }
        }
        if (canManageCustomers) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionTile(tr("Khách hàng", "Customers"), Icons.Outlined.Group, Modifier.weight(1f)) { onNavigate("customers") }
                ActionTile(tr("Thiết bị", "Devices"), Icons.Outlined.Smartphone, Modifier.weight(1f)) { onNavigate("devices") }
            }
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
    canCreate: Boolean = true
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
        Heading(tr("Kịch bản", "Scripts"), tr("Tạo, quản lý và phát hành kịch bản", "Create, manage and publish scripts"))
        if (canCreate) {
            Button(onClick = onCreate, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Add, contentDescription = null)
                Spacer(Modifier.width(7.dp))
                Text(tr("Tạo kịch bản mới", "Create new script"))
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
    readOnly: Boolean = false,
    onCodeChange: (String) -> Unit,
    modifier: Modifier,
    onBack: () -> Unit,
    onMessage: (String) -> Unit,
    onSaveDraft: () -> Unit
) {
    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(onClick = onBack) { Text(tr("← Quay lại", "← Back")) }
        Heading(name, tr("Bản nháp · Lưu trên thiết bị", "Draft · Saved on this device"))
        Text(
            if (readOnly) tr("Chế độ kiểm duyệt: chỉ được xem nội dung, không thể sửa mã.", "Review mode: content is read-only.")
            else tr("Ngôn ngữ riêng AUTU-LIO sẽ được định nghĩa trước khi thực thi. Nội dung dưới đây chưa chạy được.", "The AUTU-LIO language will be specified before execution. Text below is not executable."),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp
        )
        OutlinedTextField(
            value = code,
            onValueChange = onCodeChange,
            enabled = !readOnly,
            modifier = Modifier.fillMaxWidth().weight(1f),
            minLines = 10,
            placeholder = { Text(tr("Nhập nội dung bản nháp...", "Enter draft content...")) }
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onMessage(tr("Bộ kiểm tra cú pháp chưa được tích hợp.", "Syntax checker is not integrated.")) }, modifier = Modifier.weight(1f)) { Text(tr("Kiểm tra", "Check")) }
            Button(onClick = { onMessage(tr("Runtime chưa sẵn sàng để chạy thử.", "Runtime is not ready for testing.")) }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Outlined.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(5.dp))
                Text(tr("Chạy thử", "Test"))
            }
        }
        if (!readOnly) {
            OutlinedButton(onClick = onSaveDraft, modifier = Modifier.fillMaxWidth()) {
                Text(tr("Lưu bản nháp trên thiết bị", "Save draft on this device"))
            }
        }
    }
}

@Composable
private fun MoreScreen(
    modifier: Modifier,
    page: String,
    onSelect: (String) -> Unit,
    onTheme: () -> Unit,
    onMessage: (String) -> Unit,
    sessionToken: String,
    onLogout: () -> Unit
) {
    val items = listOf(
        Triple("team", tr("Cộng tác viên", "Collaborators"), Icons.Outlined.Group),
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
                    "licenses" -> tr("Cấp, gia hạn và thu hồi quyền sử dụng", "Issue, renew and revoke access")
                    "library" -> tr("Quản lý hàm và thành phần dùng chung", "Manage shared functions and components")
                    "release" -> tr("Quản lý phiên bản và phân phối", "Manage versions and distribution")
                    "logs" -> tr("Theo dõi sự kiện và lỗi hệ thống", "Review system events and errors")
                    else -> tr("Tùy chỉnh ứng dụng quản trị", "Customize the admin application")
                }
            )
            when (page) {
                "team" -> CollaboratorsManagementContent(sessionToken)
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
                    SettingRow(Icons.Outlined.Info, tr("Thông tin ứng dụng", "App information"), "AUTU-LIO Admin Studio · 0.1.0")
                    SettingRow(Icons.Outlined.Lock, tr("Bảo mật", "Security"), tr("Quyền quản trị được xác minh trên máy chủ.", "Admin permissions are verified by the server."))
                    SettingRow(Icons.Outlined.ExitToApp, tr("Đăng xuất", "Sign out"), tr("Kết thúc phiên đăng nhập AUTU-LIO", "End your AUTU-LIO session"), onLogout)
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

private fun roleTitle(role: String): String = when (role) {
    "OWNER" -> tr("Chủ sở hữu", "Owner")
    "DEVELOPER" -> tr("Nhà phát triển", "Developer")
    "DESIGNER" -> tr("Nhà thiết kế", "Designer")
    "REVIEWER" -> tr("Người kiểm duyệt", "Reviewer")
    "CUSTOMER" -> tr("Người dùng", "Customer")
    else -> role
}

@Composable
private fun AdminAccountPage(
    role: AccessRole,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        BrandHeader(admin = true)
        Heading(tr("Tài khoản cộng tác viên", "Collaborator account"), tr("Vai trò được máy chủ xác minh", "Role verified by the server"))
        CardBox {
            Text(tr("Vai trò hiện tại", "Current role"), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            Text(roleTitle(role.name), fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Text(
                when (role) {
                    AccessRole.DEVELOPER -> tr("Bạn có thể tạo, chỉnh sửa và kiểm thử kịch bản được giao.", "You can create, edit and test assigned scripts.")
                    AccessRole.DESIGNER -> tr("Bạn có thể thiết kế giao diện và xem trước thay đổi.", "You can design the interface and preview changes.")
                    AccessRole.REVIEWER -> tr("Bạn có thể kiểm tra nội dung và duyệt yêu cầu thay đổi.", "You can inspect content and review change requests.")
                    else -> tr("Quyền truy cập do chủ sở hữu quản lý.", "Access is managed by the owner.")
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
        }
        OutlinedButton(onClick = onLogout, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.ExitToApp, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(tr("Đăng xuất", "Sign out"))
        }
    }
}

@Composable
private fun CollaboratorsManagementContent(sessionToken: String) {
    val scope = rememberCoroutineScope()
    var email by rememberSaveable { mutableStateOf("") }
    var selectedRole by rememberSaveable { mutableStateOf("DEVELOPER") }
    var loading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var invitation by remember { mutableStateOf<CollaboratorInvitation?>(null) }
    var collaborators by remember { mutableStateOf<List<Collaborator>>(emptyList()) }
    var editTarget by remember { mutableStateOf<Collaborator?>(null) }
    var revokeTarget by remember { mutableStateOf<Collaborator?>(null) }
    var roleToAssign by rememberSaveable { mutableStateOf("DEVELOPER") }

    LaunchedEffect(sessionToken) {
        if (sessionToken.isNotBlank()) {
            runCatching { AutuLioApi.listCollaborators(sessionToken) }
                .onSuccess { collaborators = it }
                .onFailure { errorMessage = it.message ?: tr("Không tải được cộng tác viên.", "Could not load collaborators.") }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Heading(
            tr("Quản lý cộng tác viên", "Collaborator management"),
            tr("Mời người khác tham gia thiết kế, lập trình hoặc kiểm duyệt.", "Invite others to design, develop or review.")
        )
        CardBox {
            Text(tr("Mời cộng tác viên", "Invite a collaborator"), fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Gmail") },
                placeholder = { Text("name@gmail.com") }
            )
            Text(tr("Chọn quyền", "Choose a role"), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                listOf(
                    "DEVELOPER" to tr("Lập trình", "Developer"),
                    "DESIGNER" to tr("Thiết kế", "Designer"),
                    "REVIEWER" to tr("Kiểm duyệt", "Reviewer")
                ).forEach { option ->
                    FilterChip(
                        selected = selectedRole == option.first,
                        onClick = { selectedRole = option.first },
                        label = { Text(option.second) }
                    )
                }
            }
            Button(
                onClick = {
                    scope.launch {
                        loading = true
                        errorMessage = null
                        try {
                            invitation = AutuLioApi.createInvitation(sessionToken, email.trim(), selectedRole)
                            collaborators = AutuLioApi.listCollaborators(sessionToken)
                        } catch (error: Exception) {
                            errorMessage = error.message ?: tr("Không tạo được lời mời.", "Could not create the invitation.")
                        } finally {
                            loading = false
                        }
                    }
                },
                enabled = !loading && email.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (loading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(tr("Tạo lời mời", "Create invitation"))
            }
            Text(
                tr("Mã mời có hiệu lực 7 ngày và chỉ hiển thị một lần. Người được mời phải đăng nhập Google bằng đúng Gmail đã nhập.",
                    "The invitation code lasts 7 days and is shown once. The invitee must sign in with the exact Google email entered above."),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 16.sp
            )
        }

        invitation?.let { created ->
            CardBox {
                Text(tr("Lời mời đã tạo", "Invitation created"), fontWeight = FontWeight.SemiBold)
                Text(created.email + " · " + roleTitle(created.role), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                SelectionContainer {
                    Text(created.code, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
                Text(
                    tr("Sao chép mã và gửi riêng cho người được mời. Mã không được lưu lại ở máy chủ dưới dạng văn bản gốc.",
                        "Copy this code and send it privately. The original code is not stored on the server."),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )
            }
        }

        if (!errorMessage.isNullOrBlank()) {
            Text(errorMessage.orEmpty(), color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
        }

        Text(tr("Danh sách cộng tác viên", "Collaborators"), fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
        if (collaborators.isEmpty()) {
            EmptyState(
                Icons.Outlined.Group,
                tr("Chưa có cộng tác viên", "No collaborators"),
                tr("Người đã chấp nhận lời mời sẽ xuất hiện tại đây.", "People who accept an invitation will appear here.")
            )
        } else {
            collaborators.forEach { collaborator ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(Modifier.fillMaxWidth().padding(13.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text(collaborator.email, fontWeight = FontWeight.SemiBold)
                        Text(
                            roleTitle(collaborator.role) + " · " + if (collaborator.status == "ACTIVE") tr("Đang hoạt động", "Active") else tr("Đã thu hồi", "Revoked"),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    editTarget = collaborator
                                    roleToAssign = collaborator.role
                                },
                                enabled = collaborator.status == "ACTIVE" && !loading,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(tr("Đổi quyền", "Change role"))
                            }
                            OutlinedButton(
                                onClick = { revokeTarget = collaborator },
                                enabled = collaborator.status == "ACTIVE" && !loading,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(tr("Thu hồi", "Revoke"))
                            }
                        }
                    }
                }
            }
        }
    }

    editTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { editTarget = null },
            title = { Text(tr("Đổi vai trò", "Change role")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(target.email, fontSize = 12.sp)
                    listOf(
                        "DEVELOPER" to tr("Nhà phát triển", "Developer"),
                        "DESIGNER" to tr("Nhà thiết kế", "Designer"),
                        "REVIEWER" to tr("Người kiểm duyệt", "Reviewer"),
                        "CUSTOMER" to tr("Người dùng", "Customer")
                    ).forEach { option ->
                        FilterChip(
                            selected = roleToAssign == option.first,
                            onClick = { roleToAssign = option.first },
                            label = { Text(option.second) }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val selected = roleToAssign
                    editTarget = null
                    scope.launch {
                        loading = true
                        try {
                            AutuLioApi.updateCollaboratorRole(sessionToken, target.id, selected)
                            collaborators = AutuLioApi.listCollaborators(sessionToken)
                        } catch (error: Exception) {
                            errorMessage = error.message ?: tr("Không đổi được quyền.", "Could not change the role.")
                        } finally {
                            loading = false
                        }
                    }
                }) { Text(tr("Lưu", "Save")) }
            },
            dismissButton = {
                TextButton(onClick = { editTarget = null }) { Text(tr("Hủy", "Cancel")) }
            }
        )
    }

    revokeTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { revokeTarget = null },
            title = { Text(tr("Thu hồi quyền cộng tác viên?", "Revoke collaborator access?")) },
            text = { Text(tr("Người này sẽ không thể truy cập khu vực cộng tác viên sau khi máy chủ xác nhận thu hồi.", "This person will lose collaborator access after the server confirms revocation.")) },
            confirmButton = {
                TextButton(onClick = {
                    revokeTarget = null
                    scope.launch {
                        loading = true
                        try {
                            AutuLioApi.revokeCollaborator(sessionToken, target.id)
                            collaborators = AutuLioApi.listCollaborators(sessionToken)
                        } catch (error: Exception) {
                            errorMessage = error.message ?: tr("Không thu hồi được quyền.", "Could not revoke access.")
                        } finally {
                            loading = false
                        }
                    }
                }) { Text(tr("Thu hồi", "Revoke")) }
            },
            dismissButton = {
                TextButton(onClick = { revokeTarget = null }) { Text(tr("Hủy", "Cancel")) }
            }
        )
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
private fun CustomerHome(modifier: Modifier, onScripts: () -> Unit, authenticated: Boolean = false) {
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
        Text(
            if (authenticated) tr("Tài khoản đã được xác minh", "Account verified")
            else tr("Prototype giao diện · Chế độ xem thử", "UI prototype · Preview mode"),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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
private fun CustomerDeviceLicensePage(modifier: Modifier, signedIn: Boolean = false) {
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
                    Text(
                        if (signedIn) tr("Đã đăng nhập", "Signed in")
                        else tr("Chế độ xem thử", "Preview mode"),
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        if (signedIn) tr("Dữ liệu thiết bị và giấy phép sẽ xuất hiện sau khi các API tương ứng được kết nối.",
                            "Device and license data will appear after their APIs are connected.")
                        else tr("Chưa có dữ liệu thiết bị hoặc giấy phép được truy xuất.",
                            "No device or license data has been retrieved."),
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
private fun SettingsScreen(modifier: Modifier, onTheme: () -> Unit, onLogout: () -> Unit = {}) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
        BrandHeader(admin = false)
        Heading(tr("Cài đặt", "Settings"), tr("Tùy chỉnh AUTU-LIO", "Customize AUTU-LIO"))
        SettingRow(Icons.Outlined.Palette, tr("Giao diện", "Appearance"), tr("Chuyển đổi sáng và tối", "Switch light and dark mode"), onTheme)
        SettingRow(Icons.Outlined.Info, tr("Thông tin ứng dụng", "App information"), "AUTU-LIO 0.1.0 · Prototype")
        SettingRow(Icons.Outlined.Lock, tr("Quyền riêng tư", "Privacy"), tr("Thông tin quyền sẽ được bổ sung", "Permission details will be provided"))
        SettingRow(Icons.Outlined.ExitToApp, tr("Đăng xuất", "Sign out"), tr("Kết thúc phiên đăng nhập AUTU-LIO", "End your AUTU-LIO session"), onLogout)
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
            Column {
                Text("AUTU-LIO", fontWeight = FontWeight.Bold, fontSize = 17.sp, letterSpacing = 0.5.sp)
                Text(if (admin) "ADMIN STUDIO" else "AUTOCLICK", fontSize = 10.sp, letterSpacing = 1.1.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun Heading(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(subtitle, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CardBox(content: @Composable ColumnScope.() -> Unit) {
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
private fun EmptyState(icon: ImageVector, title: String, message: String) {
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
