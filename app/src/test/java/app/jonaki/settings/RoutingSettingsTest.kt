package app.jonaki.settings

import app.jonaki.providers.openaicompatible.OpenRouterRouting
import org.junit.Assert.assertEquals
import org.junit.Test

class RoutingSettingsTest {
    private val routing = RoutingSettings(
        openRouter = OpenRouterRouting.PRIVATE_THEN_CHEAPEST,
        overrides = mapOf("openrouter:z-ai/glm-5.3-flash" to OpenRouterRouting.AUTOMATIC),
    )

    @Test
    fun aModelWithoutOverrideUsesTheServiceSetting() {
        assertEquals(OpenRouterRouting.PRIVATE_THEN_CHEAPEST, routing.effectiveFor("openrouter:anthropic/claude-sonnet-5.5"))
    }

    @Test
    fun aModelOverrideWins() {
        assertEquals(OpenRouterRouting.AUTOMATIC, routing.effectiveFor("openrouter:z-ai/glm-5.3-flash"))
    }

    @Test
    fun settingAnOverrideToNullFollowsTheServiceAgain() {
        val cleared = routing.withOverride("openrouter:z-ai/glm-5.3-flash", null)

        assertEquals(OpenRouterRouting.PRIVATE_THEN_CHEAPEST, cleared.effectiveFor("openrouter:z-ai/glm-5.3-flash"))
    }

    @Test
    fun overridesSurviveTheirTextForm() {
        assertEquals(routing.overrides, RoutingSettings.overridesFromText(RoutingSettings.overridesToText(routing.overrides)))
    }

    @Test
    fun brokenOverrideLinesAreSkipped() {
        assertEquals(emptyMap<String, OpenRouterRouting>(), RoutingSettings.overridesFromText("no-tab-here\nopenrouter:x\tNOT_A_ROUTING"))
    }
}
