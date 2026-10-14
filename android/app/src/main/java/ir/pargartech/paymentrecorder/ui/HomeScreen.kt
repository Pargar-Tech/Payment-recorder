@file:OptIn(ExperimentalMaterial3Api::class)

package ir.pargartech.paymentrecorder.ui

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.pargartech.paymentrecorder.core.export.CsvExporter
import ir.pargartech.paymentrecorder.core.export.ExportRow
import ir.pargartech.paymentrecorder.core.money.Money
import ir.pargartech.paymentrecorder.core.parser.Banks
import ir.pargartech.paymentrecorder.core.parser.TextNormalizer
import ir.pargartech.paymentrecorder.core.parser.TxType
import ir.pargartech.paymentrecorder.data.Scope
import ir.pargartech.paymentrecorder.data.TxRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun HomeScreen(
    vm: MainViewModel,
    hasSmsPermission: Boolean,
    onRequestPermission: () -> Unit,
    onOpenTransaction: (String) -> Unit,
) {
    val month by vm.month.collectAsState()
    val rows by vm.rows.collectAsState()
    val filter by vm.filter.collectAsState()
    val accounts by vm.accounts.collectAsState()
    val categories by vm.categories.collectAsState()
    val scan by vm.scan.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) scope.launch { exportCsv(context, uri, rows) }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    // Auto-mirrored arrows: in RTL "back" points right and sits on the right.
                    IconButton(onClick = vm::previousMonth) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "ماه قبل")
                    }
                    Text(month.label())
                    IconButton(onClick = vm::nextMonth) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "ماه بعد")
                    }
                }
            },
            actions = {
                IconButton(onClick = { exportLauncher.launch("transactions-${month.year}-${month.month}.csv") }) {
                    Icon(Icons.Filled.Share, contentDescription = "خروجی اکسل")
                }
            },
        )

        LazyColumn(Modifier.fillMaxSize()) {
            if (!hasSmsPermission) {
                item { PermissionCard(onRequestPermission) }
            } else if (!vm.initialScanDone && scan is ScanState.Idle) {
                item { ScanPrompt(onScan = vm::scanInbox) }
            }
            if (scan !is ScanState.Idle) {
                item { ScanCard(scan, vm::dismissScanResult) }
            }
            item { SummaryCard(rows) }
            item {
                Filters(
                    filter = filter,
                    accounts = accounts,
                    categories = categories,
                    onChange = vm::setFilter,
                )
            }
            if (rows.isEmpty()) {
                item { EmptyState("تراکنشی در این ماه نیست.") }
            }
            items(rows, key = { it.tx.id }) { row ->
                TxListItem(row, onClick = { onOpenTransaction(row.tx.id) })
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun PermissionCard(onRequest: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("دسترسی به پیامک لازم است", fontWeight = FontWeight.Bold)
            Text(
                "برنامه پیامک‌های بانکی را می‌خواند تا تراکنش‌ها را ثبت کند. هیچ داده‌ای از گوشی خارج نمی‌شود؛ " +
                    "برنامه اصلاً دسترسی اینترنت ندارد.",
            )
            Text(
                "اگر دکمه مجوز کار نکرد (اندروید ۱۳ به بالا): تنظیمات › برنامه‌ها › تراکنش‌های من › منوی سه‌نقطه › " +
                    "Allow restricted settings",
                style = MaterialTheme.typography.bodySmall,
            )
            Button(onClick = onRequest) { Text("اجازه دسترسی") }
        }
    }
}

private val SCAN_OPTIONS = listOf(1 to "۱ ماه", 3 to "۳ ماه", 12 to "۱۲ ماه", null to "همه")

