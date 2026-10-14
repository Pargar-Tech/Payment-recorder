@file:OptIn(ExperimentalMaterial3Api::class)

package ir.pargartech.paymentrecorder.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import ir.pargartech.paymentrecorder.core.money.Money
import ir.pargartech.paymentrecorder.core.parser.ParseResult
import ir.pargartech.paymentrecorder.core.parser.SmsMasker
import ir.pargartech.paymentrecorder.core.parser.SmsParser
import ir.pargartech.paymentrecorder.core.parser.TxType
import ir.pargartech.paymentrecorder.data.AccountEntity
import ir.pargartech.paymentrecorder.data.ReviewItemEntity
import ir.pargartech.paymentrecorder.data.SenderTrust

/** Bank-looking SMS the parser couldn't read: record by hand, dismiss, or teach the app about the sender. */
@Composable
fun ReviewScreen(vm: MainViewModel) {
    val reviewItems by vm.reviews.collectAsState()
    val accounts by vm.accounts.collectAsState()
    val context = LocalContext.current
    var resolving by remember { mutableStateOf<ReviewItemEntity?>(null) }
    var ignoring by remember { mutableStateOf<ReviewItemEntity?>(null) }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("نیاز به بررسی") })
        if (reviewItems.isEmpty()) EmptyState("موردی برای بررسی نیست.")
        LazyColumn {
            items(reviewItems, key = { it.id }) { item ->
                ReviewCard(
                    item = item,
                    onRecord = { resolving = item },
                    onDismiss = { vm.dismissReview(item) },
                    onIgnoreSender = { ignoring = item },
                    onShare = { shareMasked(context, item) },
                )
            }
        }
    }

    ignoring?.let { item ->
        // Ignoring drops every future SMS from this sender, so make it a deliberate choice.
        AlertDialog(
            onDismissRequest = { ignoring = null },
            title = { Text("نادیده‌گرفتن فرستنده") },
            text = {
                Text(
                    "از این به بعد هیچ پیامکی از «${item.sender}» ثبت یا بررسی نمی‌شود. " +
                        "اگر این فرستنده بانک شماست، به‌جای آن «رد» را بزنید. " +
                        "بعداً می‌توانید آن را از تنظیمات › فرستنده‌ها برگردانید.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.setSenderTrust(item.sender, SenderTrust.IGNORED)
                    ignoring = null
                }) { Text("نادیده بگیر", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { ignoring = null }) { Text("انصراف") } },
        )
    }

    resolving?.let { item ->
        ResolveDialog(
            item = item,
            accounts = accounts,
            onDismiss = { resolving = null },
            onSave = { type, rial, accountId, trust ->
                vm.resolveReview(item, type, rial, accountId, trust)
                resolving = null
            },
        )
    }
}

@Composable
private fun ReviewCard(
    item: ReviewItemEntity,
    onRecord: () -> Unit,
    onDismiss: () -> Unit,
    onIgnoreSender: () -> Unit,
    onShare: () -> Unit,
) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("${item.sender} · ${formatDateTime(item.receivedAt)}", style = MaterialTheme.typography.labelMedium)
            Text(reasonLabel(item.reason), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            Text(item.body, style = MaterialTheme.typography.bodyMedium)
            Row {
                TextButton(onClick = onRecord) { Text("ثبت دستی") }
                TextButton(onClick = onDismiss) { Text("رد") }
            }
            Row {
                TextButton(onClick = onIgnoreSender) { Text("نادیده‌گرفتن فرستنده") }
                TextButton(onClick = onShare) { Text("ارسال نمونه") }
            }
        }
    }
}

private fun reasonLabel(reason: String) = when (reason) {
    "UNKNOWN_SENDER" -> "فرستنده ناشناخته"
    "NO_TYPE" -> "نوع (واریز/برداشت) مشخص نیست"
    "NO_AMOUNT" -> "مبلغ پیدا نشد"
    else -> reason
}

@Composable
private fun ResolveDialog(
    item: ReviewItemEntity,
    accounts: List<AccountEntity>,
    onDismiss: () -> Unit,
    onSave: (TxType, Long, String?, Boolean) -> Unit,
) {
    // Pre-fill whatever the parser managed to read.
    val guess = remember(item.id) { SmsParser().parse(item.sender, item.body) as? ParseResult.NeedsReview }
    var type by remember { mutableStateOf(guess?.type) }
    var amount by remember { mutableStateOf(guess?.amountRial?.let { Money.formatToman(it, persianDigits = false) } ?: "") }
    var account by remember { mutableStateOf<AccountEntity?>(null) }
    var trust by remember { mutableStateOf(item.reason == "UNKNOWN_SENDER") }
    val rial = Money.parseTomanInput(amount)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ثبت دستی") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = type == TxType.DEPOSIT, onClick = { type = TxType.DEPOSIT }, label = { Text("واریز") })
                    FilterChip(selected = type == TxType.WITHDRAWAL, onClick = { type = TxType.WITHDRAWAL }, label = { Text("برداشت") })
                }
                OutlinedTextField(
                    amount, { amount = it },
                    label = { Text("مبلغ (تومان)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = amount.isNotEmpty() && rial == null,
                    modifier = Modifier.fillMaxWidth(),
                )
                Picker(
                    label = "حساب",
                    selected = account,
                    options = accounts,
                    optionLabel = { it.displayName },
                    nullLabel = "خودکار از پیامک",
                    onSelect = { account = it },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = trust, onCheckedChange = { trust = it })
                    Text("این فرستنده بانک است؛ پیامک‌های بعدی خودکار ثبت شوند")
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = type != null && rial != null,
                onClick = { onSave(type!!, rial!!, account?.id, trust) },
            ) { Text("ثبت") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}

/** Shares a masked copy so the parser can be taught this bank's format. */
private fun shareMasked(context: Context, item: ReviewItemEntity) {
    val text = "نمونه پیامک بانکی (ارقام پوشانده شده؛ قبل از ارسال نام‌ها را بررسی کنید)\n" +
        "فرستنده: ${item.sender}\n\n${SmsMasker.mask(item.body)}"
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(send, "ارسال نمونه پیامک"))
}
