package com.autulio.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            if (BuildConfig.FLAVOR == "admin") AdminApp() else CustomerApp()
        }
    }
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

private val customerTabs = listOf(
    Tab("home", "Trang chủ", "Home", Icons.Outlined.Home),
    Tab("scripts", "Kịch bản", "Scripts", Icons.Outlined.ListAlt),
    Tab("device", "Thiết bị", "Device", Icons.Outlined.Smartphone),
    Tab("settings", "Cài đặt", "Settings", Icons.Outlined.Settings)
)

@Composable
private fun AdminApp() {
    var dark by rememberSaveable { mutableStateOf(true) }
    var page by rememberSaveable { mutableStateOf("overview") }
    var editor by rememberSaveable { mutableStateOf<String?>(null) }
    var createDialog by remember { mutableStateOf(false) }
    val scripts = remember { mutableStateListOf<String>() }
    val host = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    AppTheme(dark) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(host) },
            bottomBar = {
                if (editor == null) NavigationBar {
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
            if (editor != null) {
                EditorScreen(
                    name = editor!!,
                    modifier = Modifier.padding(padding),
                    onBack = { editor = null },
                    onMessage = { scope.launch { host.showSnackbar(it) } }
                )
            } else when (page) {
                "overview" -> OverviewScreen(
                    Modifier.padding(padding),
                    onNavigate = { page = it },
                    onCreate = { createDialog = true }
                )
                "scripts" -> ScriptsScreen(
                    Modifier.padding(padding),
                    scripts = scripts,
                    onCreate = { createDialog = true },
                    onOpen = { editor = it }
                )
                "customers" -> EmptyPage(
                    Modifier.padding(padding),
                    tr("Khách hàng", "Customers"),
                    tr("Quản lý tài khoản người dùng", "Manage user accounts"),
                    Icons.Outlined.Group
                )
                "devices" -> EmptyPage(
                    Modifier.padding(padding),
                    tr("Thiết bị", "Devices"),
                    tr("Theo dõi thiết bị đã đăng ký", "Review registered devices"),
                    Icons.Outlined.Smartphone
                )
                else -> MoreScreen(
                    Modifier.padding(padding),
                    page,
                    onSelect = { page = it },
                    onTheme = { dark = !dark }
                )
            }
        }

        if (createDialog) CreateDialog(
            onDismiss = { createDialog = false },
            onCreate = { name ->
                scripts.add(name)
                editor = name
                page = "scripts"
                createDialog = false
            }
        )
    }
}

@Composable
private fun CustomerApp() {
    var dark by rememberSaveable { mutableStateOf(true) }
    var page by rememberSaveable { mutableStateOf("home") }
    AppTheme(dark) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
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
                "home" -> CustomerHome(Modifier.padding(padding), onScripts = { page = "scripts" })
                "scripts" -> EmptyPage(
                    Modifier.padding(padding),
                    tr("Kịch bản", "Scripts"),
                    tr("Kịch bản do chủ ứng dụng phát hành sẽ xuất hiện tại đây.", "Scripts published by the app owner will appear here."),
                    Icons.Outlined.ListAlt
                )
                "device" -> EmptyPage(
                    Modifier.padding(padding),
                    tr("Thiết bị và giấy phép", "Device and license"),
                    tr("Thông tin đăng ký sẽ xuất hiện sau khi đăng nhập.", "Registration details will appear after sign-in."),
                    Icons.Outlined.Smartphone
                )
                else -> SettingsScreen(Modifier.padding(padding), onTheme = { dark = !dark })
            }
        }
    }
}

