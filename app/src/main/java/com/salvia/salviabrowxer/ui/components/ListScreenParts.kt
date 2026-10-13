package com.salvia.salviabrowxer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.salvia.salviabrowxer.ui.theme.CharcoalSurface
import androidx.compose.ui.unit.dp
import com.salvia.salviabrowxer.ui.theme.AuroraTeal
import com.salvia.salviabrowxer.ui.theme.CharcoalElevated
import com.salvia.salviabrowxer.ui.theme.PearlWhite
import com.salvia.salviabrowxer.ui.theme.SilverMid

/** The one search field used by the bookmarks and history screens. */
@Composable
fun ListSearchRow(query: String, hint: String, onQueryChange: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().background(CharcoalElevated).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Search, null, tint = SilverMid, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Box(modifier = Modifier.weight(1f)) {
            if (query.isEmpty()) Text(hint, style = MaterialTheme.typography.bodyMedium, color = SilverMid)
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(color = PearlWhite),
                cursorBrush = SolidColor(AuroraTeal),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                // The hint text vanishes once the user types, so the field names itself.
                modifier = Modifier.semantics { contentDescription = hint }
            )
        }
    }
}

/** One sentence and, when there is something to do about it, one action. */
@Composable
fun ListEmptyState(text: String, actionLabel: String? = null, onAction: () -> Unit = {}) {
    Column(
        modifier = Modifier.fillMaxSize().padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = SilverMid)
        if (actionLabel != null) {
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onAction) { Text(actionLabel, color = AuroraTeal) }
        }
    }
}

/**
 * A masked field for the two places a password is typed: setting one in settings and entering one to
 * open the locked folder. `PasswordVisualTransformation` is what keeps it out of screenshots and
 * shoulder-surfing distance; the keyboard hint asks the platform for a password keyboard, which is
 * also what stops most IMEs from remembering it.
 */
@Composable
fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    modifier: Modifier = Modifier
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
        textStyle = TextStyle(color = PearlWhite),
        cursorBrush = SolidColor(AuroraTeal),
        modifier = modifier
            .fillMaxWidth()
            .background(CharcoalSurface, RoundedCornerShape(10.dp))
            .padding(12.dp)
            .semantics { contentDescription = hint }
    )
}
