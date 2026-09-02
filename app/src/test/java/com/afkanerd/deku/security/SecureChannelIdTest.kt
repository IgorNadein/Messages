package com.afkanerd.deku.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class SecureChannelIdTest {
    @Test
    fun sameContactNumberOnDifferentSimsHasDifferentRatchetState() {
        val first = SecureChannelId.storageAddress("+79990000000", 10)
        val second = SecureChannelId.storageAddress("+79990000000", 20)

        assertNotEquals(first, second)
        assertEquals(first, SecureChannelId.storageAddress("+79990000000", 10))
    }

    @Test
    fun differentContactNumbersOnSameSimHaveDifferentRatchetState() {
        assertNotEquals(
            SecureChannelId.storageAddress("+79990000000", 10),
            SecureChannelId.storageAddress("+79990000001", 10),
        )
    }
}
