package app.jonaki.guards.jev

import org.junit.Assert.assertEquals
import org.junit.Test

class JevNotesTest {
    @Test
    fun anActionThatRanWithoutACardShowsEffectAndBothNumbersWithTwoDecimals() {
        val note = JevNotes.ranWithoutCard(effect = "reversible", effectConfidence = 0.97, servesRequest = 0.81)

        assertEquals("Jev: ran without a card (reversible 0.97, asked for 0.81)", note)
    }

    @Test
    fun aReadOnlyEffectIsWrittenWithASpaceAndWholeNumbersGetTwoDecimals() {
        val note = JevNotes.ranWithoutCard(effect = "read_only", effectConfidence = 0.9, servesRequest = 1.0)

        assertEquals("Jev: ran without a card (read only 0.90, asked for 1.00)", note)
    }

    @Test
    fun aCardForAnUnsureRequestSaysSo() {
        val note = JevNotes.cardShown(
            effect = "reversible",
            effectConfidence = 0.95,
            servesRequest = 0.41,
            effectIsSafe = true,
            effectIsSure = true,
            requestIsServed = false,
        )

        assertEquals("Jev: card shown (not sure the user asked, 0.41)", note)
    }

    @Test
    fun aCardForARiskyEffectNamesTheEffect() {
        val note = JevNotes.cardShown("sends_out", 0.95, 0.9, effectIsSafe = false, effectIsSure = true, requestIsServed = true)

        assertEquals("Jev: card shown (sends data out 0.95)", note)
    }

    @Test
    fun aCardForAnUnsureEffectGivesTheConfidence() {
        val note = JevNotes.cardShown("reversible", 0.58, 0.9, effectIsSafe = true, effectIsSure = false, requestIsServed = true)

        assertEquals("Jev: card shown (not sure of the effect, reversible 0.58)", note)
    }

    @Test
    fun everyReasonThatStoppedTheActionIsListed() {
        val note = JevNotes.cardShown("reversible", 0.58, 0.01, effectIsSafe = true, effectIsSure = false, requestIsServed = false)

        assertEquals("Jev: card shown (not sure of the effect, reversible 0.58; not sure the user asked, 0.01)", note)
    }

    @Test
    fun aMissingAnswerForAnActionSaysTheCardWasShown() {
        assertEquals("Jev: no answer, card shown", JevNotes.noAnswerForAction())
    }

    @Test
    fun aFlaggedResultGivesItsProbability() {
        assertEquals("Jev: result flagged (0.83)", JevNotes.result(isFlagged = true, probability = 0.83))
    }

    @Test
    fun aClearResultGivesItsProbability() {
        assertEquals("Jev: result clear (0.04)", JevNotes.result(isFlagged = false, probability = 0.04))
    }

    @Test
    fun aMissingAnswerForAResultSaysItWasNotChecked() {
        assertEquals("Jev: no answer, result not checked", JevNotes.noAnswerForResult())
    }
}
