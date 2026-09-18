package com.nia.arcade

import com.nia.arcade.model.DirectionScheme
import com.nia.arcade.model.InputProfile
import org.junit.Assert.assertEquals
import org.junit.Test

class InputMappingTest {
    @Test
    fun enumContractsRemainStable() {
        assertEquals("KEYBOARD", InputProfile.KEYBOARD.name)
        assertEquals("CURSOR", InputProfile.CURSOR.name)
        assertEquals("ARROWS", DirectionScheme.ARROWS.name)
        assertEquals("WASD", DirectionScheme.WASD.name)
    }
}
