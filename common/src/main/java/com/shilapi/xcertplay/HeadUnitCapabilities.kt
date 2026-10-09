package com.shilapi.xcertplay

import android.content.Context
import com.shilapi.xcertplay.hud.BydOutputSettings

/**
 * The only capability boundary that product UI may use. Detection failures always fall back to the
 * safest profile: a generic head unit without access to vendor-only services.
 */
object HeadUnitCapabilities {
    enum class Family(val key: String) {
        GENERIC("generic"),
        BYD("byd"),
    }

    enum class Feature {
        OEM_NAVIGATION,
        OEM_VEHICLE_DATA,
        OEM_AUTOMATIC_HOTSPOT,
        OEM_STEERING_WHEEL_KEYS,
        OEM_CALL_CONTROLS,
    }

    data class Snapshot(
        val family: Family,
        val features: Set<Feature>,
    ) {
        init {
            require(family != Family.GENERIC || features.isEmpty()) {
                "The generic profile must not advertise OEM features"
            }
        }

        val isGeneric: Boolean get() = family == Family.GENERIC

        fun supports(feature: Feature): Boolean = feature in features
    }

    fun detect(context: Context): Snapshot {
        val byd = runCatching { BydOutputSettings.available(context.applicationContext) }
            .getOrDefault(false)
        val navigation = if (!byd) {
            false
        } else {
            runCatching { BydOutputSettings.navigationAvailable(context.applicationContext) }
                .getOrDefault(false)
        }
        return runCatching { profile(byd, navigation) }.getOrDefault(generic())
    }

    fun detect(
        context: Context,
        isBydHeadUnit: Boolean,
        oemNavigationAvailable: Boolean,
    ): Snapshot = runCatching { profile(isBydHeadUnit, oemNavigationAvailable) }.getOrDefault(generic())

    /** Convenience for runtime gates that must not duplicate capability-detection rules. */
    fun supports(context: Context, feature: Feature): Boolean = detect(context).supports(feature)

    fun generic(): Snapshot = Snapshot(Family.GENERIC, emptySet())

    fun byd(
        oemNavigation: Boolean = false,
        oemVehicleData: Boolean = true,
        automaticHotspot: Boolean = true,
    ): Snapshot = Snapshot(
        Family.BYD,
        buildSet {
            if (oemNavigation) add(Feature.OEM_NAVIGATION)
            if (oemVehicleData) {
                add(Feature.OEM_VEHICLE_DATA)
                add(Feature.OEM_STEERING_WHEEL_KEYS)
                add(Feature.OEM_CALL_CONTROLS)
            }
            if (automaticHotspot) add(Feature.OEM_AUTOMATIC_HOTSPOT)
        },
    )

    private fun profile(byd: Boolean, oemNavigation: Boolean): Snapshot =
        if (byd) byd(oemNavigation = oemNavigation) else generic()
}
