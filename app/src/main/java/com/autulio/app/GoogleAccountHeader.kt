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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
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
import androidx.compose.ui.platform.LocalContext
import com.autulio.app.auth.AuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

@Composable
internal fun GoogleAccountHeader(
    accessToken: String?,
    fallbackEmail: String? = null
) {
    if (accessToken.isNullOrBlank()) return

    val context = LocalContext.current
    val repository = remember(context) { AuthRepository(context) }
    var account by remember(accessToken) { mutableStateOf<com.autulio.app.auth.AuthenticatedUser?>(null) }

    LaunchedEffect(accessToken) {
        account = runCatching { repository.currentUser(accessToken) }.getOrNull()
    }

    val name = account?.displayName
        ?: account?.googleSignIn?.let { details ->
            listOfNotNull(details.givenName, details.familyName)
                .filter { it.isNotBlank() }
                .joinToString(" ")
                .takeIf { it.isNotBlank() }
        }
    val email = account?.email ?: fallbackEmail.orEmpty()

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            GoogleProfileAvatar(account?.googleSignIn?.pictureUrl)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                Text(
                    tr("Tài khoản Google", "Google account"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp
                )
                if (!name.isNullOrBlank()) {
                    Text(
                        name,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (email.isNotBlank()) {
                    Text(
                        email,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun GoogleProfileAvatar(pictureUrl: String?) {
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
            .size(34.dp)
            .clip(CircleShape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        val image = bitmap
        if (image != null) {
            Image(
                bitmap = image.asImageBitmap(),
                contentDescription = tr("Ảnh đại diện Google", "Google profile picture"),
                modifier = Modifier.matchParentSize().clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        } else {
            Icon(
                Icons.Outlined.AccountCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(25.dp)
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
