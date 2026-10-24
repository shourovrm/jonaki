package app.jonaki.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.jonaki.core.ui.JonakiIcons
import app.jonaki.core.ui.ThinkingChoice
import app.jonaki.core.ui.thinkingChoiceLabel
import app.jonaki.core.ui.JonakiTheme
import app.jonaki.core.ui.MonospaceFamily
import app.jonaki.core.ui.UsageFormat

/** The chat services block of Settings: one card per added service, then "Add service" (D-028). */
@Composable
internal fun ChatServicesSection(state: SettingsUiState, actions: SettingsActions) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val today = state.freeCallsToday
        val limit = state.freeCallLimit
        val freeCalls = if (today != null && limit != null) FreeCalls(today, limit) else null
        for (card in state.chatServices) {
            ServiceCard(card, freeCalls, actions)
        }
        if (state.addableServices.isNotEmpty()) {
            AddServiceDropdown(state.addableServices, actions.onAddService)
        }
    }
}

@Composable
private fun ServiceCard(card: ChatServiceCardUi, freeCalls: FreeCalls?, actions: SettingsActions) {
    // A card with no key yet opens by itself, so the key field is in view.
    val startsOpen = card.apiKey != null && !card.apiKey.isSet
    val summary = collapsedSummary(card.apiKey, card.models.size)
    ServiceCardFrame(card.serviceKey, startsOpen, card.displayName, summary, card.account) {
        CardBody(card, freeCalls, actions)
    }
}

/**
 * The card every service block shares, chat and image: a header with the
 * name and a one-line summary that opens the [body]. [stateKey] keeps the
 * card open or closed across recompositions and rotation.
 */
@Composable
internal fun ServiceCardFrame(
    stateKey: String,
    startsOpen: Boolean,
    displayName: String,
    summary: String,
    account: AccountLineUi?,
    body: @Composable () -> Unit,
) {
    var open by rememberSaveable(stateKey) { mutableStateOf(startsOpen) }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    ) {
        Column {
            CardHeader(displayName, summary, account, open, onToggle = { open = !open })
            if (open) {
                body()
            }
        }
    }
}

@Composable
private fun CardHeader(displayName: String, summary: String, account: AccountLineUi?, open: Boolean, onToggle: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .heightIn(min = 64.dp)
            .padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (!open) {
                Text(
                    summary,
                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonospaceFamily),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                account?.let { line -> AccountLine(line) }
            }
        }
        Icon(
            if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** "sk-o•••• · 3 models", or the model count alone for a service with no key. */
@Composable
internal fun collapsedSummary(slot: KeySlot?, modelCount: Int): String {
    val models = pluralStringResource(R.plurals.settings_model_count, modelCount, modelCount)
    return when {
        slot == null -> models
        slot.isSet -> "${slot.maskedKey.orEmpty()} · $models"
        else -> "${stringResource(R.string.settings_key_not_set)} · $models"
    }
}

/** Money left and this month's spend (D-032); red when under one dollar is left. */
@Composable
private fun AccountLine(account: AccountLineUi) {
    val lowDescription = stringResource(R.string.settings_balance_low)
    val color = if (account.isLow) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    // Colour alone does not reach a screen reader user, so a low balance is also said.
    val description = if (account.isLow) "${account.text}. $lowDescription" else account.text
    Text(
        account.text,
        style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonospaceFamily),
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.semantics { contentDescription = description },
    )
}

/** The service that needs no key and no routing: Jonaki Free (D-186). */
private const val FREE_SERVICE_KEY = "jonaki-free"

@Composable
private fun CardBody(card: ChatServiceCardUi, state: FreeCalls?, actions: SettingsActions) {
    val isFree = card.serviceKey == FREE_SERVICE_KEY
    val slot = card.apiKey
    if (slot != null) {
        KeyField(stringResource(R.string.settings_api_key), slot, actions)
    }
    if (isFree && state != null) {
        Text(
            stringResource(R.string.settings_free_calls_today, state.today, state.limit),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
        )
    }
    val routing = card.routing
    if (routing != null && !isFree) {
        Text(
            stringResource(R.string.settings_routing),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 6.dp),
        )
        RoutingChooser(routing, onSelect = { actions.onServiceRoutingChange(card.serviceKey, it) })
    }
    if (card.models.isNotEmpty()) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(top = 12.dp))
        if (!isFree) {
            PriceHeader()
        }
    }
    for (model in card.models) {
        ModelRow(model, card, actions)
    }
    if (isFree) {
        return
    }
    val needsKey = slot != null && !slot.isSet
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        Column {
            TextButton(onClick = { actions.onAddModels(card.serviceKey) }, enabled = !needsKey) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.settings_add_model))
            }
            if (needsKey) {
                Text(
                    stringResource(R.string.settings_add_model_needs_key),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 12.dp, bottom = 8.dp),
                )
            }
        }
        Spacer(Modifier.weight(1f))
        TextButton(onClick = { actions.onRemoveService(card.serviceKey) }) {
            Text(stringResource(R.string.settings_remove_service), color = JonakiTheme.colors.deny)
        }
    }
}

