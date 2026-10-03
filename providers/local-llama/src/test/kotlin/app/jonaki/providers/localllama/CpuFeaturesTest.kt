package app.jonaki.providers.localllama

import org.junit.Assert.assertEquals
import org.junit.Test

class CpuFeaturesTest {
    // Shortened from a Snapdragon 7s Gen 3 (Cortex-A720 and A520 cores).
    private val modernCore = "Features\t: fp asimd evtstrm aes pmull sha1 sha2 crc32 atomics fphp asimdhp cpuid asimdrdm " +
        "jscvt fcma lrcpc dcpop sha3 sm3 sm4 asimddp sha512 asimdfhm dit uscat ilrcpc flagm ssbs sb paca pacg " +
        "dcpodp sve2 sveaes i8mm bf16"

    // A Cortex-A76 (Snapdragon 855) has dot product but no i8mm.
    private val olderCore = "Features\t: fp asimd evtstrm aes pmull sha1 sha2 crc32 atomics fphp asimdhp cpuid asimdrdm " +
        "lrcpc dcpop asimddp"

    @Test
    fun aPhoneWithEveryFeatureMissesNothing() {
        val cpuinfo = "processor\t: 0\n$modernCore\n\nprocessor\t: 1\n$modernCore\n"
        assertEquals(emptyList<String>(), CpuFeatures.missing(cpuinfo))
    }

    @Test
    fun anOlderCoreLacksI8mm() {
        assertEquals(listOf("i8mm"), CpuFeatures.missing("processor\t: 0\n$olderCore\n"))
    }

    @Test
    fun oneCoreWithoutAFeatureIsEnoughToRefuse() {
        val cpuinfo = "processor\t: 0\n$modernCore\n\nprocessor\t: 1\n$olderCore\n"
        assertEquals(listOf("i8mm"), CpuFeatures.missing(cpuinfo))
    }

    @Test
    fun noFeatureLinesMeansNothingIsKnownToBeThere() {
        assertEquals(listOf("asimddp", "asimdhp", "i8mm"), CpuFeatures.missing("Hardware\t: unknown\n"))
    }
}
