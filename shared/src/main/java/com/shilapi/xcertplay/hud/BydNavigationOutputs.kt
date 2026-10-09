package com.shilapi.xcertplay.hud

import android.content.Context
import com.shilapi.xcertplay.iap2.wire.Iap2Frame

/** Nonblocking boundary between phone control messages and vendor services. */
object BydNavigationOutputs {
    /** Recover a journaled interrupted output when the app opens, even before a phone reconnects. */
    fun onAppOpened(context: Context) {
        val app = context.applicationContext
        if (!BydOutputSettings.active(app)) return
        BydBluetoothSuspend.onAppOpened(app)
        BydOemClusterNavi.restoreIfNeeded(app)
        BydDiLink3ClusterOutput.restoreIfNeeded(app)
        com.shilapi.xcertplay.network.WifiScanPause.restoreIfNeeded(app)
        if (BydStandaloneHudOutput.available(app)) start(app)
        // Read the battery early, so a reading is ready when CarPlay identifies (see batteryStatus).
        if (BydOutputSettings.batteryToIphoneActive(app)) BydBatteryStatus.start(app)
        // DiLink 3 creates its cluster map display only once the cluster has projected.
        if (BydOutputSettings.enabled(app)) BydClusterBridge.prepareProjectionDisplay(app)
    }
    fun setDiagnosticHold(hold: Boolean) { BydStandaloneHudOutput.syntheticHold = hold }
    @Volatile private var useStandalone = false

    /** Set only after a context has passed the BYD production profile check. */
    @Volatile private var oemSessionActive = false
    @Volatile private var overlayListener: ((ClusterTurnGuidance?) -> Unit)? = null
    private val overlayLock = Any()
    private var publishedOverlay: ClusterTurnGuidance? = null
    private val overlayRoute = BydHudRouteState(
        staleRouteNs = 120_000_000_000L,
        emptyListHideNs = 8_000_000_000L,
        keepAcrossNoRoute = true,
    )
    private val standalone = NavigationOutputWorker("diplay-standalone-output", BydStandaloneNavigationBridge::clear)
    private val hud = NavigationOutputWorker("diplay-hud-output", BydHudBridge::clear)
    private val cluster = NavigationOutputWorker("diplay-cluster-output", BydClusterBridge::clear)

    /** The host reports whether its CarPlay map window is on the cluster (see [BydClusterMapPause]). */
    fun setClusterMapShown(shown: Boolean) {
        if (!oemSessionActive) return
        BydClusterMapPause.clusterMapShown = shown
        BydClusterBridge.setMapShown(shown)
    }

    /** The running CarPlay session: told every second whether the cluster currently shows the map. */
    fun setClusterStreamControl(control: (Boolean) -> Unit) { BydClusterMapPause.streamControl = control }

    /** Latest ADB wheel-menu navi mode, or null when it cannot be read. */
    fun clusterNaviMode(): BydClusterNaviMode? = BydClusterMapPause.lastNaviMode

    /** Called whenever the ADB navi mode changes. Pass null to stop following. */
    fun setClusterNaviModeListener(listener: ((BydClusterNaviMode?) -> Unit)?) {
        if (!oemSessionActive && listener != null) return
        BydClusterMapPause.onNaviMode = listener
        listener?.invoke(BydClusterMapPause.lastNaviMode)
    }

    fun clearClusterStreamControl(control: (Boolean) -> Unit) {
        if (BydClusterMapPause.streamControl == control) BydClusterMapPause.streamControl = null
    }

    /**
     * The car's battery for the iPhone's vehicle status; starts reading it over adb. The electric
     * vehicle is declared only once a reading is there (see withVehicleStatusFrom).
     */
    fun batteryStatus(context: Context): com.shilapi.xcertplay.transport.VehicleStatusProvider {
        if (!BydOutputSettings.active(context)) return com.shilapi.xcertplay.transport.VehicleStatusProvider { null }
        return BydBatteryStatus.also { it.start(context) }
    }

    /** The car's wheel speed and gear for the iPhone's dead reckoning; read over adb while asked for. */
    fun wheelSpeed(context: Context): com.shilapi.xcertplay.transport.VehicleSpeedSource {
        if (!BydOutputSettings.active(context)) return InertVehicleSpeedSource
        return BydWheelSpeedSource.attach(context)
    }

    /** Whether the car is in P (read over adb), or null when it cannot tell. Blocking. */
    fun parked(context: Context): Boolean? {
        if (!BydOutputSettings.active(context)) return null
        return BydParkedState.parked(context.applicationContext)
    }

