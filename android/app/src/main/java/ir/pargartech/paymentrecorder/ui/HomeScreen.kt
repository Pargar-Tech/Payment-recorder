package ir.pargartech.paymentrecorder.ui

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.pargartech.paymentrecorder.core.date.JalaliDate
import ir.pargartech.paymentrecorder.core.export.CsvExporter
import ir.pargartech.paymentrecorder.core.export.ExportRow
import ir.pargartech.paymentrecorder.core.money.Money
import ir.pargartech.paymentrecorder.core.parser.Banks
import ir.pargartech.paymentrecorder.core.parser.TxType
import ir.pargartech.paymentrecorder.data.AccountEntity
import ir.pargartech.paymentrecorder.data.CategoryEntity
import ir.pargartech.paymentrecorder.data.Scope
import ir.pargartech.paymentrecorder.data.TxFilter
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

    LightStatusBarIcons()

    val visible = rows.filterNot { it.tx.hidden }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) scope.launch { exportCsv(context, uri, visible) }
    }
    // Rows come newest first; group them under a header per day.
    val days = rows.groupBy { JalaliDate.fromEpochMillis(it.tx.occurredAt) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            HeroCard(
                monthLabel = month.label(),
                rows = visible,
                onPrevious = vm::previousMonth,
                onNext = vm::nextMonth,
                onExport = { exportLauncher.launch("transactions-${month.year}-${month.month}.csv") },
            )
        }
        if (!hasSmsPermission) {
            item { PermissionCard(onRequestPermission) }
        } else if (!vm.initialScanDone && scan is ScanState.Idle) {
            item { Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) { ScanPrompt(onScan = vm::scanInbox) } }
        }
        if (scan !is ScanState.Idle) {
            item { Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) { ScanCard(scan, vm::dismissScanResult) } }
        }
        item { Filters(filter, accounts, categories, vm::setFilter) }
        if (rows.isEmpty()) {
            item { EmptyState("تراکنشی در این ماه نیست.", Icons.Filled.Email) }
        }
        days.forEach { (day, dayRows) ->
            item(key = "day-${day.format()}") { DayHeader(dayRows) }
            items(dayRows, key = { it.tx.id }) { row ->
                TxListItem(row, onClick = { onOpenTransaction(row.tx.id) })
            }
        }
    }
}

@Composable
private fun HeroCard(
    monthLabel: String,
    rows: List<TxRow>,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onExport: () -> Unit,
) {
    val deposits = rows.filter { it.tx.type == TxType.DEPOSIT }.sumOf { it.tx.amountRial }
    val withdrawals = rows.filter { it.tx.type == TxType.WITHDRAWAL }.sumOf { it.tx.amountRial }
    val net = deposits - withdrawals
    val onHero = AppTheme.colors.onHero
    val heroIcon = IconButtonDefaults.iconButtonColors(contentColor = onHero)

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
            .background(AppTheme.colors.hero)
            .statusBarsPadding()
            .padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 22.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Auto-mirrored arrows: in RTL "back" points right and sits on the right.
            IconButton(onClick = onPrevious, colors = heroIcon) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "ماه قبل")
            }
            Text(monthLabel, color = onHero, style = MaterialTheme.typography.titleLarge)
            IconButton(onClick = onNext, colors = heroIcon) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "ماه بعد")
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onExport, colors = heroIcon) {
                Icon(Icons.Filled.Share, contentDescription = "خروجی اکسل")
            }
        }
        Column(Modifier.padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(8.dp))
            Text("خالص این ماه", color = onHero.copy(alpha = 0.75f), style = MaterialTheme.typography.labelLarge)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    (if (net < 0) "−" else if (net > 0) "+" else "") + Money.formatToman(kotlin.math.abs(net)),
                    color = onHero,
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                )
                Text(" تومان", color = onHero.copy(alpha = 0.8f), style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 6.dp))
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HeroStat("واریز", deposits, Icons.Filled.KeyboardArrowDown, Modifier.weight(1f))
                HeroStat("برداشت", withdrawals, Icons.Filled.KeyboardArrowUp, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun HeroStat(label: String, rial: Long, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier) {
    val onHero = AppTheme.colors.onHero
    Row(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.14f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(32.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = onHero, modifier = Modifier.size(18.dp))
        }
        Column {
            Text(label, color = onHero.copy(alpha = 0.75f), style = MaterialTheme.typography.labelMedium)
            Text(Money.formatToman(rial), color = onHero, style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun PermissionCard(onRequest: () -> Unit) {
    SectionCard(
        Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        title = "دسترسی به پیامک لازم است",
        icon = Icons.Filled.Email,
        container = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
    ) {
        Text(
            "برنامه پیامک‌های بانکی را می‌خواند تا تراکنش‌ها را ثبت کند. هیچ داده‌ای از گوشی خارج نمی‌شود؛ " +
                "برنامه اصلاً دسترسی اینترنت ندارد.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            "اگر دکمه مجوز کار نکرد (اندروید ۱۳ به بالا): تنظیمات › برنامه‌ها › تراکنش‌های من › منوی سه‌نقطه › " +
                "Allow restricted settings",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onRequest, modifier = Modifier.fillMaxWidth()) { Text("اجازه دسترسی") }
    }
}

private val SCAN_OPTIONS = listOf(1 to "۱ ماه", 3 to "۳ ماه", 12 to "۱۲ ماه", null to "همه")

@Composable
fun ScanPrompt(onScan: (Int?) -> Unit) {
    SectionCard(title = "اسکن پیامک‌های قبلی", icon = Icons.Filled.Refresh) {
        Text("تا چه مدت قبل؟", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SCAN_OPTIONS.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { (months, label) ->
                    FilledTonalButton(onClick = { onScan(months) }, modifier = Modifier.weight(1f)) { Text(label) }
                }
            }
        }
    }
}

@Composable
fun ScanCard(scan: ScanState, onDismiss: () -> Unit) {
    SectionCard(icon = null) {
        when (scan) {
            is ScanState.Running -> {
                Text(fa("در حال اسکن… ${scan.done} از ${scan.total}"), style = MaterialTheme.typography.bodyMedium)
                if (scan.total > 0) {
                    LinearProgressIndicator(
                        progress = { scan.done.toFloat() / scan.total },
                        modifier = Modifier.fillMaxWidth().clip(CircleShape),
                    )
                } else {
                    LinearProgressIndicator(Modifier.fillMaxWidth().clip(CircleShape))
                }
            }
            is ScanState.Finished -> {
                val s = scan.summary
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    IconBubble(Icons.Filled.Refresh)
                    Text(
                        fa("${s.read} پیامک خوانده شد: ${s.recorded} تراکنش ثبت شد، ${s.needsReview} مورد نیاز به بررسی."),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                }
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("باشه") }
            }
            is ScanState.Failed -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    IconBubble(Icons.Filled.Warning, MaterialTheme.colorScheme.error, MaterialTheme.colorScheme.errorContainer)
                    Text(scan.message, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
                }
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("باشه") }
            }
            ScanState.Idle -> Unit
        }
    }
}

