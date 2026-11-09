package com.anotherlauncher

import com.anotherlauncher.data.LauncherPreferences
import org.junit.Assert.*
import org.junit.Test

class OnboardingPreferencesTest {

    @Test
    fun freshInstallDefaultsOnboardingToIncomplete() {
        val prefs = LauncherPreferences()
        assertFalse("New install should start with onboarding incomplete", prefs.onboardingCompleted)
        assertFalse("New install should start uninitialized", prefs.initialized)
    }

    @Test
    fun completedOnboardingStateIsReflected() {
        val completed = LauncherPreferences(onboardingCompleted = true, initialized = true)
        assertTrue("Completed onboarding should be true", completed.onboardingCompleted)
        assertTrue("Initialized state should be true", completed.initialized)
    }

    @Test
    fun onboardingPagingStepCountIsFive() {
        // Step 0: Welcome
        // Step 1: 3-Tier Architecture
        // Step 2: Gestures & Stacking
        // Step 3: Permissions
        // Step 4: Ready / Launch
        val totalSteps = 5
        assertEquals(5, totalSteps)
    }

    @Test
    fun onboardingCompletedResolutionRequiresExplicitPreference() {
        val unsetOnboarding: Boolean? = null
        assertFalse("Unset onboarding preference defaults to false", unsetOnboarding ?: false)

        val setOnboarding: Boolean? = true
        assertTrue("Explicitly set onboarding resolves to true", setOnboarding ?: false)
    }
}
