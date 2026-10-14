@file:OptIn(ExperimentalMaterial3Api::class)

package ir.pargartech.paymentrecorder.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ir.pargartech.paymentrecorder.backup.BackupManager
import ir.pargartech.paymentrecorder.core.date.JalaliDate
import ir.pargartech.paymentrecorder.core.parser.TxType
import ir.pargartech.paymentrecorder.data.AccountEntity
import ir.pargartech.paymentrecorder.data.CategoryEntity
import ir.pargartech.paymentrecorder.data.SenderTrust
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    vm: MainViewModel,
    lockEnabled: Boolean,
    lockAvailable: Boolean,
    onLockChange: (Boolean) -> Unit,
) {
    val categories by vm.categories.collectAsState()
    val rules by vm.rules.collectAsState()
    val accounts by vm.accounts.collectAsState()
    val senders by vm.senders.collectAsState()
    val scan by vm.scan.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var newCategory by remember { mutableStateOf("") }
    var addingRule by remember { mutableStateOf(false) }
    var rescanOpen by remember { mutableStateOf(false) }
    var confirmRestore by remember { mutableStateOf(false) }

    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) {
            scope.launch {
                val ok = runCatching { BackupManager.export(context, uri) }.isSuccess
                Toast.makeText(context, if (ok) "پشتیبان ذخیره شد" else "پشتیبان‌گیری ناموفق بود", Toast.LENGTH_SHORT).show()
            }
        }
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val error = runCatching { BackupManager.restore(context, uri) }.getOrElse { "بازیابی ناموفق بود" }
                if (error != null) Toast.makeText(context, error, Toast.LENGTH_LONG).show()
            }
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        ScreenTitle("تنظیمات")
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionCard(title = "امنیت", icon = Icons.Filled.Lock) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("قفل با اثر انگشت / رمز گوشی", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            if (lockAvailable) "پس از ۳۰ ثانیه در پس‌زمینه دوباره قفل می‌شود"
                            else "برای فعال‌کردن قفل، ابتدا قفل صفحه گوشی را تنظیم کنید.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = lockEnabled, enabled = lockAvailable, onCheckedChange = onLockChange)
                }
            }

            SectionCard(title = "پشتیبان", icon = Icons.Filled.Done) {
                Text(
                    "فایل پشتیبان شامل همه تراکنش‌ها و یادداشت‌هاست و رمزنگاری نشده؛ آن را جای امن نگه دارید.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(modifier = Modifier.weight(1f), onClick = {
                        val today = JalaliDate.fromEpochMillis(System.currentTimeMillis()).format().replace('/', '-')
                        backupLauncher.launch("payment-recorder-$today.db")
                    }) { Text("تهیه پشتیبان") }
                    OutlinedButton(modifier = Modifier.weight(1f), onClick = { confirmRestore = true }) { Text("بازیابی") }
                }
            }

            SectionCard(title = "پیامک‌ها", icon = Icons.Filled.Email) {
                OutlinedButton(
                    enabled = scan !is ScanState.Running,
                    onClick = { rescanOpen = true },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("اسکن دوباره صندوق پیامک") }
                if (scan !is ScanState.Idle) ScanCard(scan, vm::dismissScanResult)
            }

            if (senders.isNotEmpty()) {
                SectionCard(title = "فرستنده‌ها", icon = Icons.Filled.Person) {
                    Text(
                        "فرستنده‌هایی که بانک دانسته یا نادیده گرفته‌اید. با حذف، پیامک‌های بعدی این فرستنده دوباره عادی بررسی می‌شوند.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    senders.forEach { sender ->
                        SettingRow(
                            title = sender.sender,
                            subtitle = if (sender.trust == SenderTrust.TRUSTED) "بانک — خودکار ثبت می‌شود" else "نادیده گرفته می‌شود",
                            onDelete = { vm.forgetSender(sender.sender) },
                        )
                    }
                }
            }

            SectionCard(title = "دسته‌ها", icon = Icons.Filled.List) {
                if (categories.isNotEmpty()) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        categories.forEach {
                            Text(
                                it.name,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer)
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                            )
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        newCategory, { newCategory = it }, label = { Text("دسته جدید") },
                        modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.small, singleLine = true,
                    )
                    Button(
                        enabled = newCategory.isNotBlank(),
                        onClick = {
                            vm.addCategory(newCategory)
                            newCategory = ""
                        },
                    ) { Text("افزودن") }
                }
            }

            SectionCard(title = "قوانین دسته‌بندی خودکار", icon = Icons.Filled.Build) {
                Text(
                    "قانون‌ها به ترتیب بررسی می‌شوند و اولین قانون منطبق، دسته تراکنش جدید را تعیین می‌کند.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                rules.forEach { rule ->
                    val category = categories.firstOrNull { it.id == rule.categoryId }?.name ?: "؟"
                    val parts = listOfNotNull(
                        rule.bodyContains?.let { "شامل «$it»" },
                        rule.accountId?.let { id -> accounts.firstOrNull { it.id == id }?.displayName?.let { "حساب $it" } },
                        rule.type?.let { if (it == TxType.DEPOSIT) "واریز" else "برداشت" },
                    )
                    SettingRow(
                        title = "← $category",
                        subtitle = parts.joinToString(" و ").ifEmpty { "همه تراکنش‌ها" },
                        onDelete = { vm.deleteRule(rule.id) },
                    )
                }
                OutlinedButton(
                    enabled = categories.isNotEmpty(),
                    onClick = { addingRule = true },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("افزودن قانون") }
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    if (rescanOpen) {
        AlertDialog(
            onDismissRequest = { rescanOpen = false },
            title = { Text("اسکن دوباره") },
            text = { ScanPrompt(onScan = { vm.scanInbox(it); rescanOpen = false }) },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { rescanOpen = false }) { Text("انصراف") } },
        )
    }

    if (confirmRestore) {
        AlertDialog(
            onDismissRequest = { confirmRestore = false },
            title = { Text("بازیابی پشتیبان") },
            text = { Text("همه داده‌های فعلی با محتوای فایل پشتیبان جایگزین می‌شود و برنامه دوباره باز می‌شود.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmRestore = false
                    restoreLauncher.launch(arrayOf("*/*"))
                }) { Text("انتخاب فایل") }
            },
            dismissButton = { TextButton(onClick = { confirmRestore = false }) { Text("انصراف") } },
        )
    }

    if (addingRule) {
        AddRuleDialog(
            categories = categories,
            accounts = accounts,
            onDismiss = { addingRule = false },
            onSave = { categoryId, contains, accountId, type ->
                vm.addRule(categoryId, contains, accountId, type)
                addingRule = false
            },
        )
    }
}