/** Calls used today and the daily limit, both known. */
private data class FreeCalls(val today: Int, val limit: Int)

/** Width of one price column; the header and every model row use it, so the numbers sit under their words. */
private val PriceColumnWidth = 48.dp

/** Space the star and the menu button take on either side of a model row's text. */
private val ModelRowSideWidth = 48.dp

/** One header names the unit for every price below it, so a row is only a name and three numbers. */
@Composable
private fun PriceHeader() {
    val style = MaterialTheme.typography.labelSmall
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(start = 4.dp + ModelRowSideWidth, end = 4.dp + ModelRowSideWidth, top = 8.dp, bottom = 2.dp),
    ) {
        Text(
            stringResource(R.string.settings_per_million),
            style = style,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        for (label in listOf(R.string.settings_price_head_in, R.string.settings_price_head_out, R.string.settings_price_head_cache)) {
            Text(
                stringResource(label),
                style = style,
                color = color,
                maxLines = 1,
                softWrap = false,
                textAlign = TextAlign.End,
                modifier = Modifier.width(PriceColumnWidth),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoutingChooser(selected: RoutingUi, onSelect: (RoutingUi) -> Unit) {
    val options = RoutingUi.entries
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == selected,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                icon = { Icon(routingIcon(option), contentDescription = null, modifier = Modifier.size(16.dp)) },
            ) {
                Text(
                    stringResource(routingShortLabel(option)),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModelRow(model: ServiceModelUi, card: ChatServiceCardUi, actions: SettingsActions) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
    ) {
        DefaultStar(model, onMakeDefault = { actions.onModelSetDefault(model.key) })
        Column(Modifier.weight(1f).padding(top = 10.dp)) {
            // Line 1: the name ends in "…" when long (D-029).
            Text(
                model.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // Line 2: the plan tag and the context window on the left, the three prices in columns on the right.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    PlanTag(model.freePlan)
                    val window = model.contextWindowTokens
                    if (window != null) {
                        Text(
                            UsageFormat.tokenCount(window),
                            style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonospaceFamily),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
                if (card.serviceKey != FREE_SERVICE_KEY) {
                    PriceValue(model.inputPricePerMillion)
                    PriceValue(model.outputPricePerMillion)
                    PriceValue(model.cachedInputPricePerMillion)
                }
            }
            ThinkingLine(model.thinking)
            val label = routingLabelFor(model, card.routing)
            if (model.pinnedProviders.isNotEmpty()) {
                PinnedProvidersLine(model.pinnedProviders)
            } else if (label != null) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                    Icon(routingIcon(label), contentDescription = null, tint = JonakiTheme.colors.live, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        stringResource(routingLongLabel(label)),
                        style = MaterialTheme.typography.labelMedium,
                        color = JonakiTheme.colors.live,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        ModelMenu(model, card, actions)
    }
}

/** The first chosen provider and how many more follow, in the place a routing override is shown. */
@Composable
private fun PinnedProvidersLine(tags: List<String>) {
    val first = providerOfTag(tags.first())
    val text = if (tags.size == 1) first else stringResource(R.string.settings_providers_label, first, tags.size - 1)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
        Icon(JonakiIcons.PushPin, contentDescription = null, tint = JonakiTheme.colors.live, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = JonakiTheme.colors.live,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The chosen thinking level, under the prices; nothing while the model decides itself. */
@Composable
private fun ThinkingLine(choice: ThinkingChoice?) {
    if (choice == null || choice == ThinkingChoice.DEFAULT) {
        return
    }
    Text(
        stringResource(app.jonaki.core.ui.R.string.ui_thinking_is, thinkingChoiceLabel(choice)),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(top = 2.dp),
    )
}

@Composable
private fun DefaultStar(model: ServiceModelUi, onMakeDefault: () -> Unit) {
    if (model.isDefault) {
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            Icon(
                Icons.Filled.Star,
                contentDescription = stringResource(R.string.settings_model_is_default),
                tint = JonakiTheme.colors.live,
            )
        }
        return
    }
    IconButton(onClick = onMakeDefault) {
        Icon(
            JonakiIcons.StarBorder,
            contentDescription = stringResource(R.string.settings_make_default, model.name),
            tint = MaterialTheme.colorScheme.outline,
        )
    }
}

@Composable
private fun PriceValue(usdPerMillion: Double?) {
    Text(
        UsageFormat.price(usdPerMillion),
        style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonospaceFamily),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        softWrap = false,
        textAlign = TextAlign.End,
        modifier = Modifier.width(PriceColumnWidth),
    )
}

@Composable
private fun ModelMenu(model: ServiceModelUi, card: ChatServiceCardUi, actions: SettingsActions) {
    var open by remember { mutableStateOf(false) }
    var showProviders by remember { mutableStateOf(false) }
    if (showProviders) {
        ModelProvidersSheet(model, actions, onDismiss = { showProviders = false })
    }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.settings_model_options, model.name))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (!model.isDefault) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.settings_set_default)) },
                    leadingIcon = { Icon(Icons.Filled.Star, contentDescription = null) },
                    onClick = {
                        open = false
                        actions.onModelSetDefault(model.key)
                    },
                )
            }
            if (card.routing != null) {
                Text(
                    stringResource(R.string.settings_routing),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
                )
                RoutingMenuItem(
                    text = stringResource(R.string.settings_routing_same_as, card.displayName),
                    checked = model.routingOverride == null,
                    onClick = {
                        open = false
                        actions.onModelRoutingChange(model.key, null)
                    },
                )
                for (option in RoutingUi.entries) {
                    RoutingMenuItem(
                        text = stringResource(routingLongLabel(option)),
                        checked = model.routingOverride == option,
                        onClick = {
                            open = false
                            actions.onModelRoutingChange(model.key, option)
                        },
                    )
                }
                RoutingMenuItem(
                    text = stringResource(R.string.settings_model_providers),
                    checked = model.pinnedProviders.isNotEmpty(),
                    onClick = {
                        open = false
                        showProviders = true
                    },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            val thinking = model.thinking
            if (thinking != null) {
                Text(
                    stringResource(app.jonaki.core.ui.R.string.ui_thinking),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
                )
                for (choice in ThinkingChoice.entries) {
                    RoutingMenuItem(
                        text = thinkingChoiceLabel(choice),
                        checked = thinking == choice,
                        onClick = {
                            open = false
                            actions.onModelThinkingChange(model.key, choice)
                        },
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.settings_remove_model), color = JonakiTheme.colors.deny) },
                onClick = {
                    open = false
                    actions.onModelRemove(model.key)
                },
            )
        }
    }
}

