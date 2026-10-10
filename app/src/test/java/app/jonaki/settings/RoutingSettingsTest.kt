package app.jonaki.settings

import app.jonaki.providers.openaicompatible.OpenRouterRoute
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
        assertEquals(OpenRouterRoute(OpenRouterRouting.PRIVATE_THEN_CHEAPEST), routing.effectiveFor("openrouter:anthropic/claude-sonnet-5.5"))
    }

    @Test
    fun aModelOverrideWins() {
        assertEquals(OpenRouterRoute(OpenRouterRouting.AUTOMATIC), routing.effectiveFor("openrouter:z-ai/glm-5.3-flash"))
    }

    @Test
    fun settingAnOverrideToNullFollowsTheServiceAgain() {
        val cleared = routing.withOverride("openrouter:z-ai/glm-5.3-flash", null)

        assertEquals(OpenRouterRoute(OpenRouterRouting.PRIVATE_THEN_CHEAPEST), cleared.effectiveFor("openrouter:z-ai/glm-5.3-flash"))
    }

    @Test
    fun overridesSurviveTheirTextForm() {
        assertEquals(routing.overrides, RoutingSettings.overridesFromText(RoutingSettings.overridesToText(routing.overrides)))
    }

    @Test
    fun brokenOverrideLinesAreSkipped() {
        assertEquals(emptyMap<String, OpenRouterRouting>(), RoutingSettings.overridesFromText("no-tab-here\nopenrouter:x\tNOT_A_ROUTING"))
    }

    private val glmKey = "openrouter:z-ai/glm-5.3-flash"
    private val glmPinned = PinnedProviders(listOf("deepinfra/fp4", "fireworks", "z-ai/fp8"), allowFallbacks = false)

    @Test
    fun pinnedProvidersReplaceTheRoutingChoiceInTheRoute() {
        val withPinned = routing.withPinned(glmKey, glmPinned)

        assertEquals(
            OpenRouterRoute(OpenRouterRouting.AUTOMATIC, listOf("deepinfra/fp4", "fireworks", "z-ai/fp8"), allowFallbacks = false),
            withPinned.effectiveFor(glmKey),
        )
    }

    @Test
    fun otherModelsAreNotAffectedByAPinnedList() {
        val withPinned = routing.withPinned(glmKey, glmPinned)

        assertEquals(
            OpenRouterRoute(OpenRouterRouting.PRIVATE_THEN_CHEAPEST),
            withPinned.effectiveFor("openrouter:anthropic/claude-sonnet-5.5"),
        )
    }

    @Test
    fun clearingThePinnedListReturnsToTheRoutingChoice() {
        val cleared = routing.withPinned(glmKey, glmPinned).withPinned(glmKey, PinnedProviders(emptyList()))

        assertEquals(OpenRouterRoute(OpenRouterRouting.AUTOMATIC), cleared.effectiveFor(glmKey))
        assertEquals(emptyMap<String, PinnedProviders>(), cleared.pinned)
        assertEquals(emptyMap<String, PinnedProviders>(), routing.withPinned(glmKey, glmPinned).withPinned(glmKey, null).pinned)
    }

    @Test
    fun pinnedProvidersSurviveTheirTextForm() {
        val pinned = mapOf(
            glmKey to glmPinned,
            "openrouter:deepseek/deepseek-v4.1-flash:free" to PinnedProviders(listOf("novita/fp8"), allowFallbacks = true),
        )

        assertEquals(pinned, RoutingSettings.pinnedFromText(RoutingSettings.pinnedToText(pinned)))
    }

    @Test
    fun theTagOrderIsKeptInTheTextForm() {
        val pinned = mapOf(glmKey to PinnedProviders(listOf("z-ai/fp8", "deepinfra/fp4")))

        assertEquals(listOf("z-ai/fp8", "deepinfra/fp4"), RoutingSettings.pinnedFromText(RoutingSettings.pinnedToText(pinned)).getValue(glmKey).tags)
    }

    @Test
    fun emptyTextGivesNoPinnedProviders() {
        assertEquals(emptyMap<String, PinnedProviders>(), RoutingSettings.pinnedFromText(""))
    }

    @Test
    fun unreadablePinnedTextIsDroppedWithoutCrashing() {
        val garbage = listOf(
            "no-tabs-at-all",
            "$glmKey\tmaybe\tdeepinfra/fp4",
            "$glmKey\ttrue\t",
            "\ttrue\tdeepinfra/fp4",
            "$glmKey\ttrue\tdeepinfra/fp4\textra",
        ).joinToString("\n")

        assertEquals(emptyMap<String, PinnedProviders>(), RoutingSettings.pinnedFromText(garbage))
        assertEquals(emptyMap<String, PinnedProviders>(), RoutingSettings.pinnedFromText("\u0000\uFFFF binary \t\t\t"))
    }

    @Test
    fun oneBrokenLineDoesNotHideTheGoodLines() {
        val text = "garbage\n$glmKey\tfalse\tdeepinfra/fp4 fireworks"

        assertEquals(
            mapOf(glmKey to PinnedProviders(listOf("deepinfra/fp4", "fireworks"), allowFallbacks = false)),
            RoutingSettings.pinnedFromText(text),
        )
    }
}
