package com.autulio.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.DateFormat
import java.util.Date
import java.util.Locale

private fun formatPresenceTime(value: Long?): String {
    if (value == null || value <= 0L) return "Chưa có"
    return DateFormat.getDateTimeInstance(
        DateFormat.SHORT,
        DateFormat.SHORT,
        Locale.forLanguageTag("vi-VN")
    ).format(Date(value * 1000L))
}

@Composable
internal fun AdminIconValue(
    icon: ImageVector,
    description: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

@Composable
internal fun AdminPresenceInfo(
    presenceSeenAt: Long?,
    appForegroundAt: Long?,
    runtimeState: String,
    isAllowed: Boolean = true
) {
    val nowSeconds = System.currentTimeMillis() / 1000L
    val ageSeconds = presenceSeenAt
        ?.takeIf { isAllowed && it > 0L }
        ?.let { (nowSeconds - it).coerceAtLeast(0L) }

    val connection = when {
        !isAllowed -> "Bị chặn"
        ageSeconds == null -> "Chưa có dữ liệu"
        ageSeconds <= 120L -> "Đang kết nối"
        ageSeconds <= 600L -> "Vừa hoạt động"
        else -> "Ngoại tuyến"
    }
    val connectionIcon = if (isAllowed && ageSeconds != null && ageSeconds <= 120L) {
        Icons.Outlined.Wifi
    } else {
        Icons.Outlined.WifiOff
    }

    val appIsForeground = isAllowed &&
        ageSeconds != null &&
        ageSeconds <= 120L &&
        appForegroundAt != null &&
        appForegroundAt > 0L &&
        nowSeconds - appForegroundAt <= 120L
    val appLabel = when {
        !isAllowed || ageSeconds == null -> "Chưa rõ"
        appIsForeground -> "Mở gần đây"
        ageSeconds <= 600L -> "Đã chuyển nền"
        else -> "Không rõ"
    }

    val runtimeFresh = isAllowed && ageSeconds != null && ageSeconds <= 120L
    val runtimeIcon: ImageVector
    val runtimeLabel: String
    if (!runtimeFresh) {
        runtimeIcon = Icons.Outlined.AccessTime
        runtimeLabel = "Chưa rõ"
    } else {
        when (runtimeState) {
            "RUNNING" -> {
                runtimeIcon = Icons.Outlined.PlayArrow
                runtimeLabel = "Đang chạy"
            }
            "PAUSED" -> {
                runtimeIcon = Icons.Outlined.Pause
                runtimeLabel = "Tạm dừng"
            }
            "STOPPED" -> {
                runtimeIcon = Icons.Outlined.Stop
                runtimeLabel = "Đã dừng"
            }
            else -> {
                runtimeIcon = Icons.Outlined.AccessTime
                runtimeLabel = "Chưa rõ"
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            AdminIconValue(connectionIcon, "Trạng thái kết nối", connection)
            AdminIconValue(Icons.Outlined.Apps, "Ứng dụng", appLabel)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            AdminIconValue(runtimeIcon, "Trạng thái AUTU", runtimeLabel)
            if (isAllowed && presenceSeenAt != null && presenceSeenAt > 0L) {
                AdminIconValue(
                    Icons.Outlined.AccessTime,
                    "Lần kết nối gần nhất",
                    formatPresenceTime(presenceSeenAt)
                )
            }
        }
    }
}
