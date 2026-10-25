package app.jonaki.feature.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

private val cardShape = RoundedCornerShape(26.dp)

// How far each waiting card's edge shows under the one in front.
private val waitingCardStep = 10.dp

/**
 * The first-run setup (D-184): four cards, one at a time, each with Skip.
 * The edges under the front card are the cards still to come. Every card
 * writes a setting that Settings also has, so nothing here is needed again.
 */
@Composable
fun SetupDeckScreen(state: SetupDeckUi, actions: SetupDeckActions, modifier: Modifier = Modifier) {
    val cardNumber = state.card.ordinal + 1
    val cardCount = SetupCard.entries.size
    Column(
        modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 4.dp),
        ) {
            Text(
                stringResource(R.string.setup_count, cardNumber, cardCount),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = actions.onSkipAll) {
                Text(stringResource(R.string.setup_skip_all), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Deck(waitingCards = cardCount - cardNumber, modifier = Modifier.weight(1f)) {
            when (state.card) {
                SetupCard.MODEL -> when {
                    state.model.showsKeyStep -> ModelKeyCard(state.model, actions)
                    state.model.showsServiceList -> ModelServiceCard(state.model, actions)
                    else -> StartCard(state.model, actions)
                }
                SetupCard.SEARCH -> SearchCard(state.search, actions)
                SetupCard.NOTIFICATIONS -> NotificationsCard(actions)
            }
        }
    }
}

@Composable
private fun Deck(waitingCards: Int, modifier: Modifier = Modifier, front: @Composable ColumnScope.() -> Unit) {
    Box(modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 12.dp)) {
        for (position in waitingCards downTo 1) {
            Surface(
                shape = cardShape,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = waitingCardStep * position, vertical = waitingCardStep * (waitingCards - position))
                    .height(60.dp),
            ) {}
        }
        Surface(
            shape = cardShape,
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxSize().padding(bottom = waitingCardStep * waitingCards),
        ) {
            Column(content = front)
        }
    }
}

@Composable
private fun ColumnScope.CardBody(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 24.dp, bottom = 12.dp),
        )
        content()
    }
}