@Composable
private fun Filters(
    filter: TxFilter,
    accounts: List<AccountEntity>,
    categories: List<CategoryEntity>,
    onChange: (TxFilter) -> Unit,
) {
    Column(Modifier.padding(top = 14.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val chipColors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primary,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
            )
            listOf(null, Scope.PERSONAL, Scope.BUSINESS).forEach { s ->
                FilterChip(
                    selected = filter.scope == s,
                    onClick = { onChange(filter.copy(scope = s)) },
                    label = { Text(s?.label() ?: "همه") },
                    colors = chipColors,
                    shape = CircleShape,
                )
            }
            FilterChip(
                selected = filter.showHidden,
                onClick = { onChange(filter.copy(showHidden = !filter.showHidden)) },
                label = { Text("پنهان‌ها") },
                colors = chipColors,
                shape = CircleShape,
            )
        }
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
private fun DayHeader(dayRows: List<TxRow>) {
    val net = dayRows.filterNot { it.tx.hidden }
        .sumOf { if (it.tx.type == TxType.DEPOSIT) it.tx.amountRial else -it.tx.amountRial }
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            formatDay(dayRows.first().tx.occurredAt),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            (if (net < 0) "−" else "+") + Money.formatToman(kotlin.math.abs(net)),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TxListItem(row: TxRow, onClick: () -> Unit) {
    val tx = row.tx
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BankAvatar(row.bankId)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(row.accountName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val extra = listOfNotNull(
                row.categoryName,
                tx.payerName.ifBlank { null },
                tx.note.ifBlank { null },
            )
            Text(
                (listOf(formatTime(tx.occurredAt)) + extra).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                signedToman(tx.type, tx.amountRial),
                style = MoneyStyle,
                color = if (tx.hidden) MaterialTheme.colorScheme.outline else tx.type.color(),
                maxLines = 1,
            )
            if (tx.hidden) {
                Text("پنهان", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

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

/** The hero is dark in both themes, so the status bar icons over it must be light while Home is shown. */
@Composable
private fun LightStatusBarIcons() {
    val view = LocalView.current
    if (view.isInEditMode) return
    DisposableEffect(view) {
        val window = (view.context as Activity).window
        val controller = WindowCompat.getInsetsController(window, view)
        val previous = controller.isAppearanceLightStatusBars
        controller.isAppearanceLightStatusBars = false
        onDispose { controller.isAppearanceLightStatusBars = previous }
    }
}
