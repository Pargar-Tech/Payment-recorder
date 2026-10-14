package ir.pargartech.paymentrecorder.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.pargartech.paymentrecorder.core.date.JalaliDate
import ir.pargartech.paymentrecorder.core.money.Money
import ir.pargartech.paymentrecorder.core.parser.Banks
import ir.pargartech.paymentrecorder.core.parser.TextNormalizer
import ir.pargartech.paymentrecorder.core.parser.TxType
import ir.pargartech.paymentrecorder.data.Scope
import java.time.Instant
import java.time.format.DateTimeFormatter

private val TIME = DateTimeFormatter.ofPattern("HH:mm")

fun fa(s: String) = TextNormalizer.toPersianDigits(s)

/** "۱۴۰۳/۰۷/۲۲ ۱۰:۱۵" */
fun formatDateTime(millis: Long): String {
    val date = JalaliDate.fromEpochMillis(millis).format()
    return fa("$date ${formatTime(millis)}")
}

/** "۱۰:۱۵" (Tehran time) */
fun formatTime(millis: Long): String = fa(TIME.format(Instant.ofEpochMilli(millis).atZone(JalaliDate.TEHRAN)))

/** "۲۲ مهر" */
fun formatDay(millis: Long): String {
    val d = JalaliDate.fromEpochMillis(millis)
    return fa("${d.day} ${JalaliDate.MONTH_NAMES[d.month - 1]}")
}

fun Scope.label(): String = when (this) {
    Scope.UNSET -> "تعیین نشده"
    Scope.PERSONAL -> "شخصی"
    Scope.BUSINESS -> "کاری"
}

@Composable
fun TxType.color(): Color = if (this == TxType.DEPOSIT) AppTheme.colors.deposit else AppTheme.colors.withdrawal

/** "+۱٬۲۰۰ تومان" / "−۱٬۲۰۰ تومان" */
fun signedToman(type: TxType, rial: Long) = (if (type == TxType.DEPOSIT) "+" else "−") + Money.formatToman(rial) + " تومان"

// Muted, distinct hues; a bank always gets the same one.
private val AVATAR_COLORS = listOf(
    Color(0xFF0B7A66), Color(0xFF3949AB), Color(0xFFC2185B), Color(0xFFEF6C00),
    Color(0xFF00838F), Color(0xFF6D4C41), Color(0xFF7B1FA2), Color(0xFF2E7D32),
    Color(0xFFAD1457), Color(0xFF455A64),
)

/** Round badge with the bank's first letter, coloured per bank. */
@Composable
fun BankAvatar(bankId: String, modifier: Modifier = Modifier, size: Int = 44) {
    val name = Banks.displayName(bankId)
    val color = AVATAR_COLORS[Math.floorMod(bankId.hashCode(), AVATAR_COLORS.size)]
    Box(
        modifier.size(size.dp).clip(CircleShape).background(color.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name.firstOrNull { it.isLetterOrDigit() }?.toString() ?: "؟",
            color = color,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Black,
        )
    }
}

/** Small coloured pill: personal / business / unset. */
@Composable
fun ScopeBadge(scope: Scope) {
    val (bg, fg) = when (scope) {
        Scope.PERSONAL -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        Scope.BUSINESS -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        Scope.UNSET -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        scope.label(),
        style = MaterialTheme.typography.labelMedium,
        color = fg,
        modifier = Modifier.clip(CircleShape).background(bg).padding(horizontal = 10.dp, vertical = 3.dp),
    )
}

/** A rounded white card with an optional title and icon; the building block of every screen. */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    icon: ImageVector? = null,
    container: Color = MaterialTheme.colorScheme.surfaceContainer,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = container,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (title != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (icon != null) IconBubble(icon)
                    Text(title, style = MaterialTheme.typography.titleMedium)
                }
            }
            content()
        }
    }
}

@Composable
fun IconBubble(
    icon: ImageVector,
    tint: Color = MaterialTheme.colorScheme.primary,
    background: Color = MaterialTheme.colorScheme.primaryContainer,
    size: Int = 36,
) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(background), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size((size * 0.55).dp))
    }
}

/** A labelled pill that opens a dropdown of [options]; null value = "all"/"none". */
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
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
                .clickable { open = true }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    selected?.let(optionLabel) ?: nullLabel ?: "—",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
fun EmptyState(text: String, icon: ImageVector? = null) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (icon != null) {
            IconBubble(
                icon,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                background = MaterialTheme.colorScheme.surfaceVariant,
                size = 64,
            )
        }
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** Large screen title used instead of a plain TopAppBar on the main tabs. */
@Composable
fun ScreenTitle(title: String, subtitle: String? = null, actions: @Composable () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        actions()
    }
}

val MoneyStyle: TextStyle
    @Composable get() = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
