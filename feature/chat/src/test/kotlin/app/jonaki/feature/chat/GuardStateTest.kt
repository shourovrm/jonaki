package app.jonaki.feature.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class GuardStateTest {
    @Test
    fun switchedOffIsOffWithOrWithoutAKey() {
        assertEquals(GuardState.OFF, guardStateOf(isSwitchedOn = false, hasOpenRouterKey = true))
        assertEquals(GuardState.OFF, guardStateOf(isSwitchedOn = false, hasOpenRouterKey = false))
    }

    @Test
    fun switchedOnWithAKeyIsOn() {
        assertEquals(GuardState.ON, guardStateOf(isSwitchedOn = true, hasOpenRouterKey = true))
    }

    @Test
    fun switchedOnWithoutAKeyIsOnWithoutKey() {
        assertEquals(GuardState.ON_WITHOUT_KEY, guardStateOf(isSwitchedOn = true, hasOpenRouterKey = false))
    }
}
