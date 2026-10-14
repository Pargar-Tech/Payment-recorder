@file:OptIn(ExperimentalMaterial3Api::class)

package ir.pargartech.paymentrecorder.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
    val tx = current.tx
    val deposit = tx.type == TxType.DEPOSIT

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("جزئیات تراکنش") },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "بازگشت") }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        )
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Header: tinted by direction.
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.extraLarge)
                    .background(if (deposit) AppTheme.colors.depositContainer else AppTheme.colors.withdrawalContainer)
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                BankAvatar(current.bankId, size = 56)
                Spacer(Modifier.height(4.dp))
                Text(
                    if (deposit) "واریز" else "برداشت",
                    style = MaterialTheme.typography.labelLarge,
                    color = tx.type.color(),
                )
                Text(
                    signedToman(tx.type, tx.amountRial),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    color = tx.type.color(),
                )
                Text(
                    "${current.accountName} · ${Banks.displayName(current.bankId)}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ScopeBadge(current.scope)
                    Text(formatDateTime(tx.occurredAt), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                tx.balanceRial?.let {
                    Text(
                        "مانده: ${Money.formatToman(it)} تومان",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.7f))
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                    )
                }
            }

            SectionCard(title = "یادداشت‌های شما") {
                OutlinedTextField(payer, { payer = it }, label = { Text("پرداخت‌کننده / طرف حساب") },
                    modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.small)
                OutlinedTextField(note, { note = it }, label = { Text("یادداشت") },
                    modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.small)
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
                    Column(Modifier.weight(1f)) {
                        Text("پنهان کردن", style = MaterialTheme.typography.bodyLarge)
                        Text("در جمع‌ها و خروجی حساب نمی‌شود", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = hidden, onCheckedChange = { hidden = it })
                }
            }

            Button(
                onClick = {
                    vm.updateTransaction(tx.id, note.trim(), payer.trim(), categoryId, hidden)
                    onBack()
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("ذخیره") }

            SectionCard(title = "متن پیامک", icon = Icons.Filled.Email) {
                Text(tx.smsSender, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(tx.smsBody, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
