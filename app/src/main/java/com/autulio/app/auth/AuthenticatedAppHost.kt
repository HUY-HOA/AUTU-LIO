package com.autulio.app.auth

import android.app.Activity
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.autulio.app.AdminApp
import com.autulio.app.CustomerApp
import com.autulio.app.access.AccessRole
import com.autulio.app.access.hasAutomationAccessibilityPermission
import com.autulio.app.tr
import kotlinx.coroutines.launch

@Composable
internal fun AuthenticatedAppHost() {
    val context = LocalContext.current
    val activity = context as? Activity
    val repository = remember(context) { AuthRepository(context) }
    val store = remember(context) { SecureSessionStore(context) }
    val scope = rememberCoroutineScope()

    var user by remember { mutableStateOf<AuthenticatedUser?>(null) }
    var accessToken by remember { mutableStateOf<String?>(null) }
    var restoring by remember { mutableStateOf(true) }
    var working by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var hasSavedSession by remember { mutableStateOf(false) }
    var automationPermissionGranted by remember { mutableStateOf(hasAutomationAccessibilityPermission(context)) }
    val permissionSettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        automationPermissionGranted = hasAutomationAccessibilityPermission(context)
    }

    DisposableEffect(activity) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                automationPermissionGranted = hasAutomationAccessibilityPermission(context)
            }
        }
        activity?.lifecycle?.addObserver(observer)
        onDispose { activity?.lifecycle?.removeObserver(observer) }
    }

    suspend fun restoreSession() {
        restoring = true
        errorMessage = ""
        val savedToken = store.read()
        hasSavedSession = !savedToken.isNullOrBlank()
        if (savedToken.isNullOrBlank()) {
            accessToken = null
            user = null
            restoring = false
            return
        }
        try {
            val verifiedUser = repository.currentUser(savedToken)
            accessToken = savedToken
            user = verifiedUser
            hasSavedSession = true
        } catch (error: Exception) {
            if (error is AuthApiException && error.statusCode in 401..403) {
                store.clear()
                accessToken = null
                user = null
                hasSavedSession = false
                errorMessage = error.message.orEmpty()
            } else {
                errorMessage = tr(
                    "Không thể xác minh phiên vì máy chủ đang ngoại tuyến. Hãy kết nối mạng rồi thử lại.",
                    "The saved session could not be verified because the server is unavailable. Connect to the internet and retry."
                )
            }
        } finally {
            restoring = false
        }
    }

    LaunchedEffect(Unit) {
        restoreSession()
    }

    fun signOut() {
        val currentToken = accessToken
        scope.launch {
            if (!currentToken.isNullOrBlank()) repository.signOut(currentToken)
            store.clear()
            accessToken = null
            user = null
            hasSavedSession = false
            errorMessage = ""
        }
    }

    if (!automationPermissionGranted) {
        AutomationPermissionScreen(
            onOpenSettings = {
                permissionSettingsLauncher.launch(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            },
            onCheckAgain = {
                automationPermissionGranted = hasAutomationAccessibilityPermission(context)
            }
        )
        return
    }

    val currentUser = user
    val currentToken = accessToken
    if (currentUser != null && currentToken != null) {
        if (currentUser.role == AccessRole.OWNER) {
            AdminApp(accessToken = currentToken, onLogout = ::signOut)
        } else {
            CustomerApp(accessToken = currentToken, email = currentUser.email)
        }
        return
    }

    AuthScreen(
        isConfigured = repository.isConfigured,
        restoring = restoring,
        working = working,
        hasSavedSession = hasSavedSession,
        errorMessage = errorMessage,
        onRetrySession = { scope.launch { restoreSession() } },
        onSignIn = {
            if (activity == null) {
                errorMessage = tr("Không thể mở giao diện đăng nhập trên màn hình này.", "Sign-in cannot be launched from this screen.")
            } else {
                scope.launch {
                    working = true
                    errorMessage = ""
                    try {
                        val session = repository.signIn(activity)
                        store.save(session.accessToken)
                        accessToken = session.accessToken
                        user = session.user
                        hasSavedSession = true
                    } catch (error: Exception) {
                        errorMessage = error.message?.takeIf { it.isNotBlank() }
                            ?: tr("Đăng nhập thất bại. Vui lòng thử lại.", "Sign-in failed. Please try again.")
                    } finally {
                        working = false
                    }
                }
            }
        }
    )
}

