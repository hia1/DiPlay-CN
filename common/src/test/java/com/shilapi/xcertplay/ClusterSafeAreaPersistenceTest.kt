package com.shilapi.xcertplay

import com.shilapi.xcertplay.airplay.SafeAreaRect
import com.shilapi.xcertplay.airplay.CarPlayClusterDisplay
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import org.junit.Assert.*
import org.robolectric.Shadows.shadowOf
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
class ClusterSafeAreaPersistenceTest {
    @Test fun clusterBoxDoesNotOverwriteTheMainMappingAtTheSameSize() {
        val context = RuntimeEnvironment.getApplication()
        val main = SafeAreaRect(20, 30, 1900, 700)
        val cluster = SafeAreaRect(300, 100, 1500, 620)
        AirPlayPersistence.saveSafeAreaRect(context, 1920, 720, main)
        AirPlayPersistence.saveClusterSafeAreaRect(context, cluster)
        assertEquals(main, AirPlayPersistence.loadSafeAreaRect(context, 1920, 720))
        assertEquals(cluster, AirPlayPersistence.loadClusterSafeAreaRect(context))
        AirPlayPersistence.clearClusterSafeAreaRect(context)
        assertNull(AirPlayPersistence.loadClusterSafeAreaRect(context))
        assertEquals(main, AirPlayPersistence.loadSafeAreaRect(context, 1920, 720))
    }

    @Test fun mapWithBuiltInTurnCardDefaultsButSavedChoicesRemain() {
        val context = RuntimeEnvironment.getApplication()
        // Native DiLink 3/4 cluster routing is a BYD-only profile.
        shadowOf(context.packageManager).installPackage(PackageInfo().apply {
            packageName = "com.byd.carsettings"
            applicationInfo = ApplicationInfo().apply {
                packageName = "com.byd.carsettings"
                flags = ApplicationInfo.FLAG_SYSTEM
            }
        })
        AirPlayPersistence.saveAdbClusterEnabled(context, true)
        assertEquals(com.shilapi.xcertplay.airplay.CarPlayClusterDisplay.Content.INSTRUMENTS,
            AirPlayPersistence.loadClusterContent(context))
        AirPlayPersistence.saveClusterContent(context, com.shilapi.xcertplay.airplay.CarPlayClusterDisplay.Content.MAP)
        assertEquals(com.shilapi.xcertplay.airplay.CarPlayClusterDisplay.Content.MAP,
            AirPlayPersistence.loadClusterContent(context))
    }

    @Test fun genericHeadUnitNeverDefaultsToTheBydInstrumentContent() {
        val context = RuntimeEnvironment.getApplication()
        // A generic head unit must not inherit the BYD native-cluster default, even with the
        // experimental ADB switch saved from an older installation.
        AirPlayPersistence.saveAdbClusterEnabled(context, true)
        assertEquals(CarPlayClusterDisplay.Content.MAP, AirPlayPersistence.loadClusterContent(context))
        AirPlayPersistence.saveClusterContent(context, CarPlayClusterDisplay.Content.TURN_CARD)
        assertEquals(CarPlayClusterDisplay.Content.TURN_CARD, AirPlayPersistence.loadClusterContent(context))
    }

}
