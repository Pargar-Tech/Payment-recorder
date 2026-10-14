@file:OptIn(ExperimentalMaterial3Api::class)

package ir.pargartech.paymentrecorder.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.pargartech.paymentrecorder.core.money.Money
import ir.pargartech.paymentrecorder.core.parser.Banks
import ir.pargartech.paymentrecorder.core.parser.TxType

/** Shows one transaction; amounts are read-only, notes/payer/category/hidden are editable. */
@Composable
fun TransactionScreen(vm: MainViewModel, id: String, onBack: () -> Unit) {
    val flow = remember(id) { vm.transaction(id) }
    val row by flow.collectAsState(initial = null)
    val categories by vm.categories.collectAsState()

    var note by remember(id) { mutableStateOf("") }
    var payer by remember(id) { mutableStateOf("") }
    var categoryId by remember(id) { mutableStateOf<String?>(null) }
    var hidden by remember(id) { mutableStateOf(false) }
    var loaded by remember(id) { mutableStateOf(false) }
    LaunchedEffect(row != null) {
        val tx = row?.tx
        if (tx != null && !loaded) {
            note = tx.note
            payer = tx.payerName
            categoryId = tx.categoryId
            hidden = tx.hidden
            loaded = true
        }
    }
    val current = row ?: return

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("جزئیات تراکنش") },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "بازگشت") }
            },
        )
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val tx = current.tx
            val deposit = tx.type == TxType.DEPOSIT
            Text(
                (if (deposit) "واریز " else "برداشت ") + Money.formatToman(tx.amountRial) + " تومان",
                style = MaterialTheme.typography.headlineSmall,
                color = if (deposit) DepositColor else WithdrawalColor,
                fontWeight = FontWeight.Bold,
            )
            Text("${current.accountName} · ${Banks.displayName(current.bankId)} · ${current.scope.label()}")
            Text(formatDateTime(tx.occurredAt))
            tx.balanceRial?.let { Text("مانده: ${Money.formatToman(it)} تومان") }

            OutlinedTextField(payer, { payer = it }, label = { Text("پرداخت‌کننده / طرف حساب") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(note, { note = it }, label = { Text("یادداشت") }, modifier = Modifier.fillMaxWidth())
            Picker(
                label = "دسته",
                selected = categories.firstOrNull { it.id == categoryId },
                options = categories,
                optionLabel = { it.name },
                nullLabel = "بدون دسته",
                onSelect = { categoryId = it?.id },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = hidden, onCheckedChange = { hidden = it })
                Text("  پنهان کردن (در جمع‌ها حساب نمی‌شود)")
            }
            Button(
                onClick = {
                    vm.updateTransaction(tx.id, note.trim(), payer.trim(), categoryId, hidden)
                    onBack()
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("ذخیره") }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("متن پیامک (${tx.smsSender})", style = MaterialTheme.typography.labelMedium)
                    Text(tx.smsBody, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