@Composable
private fun SettingRow(title: String, subtitle: String, onDelete: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(start = 14.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = "حذف", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AddRuleDialog(
    categories: List<CategoryEntity>,
    accounts: List<AccountEntity>,
    onDismiss: () -> Unit,
    onSave: (String, String?, String?, TxType?) -> Unit,
) {
    var category by remember { mutableStateOf<CategoryEntity?>(null) }
    var contains by remember { mutableStateOf("") }
    var account by remember { mutableStateOf<AccountEntity?>(null) }
    var type by remember { mutableStateOf<TxType?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("قانون جدید") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Picker("دسته", category, categories, { it.name }, null, { category = it }, Modifier.fillMaxWidth())
                OutlinedTextField(contains, { contains = it }, label = { Text("متن پیامک شامل (اختیاری)") }, modifier = Modifier.fillMaxWidth())
                Picker("حساب", account, accounts, { it.displayName }, "هر حساب", { account = it }, Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = type == null, onClick = { type = null }, label = { Text("هر دو") })
                    FilterChip(selected = type == TxType.DEPOSIT, onClick = { type = TxType.DEPOSIT }, label = { Text("واریز") })
                    FilterChip(selected = type == TxType.WITHDRAWAL, onClick = { type = TxType.WITHDRAWAL }, label = { Text("برداشت") })
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = category != null,
                onClick = { onSave(category!!.id, contains.ifBlank { null }, account?.id, type) },
            ) { Text("ذخیره") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}