    fun start(context: Context) {
        val app = context.applicationContext
        oemSessionActive = BydOutputSettings.active(app)
        if (!oemSessionActive) {
            endNow()
            return
        }
        useStandalone = BydStandaloneHudOutput.available(app)
        if (useStandalone) standalone.start { BydStandaloneNavigationBridge.initialize(app) }
        else {
            hud.start { BydHudBridge.initialize(app) }
            cluster.start { BydClusterBridge.initialize(app) }
        }
        BydClusterMapPause.initialize(app)
        BydClusterSong.attach(app)
        BydCarPlayCall.attach(app)
    }

    internal fun onFrame(frame: Iap2Frame) {
        if (!oemSessionActive) return
        if (frame.messageId == ClusterSongState.NOW_PLAYING_UPDATE) {
            BydClusterSong.onFrame(frame)
            return
        }
        if (frame.messageId == CarPlayCallState.CALL_STATE_UPDATE) {
            BydCarPlayCall.onFrame(frame)
            return
        }
        if (frame.messageId != BydHudRouteState.ROUTE_GUIDANCE_UPDATE &&
            frame.messageId != BydHudRouteState.ROUTE_GUIDANCE_MANEUVER_UPDATE) return
        val owned = frame // Iap2Frame is immutable and defensively copies its payload.
        updateOverlay(owned)
        if (useStandalone) standalone.submit { BydStandaloneNavigationBridge.onFrame(owned) }
        else {
            hud.submit { BydHudBridge.onFrame(owned) }
            cluster.submit { BydClusterBridge.onFrame(owned) }
        }
    }

    /** Live next-turn state for the dashboard overlay. Called from the iAP2 thread. */
    fun setTurnOverlayListener(listener: ((ClusterTurnGuidance?) -> Unit)?) {
        overlayListener = listener
        val next = currentOverlay()
        synchronized(overlayLock) { publishedOverlay = next }
        listener?.invoke(next)
    }

    private fun updateOverlay(frame: Iap2Frame) {
        val change = synchronized(overlayLock) { overlayRoute.accept(frame.messageId, frame.payload) }
        if (change != BydHudRouteChange.NONE) refreshTurnOverlay()
    }

    /** Called every second while the presentation owner lives, even without incoming frames. */
    fun refreshTurnOverlay() {
        val next = synchronized(overlayLock) {
            val current = currentOverlay()
            if (current == publishedOverlay) return
            publishedOverlay = current
            current
        }
        overlayListener?.invoke(next)
    }

    private fun currentOverlay(): ClusterTurnGuidance? = synchronized(overlayLock) {
        overlayRoute.currentApple()?.let { apple ->
            ClusterTurnGuidance.from(BydClusterFrame.from(apple)).copy(
                arrivalEpochSeconds = apple.arrivalEpochSeconds,
                remainingSeconds = apple.remainingSeconds,
                remainingMeters = apple.remainingMeters,
            )
        }
    }

    /** The dashboard song setting changed; applies at once. */
    fun clusterSongChanged(enabled: Boolean) {
        if (oemSessionActive) BydClusterSong.settingChanged(enabled)
    }

    /** The CarPlay call setting changed; applies at once. */
    fun carPlayCallsChanged(enabled: Boolean) {
        if (oemSessionActive) BydCarPlayCall.settingChanged(enabled)
    }

    /** A CarPlay session became active; ready the call card so calls show without the watcher's start delay. */
    fun carPlaySessionStarted() {
        if (oemSessionActive) BydCarPlayCall.sessionStarted()
    }

    /** The iPhone's current call, for the steering wheel's call keys. */
    fun carPlayCall(): CarPlayCallCard? = BydCarPlayCall.current()

    /** The dashboard song's "only when it changes" setting changed; applies at once. */
    fun clusterSongOnChangeChanged() {
        if (oemSessionActive) BydClusterSong.onChangeSettingChanged()
    }

    /** A short note where the song shows on the dashboard; needs the same ADB access as the song. */
    fun dashboardNote(text: String, source: Int? = null) {
        if (oemSessionActive) BydClusterSong.note(text, source)
    }

    /** Best effort while alive; Android does not guarantee callbacks before force-stop. */
    fun endNow(preserveTurnOverlay: Boolean = false) {
        oemSessionActive = false
        standalone.clear(); hud.clear(); cluster.clear(); BydClusterSong.end(); BydCarPlayCall.end()
        // Only a wireless session replacement retains the card. Explicit controller close
        // and wired disconnect still clear it immediately.
        if (!preserveTurnOverlay) {
            synchronized(overlayLock) { overlayRoute.clear() }
            refreshTurnOverlay()
        }
    }
}

/** No-op provider used when BYD protocol access is not available on the current head unit. */
private object InertVehicleSpeedSource : com.shilapi.xcertplay.transport.VehicleSpeedSource {
    override fun start() = Unit
    override fun stop() = Unit
    override fun drain(): com.shilapi.xcertplay.transport.VehicleSpeedReading? = null
}
