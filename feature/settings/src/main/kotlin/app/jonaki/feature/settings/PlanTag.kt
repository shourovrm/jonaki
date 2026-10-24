package app.jonaki.feature.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.jonaki.core.ui.JonakiTheme

/** "Free plan" or "Paid plan only" from [freePlan]; nothing while the service has not said. */
@Composable
internal fun PlanTag(freePlan: Boolean?) {
    if (freePlan == null) {
        return
    }
    val isFree = freePlan
    Text(
        stringResource(if (isFree) R.string.settings_plan_free else R.string.settings_plan_paid),
        style = MaterialTheme.typography.labelMedium,
        color = if (isFree) JonakiTheme.colors.live else MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(end = 8.dp),
    )
}
