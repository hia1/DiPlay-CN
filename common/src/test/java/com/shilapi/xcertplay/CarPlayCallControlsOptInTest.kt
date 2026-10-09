package com.shilapi.xcertplay

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import com.shilapi.xcertplay.hud.BydCarPlayCall
import com.shilapi.xcertplay.hud.BydOutputSettings
import com.shilapi.xcertplay.hud.CarPlayCallState
import com.shilapi.xcertplay.iap2.message.Iap2Messages
import com.shilapi.xcertplay.orchestration.CarPlayController
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
class CarPlayCallControlsOptInTest {
    private val app get() = RuntimeEnvironment.getApplication()

    @Before fun enableBydProfile() {
        shadowOf(app.packageManager).installPackage(PackageInfo().apply {
            packageName = "com.byd.carsettings"
            applicationInfo = ApplicationInfo().apply {
                packageName = "com.byd.carsettings"
                flags = ApplicationInfo.FLAG_SYSTEM
            }
        })
    }

    @After fun cleanup() {
        BydOutputSettings.setCarPlayCallControls(app, false)
        BydCarPlayCall.end()
        shadowOf(app.packageManager).removePackage("com.byd.carsettings")
    }

    @Test fun newCallKeysCannotConsumeOrSendAnythingWithoutOptIn() {
        val controller = mock(CarPlayController::class.java)
        for (code in listOf(313, 314, 309)) {
            assertFalse(CarPlayCallKeys.onKey(app, code, true, controller))
            assertFalse(CarPlayCallKeys.onKey(app, code, false, controller))
        }
        verifyNoInteractions(controller)
    }

    @Test fun explicitOptInCannotEnableUnverifiedControlsOnGenericHeadUnit() {
        shadowOf(app.packageManager).removePackage("com.byd.carsettings")
        val controller = mock(CarPlayController::class.java)
        `when`(controller.activeAirPlaySessionToken()).thenReturn(Any())
        BydCarPlayCall.onFrame(Iap2Messages.buildRaw(CarPlayCallState.CALL_STATE_UPDATE) {
            u8(2, 2); string(4, "incoming-call")
        })
        BydOutputSettings.setCarPlayCallControls(app, true)
        assertFalse(CarPlayCallKeys.onKey(app, 313, true, controller))
        assertFalse(CarPlayCallKeys.onKey(app, 313, false, controller))
        verifyNoInteractions(controller)
    }

    @Test fun explicitOptInEnablesAnswerAndDisableRestoresPassThrough() {
        val controller = mock(CarPlayController::class.java)
        `when`(controller.activeAirPlaySessionToken()).thenReturn(Any())
        BydCarPlayCall.onFrame(Iap2Messages.buildRaw(CarPlayCallState.CALL_STATE_UPDATE) {
            u8(2, 2); string(4, "incoming-call")
        })
        BydOutputSettings.setCarPlayCallControls(app, true)
        assertTrue(CarPlayCallKeys.onKey(app, 313, true, controller))
        assertTrue(CarPlayCallKeys.onKey(app, 313, false, controller))
        verify(controller).answerCall()
        BydOutputSettings.setCarPlayCallControls(app, false)
        assertFalse(CarPlayCallKeys.onKey(app, 314, false, controller))
        verify(controller, never()).endCall()
    }
}
