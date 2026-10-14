@file:OptIn(ExperimentalMaterial3Api::class)

package ir.pargartech.paymentrecorder.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.pargartech.paymentrecorder.core.parser.Banks
import ir.pargartech.paymentrecorder.data.AccountEntity
import ir.pargartech.paymentrecorder.data.Scope

/** Accounts are created automatically from SMS; here the user names them and tags personal/business. */
@Composable
fun AccountsScreen(vm: MainViewModel) {
    val accounts by vm.accounts.collectAsState()
    var editing by remember { mutableStateOf<AccountEntity?>(null) }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("حساب‌ها") })
        if (accounts.isEmpty()) EmptyState("حسابی هنوز ثبت نشده. با اولین پیامک بانکی، حساب خودکار ساخته می‌شود.")
        LazyColumn {
            items(accounts, key = { it.id }) { account ->
                ListItem(
                    modifier = Modifier.clickable { editing = account },
                    headlineContent = { Text(account.displayName) },
                    supportingContent = {
                        val key = if (account.accountKey.isNotEmpty()) " · …${account.accountKey}" else ""
                        Text("${Banks.displayName(account.bankId)}$key")
                    },
                    trailingContent = { Text(account.scope.label()) },
                )
                HorizontalDivider()
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