@Composable
private fun AuthScreen(
    isConfigured: Boolean,
    restoring: Boolean,
    working: Boolean,
    hasSavedSession: Boolean,
    errorMessage: String,
    onRetrySession: () -> Unit,
    onSignIn: () -> Unit
) {
    MaterialTheme(colorScheme = darkColorScheme(primary = androidx.compose.ui.graphics.Color(0xFF4F8CFF))) {
        Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
            Column(
                Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Outlined.Code,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(58.dp)
                )
                Spacer(Modifier.height(18.dp))
                Text("AUTU-LIO", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    tr("Đăng nhập để xác minh quyền tài khoản", "Sign in to verify your account access"),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(22.dp))
                if (restoring) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(10.dp))
                    Text(tr("Đang xác minh phiên đăng nhập…", "Verifying your session…"), fontSize = 12.sp)
                } else {
                    Button(
                        onClick = onSignIn,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = isConfigured && !working
                    ) {
                        if (working) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Outlined.Person, contentDescription = null)
                        }
                        Spacer(Modifier.width(9.dp))
                        Text(tr("Đăng nhập bằng Google", "Continue with Google"))
                    }
                    if (hasSavedSession) {
                        OutlinedButton(
                            onClick = onRetrySession,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !working
                        ) {
                            Text(tr("Thử xác minh phiên đã lưu", "Retry saved session verification"))
                        }
                    }
                    if (!isConfigured) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            tr(
                                "Đăng nhập chưa được bật. Cần cấu hình Auth API URL và Google Server Client ID trước khi sử dụng.",
                                "Sign-in is not enabled yet. Configure the Auth API URL and Google Server Client ID first."
                            ),
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                    if (errorMessage.isNotBlank()) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            errorMessage,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                    Spacer(Modifier.height(18.dp))
                    Text(
                        tr(
                            "AUTU-LIO không tự cấp quyền Admin. Vai trò được xác minh từ máy chủ sau khi Google xác thực tài khoản.",
                            "AUTU-LIO never grants Admin access locally. The server verifies your role after Google authenticates your account."
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun AutomationPermissionScreen(
    onOpenSettings: () -> Unit,
    onCheckAgain: () -> Unit
) {
    MaterialTheme(colorScheme = darkColorScheme(primary = androidx.compose.ui.graphics.Color(0xFF4F8CFF))) {
        Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
            Column(
                Modifier.fillMaxSize().padding(padding).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Outlined.Code,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(58.dp)
                )
                Spacer(Modifier.height(18.dp))
                Text("AUTU-LIO", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(12.dp))
                Text(
                    tr(
                        "Cần bật quyền Trợ năng để sử dụng AUTU-LIO",
                        "Accessibility access is required to use AUTU-LIO"
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    tr(
                        "Quyền này cho phép ứng dụng thực hiện thao tác chạm/vuốt và hiển thị bảng điều khiển nổi trên ứng dụng khác khi bạn tạo, chỉnh sửa hoặc chạy script. AUTU-LIO chỉ dùng quyền này cho các thao tác do bạn chủ động thực hiện.",
                        "This permission lets the app perform taps/swipes and display its floating controller over other apps while you create, edit or run scripts. AUTU-LIO uses it only for actions you explicitly start."
                    ),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(22.dp))
                Button(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
                    Text(tr("Mở Cài đặt để cấp quyền", "Open Settings to grant access"))
                }
                OutlinedButton(onClick = onCheckAgain, modifier = Modifier.fillMaxWidth()) {
                    Text(tr("Tôi đã cấp quyền · Kiểm tra lại", "I granted access · Check again"))
                }
            }
        }
    }
}
