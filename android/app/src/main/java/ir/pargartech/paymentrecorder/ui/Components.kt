package ir.pargartech.paymentrecorder.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.pargartech.paymentrecorder.core.date.JalaliDate
import ir.pargartech.paymentrecorder.core.parser.TextNormalizer
import ir.pargartech.paymentrecorder.data.Scope
import java.time.Instant
import java.time.format.DateTimeFormatter

private val TIME = DateTimeFormatter.ofPattern("HH:mm")

/** "۱۴۰۳/۰۷/۲۲ ۱۰:۱۵" */
fun formatDateTime(millis: Long): String {
    val date = JalaliDate.fromEpochMillis(millis).format()
    val time = TIME.format(Instant.ofEpochMilli(millis).atZone(JalaliDate.TEHRAN))
    return TextNormalizer.toPersianDigits("$date $time")
}

fun Scope.label(): String = when (this) {
    Scope.UNSET -> "تعیین نشده"
    Scope.PERSONAL -> "شخصی"
    Scope.BUSINESS -> "کاری"
}

/** A labelled button that opens a dropdown of [options]; null value = "all"/"none". */
@Composable
fun <T> Picker(
    label: String,
    selected: T?,
    options: List<T>,
    optionLabel: (T) -> String,
    nullLabel: String?,
    onSelect: (T?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Text("$label: ${selected?.let(optionLabel) ?: nullLabel ?: "—"}", maxLines = 1)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (nullLabel != null) {
                DropdownMenuItem(text = { Text(nullLabel) }, onClick = { onSelect(null); open = false })
            }
            options.forEach { option ->
                DropdownMenuItem(text = { Text(optionLabel(option)) }, onClick = { onSelect(option); open = false })
            }
        }
    }
}

@Composable
fun EmptyState(text: String) {
    Column(Modifier.fillMaxWidth().padding(32.dp)) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
