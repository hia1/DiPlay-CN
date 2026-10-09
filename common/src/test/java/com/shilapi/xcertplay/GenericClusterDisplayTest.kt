package com.shilapi.xcertplay

import android.content.Context
import android.hardware.display.DisplayManager
import android.view.Display
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDisplayManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
class GenericClusterDisplayTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val manager get() = context.getSystemService(DisplayManager::class.java)

    @Before fun reset() {
        context.getSharedPreferences("xcertplay_airplay", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun discoveryOnlyReturnsPublicPresentationDisplaysWithValidGeometry() {
        val presentation = display("Instrument cluster", "w1280dp-h480dp-mdpi")
        val ordinary = display("Ordinary virtual display", "w1920dp-h720dp-mdpi")
        shadowOf(manager.getDisplay(ordinary)).setFlags(0)
        try {
            val targets = GenericClusterDisplay.presentationTargets(context)
            assertEquals(listOf(presentation), targets.map { it.displayId })
            assertEquals(1280, targets.single().width)
            assertEquals(480, targets.single().height)
        } finally {
            ShadowDisplayManager.removeDisplay(ordinary)
            ShadowDisplayManager.removeDisplay(presentation)
        }
    }

    @Test fun aLoneTargetIsAutomaticOnlyWhenRequested() {
        val id = display("Instrument cluster", "w1280dp-h480dp-mdpi")
        val target = GenericClusterDisplay.targetOf(manager.getDisplay(id))!!
        try {
            assertNull(GenericClusterDisplay.resolve(context, listOf(target), automaticWhenUnique = false))
            assertEquals(target.displayId, GenericClusterDisplay.resolve(
                context, listOf(target), automaticWhenUnique = true,
            )?.displayId)
        } finally {
            ShadowDisplayManager.removeDisplay(id)
        }
    }

    @Test fun savedSelectionFallsBackToNameAndGeometryWhenTheDisplayIdChanges() {
        val first = GenericClusterDisplay.Target(7, "Instrument cluster", 1280, 480)
        val replacement = GenericClusterDisplay.Target(19, "Instrument cluster", 1280, 480)
        AirPlayPersistence.saveClusterDisplayTarget(context, first.stableKey)
        assertEquals(replacement, GenericClusterDisplay.resolve(
            context, listOf(replacement), automaticWhenUnique = false,
        ))
    }

    @Test fun malformedSelectionIsIgnored() {
        AirPlayPersistence.saveClusterDisplayTarget(context, "not-a-target")
        assertNull(GenericClusterDisplay.decode(AirPlayPersistence.loadClusterDisplayTarget(context)))
        assertTrue(GenericClusterDisplay.presentationTargets(context).isEmpty())
    }

    private fun display(name: String, spec: String): Int {
        val id = ShadowDisplayManager.addDisplay(spec, 5)
        shadowOf(manager.getDisplay(id)).apply {
            setName(name)
            setFlags(Display.FLAG_PRESENTATION)
        }
        return id
    }
}