@Composable
private fun RoutingMenuItem(text: String, checked: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(text) },
        leadingIcon = {
            if (checked) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            } else {
                Spacer(Modifier.size(24.dp))
            }
        },
        onClick = onClick,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AddServiceDropdown(services: List<AddableServiceUi>, onAdd: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    ) {
        OutlinedTextField(
            value = stringResource(R.string.settings_add_service),
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            textStyle = MaterialTheme.typography.titleSmall.copy(color = MaterialTheme.colorScheme.primary),
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            for (service in services) {
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(service.displayName, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                service.hint,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    },
                    onClick = {
                        expanded = false
                        onAdd(service.key)
                    },
                )
            }
        }
    }
}

internal fun routingIcon(routing: RoutingUi): ImageVector = when (routing) {
    RoutingUi.PRIVATE_THEN_CHEAPEST -> JonakiIcons.Shield
    RoutingUi.CHEAPEST -> JonakiIcons.PriceTag
    RoutingUi.AUTOMATIC -> JonakiIcons.Automatic
}

private fun routingShortLabel(routing: RoutingUi): Int = when (routing) {
    RoutingUi.PRIVATE_THEN_CHEAPEST -> R.string.settings_routing_private_short
    RoutingUi.CHEAPEST -> R.string.settings_routing_cheapest
    RoutingUi.AUTOMATIC -> R.string.settings_routing_auto_short
}

private fun routingLongLabel(routing: RoutingUi): Int = when (routing) {
    RoutingUi.PRIVATE_THEN_CHEAPEST -> R.string.settings_routing_private
    RoutingUi.CHEAPEST -> R.string.settings_routing_cheapest
    RoutingUi.AUTOMATIC -> R.string.settings_routing_automatic
}
