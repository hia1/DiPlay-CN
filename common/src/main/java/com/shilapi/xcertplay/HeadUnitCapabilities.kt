package com.shilapi.xcertplay

import android.content.Context
import com.shilapi.xcertplay.hud.BydOutputSettings

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
    }

    data class Snapshot(
        val family: Family,
        val features: Set<Feature>,
    ) {
        val isGeneric: Boolean get() = family == Family.GENERIC

        fun supports(feature: Feature): Boolean = feature in features
    }

    fun detect(
        context: Context,
        isBydHeadUnit: Boolean = CarHotspotSetup.isBydHeadUnit(context),
        oemNavigationAvailable: Boolean = BydOutputSettings.navigationAvailable(context),
    ): Snapshot {
        val byd = isBydHeadUnit || oemNavigationAvailable || BydOutputSettings.available(context)
        val features = buildSet {
            if (oemNavigationAvailable) add(Feature.OEM_NAVIGATION)
            if (byd) {
                add(Feature.OEM_VEHICLE_DATA)
                add(Feature.OEM_STEERING_WHEEL_KEYS)
            }
            if (isBydHeadUnit) add(Feature.OEM_AUTOMATIC_HOTSPOT)
        }
        return Snapshot(if (byd) Family.BYD else Family.GENERIC, features)
    }

    fun generic(): Snapshot = Snapshot(Family.GENERIC, emptySet())

    fun byd(
        oemNavigation: Boolean = false,
        oemVehicleData: Boolean = true,
        automaticHotspot: Boolean = true,
    ): Snapshot = Snapshot(
        Family.BYD,
        buildSet {
            if (oemNavigation) add(Feature.OEM_NAVIGATION)
            if (oemVehicleData) add(Feature.OEM_VEHICLE_DATA)
            if (automaticHotspot) add(Feature.OEM_AUTOMATIC_HOTSPOT)
            if (oemVehicleData) add(Feature.OEM_STEERING_WHEEL_KEYS)
        },
    )
}
