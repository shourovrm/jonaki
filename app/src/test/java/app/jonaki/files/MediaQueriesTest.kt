package app.jonaki.files

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaQueriesTest {
    @Test
    fun newestAddedFirstWithTheIdBreakingTies() {
        assertEquals("date_added DESC, _id DESC", MediaQueries.NEWEST_FIRST)
    }

    @Test
    fun olderAndroidGetsThePageInTheSortOrder() {
        assertEquals(
            "date_added DESC, _id DESC LIMIT 120 OFFSET 240",
            MediaQueries.newestFirstPage(offset = 240, limit = 120),
        )
    }
}
