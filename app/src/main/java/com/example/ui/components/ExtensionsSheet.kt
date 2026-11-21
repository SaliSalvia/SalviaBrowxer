package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.ExtensionEntity
import com.example.ui.theme.JetBrainsMonoFontFamily

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtensionsSheet(
    extensions: List<ExtensionEntity>,
    onToggleEnabled: (String, Boolean) -> Unit,
    onSetInstalled: (String, Boolean) -> Unit,
    onCreateCustomExtension: (name: String, desc: String, matchPattern: String, runAt: String, jsCode: String, cssCode: String) -> Unit,
    onDeleteCustomExtension: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    BackHandler(onBack = onDismiss)
    var selectedTab by remember { mutableIntStateOf(0) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var inspectingExtension by remember { mutableStateOf<ExtensionEntity?>(null) }

    val installed = extensions.filter { it.isInstalled }
    val catalog = extensions.filterNot { it.isInstalled }

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Extension,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Column {
                        Text(
                            text = "افزونه‌ها (Add-ons)",
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            text = "پشتیبانی به سبک فایرفاکس • اسکریپت و استایل وب‌اکستنشن",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { showCreateDialog = true },
                        modifier = Modifier.testTag("add_custom_extension_button"),
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "ساخت افزونه جدید")
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_extensions_button"),
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "بستن")
                    }
                }
            }

            PrimaryTabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("نصب‌شده (${installed.size})") },
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("فروشگاه افزونه‌ها (${catalog.size})") },
                )
            }

            val listToShow = if (selectedTab == 0) installed else catalog

            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f),
            ) {
                items(listToShow, key = { it.id }) { ext ->
                    ExtensionCard(
                        extension = ext,
                        onToggleEnabled = { enabled -> onToggleEnabled(ext.id, enabled) },
                        onInstallToggle = { install -> onSetInstalled(ext.id, install) },
                        onInspect = { inspectingExtension = ext },
                        onDeleteCustom = { onDeleteCustomExtension(ext.id) },
                    )
                }

                item {
                    OutlinedButton(
                        onClick = { showCreateDialog = true },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("افزودن افزونه سفارشی (UserScript / CSS)")
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateExtensionDialog(
            onDismiss = { showCreateDialog = false },
            onSave = { name, desc, match, runAt, js, css ->
                onCreateCustomExtension(name, desc, match, runAt, js, css)
                showCreateDialog = false
                selectedTab = 0
            },
        )
    }

    inspectingExtension?.let { ext ->
        AlertDialog(
            onDismissRequest = { inspectingExtension = null },
            title = { Text(ext.name) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("نسخه: ${ext.version} • سازنده: ${ext.author}", style = MaterialTheme.typography.labelMedium)
                    Text("الگوی آدرس: ${ext.matchPattern}", style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily))
                    Text("زمان اجرا: ${ext.runAt}", style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily))
                    Text("دسترسی‌ها: ${ext.permissions}", style = MaterialTheme.typography.labelSmall)
                    if (ext.cssCode.isNotBlank()) {
                        Text("CSS:", style = MaterialTheme.typography.labelMedium)
                        Text(ext.cssCode, style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily))
                    }
                    if (ext.jsCode.isNotBlank()) {
                        Text("JavaScript:", style = MaterialTheme.typography.labelMedium)
                        Text(ext.jsCode, style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { inspectingExtension = null }) {
                    Text("بستن")
                }
            },
        )
    }
}

@Composable
private fun ExtensionCard(
    extension: ExtensionEntity,
    onToggleEnabled: (Boolean) -> Unit,
    onInstallToggle: (Boolean) -> Unit,
    onInspect: () -> Unit,
    onDeleteCustom: () -> Unit,
) {
    val icon: ImageVector = when (extension.iconKey) {
        "dark_mode" -> Icons.Default.DarkMode
        "shield" -> Icons.Default.Shield
        "fast_forward" -> Icons.Default.FastForward
        "content_copy" -> Icons.Default.ContentCopy
        "speed" -> Icons.Default.Speed
        "code" -> Icons.Default.Code
        else -> Icons.Default.Extension
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                if (extension.isEnabled && extension.isInstalled) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                } else {
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                },
                RoundedCornerShape(18.dp),
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = extension.name,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = extension.name,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "v${extension.version} • ${extension.author}",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (extension.isInstalled) {
                    Switch(
                        checked = extension.isEnabled,
                        onCheckedChange = onToggleEnabled,
                        modifier = Modifier.testTag("switch_ext_${extension.id}"),
                    )
                } else {
                    Button(
                        onClick = { onInstallToggle(true) },
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text("نصب")
                    }
                }
            }

            Text(
                text = extension.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "الگو: ${extension.matchPattern}",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                    color = MaterialTheme.colorScheme.primary,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = onInspect) {
                        Text("مشاهده کد")
                    }
                    if (extension.isInstalled) {
                        if (extension.isBuiltIn) {
                            TextButton(onClick = { onInstallToggle(false) }) {
                                Text("حذف نصب", color = MaterialTheme.colorScheme.error)
                            }
                        } else {
                            IconButton(onClick = onDeleteCustom) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "حذف افزونه",
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CreateExtensionDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, String, String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var matchPattern by remember { mutableStateOf("*://*/*") }
    var runAt by remember { mutableStateOf("document_end") }
    var jsCode by remember { mutableStateOf("") }
    var cssCode by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ساخت افزونه سفارشی (WebExtension)") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("نام افزونه") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("توضیحات") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = matchPattern,
                    onValueChange = { matchPattern = it },
                    label = { Text("الگوی آدرس (مثلاً *://*/*)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = runAt == "document_end",
                        onClick = { runAt = "document_end" },
                        label = { Text("document_end") },
                    )
                    FilterChip(
                        selected = runAt == "document_start",
                        onClick = { runAt = "document_start" },
                        label = { Text("document_start") },
                    )
                }
                OutlinedTextField(
                    value = jsCode,
                    onValueChange = { jsCode = it },
                    label = { Text("کد JavaScript") },
                    minLines = 3,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = cssCode,
                    onValueChange = { cssCode = it },
                    label = { Text("کد CSS (اختیاری)") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name, description, matchPattern, runAt, jsCode, cssCode) },
                enabled = name.isNotBlank() && (jsCode.isNotBlank() || cssCode.isNotBlank()),
            ) {
                Text("نصب و فعال‌سازی")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("انصراف") }
        },
    )
}
