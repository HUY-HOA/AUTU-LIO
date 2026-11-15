package com.autulio.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.autulio.app.script.ScriptCompiler
import com.autulio.app.script.ScriptTemplate
import com.autulio.app.script.ScriptTemplateCatalog
import java.util.Locale

@Composable
internal fun AdminTemplateLibraryScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onOpenTemplate: (ScriptTemplate) -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("all") }

    val templates = ScriptTemplateCatalog.templates
    val filtered = templates.filter { template ->
        val matchesCategory = category == "all" || templateCategory(template.id) == category
        val text = listOf(
            template.nameVi,
            template.nameEn,
            template.descriptionVi,
            template.descriptionEn,
            template.source
        ).joinToString(" ").lowercase(Locale.ROOT)
        matchesCategory && (query.isBlank() || text.contains(query.trim().lowercase(Locale.ROOT)))
    }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TextButton(onClick = onBack) { Text(tr("← Quay lại", "← Back")) }
        Heading(
            tr("Thư viện mẫu AUTU", "AUTU template library"),
            tr(
                "${templates.size} mẫu lệnh có thể mở trực tiếp trong trình soạn thảo.",
                "${templates.size} ready-to-edit templates that open directly in the editor."
            )
        )
        CardBox {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Icon(Icons.Outlined.Code, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f)) {
                    Text(
                        tr("Tương thích AUTU Script ${ScriptCompiler.LANGUAGE_VERSION}", "Compatible with AUTU Script ${ScriptCompiler.LANGUAGE_VERSION}"),
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        tr("Mỗi mẫu được kiểm tra cú pháp trước khi đưa vào thư viện.", "Every template is syntax-checked before appearing in this library."),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
            }
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            placeholder = { Text(tr("Tìm theo tên, mô tả hoặc lệnh", "Search name, description or command")) }
        )
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            listOf(
                "all" to tr("Tất cả", "All"),
                "actions" to tr("Thao tác", "Actions"),
                "logic" to tr("Điều kiện và vòng lặp", "Logic and loops"),
                "system" to tr("Android", "Android")
            ).forEach { (key, label) ->
                FilterChip(
                    selected = category == key,
                    onClick = { category = key },
                    label = { Text(label) }
                )
            }
        }
        Text(
            tr("Đang hiển thị ${filtered.size}/${templates.size} mẫu", "Showing ${filtered.size}/${templates.size} templates"),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp
        )
        if (filtered.isEmpty()) {
            EmptyState(
                Icons.Outlined.Search,
                tr("Không tìm thấy mẫu phù hợp", "No matching templates"),
                tr("Thử từ khóa hoặc nhóm khác.", "Try another search term or category.")
            )
        } else {
            filtered.forEach { template ->
                val result = remember(template.id) { ScriptCompiler.compile(template.source) }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    shape = RoundedCornerShape(15.dp)
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(13.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                tr(template.nameVi, template.nameEn),
                                modifier = Modifier.weight(1f),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                            Text(
                                if (result.isSuccess) "Script ${ScriptCompiler.LANGUAGE_VERSION}" else tr("Lỗi mẫu", "Invalid"),
                                color = if (result.isSuccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Text(
                            tr(template.descriptionVi, template.descriptionEn),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(9.dp)
                        ) {
                            Text(
                                template.source,
                                modifier = Modifier.fillMaxWidth().padding(10.dp),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                lineHeight = 15.sp,
                                maxLines = 5,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Button(
                            onClick = { onOpenTemplate(template) },
                            enabled = result.isSuccess,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(tr("Mở và chỉnh sửa mẫu", "Open and edit template"))
                            Spacer(Modifier.width(7.dp))
                            Icon(Icons.Outlined.ArrowForward, contentDescription = null)
                        }
                    }
                }
            }
        }
    }
}

private fun templateCategory(id: String): String = when (id) {
    "single_tap", "tap_sequence", "long_press", "swipe_up", "open_app" -> "actions"
    "repeat_tap", "if_battery", "while_loop", "wait_until_condition", "until_loop" -> "logic"
    else -> "system"
}
