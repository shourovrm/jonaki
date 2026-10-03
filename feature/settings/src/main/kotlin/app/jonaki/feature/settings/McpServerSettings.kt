package app.jonaki.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** One MCP server in Settings (D-104). The header's value never comes back to the screen. */
@Immutable
data class McpServerUi(
    val id: String,
    val name: String,
    val url: String,
    /** For example "Authorization"; null when the server takes no header. */
    val headerName: String? = null,
    val hasHeaderValue: Boolean = false,
)

/** What the server dialog saves. */
data class McpServerInput(
    /** Null for a new server. */
    val id: String?,
    val name: String,
    val url: String,
    /** Blank with no new value removes the header; blank with a new value means "Authorization". */
    val headerName: String,
    /** Null keeps the saved value; text replaces it. */
    val headerValue: String?,
)

/** The checks the server dialog runs before it saves. */
object McpServerForm {
    enum class Problem {
        NAME_MISSING,
        NAME_TAKEN,
        URL_INVALID,
    }

    /** Null when [name] and [url] can be saved; the model finds a server by its name, so names are unique. */
    fun problem(name: String, url: String, servers: List<McpServerUi>, editingId: String?): Problem? {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) {
            return Problem.NAME_MISSING
        }
        val taken = servers.any { server -> server.id != editingId && server.name.equals(trimmedName, ignoreCase = true) }
        if (taken) {
            return Problem.NAME_TAKEN
        }
        if (!isWebAddress(url.trim())) {
            return Problem.URL_INVALID
        }
        return null
    }

    private fun isWebAddress(url: String): Boolean {
        val scheme = listOf("https://", "http://").firstOrNull { prefix -> url.startsWith(prefix, ignoreCase = true) }
            ?: return false
        val host = url.substring(scheme.length).substringBefore('/').substringBefore(':')
        return host.isNotEmpty() && host.none { character -> character.isWhitespace() }
    }
}

/** The rows inside the MCP servers group: one per server, then "Add server". */
@Composable
internal fun McpServerRows(servers: List<McpServerUi>, actions: SettingsActions) {
    // "" while closed, NEW_SERVER for a new one, else the id being edited.
    var dialogFor by rememberSaveable { mutableStateOf("") }
    for (server in servers) {
        McpServerRow(server, onClick = { dialogFor = server.id })
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { dialogFor = NEW_SERVER }
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp),
    ) {
        Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.size(12.dp))
        Text(stringResource(R.string.settings_mcp_add), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
    }
    if (dialogFor.isEmpty()) {
        return
    }
    val editing = servers.firstOrNull { server -> server.id == dialogFor }
    if (dialogFor != NEW_SERVER && editing == null) {
        // The server was removed while its dialog was open.
        dialogFor = ""
        return
    }
    McpServerDialog(
        editing = editing,
        servers = servers,
        onSave = { input ->
            actions.onMcpServerSave(input)
            dialogFor = ""
        },
        onRemove = { id ->
            actions.onMcpServerRemove(id)
            dialogFor = ""
        },
        onDismiss = { dialogFor = "" },
    )
}

/** The name keeps one line; the address wraps to two lines, then "…" (D-029). */
@Composable
private fun McpServerRow(server: McpServerUi, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(server.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            server.url,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun McpServerDialog(
    editing: McpServerUi?,
    servers: List<McpServerUi>,
    onSave: (McpServerInput) -> Unit,
    onRemove: (id: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(editing?.name.orEmpty()) }
    var url by rememberSaveable { mutableStateOf(editing?.url.orEmpty()) }
    var headerName by rememberSaveable { mutableStateOf(editing?.headerName.orEmpty()) }
    var headerValue by rememberSaveable { mutableStateOf("") }
    // Problems show only after the first Save, not while the user is still typing.
    var triedToSave by rememberSaveable { mutableStateOf(false) }
    val problem = McpServerForm.problem(name, url, servers, editing?.id)
    val shownProblem = if (triedToSave) problem else null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (editing == null) R.string.settings_mcp_add else R.string.settings_mcp_edit)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { changed -> name = changed.take(MAX_NAME_LENGTH) },
                    label = { Text(stringResource(R.string.settings_mcp_name)) },
                    singleLine = true,
                    isError = shownProblem == McpServerForm.Problem.NAME_MISSING || shownProblem == McpServerForm.Problem.NAME_TAKEN,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { changed -> url = changed },
                    label = { Text(stringResource(R.string.settings_mcp_url)) },
                    placeholder = { Text("https://…/mcp") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    maxLines = 3,
                    isError = shownProblem == McpServerForm.Problem.URL_INVALID,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                OutlinedTextField(
                    value = headerName,
                    onValueChange = { changed -> headerName = changed },
                    label = { Text(stringResource(R.string.settings_mcp_header)) },
                    placeholder = { Text("Authorization") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                OutlinedTextField(
                    value = headerValue,
                    onValueChange = { changed -> headerValue = changed },
                    label = { Text(stringResource(R.string.settings_mcp_header_value)) },
                    placeholder = {
                        val hint = if (editing?.hasHeaderValue == true) R.string.settings_mcp_header_saved else R.string.settings_mcp_header_hint
                        Text(stringResource(hint))
                    },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                if (shownProblem != null) {
                    Text(
                        stringResource(messageFor(shownProblem)),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                if (editing != null) {
                    TextButton(onClick = { onRemove(editing.id) }, modifier = Modifier.padding(top = 8.dp)) {
                        Text(stringResource(R.string.settings_mcp_remove), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    triedToSave = true
                    if (problem == null) {
                        val input = McpServerInput(
                            id = editing?.id,
                            name = name.trim(),
                            url = url.trim(),
                            headerName = headerName.trim(),
                            headerValue = headerValue.trim().ifEmpty { null },
                        )
                        onSave(input)
                    }
                },
            ) {
                Text(stringResource(R.string.settings_key_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_cancel)) }
        },
    )
}

private fun messageFor(problem: McpServerForm.Problem): Int = when (problem) {
    McpServerForm.Problem.NAME_MISSING -> R.string.settings_mcp_name_missing
    McpServerForm.Problem.NAME_TAKEN -> R.string.settings_mcp_name_taken
    McpServerForm.Problem.URL_INVALID -> R.string.settings_mcp_url_invalid
}

private const val NEW_SERVER = "new"
private const val MAX_NAME_LENGTH = 40
