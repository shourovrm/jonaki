package app.jonaki.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class RoutingLabelTest {
    private val model = ServiceModelUi(key = "openrouter:x", name = "X")

    @Test
    fun noOverrideShowsNoLabel() {
        assertEquals(null, routingLabelFor(model, RoutingUi.PRIVATE_THEN_CHEAPEST))
    }

    @Test
    fun overrideEqualToTheCardShowsNoLabel() {
        val same = model.copy(routingOverride = RoutingUi.CHEAPEST)
        assertEquals(null, routingLabelFor(same, RoutingUi.CHEAPEST))
    }

    @Test
    fun overrideThatDiffersIsShown() {
        val automatic = model.copy(routingOverride = RoutingUi.AUTOMATIC)
        assertEquals(RoutingUi.AUTOMATIC, routingLabelFor(automatic, RoutingUi.PRIVATE_THEN_CHEAPEST))
    }
}
