package com.shilapi.xcertplay

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
class HeadUnitCapabilitiesTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @Test fun genericHeadUnitHasNoOemFeatures() {
        val capabilities = HeadUnitCapabilities.detect(context)
        assertEquals(HeadUnitCapabilities.Family.GENERIC, capabilities.family)
        assertTrue(capabilities.isGeneric)
        assertTrue(capabilities.features.isEmpty())
        HeadUnitCapabilities.Feature.entries.forEach { assertFalse(capabilities.supports(it)) }
    }

    @Test fun systemBydSettingsPackageSelectsTheBydProfileWithoutNavigationOutput() {
        shadowOf(context.packageManager).installPackage(PackageInfo().apply {
            packageName = "com.byd.carsettings"
            applicationInfo = ApplicationInfo().apply {
                packageName = "com.byd.carsettings"
                flags = ApplicationInfo.FLAG_SYSTEM
            }
        })
        val capabilities = HeadUnitCapabilities.detect(context)
        assertEquals(HeadUnitCapabilities.Family.BYD, capabilities.family)
        assertTrue(capabilities.supports(HeadUnitCapabilities.Feature.OEM_VEHICLE_DATA))
        assertTrue(capabilities.supports(HeadUnitCapabilities.Feature.OEM_AUTOMATIC_HOTSPOT))
        assertFalse(capabilities.supports(HeadUnitCapabilities.Feature.OEM_NAVIGATION))
    }

    @Test fun navigationReceiverAddsOnlyNavigationToTheExplicitGenericFactory() {
        val capabilities = HeadUnitCapabilities.byd(
            oemNavigation = true,
            oemVehicleData = false,
            automaticHotspot = false,
        )
        assertTrue(capabilities.supports(HeadUnitCapabilities.Feature.OEM_NAVIGATION))
        assertFalse(capabilities.supports(HeadUnitCapabilities.Feature.OEM_VEHICLE_DATA))
        assertFalse(capabilities.supports(HeadUnitCapabilities.Feature.OEM_AUTOMATIC_HOTSPOT))
    }
}
