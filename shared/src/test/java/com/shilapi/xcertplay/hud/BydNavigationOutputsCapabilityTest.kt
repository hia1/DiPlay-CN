package com.shilapi.xcertplay.hud

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import com.shilapi.xcertplay.iap2.wire.Iap2Frame
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
class BydNavigationOutputsCapabilityTest {
    private val app get() = RuntimeEnvironment.getApplication()
    private val active = BydNavigationOutputs::class.java
        .getDeclaredField("oemSessionActive")
        .apply { isAccessible = true }

    @Before fun reset() {
        BydNavigationOutputs.endNow()
        BydClusterSong.end()
        BydCarPlayCall.end()
    }

    @After fun tearDown() {
        BydNavigationOutputs.endNow()
        shadowOf(app.packageManager).removePackage("com.byd.carsettings")
    }

    @Test fun genericStartDoesNotOpenTheBydProtocolSession() {
        BydNavigationOutputs.start(app)

        assertFalse(active.getBoolean(BydNavigationOutputs))
        BydNavigationOutputs.onFrame(Iap2Frame(ClusterSongState.NOW_PLAYING_UPDATE, ByteArray(0)))
        assertFalse(active.getBoolean(BydNavigationOutputs))
        assertFalse(BydNavigationOutputs.wheelSpeed(app) === BydWheelSpeedSource)
        assertNull(BydNavigationOutputs.batteryStatus(app).snapshot())
        assertNull(BydNavigationOutputs.parked(app))
        assertNull(BydClusterSong.current())
        assertNull(BydCarPlayCall.current())
    }

    @Test fun bydProfileStillSelectsTheBydProtocolProviders() {
        installBydProfile()

        assertTrue(BydOutputSettings.active(app))
        assertTrue(BydNavigationOutputs.wheelSpeed(app) === BydWheelSpeedSource)
    }

    private fun installBydProfile() {
        shadowOf(app.packageManager).installPackage(PackageInfo().apply {
            packageName = "com.byd.carsettings"
            applicationInfo = ApplicationInfo().apply {
                packageName = "com.byd.carsettings"
                flags = ApplicationInfo.FLAG_SYSTEM
            }
        })
    }
}
