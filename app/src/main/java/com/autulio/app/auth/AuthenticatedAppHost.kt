package com.autulio.app.auth

import android.app.Activity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
            AutoClickAccessibilityService.invalidateSession()
            accessToken = null
            user = null
            restoring = false
            return
        }
        try {
            val verifiedUser = repository.currentUser(savedToken)
            AutoClickAccessibilityService.markSessionVerified(verifiedUser.role, savedToken)
            accessToken = savedToken
            user = verifiedUser
            hasSavedSession = true
        } catch (error: Exception) {
            if (error is AuthApiException && error.statusCode in 401..403) {
                AutoClickAccessibilityService.invalidateSession()
                store.clear()
                accessToken = null
                user = null
                hasSavedSession = false
                errorMessage = error.message.orEmpty()
            } else {
                AutoClickAccessibilityService.invalidateSession()
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
                if (accessToken == token) {
                    AutoClickAccessibilityService.markSessionVerified(verifiedUser.role, token)
                    user = verifiedUser
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                AutoClickAccessibilityService.invalidateSession()
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

    // Record only a minimal heartbeat while the customer app is in the foreground.
    // When the app is backgrounded, the accessibility service reports presence only
    // while an AUTU script is actively running or paused.
    LaunchedEffect(accessToken, user?.role) {
        val token = accessToken ?: return@LaunchedEffect
        if (user?.role != AccessRole.CUSTOMER) return@LaunchedEffect
        val lifecycle = (activity as? LifecycleOwner)?.lifecycle ?: return@LaunchedEffect
        val identity = runCatching {
            SecureDeviceIdentityStore(context).getExistingIdentity()
        }.getOrNull() ?: return@LaunchedEffect

        while (true) {
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                runCatching {
                    repository.reportCustomerPresence(
                        accessToken = token,
                        deviceFingerprint = identity.fingerprintSha256,
                        appForeground = true,
                        runtimeState = AutoClickAccessibilityService.currentRuntimePresenceState()
                    )
                }
            }
            delay(45_000)
        }
    }

    fun signOut() {
        val currentToken = accessToken
        AutoClickAccessibilityService.invalidateSession()
        store.clear()
        accessToken = null
        user = null
        hasSavedSession = false
        errorMessage = ""
        scope.launch {
            if (!currentToken.isNullOrBlank()) repository.signOut(currentToken)
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
                        AutoClickAccessibilityService.markSessionVerified(session.user.role, session.accessToken)
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
        Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
            Column(
                Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    "AUTU-LIO",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp
                    ),
                    color = Color(0xFF5B8CFF),
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(12.dp))
                Canvas(Modifier.size(84.dp)) {
                    val w = size.width
                    val h = size.height
                    val brackets = Path().apply {
                        moveTo(w * 0.30f, h * 0.23f)
                        lineTo(w * 0.12f, h * 0.5f)
                        lineTo(w * 0.30f, h * 0.77f)
                        moveTo(w * 0.70f, h * 0.23f)
                        lineTo(w * 0.88f, h * 0.5f)
                        lineTo(w * 0.70f, h * 0.77f)
                    }
                    drawPath(
                        path = brackets,
                        color = Color(0xFF4F8CFF),
                        style = Stroke(
                            width = w * 0.075f,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                    val lightning = Path().apply {
                        moveTo(w * 0.56f, h * 0.14f)
                        lineTo(w * 0.37f, h * 0.53f)
                        lineTo(w * 0.50f, h * 0.53f)
                        lineTo(w * 0.44f, h * 0.86f)
                        lineTo(w * 0.66f, h * 0.43f)
                        lineTo(w * 0.53f, h * 0.43f)
                        close()
                    }
                    drawPath(path = lightning, color = Color(0xFF35C6C8))
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "LIOScript",
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(28.dp))
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
                    if (errorMessage.isNotBlank()) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            errorMessage,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
}
