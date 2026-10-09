package com.doffi4.doffisecure

import com.doffi4.doffisecure.security.DevModeManager
import org.junit.Assert.*
import org.junit.Test

class DevModeManagerTest {
    @Test fun `tester instructions unlock developer tools without changing vault authentication`() {
        val prefs = FakeSharedPreferences()
        val manager = DevModeManager(prefs)
        assertTrue(manager.isDevPasswordValid("Heytest08"))
        assertFalse(manager.devModeEnabled.value)
        manager.enableDevMode(true)
        assertTrue(DevModeManager(prefs).devModeEnabled.value)
        manager.enableDevMode(false)
        assertFalse(DevModeManager(prefs).devModeEnabled.value)
    }

    @Test fun `obsolete or altered tester code is rejected`() {
        val manager = DevModeManager(FakeSharedPreferences())
        listOf("IrkaSec08", "heytest08", " Heytest08", "Heytest08 ", "").forEach {
            assertFalse(manager.isDevPasswordValid(it))
        }
    }
}