@Composable
private fun OverviewScreen(modifier: Modifier, onNavigate: (String) -> Unit, onCreate: () -> Unit) {
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
        Text(tr("Truy cập nhanh", "Quick actions"), fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionTile(tr("Tạo kịch bản", "Create script"), Icons.Outlined.Add, Modifier.weight(1f), onCreate)
            ActionTile(tr("Kịch bản", "Scripts"), Icons.Outlined.ListAlt, Modifier.weight(1f)) { onNavigate("scripts") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionTile(tr("Khách hàng", "Customers"), Icons.Outlined.Group, Modifier.weight(1f)) { onNavigate("customers") }
            ActionTile(tr("Thiết bị", "Devices"), Icons.Outlined.Smartphone, Modifier.weight(1f)) { onNavigate("devices") }
        }
        Text(tr("Hoạt động gần đây", "Recent activity"), fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
        EmptyState(Icons.Outlined.Info, tr("Chưa có hoạt động", "No activity yet"), tr("Nhật ký sẽ xuất hiện khi máy chủ được kết nối.", "Activity will appear after the server is connected."))
        Text(tr("Prototype giao diện · Chưa kết nối máy chủ", "UI prototype · Server not connected"), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
    }
}

@Composable
private fun ScriptsScreen(modifier: Modifier, scripts: List<String>, onCreate: () -> Unit, onOpen: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("all") }
    val filtered = scripts.filter { it.contains(query, true) }
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        BrandHeader(admin = true)
        Heading(tr("Kịch bản", "Scripts"), tr("Tạo, quản lý và phát hành kịch bản", "Create, manage and publish scripts"))
        Button(onClick = onCreate, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.Add, contentDescription = null)
            Spacer(Modifier.width(7.dp))
            Text(tr("Tạo kịch bản mới", "Create new script"))
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
        Text(tr("Bản nháp trong bản thử nghiệm chỉ lưu tạm trong phiên hiện tại.", "Prototype drafts are temporary and kept only in this session."), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (filtered.isEmpty()) {
            EmptyState(Icons.Outlined.ListAlt, tr("Chưa có kịch bản nào", "No scripts yet"), tr("Tạo kịch bản đầu tiên để bắt đầu.", "Create your first script to get started."))
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
private fun EditorScreen(name: String, modifier: Modifier, onBack: () -> Unit, onMessage: (String) -> Unit) {
    var code by rememberSaveable(name) { mutableStateOf("") }
    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(onClick = onBack) { Text(tr("← Quay lại", "← Back")) }
        Heading(name, tr("Bản nháp · Runtime chưa được tích hợp", "Draft · Runtime is not integrated"))
        Text(tr("Ngôn ngữ riêng AUTU-LIO sẽ được định nghĩa trước khi thực thi. Nội dung dưới đây chưa chạy được.", "The AUTU-LIO language will be specified before execution. Text below is not executable."), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        OutlinedTextField(value = code, onValueChange = { code = it }, modifier = Modifier.fillMaxWidth().weight(1f), minLines = 10, placeholder = { Text(tr("Nhập nội dung bản nháp...", "Enter draft content...")) })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onMessage(tr("Bộ kiểm tra cú pháp chưa được tích hợp.", "Syntax checker is not integrated.")) }, modifier = Modifier.weight(1f)) { Text(tr("Kiểm tra", "Check")) }
            Button(onClick = { onMessage(tr("Runtime chưa sẵn sàng để chạy thử.", "Runtime is not ready for testing.")) }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Outlined.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(5.dp))
                Text(tr("Chạy thử", "Test"))
            }
        }
        OutlinedButton(onClick = { onMessage(tr("Bản nháp chưa được đồng bộ lên máy chủ.", "Draft has not been synced to a server.")) }, modifier = Modifier.fillMaxWidth()) {
            Text(tr("Lưu bản nháp", "Save draft"))
        }
    }
}

@Composable
private fun MoreScreen(modifier: Modifier, page: String, onSelect: (String) -> Unit, onTheme: () -> Unit) {
    val items = listOf(
        Triple("licenses", tr("Giấy phép", "Licenses"), Icons.Outlined.VerifiedUser),
        Triple("library", tr("Thư viện", "Libraries"), Icons.Outlined.ListAlt),
        Triple("release", tr("Phát hành", "Releases"), Icons.Outlined.PlayArrow),
        Triple("logs", tr("Nhật ký", "Logs"), Icons.Outlined.Info),
        Triple("settings", tr("Cài đặt", "Settings"), Icons.Outlined.Settings)
    )
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BrandHeader(admin = true)
        Heading(if (page == "more") tr("Quản lý khác", "More management") else items.firstOrNull { it.first == page }?.second ?: tr("Quản lý", "Management"), tr("Công cụ quản trị AUTU-LIO", "AUTU-LIO administration tools"))
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
        if (page != "more" && page != "settings") {
            EmptyState(Icons.Outlined.Info, tr("Chưa có dữ liệu", "No data yet"), tr("Chức năng sẽ hoạt động sau khi tích hợp hệ thống.", "This feature will work after the system is integrated."))
        }
    }
}

@Composable
private fun CustomerHome(modifier: Modifier, onScripts: () -> Unit) {
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
        Text(tr("Prototype giao diện · Chưa đăng nhập", "UI prototype · Not signed in"), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SettingsScreen(modifier: Modifier, onTheme: () -> Unit) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
        BrandHeader(admin = false)
        Heading(tr("Cài đặt", "Settings"), tr("Tùy chỉnh AUTU-LIO", "Customize AUTU-LIO"))
        SettingRow(Icons.Outlined.Palette, tr("Giao diện", "Appearance"), tr("Chuyển đổi sáng và tối", "Switch light and dark mode"), onTheme)
        SettingRow(Icons.Outlined.Info, tr("Thông tin ứng dụng", "App information"), "AUTU-LIO 0.1.0 · Prototype")
        SettingRow(Icons.Outlined.Lock, tr("Quyền riêng tư", "Privacy"), tr("Thông tin quyền sẽ được bổ sung", "Permission details will be provided"))
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
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)) {
                Icon(Icons.Outlined.Code, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(10.dp).size(22.dp))
            }
            Column {
                Text("AUTU-LIO", fontWeight = FontWeight.Bold, fontSize = 17.sp, letterSpacing = 0.5.sp)
                Text(if (admin) "ADMIN STUDIO" else "AUTOCLICK", fontSize = 10.sp, letterSpacing = 1.1.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row {
            IconButton(onClick = {}) { Icon(Icons.Outlined.NotificationsNone, tr("Thông báo", "Notifications")) }
            IconButton(onClick = {}) { Icon(Icons.Outlined.Person, tr("Tài khoản", "Account")) }
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
