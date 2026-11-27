package top.wsdx233.r2droid.core.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import top.wsdx233.r2droid.util.R2CommandHelp
import top.wsdx233.r2droid.util.R2HelpEntry

@Composable
fun CommandSuggestButton(
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        R2CommandHelp.ensureLoaded(context)
    }

    FilledTonalIconButton(
        onClick = onToggle,
        modifier = modifier.size(40.dp)
    ) {
        Text("$", style = MaterialTheme.typography.titleSmall, fontFamily = FontFamily.Monospace)
    }
}

@Composable
fun CommandSuggestionPanel(
    currentInput: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var helpLoaded by remember { mutableStateOf(R2CommandHelp.isLoaded) }
    LaunchedEffect(Unit) {
        if (!helpLoaded) {
            R2CommandHelp.ensureLoaded(context)
            helpLoaded = true
        }
    }
    val suggestions = remember(currentInput, helpLoaded) {
        if (!helpLoaded) emptyList()
        else if (currentInput.isBlank()) R2CommandHelp.complete("a").take(30)
        else R2CommandHelp.complete(currentInput)
    }

    Surface(
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 4.dp,
        modifier = modifier.fillMaxWidth().heightIn(max = 220.dp)
    ) {
        if (suggestions.isEmpty()) {
            Text(
                "No matches",
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LazyColumn {
                items(suggestions) { entry ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(entry.command + if (entry.args.isNotEmpty()) " " else "") }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            entry.command,
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            if (entry.args.isNotEmpty()) {
                                Text(entry.args, style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            if (entry.description.isNotEmpty()) {
                                Text(entry.description, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }
            }
        }
    }
}
