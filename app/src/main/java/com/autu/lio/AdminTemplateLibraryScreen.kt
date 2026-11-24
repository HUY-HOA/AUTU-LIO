package com.autu.lio

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
import com.autu.lio.script.ScriptCompiler
import com.autu.lio.script.ScriptTemplate
import com.autu.lio.script.ScriptTemplateCatalog
import java.util.Locale

@Composable
internal fun AdminTemplateLibraryScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onOpenTemplate: (ScriptTemplate) -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("all") }
    var detailTemplateId by rememberSaveable { mutableStateOf<String?>(null) }

    val templates = ScriptTemplateCatalog.templates
    val selectedTemplate = templates.firstOrNull { it.id == detailTemplateId }
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
                                if (result.isSuccess) "Ngôn ngữ AUTU ${ScriptCompiler.LANGUAGE_VERSION}" else tr("Lỗi mẫu", "Invalid"),
                                color = if (result.isSuccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            TextButton(
                                onClick = { detailTemplateId = template.id },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 5.dp)
                            ) {
                                Text("Chi tiết")
                            }
                            Spacer(Modifier.weight(1f))
                            Button(
                                onClick = { onOpenTemplate(template) },
                                enabled = result.isSuccess,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 7.dp)
                            ) {
                                Text("Mở để chỉnh sửa")
                                Spacer(Modifier.width(5.dp))
                                Icon(Icons.Outlined.ArrowForward, contentDescription = null)
                            }
                        }
                    }
                }
            }
        }
    }

    if (selectedTemplate != null) {
        val result = remember(selectedTemplate.id) { ScriptCompiler.compile(selectedTemplate.source) }
        AlertDialog(
            onDismissRequest = { detailTemplateId = null },
            title = { Text(selectedTemplate.nameVi) },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    Text(selectedTemplate.descriptionVi)
                    Text(
                        if (result.isSuccess) "Mẫu hợp lệ với ngôn ngữ AUTU ${ScriptCompiler.LANGUAGE_VERSION}."
                        else "Mẫu đang có lỗi cú pháp.",
                        color = if (result.isSuccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        fontSize = 12.sp
                    )
                    Text("Mã mẫu", fontWeight = FontWeight.SemiBold)
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(9.dp)
                    ) {
                        Text(
                            selectedTemplate.source,
                            modifier = Modifier.fillMaxWidth().padding(10.dp),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { detailTemplateId = null }) { Text("Đóng") }
            },
            dismissButton = {
                Button(
                    onClick = {
                        detailTemplateId = null
                        onOpenTemplate(selectedTemplate)
                    },
                    enabled = result.isSuccess
                ) {
                    Text("Mở để chỉnh sửa")
                }
            }
        )
    }
}

private fun templateCategory(id: String): String = when (id) {
    "single_tap", "tap_sequence", "long_press", "swipe_up", "open_app" -> "actions"
    "repeat_tap", "if_battery", "while_loop", "wait_until_condition", "until_loop" -> "logic"
    else -> "system"
}
