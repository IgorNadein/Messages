package com.afkanerd.deku.messages.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class OneUiInsetLayoutRegressionTest {
    @Test
    fun compactBarReservesStatusBarInsteadOfDrawingNavigationUnderIt() {
        assertEquals(88, OneUiInsetLayout.totalHeight(64, 24))
    }

    @Test(expected = IllegalArgumentException::class)
    fun negativeInsetsAreRejected() {
        OneUiInsetLayout.totalHeight(64, -1)
    }
}
