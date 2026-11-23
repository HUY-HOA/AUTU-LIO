package com.autulio.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.autulio.app.auth.AuthenticatedUser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

@Composable
internal fun GoogleAccountSettingsCard(
    authenticatedUser: AuthenticatedUser?,
    fallbackEmail: String? = null,
    onLogout: () -> Unit
) {
    val details = authenticatedUser?.googleSignIn
    val email = authenticatedUser?.email ?: fallbackEmail.orEmpty()
    val name = authenticatedUser?.displayName
        ?.takeIf { it.isNotBlank() }
        ?: details?.let { profile ->
            listOfNotNull(profile.givenName, profile.familyName)
                .filter { it.isNotBlank() }
                .joinToString(" ")
                .takeIf { it.isNotBlank() }
        }
        ?: email.substringBefore("@").takeIf { it.isNotBlank() }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(14.dp),
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                GoogleProfileAvatar(details?.pictureUrl, 48.dp)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        name ?: tr("Tài khoản Google", "Google account"),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (email.isNotBlank()) {
                        Text(
                            email,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            androidx.compose.material3.HorizontalDivider()
            androidx.compose.material3.TextButton(
                onClick = onLogout,
                modifier = Modifier.align(Alignment.End)
            ) {
                Icon(Icons.Outlined.Logout, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(tr("Đăng xuất", "Sign out"))
            }
        }
    }
}

@Composable
private fun GoogleProfileAvatar(pictureUrl: String?, size: androidx.compose.ui.unit.Dp) {
    var bitmap by remember(pictureUrl) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(pictureUrl) {
        bitmap = if (pictureUrl.isNullOrBlank()) {
            null
        } else {
            withContext(Dispatchers.IO) {
                try {
                    loadGoogleProfileBitmap(pictureUrl)
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    null
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        val image = bitmap
        if (image != null) {
            Image(
                bitmap = image.asImageBitmap(),
                contentDescription = tr("Ảnh đại diện Google", "Google profile picture"),
                modifier = Modifier.fillMaxSize().clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        } else {
            Icon(
                Icons.Outlined.AccountCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(size * 0.82f)
            )
        }
    }
}

private fun loadGoogleProfileBitmap(pictureUrl: String): Bitmap? {
    val uri = Uri.parse(pictureUrl)
    if (!uri.scheme.equals("https", ignoreCase = true) || uri.host.isNullOrBlank()) return null

    val connection = (URL(pictureUrl).openConnection() as? HttpURLConnection) ?: return null
    try {
        connection.instanceFollowRedirects = false
        connection.connectTimeout = 6_000
        connection.readTimeout = 6_000
        connection.useCaches = true
        connection.setRequestProperty("Accept", "image/*")

        if (connection.responseCode !in 200..299) return null
        if (!connection.contentType.orEmpty().substringBefore(';').startsWith("image/", ignoreCase = true)) return null
        if (connection.contentLengthLong > MAX_AVATAR_BYTES) return null

        val bytes = connection.inputStream.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8 * 1024)
            var total = 0
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                if (total > MAX_AVATAR_BYTES) throw IOException("Google profile image is too large.")
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth !in 1..MAX_AVATAR_DIMENSION || bounds.outHeight !in 1..MAX_AVATAR_DIMENSION) return null

        var sampleSize = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sampleSize > 256) sampleSize *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    } finally {
        connection.disconnect()
    }
}

private const val MAX_AVATAR_BYTES = 1_048_576
private const val MAX_AVATAR_DIMENSION = 8_192
