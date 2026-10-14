package com.timachado.brothermatrizes.core.account

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrotherUsageClientTest {
    @Test fun onlyServerReservationTokensAreAccepted() {
        assertTrue(BrotherUsageClient.isValidReservationKey(
            "usage_123e4567-e89b-12d3-a456-426614174000"))
        assertTrue(BrotherUsageClient.isValidReservationKey(
            "pro_123e4567-e89b-12d3-a456-426614174000"))
        assertFalse(BrotherUsageClient.isValidReservationKey(
            "beta_123e4567-e89b-12d3-a456-426614174000"))
        assertFalse(BrotherUsageClient.isValidReservationKey("usage_user-controlled"))
        assertFalse(BrotherUsageClient.isValidReservationKey("pro_"))
        assertFalse(BrotherUsageClient.isValidReservationKey(
            "usage_123e4567-e89b-12d3-a456-426614174000/../"))
    }
}
