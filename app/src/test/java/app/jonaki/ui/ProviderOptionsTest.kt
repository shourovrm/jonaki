package app.jonaki.ui

import app.jonaki.core.modelcatalog.ProviderDataPolicy
import app.jonaki.core.modelcatalog.ProviderEndpoint
import app.jonaki.feature.settings.ProviderPrivacyUi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProviderOptionsTest {
    private fun endpoint(tag: String) = ProviderEndpoint(tag, tag, null, 0.1, 0.2, null)

    private val endpoints = listOf(endpoint("deepinfra/fp4"), endpoint("streamlake"), endpoint("deepseek"), endpoint("nobody/fp8"))

    private val policies = mapOf(
        "deepinfra" to ProviderDataPolicy(training = false, retainsPrompts = false),
        "streamlake" to ProviderDataPolicy(training = false, retainsPrompts = true),
        "deepseek" to ProviderDataPolicy(training = true, retainsPrompts = true),
    )

    @Test
    fun eachRowGetsItsPrivacyAndAnUnlistedProviderGetsNone() {
        val options = providerOptionsOf(endpoints, policies)

        assertEquals(
            listOf(ProviderPrivacyUi.PRIVATE, ProviderPrivacyUi.KEEPS_PROMPTS, ProviderPrivacyUi.MAY_TRAIN, null),
            options.map { it.privacy },
        )
    }

    @Test
    fun whenThePolicyListFailedNoRowHasAMarkAndTheRestIsUnchanged() {
        val options = providerOptionsOf(endpoints, emptyMap())

        assertEquals(endpoints.size, options.size)
        options.forEach { assertNull(it.privacy) }
        assertEquals(listOf("deepinfra/fp4", "streamlake", "deepseek", "nobody/fp8"), options.map { it.tag })
    }

    @Test
    fun aRowCarriesItsProvidersCachePriceAndNoneWhenTheProviderNamesNone() {
        val withCachePrice = endpoint("deepinfra/fp4").copy(cachedInputUsdPerMillion = 0.03)

        val options = providerOptionsOf(listOf(withCachePrice, endpoint("streamlake")), emptyMap())

        assertEquals(listOf(0.03, null), options.map { it.cachedInputPricePerMillion })
    }
}
