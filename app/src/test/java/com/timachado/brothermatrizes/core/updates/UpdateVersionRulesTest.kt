package com.timachado.brothermatrizes.core.updates

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateVersionRulesTest {
    @Test fun equalVersionsDoNotRequireUpdate() {
        assertFalse(UpdateVersionRules.isUpdateAvailable(167L, 167L))
    }

    @Test fun higherPublishedVersionOffersUpdate() {
        assertTrue(UpdateVersionRules.isUpdateAvailable(167L, 168L))
    }

    @Test fun lowerPublishedVersionNeverDowngrades() {
        assertFalse(UpdateVersionRules.isUpdateAvailable(168L, 167L))
    }

    @Test fun malformedOrUnknownInstalledVersionNeverAutoUpdates() {
        assertFalse(UpdateVersionRules.isUpdateAvailable(0L, 168L))
    }

    @Test fun minimumSupportedCodeControlsCriticalWarning() {
        assertFalse(UpdateVersionRules.isCritical(167L, 0L))
        assertFalse(UpdateVersionRules.isCritical(167L, 167L))
        assertTrue(UpdateVersionRules.isCritical(167L, 168L))
    }
}
