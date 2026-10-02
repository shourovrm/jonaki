package app.jonaki.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.jonaki.core.ui.JonakiIcons
import app.jonaki.core.ui.MonospaceFamily

/**
 * One secret. A saved key shows only its masked start ("sk-o••••") with edit
 * and delete (D-028); editing or a missing key shows an entry field. The full
 * key never comes back to the screen.
 */
@Composable
internal fun KeyField(label: String, slot: KeySlot, actions: SettingsActions) {
    var editing by rememberSaveable(slot.id) { mutableStateOf(false) }
    if (slot.isSet && !editing) {
        SavedKey(
            label = label,
            slot = slot,
            onEdit = { editing = true },
            onDelete = { actions.onKeyClear(slot.id) },
        )
        return
    }
    KeyEntry(
        label = label,
        slot = slot,
        onSave = { value ->
            actions.onKeySave(slot.id, value)
            editing = false
        },
        onCancel = if (slot.isSet) ({ editing = false }) else null,
    )
}

@Composable
private fun SavedKey(label: String, slot: KeySlot, onEdit: () -> Unit, onDelete: () -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.heightIn(min = 56.dp).padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    slot.maskedKey.orEmpty(),
                    style = MaterialTheme.typography.bodyLarge.copy(fontFamily = MonospaceFamily),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                BalanceLine(slot.balance)
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.settings_key_change))
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.settings_key_delete))
            }
        }
    }
}

/** Credit left or used (D-031); nothing at all when the service does not report it. */
@Composable
internal fun BalanceLine(balance: String?) {
    if (balance == null) {
        return
    }
    Text(
        balance,
        style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonospaceFamily),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun KeyEntry(label: String, slot: KeySlot, onSave: (String) -> Unit, onCancel: (() -> Unit)?) {
    var entered by rememberSaveable(slot.id, "entry") { mutableStateOf("") }
    var visible by rememberSaveable(slot.id, "visible") { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp)) {
        OutlinedTextField(
            value = entered,
            onValueChange = { entered = it.trim() },
            label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            placeholder = {
                Text(stringResource(if (slot.isSet) R.string.settings_key_replace_hint else R.string.settings_key_hint))
            },
            singleLine = true,
            visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
            trailingIcon = {
                IconButton(onClick = { visible = !visible }) {
                    Icon(
                        if (visible) JonakiIcons.VisibilityOff else JonakiIcons.Visibility,
                        contentDescription = stringResource(if (visible) R.string.settings_key_hide else R.string.settings_key_show),
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            if (onCancel != null) {
                TextButton(onClick = {
                    entered = ""
                    onCancel()
                }) {
                    Text(stringResource(R.string.settings_cancel))
                }
            }
            TextButton(
                onClick = {
                    onSave(entered)
                    entered = ""
                    visible = false
                },
                enabled = entered.isNotEmpty(),
            ) {
                Text(stringResource(R.string.settings_key_save))
            }
        }
    }
}
