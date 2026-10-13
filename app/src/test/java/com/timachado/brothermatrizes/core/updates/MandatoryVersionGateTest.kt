package com.timachado.brothermatrizes.core.updates

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MandatoryVersionGateTest {
    @Test fun versionBelowPublishedMinimumIsCritical() {
        assertTrue(UpdateVersionRules.isCritical(1L, 181L))
        assertTrue(UpdateVersionRules.isCritical(180L, 181L))
        assertFalse(UpdateVersionRules.isCritical(181L, 181L))
        assertFalse(UpdateVersionRules.isCritical(190L, 181L))
    }

    @Test fun minimumZeroMeansNoForcedUpdateBeforeOfficialRelease() {
        assertFalse(UpdateVersionRules.isCritical(1L, 0L))
        assertFalse(UpdateVersionRules.isCritical(180L, 0L))
        assertTrue(UpdateVersionRules.isUpdateAvailable(180L, 181L))
        assertFalse(UpdateVersionRules.isUpdateAvailable(181L, 181L))
    }
}
