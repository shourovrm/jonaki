package app.jonaki.feature.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class ModelShortNameTest {
    @Test
    fun theMakerPrefixIsDropped() {
        assertEquals("Claude Haiku 5.5", ModelShortName.of("Anthropic: Claude Haiku 5.5"))
        assertEquals("DeepSeek V4.1 Flash", ModelShortName.of("DeepSeek: DeepSeek V4.1 Flash"))
    }

    @Test
    fun aNameWithoutAPrefixIsUnchanged() {
        assertEquals("GLM 5.3 Flash", ModelShortName.of("GLM 5.3 Flash"))
        assertEquals("black-forest-labs/flux.2-klein-4b-preview", ModelShortName.of("black-forest-labs/flux.2-klein-4b-preview"))
    }

    @Test
    fun onlyTheFirstSeparatorCounts() {
        assertEquals("Model: Turbo", ModelShortName.of("Maker: Model: Turbo"))
    }

    @Test
    fun aNameThatIsOnlyAPrefixIsUnchanged() {
        assertEquals("Anthropic: ", ModelShortName.of("Anthropic: "))
    }
}
