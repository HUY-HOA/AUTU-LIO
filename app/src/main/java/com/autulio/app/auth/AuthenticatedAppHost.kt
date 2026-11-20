package com.autulio.app.auth

import android.app.Activity
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
import com.autulio.app.AdminApp
import com.autulio.app.CustomerApp
import com.autulio.app.access.AccessRole
import com.autulio.app.accessibility.AutoClickAccessibilityService
import com.autulio.app.tr
import com.autulio.app.localizedErrorMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
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

    suspend fun restoreSession() {
        restoring = true
        errorMessage = ""
        val savedToken = store.read()
        hasSavedSession = !savedToken.isNullOrBlank()
        if (savedToken.isNullOrBlank()) {
            AutoClickAccessibilityService.current()?.lockRunner()
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
                AutoClickAccessibilityService.current()?.lockRunner()
                store.clear()
                accessToken = null
                user = null
                hasSavedSession = false
                errorMessage = error.message.orEmpty()
            } else {
                AutoClickAccessibilityService.current()?.lockRunner()
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

    // Recheck server authorization periodically so a remotely revoked session
    // cannot continue using the floating runner indefinitely.
    LaunchedEffect(accessToken) {
        val token = accessToken ?: return@LaunchedEffect
        while (true) {
            delay(60_000)
            try {
                val verifiedUser = repository.currentUser(token)
                if (accessToken == token) user = verifiedUser
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                AutoClickAccessibilityService.current()?.lockRunner()
                if (accessToken == token) {
                    if (error is AuthApiException && error.statusCode in 401..403) {
                        store.clear()
                        hasSavedSession = false
                        errorMessage = error.message.orEmpty()
                    } else {
                        hasSavedSession = !store.read().isNullOrBlank()
                        errorMessage = tr(
                            "Không thể xác minh phiên. Hãy kết nối mạng rồi xác minh lại trước khi sử dụng.",
                            "The session could not be verified. Connect to the internet and verify again before using the app."
                        )
                    }
                    accessToken = null
                    user = null
                }
            }
        }
    }

    fun signOut() {
        val currentToken = accessToken
        AutoClickAccessibilityService.current()?.lockRunner()
        scope.launch {
            if (!currentToken.isNullOrBlank()) repository.signOut(currentToken)
            store.clear()
            accessToken = null
            user = null
            hasSavedSession = false
            errorMessage = ""
        }
    }

    val currentUser = user
    val currentToken = accessToken
    if (currentUser != null && currentToken != null) {
        when (currentUser.role) {
            AccessRole.OWNER -> AdminApp(accessToken = currentToken, onLogout = ::signOut)
            AccessRole.CUSTOMER -> CustomerApp(
                accessToken = currentToken,
                email = currentUser.email,
                onLogout = ::signOut
            )
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
                        errorMessage = localizedErrorMessage(error, tr("Đăng nhập thất bại. Vui lòng kiểm tra tài khoản Google và thử lại.", "Sign-in failed. Please try again."))
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
                    Text(
                        tr(
                            "Khi đăng nhập, AUTU-LIO gửi thông tin kỹ thuật cơ bản của thiết bị và trạng thái kết nối để bảo mật phiên, quản lý thiết bị và hỗ trợ tương thích. Không dùng chức năng này để đọc IMEI, số sê-ri hoặc vị trí GPS.",
                            "At sign-in, AUTU-LIO sends basic device and connection diagnostics for session security, device management and compatibility. This feature does not read IMEI, serial numbers or GPS location."
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        textAlign = TextAlign.Center
                    )
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
