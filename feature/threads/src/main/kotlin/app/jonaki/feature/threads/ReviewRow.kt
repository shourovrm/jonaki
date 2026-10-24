package app.jonaki.feature.threads

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/**
 * One row above the threads that counts what waits for the user: facts held for review
 * and skill proposals. The caller does not draw it when both counts are 0.
 */
@Composable
internal fun ReviewRow(factsToReview: Int, skillsToReview: Int, onOpen: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(start = 24.dp, end = 12.dp),
    ) {
        Text(
            reviewText(factsToReview, skillsToReview),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onOpen) {
            Text(stringResource(R.string.threads_review_open))
        }
    }
}

@Composable
private fun reviewText(factsToReview: Int, skillsToReview: Int): String {
    val facts = pluralStringResource(R.plurals.threads_review_facts, factsToReview, factsToReview)
    val skills = pluralStringResource(R.plurals.threads_review_skills, skillsToReview, skillsToReview)
    return when {
        factsToReview > 0 && skillsToReview > 0 -> stringResource(R.string.threads_review_both_kinds, facts, skills)
        factsToReview > 0 -> stringResource(R.string.threads_review_one_kind, facts)
        else -> stringResource(R.string.threads_review_one_kind, skills)
    }
}
