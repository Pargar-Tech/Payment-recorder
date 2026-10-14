@file:OptIn(ExperimentalMaterial3Api::class)

package ir.pargartech.paymentrecorder.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import ir.pargartech.paymentrecorder.core.parser.Banks
import ir.pargartech.paymentrecorder.data.AccountEntity
import ir.pargartech.paymentrecorder.data.Scope

/** Accounts are created automatically from SMS; here the user names them and tags personal/business. */
@Composable
fun AccountsScreen(vm: MainViewModel) {
    val accounts by vm.accounts.collectAsState()
    var editing by remember { mutableStateOf<AccountEntity?>(null) }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        ScreenTitle("حساب‌ها", fa("${accounts.size} حساب · برای نام‌گذاری و شخصی/کاری بزنید"))
        if (accounts.isEmpty()) {
            EmptyState("حسابی هنوز ثبت نشده. با اولین پیامک بانکی، حساب خودکار ساخته می‌شود.", Icons.Filled.Person)
        }
        LazyColumn(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(accounts, key = { it.id }) { account ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .clickable { editing = account }
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    BankAvatar(account.bankId)
                    Column(Modifier.weight(1f)) {
                        Text(account.displayName, style = MaterialTheme.typography.titleSmall)
                        val key = if (account.accountKey.isNotEmpty()) " · …${account.accountKey}" else ""
                        Text(
                            "${Banks.displayName(account.bankId)}$key",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    ScopeBadge(account.scope)
                }
            }
        }
    }

    editing?.let { account ->
        EditAccountDialog(
            account = account,
            onDismiss = { editing = null },
            onSave = { name, scope ->
                vm.updateAccount(account.id, name, scope)
                editing = null
            },
        )
    }
}

@Composable
private fun EditAccountDialog(account: AccountEntity, onDismiss: () -> Unit, onSave: (String, Scope) -> Unit) {
    var name by remember { mutableStateOf(account.displayName) }
    var scope by remember { mutableStateOf(account.scope) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ویرایش حساب") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("نام (مثلاً ملت – مغازه)") }, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(Scope.PERSONAL, Scope.BUSINESS).forEach { s ->
                        FilterChip(selected = scope == s, onClick = { scope = s }, label = { Text(s.label()) })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name.trim().ifEmpty { account.displayName }, scope) }) { Text("ذخیره") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}
