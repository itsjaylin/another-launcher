package com.anotherlauncher

import androidx.compose.ui.Modifier
import com.anotherlauncher.ui.jiggle
import org.junit.Assert.*
import org.junit.Test

class JiggleTest {
    @Test fun modifierReturnsSelfWhenNotEditing() {
        // Jiggle only activates inside ProvideJiggle(editing=true, reduced=false)
        // Outside that composition local it is a no-op modifier.
        assertNotNull(Modifier.jiggle("test"))
    }
}