/** Back and Skip on the left, the card's own action on the right; [onBack] null leaves Back out. */
@Composable
private fun CardFooter(
    onBack: (() -> Unit)?,
    onSkip: () -> Unit,
    actionLabel: String,
    actionEnabled: Boolean,
    onAction: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(start = 10.dp, end = 14.dp, top = 6.dp, bottom = 12.dp),
    ) {
        if (onBack != null) {
            TextButton(onClick = onBack) {
                Text(stringResource(R.string.setup_back), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        TextButton(onClick = onSkip) {
            Text(stringResource(R.string.setup_skip), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.weight(1f))
        Button(onClick = onAction, enabled = actionEnabled) {
            Text(actionLabel, maxLines = 1)
        }
    }
}

/** A name keeps one line and ends in "…" (D-029); [detail] gets a line of its own. */
@Composable
private fun ChoiceRow(name: String, selected: Boolean, onSelect: () -> Unit, detail: String? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(horizontal = 10.dp),
    ) {
        RadioButton(selected = selected, onClick = null, modifier = Modifier.padding(horizontal = 12.dp))
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text(name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (detail != null) {
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun KeyField(value: String, onValueChange: (String) -> Unit, savedKeyPreview: String?) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(R.string.setup_key_label), maxLines = 1) },
        placeholder = { Text(savedKeyPreview ?: stringResource(R.string.setup_key_placeholder), maxLines = 1) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp),
    )
}

@Composable
private fun ColumnScope.StartCard(model: SetupModelCardUi, actions: SetupDeckActions) {
    val selectedPhoneModel = model.phoneModels.firstOrNull { phoneModel -> phoneModel.fileName == model.selectedPhoneModelFileName }
    CardBody(stringResource(R.string.setup_start_title)) {
        Text(
            stringResource(R.string.setup_start_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 4.dp),
        )
        ChoiceRow(
            stringResource(R.string.setup_start_free),
            selected = model.start == SetupStart.JONAKI_FREE,
            onSelect = { actions.onStartSelect(SetupStart.JONAKI_FREE) },
            detail = stringResource(R.string.setup_start_free_detail, model.freeDailyLimit),
        )
        ChoiceRow(
            stringResource(R.string.setup_start_phone),
            selected = model.start == SetupStart.ON_PHONE,
            onSelect = { actions.onStartSelect(SetupStart.ON_PHONE) },
            detail = stringResource(R.string.setup_start_phone_detail),
        )
        if (model.start == SetupStart.ON_PHONE) {
            PhoneModelList(model, actions)
        }
        ChoiceRow(
            stringResource(R.string.setup_start_own_key),
            selected = model.start == SetupStart.OWN_KEY,
            onSelect = { actions.onStartSelect(SetupStart.OWN_KEY) },
            detail = stringResource(R.string.setup_start_own_key_detail),
        )
        Text(
            stringResource(R.string.setup_model_needed),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp),
        )
    }
    val downloadIsPending = model.start == SetupStart.ON_PHONE && !model.phoneDownloadStarted
    CardFooter(
        onBack = null,
        onSkip = actions.onSkip,
        actionLabel = if (downloadIsPending && selectedPhoneModel != null) {
            stringResource(R.string.setup_start_download, selectedPhoneModel.sizeText)
        } else {
            stringResource(R.string.setup_next)
        },
        actionEnabled = when {
            !downloadIsPending -> true
            selectedPhoneModel == null -> false
            else -> selectedPhoneModel.fit != SetupFit.TOO_BIG
        },
        onAction = {
            when {
                model.start == SetupStart.JONAKI_FREE || model.start == SetupStart.OWN_KEY -> actions.onStartConfirm()
                downloadIsPending -> selectedPhoneModel?.let { phoneModel -> actions.onPhoneDownload(phoneModel.fileName) }
                else -> actions.onSkip()
            }
        },
    )
}

@Composable
private fun PhoneModelList(model: SetupModelCardUi, actions: SetupDeckActions) {
    Column(Modifier.padding(start = 34.dp)) {
        for (phoneModel in model.phoneModels) {
            val fitWord = stringResource(
                when (phoneModel.fit) {
                    SetupFit.FITS -> R.string.setup_fit_fits
                    SetupFit.TIGHT -> R.string.setup_fit_tight
                    SetupFit.TOO_BIG -> R.string.setup_fit_too_big
                },
            )
            ChoiceRow(
                phoneModel.name,
                selected = phoneModel.fileName == model.selectedPhoneModelFileName,
                onSelect = { actions.onPhoneModelSelect(phoneModel.fileName) },
                detail = stringResource(R.string.setup_phone_model_detail, phoneModel.sizeText, fitWord),
            )
        }
        val note = when {
            model.phoneDownloadRefused -> R.string.setup_phone_no_space
            model.phoneDownloadStarted -> R.string.setup_phone_started
            else -> R.string.setup_phone_wifi
        }
        Text(
            stringResource(note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun ColumnScope.ModelServiceCard(model: SetupModelCardUi, actions: SetupDeckActions) {
    val selectedIsInShortList = model.services.take(SetupDeckUi.SHORT_LIST_SIZE).any { service -> service.key == model.selectedServiceKey }
    var showsAll by rememberSaveable { mutableStateOf(false) }
    val listed = if (showsAll || !selectedIsInShortList && model.selectedServiceKey != null) {
        model.services
    } else {
        model.services.take(SetupDeckUi.SHORT_LIST_SIZE)
    }
    CardBody(stringResource(R.string.setup_model_title)) {
        for (service in listed) {
            ChoiceRow(service.name, selected = service.key == model.selectedServiceKey, onSelect = { actions.onModelServiceSelect(service.key) })
        }
        if (listed.size < model.services.size) {
            TextButton(onClick = { showsAll = true }, modifier = Modifier.padding(start = 46.dp)) {
                Text(stringResource(R.string.setup_more_services))
            }
        }
        Text(
            stringResource(R.string.setup_model_needed),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp),
        )
    }
    CardFooter(
        onBack = actions.onBack,
        onSkip = actions.onSkip,
        actionLabel = stringResource(R.string.setup_next),
        actionEnabled = model.selectedServiceKey != null,
        onAction = actions.onModelServiceConfirm,
    )
}

@Composable
private fun ColumnScope.ModelKeyCard(model: SetupModelCardUi, actions: SetupDeckActions) {
    var typedKey by rememberSaveable(model.selectedServiceKey) { mutableStateOf("") }
    val hasKey = typedKey.isNotBlank() || model.savedKeyPreview != null
    // Once a key is there the service's own default model is preselected, so Next needs no picker.
    val shownModelName = model.chosenModelName ?: model.defaultModelName.takeIf { hasKey }
    CardBody(stringResource(R.string.setup_model_key_title, model.selectedServiceName)) {
        KeyField(typedKey, { typedKey = it }, model.savedKeyPreview)
        if (model.keySite != null) {
            Text(
                stringResource(R.string.setup_key_site, model.keySite),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 8.dp),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, top = 20.dp)) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text(stringResource(R.string.setup_model_label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                // The one item this row shows, so the full name wraps (D-029).
                Text(shownModelName ?: stringResource(R.string.setup_model_none), style = MaterialTheme.typography.bodyLarge)
            }
            OutlinedButton(onClick = { actions.onChooseModel(typedKey) }, enabled = hasKey) {
                Text(stringResource(if (shownModelName == null) R.string.setup_model_choose else R.string.setup_model_change), maxLines = 1)
            }
        }
    }
    CardFooter(
        onBack = actions.onBack,
        onSkip = actions.onSkip,
        actionLabel = stringResource(R.string.setup_next),
        actionEnabled = shownModelName != null,
        onAction = { actions.onModelDone(typedKey) },
    )
}

@Composable
private fun ColumnScope.SearchCard(search: SetupSearchCardUi, actions: SetupDeckActions) {
    var typedKey by rememberSaveable(search.selectedServiceKey) { mutableStateOf("") }
    CardBody(stringResource(R.string.setup_search_title)) {
        for (service in search.services) {
            ChoiceRow(service.name, selected = service.key == search.selectedServiceKey, onSelect = { actions.onSearchServiceSelect(service.key) })
        }
        Spacer(Modifier.height(10.dp))
        KeyField(typedKey, { typedKey = it }, search.savedKeyPreview)
    }
    CardFooter(
        onBack = actions.onBack,
        onSkip = actions.onSkip,
        actionLabel = stringResource(R.string.setup_next),
        actionEnabled = typedKey.isNotBlank() || search.savedKeyPreview != null,
        onAction = { actions.onSearchDone(typedKey) },
    )
}

@Composable
private fun ColumnScope.NotificationsCard(actions: SetupDeckActions) {
    CardBody(stringResource(R.string.setup_notifications_title)) {
        Text(
            stringResource(R.string.setup_notifications_detail),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 22.dp),
        )
    }
    CardFooter(
        onBack = actions.onBack,
        onSkip = actions.onSkip,
        actionLabel = stringResource(R.string.setup_allow),
        actionEnabled = true,
        onAction = actions.onAllowNotifications,
    )
}