@Composable
fun ScanPrompt(onScan: (Int?) -> Unit) {
    Card(Modifier.fillMaxWidth().padding(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("اسکن پیامک‌های قبلی", fontWeight = FontWeight.Bold)
            Text("تا چه مدت قبل؟")
            SCAN_OPTIONS.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pair.forEach { (months, label) ->
                        Button(onClick = { onScan(months) }) { Text(label) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScanCard(scan: ScanState, onDismiss: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (scan) {
                is ScanState.Running -> {
                    Text(fa("در حال اسکن… ${scan.done} از ${scan.total}"))
                    if (scan.total > 0) {
                        LinearProgressIndicator(
                            progress = { scan.done.toFloat() / scan.total },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                }
                is ScanState.Finished -> {
                    val s = scan.summary
                    Text(fa("${s.read} پیامک خوانده شد: ${s.recorded} تراکنش ثبت شد، ${s.needsReview} مورد نیاز به بررسی."))
                    TextButton(onClick = onDismiss) { Text("باشه") }
                }
                is ScanState.Failed -> {
                    Text(scan.message, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = onDismiss) { Text("باشه") }
                }
                ScanState.Idle -> Unit
            }
        }
    }
}

@Composable
private fun SummaryCard(rows: List<TxRow>) {
    val deposits = rows.filter { it.tx.type == TxType.DEPOSIT }.sumOf { it.tx.amountRial }
    val withdrawals = rows.filter { it.tx.type == TxType.WITHDRAWAL }.sumOf { it.tx.amountRial }
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            SummaryValue("واریز", deposits, DepositColor)
            SummaryValue("برداشت", withdrawals, WithdrawalColor)
            SummaryValue("خالص", deposits - withdrawals, MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}

@Composable
private fun SummaryValue(label: String, rial: Long, color: androidx.compose.ui.graphics.Color) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(Money.formatToman(rial), color = color, fontWeight = FontWeight.Bold)
        Text("تومان", style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun Filters(
    filter: ir.pargartech.paymentrecorder.data.TxFilter,
    accounts: List<ir.pargartech.paymentrecorder.data.AccountEntity>,
    categories: List<ir.pargartech.paymentrecorder.data.CategoryEntity>,
    onChange: (ir.pargartech.paymentrecorder.data.TxFilter) -> Unit,
) {
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(null, Scope.PERSONAL, Scope.BUSINESS).forEach { s ->
                FilterChip(
                    selected = filter.scope == s,
                    onClick = { onChange(filter.copy(scope = s)) },
                    label = { Text(s?.label() ?: "همه") },
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Picker(
                label = "حساب",
                selected = accounts.firstOrNull { it.id == filter.accountId },
                options = accounts,
                optionLabel = { it.displayName },
                nullLabel = "همه",
                onSelect = { onChange(filter.copy(accountId = it?.id)) },
                modifier = Modifier.weight(1f),
            )
            Picker(
                label = "دسته",
                selected = categories.firstOrNull { it.id == filter.categoryId },
                options = categories,
                optionLabel = { it.name },
                nullLabel = "همه",
                onSelect = { onChange(filter.copy(categoryId = it?.id)) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun TxListItem(row: TxRow, onClick: () -> Unit) {
    val deposit = row.tx.type == TxType.DEPOSIT
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = {
            Text(
                (if (deposit) "+" else "−") + Money.formatToman(row.tx.amountRial) + " تومان",
                color = if (deposit) DepositColor else WithdrawalColor,
                fontWeight = FontWeight.Bold,
            )
        },
        supportingContent = {
            val extra = listOfNotNull(
                row.categoryName,
                row.tx.payerName.ifBlank { null },
                row.tx.note.ifBlank { null },
            ).joinToString(" · ")
            Text(listOf(row.accountName, extra).filter { it.isNotEmpty() }.joinToString(" — "))
        },
        trailingContent = { Text(formatDateTime(row.tx.occurredAt), style = MaterialTheme.typography.labelSmall) },
    )
}

private fun fa(s: String) = TextNormalizer.toPersianDigits(s)

private suspend fun exportCsv(context: Context, uri: Uri, rows: List<TxRow>) {
    val csv = CsvExporter.export(
        rows.sortedBy { it.tx.occurredAt }.map { r ->
            ExportRow(
                occurredAt = r.tx.occurredAt,
                bankName = Banks.displayName(r.bankId),
                accountName = r.accountName,
                scopeLabel = r.scope.label(),
                type = r.tx.type,
                amountRial = r.tx.amountRial,
                balanceRial = r.tx.balanceRial,
                category = r.categoryName,
                payerName = r.tx.payerName,
                note = r.tx.note,
            )
        },
    )
    val ok = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(csv.toByteArray(Charsets.UTF_8)) }
        }.isSuccess
    }
    Toast.makeText(context, if (ok) "فایل ذخیره شد" else "ذخیره فایل ناموفق بود", Toast.LENGTH_SHORT).show()
}
