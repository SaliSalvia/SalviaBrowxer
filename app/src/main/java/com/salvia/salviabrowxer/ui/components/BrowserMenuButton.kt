package com.salvia.salviabrowxer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.salvia.salviabrowxer.ui.theme.AuroraTeal
import com.salvia.salviabrowxer.ui.theme.CharcoalElevated
import com.salvia.salviabrowxer.ui.theme.PearlWhite
import com.salvia.salviabrowxer.ui.theme.SilverMid

/**
 * One row of the browser overflow menu.
 *
 * [isChecked] renders a trailing check so a toggle (desktop site) shows its real state
 * instead of pretending to be a plain command.
 */
data class BrowserMenuItem(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
    val isEnabled: Boolean = true,
    val isChecked: Boolean = false,
    val showsCheck: Boolean = false
)

/**
 * The overflow menu anchored to its own button, so the popup lines up with the control the
 * user actually pressed. Every item must do something reachable; a dead row is a bug.
 */
@Composable
fun BrowserMenuButton(
    items: List<BrowserMenuItem>,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .heightIn(min = 54.dp)
                .clip(RoundedCornerShape(14.dp))
                .clickable(role = Role.Button, onClickLabel = contentDescription) { expanded = true },
            contentAlignment = Alignment.Center
        ) {
            // No size override: the default IconButton-equivalent target stays 48 dp tall.
            Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.MoreVert, contentDescription, tint = PearlWhite, modifier = Modifier.size(22.dp))
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(CharcoalElevated)
        ) {
            items.forEach { item ->
                DropdownMenuItem(
                    text = { Text(item.label, style = MaterialTheme.typography.bodyMedium, color = if (item.isEnabled) PearlWhite else SilverMid) },
                    leadingIcon = { Icon(item.icon, null, tint = if (item.isEnabled) SilverMid else SilverMid.copy(alpha = 0.5f), modifier = Modifier.size(20.dp)) },
                    trailingIcon = {
                        if (item.showsCheck && item.isChecked) Icon(Icons.Default.Check, null, tint = AuroraTeal, modifier = Modifier.size(18.dp))
                    },
                    enabled = item.isEnabled,
                    onClick = {
                        expanded = false
                        item.onClick()
                    }
                )
            }
        }
    }
}
