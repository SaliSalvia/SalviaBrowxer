package com.salvia.salviabrowxer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.salvia.salviabrowxer.R
import com.salvia.salviabrowxer.ui.theme.AuroraTeal
import com.salvia.salviabrowxer.ui.theme.CharcoalBorder
import com.salvia.salviabrowxer.ui.theme.CharcoalSurface
import com.salvia.salviabrowxer.ui.theme.PearlWhite
import com.salvia.salviabrowxer.ui.theme.SilverMid

/**
 * Find in page.
 *
 * The counter is the WebView's own match count — when nothing matches it says so instead of
 * showing a spinner or a fake "1/1".
 */
@Composable
fun FindInPageBar(
    query: String,
    matches: Int,
    activeMatch: Int,
    onQueryChange: (String) -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val findFieldLabel = stringResource(R.string.find_query_field)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(CharcoalSurface)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Search, null, tint = SilverMid, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(10.dp))
                .background(CharcoalSurface)
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            if (query.isEmpty()) {
                Text(stringResource(R.string.find_in_page_hint), style = MaterialTheme.typography.bodyMedium, color = SilverMid)
            }
            // The placeholder disappears as soon as the field has text, so the field carries a
            // label of its own rather than relying on the hint to name it.
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(color = PearlWhite),
                cursorBrush = SolidColor(AuroraTeal),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = findFieldLabel }
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = when {
                query.isEmpty() -> ""
                matches == 0 -> stringResource(R.string.find_no_matches)
                else -> stringResource(R.string.find_match_counter, activeMatch.coerceAtLeast(1), matches)
            },
            style = MaterialTheme.typography.labelMedium,
            color = if (matches == 0 && query.isNotEmpty()) SilverMid else AuroraTeal,
            textAlign = TextAlign.End,
            maxLines = 1
        )
        IconButton(onClick = onPrevious, enabled = matches > 0) {
            Icon(Icons.Default.KeyboardArrowUp, stringResource(R.string.find_previous), tint = if (matches > 0) PearlWhite else CharcoalBorder, modifier = Modifier.size(20.dp))
        }
        IconButton(onClick = onNext, enabled = matches > 0) {
            Icon(Icons.Default.KeyboardArrowDown, stringResource(R.string.find_next), tint = if (matches > 0) PearlWhite else CharcoalBorder, modifier = Modifier.size(20.dp))
        }
        IconButton(onClick = onClose) {
            Icon(Icons.Default.Close, stringResource(R.string.find_close), tint = SilverMid, modifier = Modifier.size(20.dp))
        }
    }
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(CharcoalBorder))
}
