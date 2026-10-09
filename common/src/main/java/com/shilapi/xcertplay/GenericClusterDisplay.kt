package com.shilapi.xcertplay

import android.content.Context
import android.hardware.display.DisplayManager
import android.view.Display

/**
 * Generic Android presentation-display discovery for instrument-cluster-like screens.
 *
 * A head unit that exposes its cluster as a standard presentation display does not need any
 * vendor service or write. Detection is deliberately conservative: DiPlay auto-selects a lone
 * presentation display only after the user enables the dashboard map, and otherwise requires an
 * explicit selection in Settings. This avoids silently writing CarPlay's map to a passenger or
 * rear-seat display when several presentation displays are present.
 */
internal object GenericClusterDisplay {
    data class Target(
        val displayId: Int,
        val name: String,
        val width: Int,
        val height: Int,
    ) {
        val stableKey: String get() = encode(this)
        val label: String get() = "${name.ifBlank { "display $displayId" }}  ·  ${width}×${height}"
    }

    private const val FIELD_SEPARATOR = "\u0000"

    fun presentationTargets(context: Context): List<Target> = runCatching {
        context.getSystemService(DisplayManager::class.java)
            ?.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
            .orEmpty()
            .mapNotNull(::targetOf)
            .distinctBy { it.stableKey }
            .sortedWith(compareBy<Target> { it.name.lowercase() }.thenBy { it.width }.thenBy { it.height })
    }.getOrDefault(emptyList())

    fun targetOf(display: Display?): Target? = runCatching {
        display?.takeIf { it.isValid && it.displayId > 0 }?.let {
            val size = ClusterMapPresentation.sizeOf(it)
            if (size.x <= 0 || size.y <= 0) return@let null
            Target(
                displayId = it.displayId,
                name = it.name.orEmpty(),
                width = size.x,
                height = size.y,
            )
        }
    }.getOrNull()

    fun resolve(
        context: Context,
        targets: List<Target> = presentationTargets(context),
        automaticWhenUnique: Boolean = AirPlayPersistence.loadClusterMapEnabled(context),
    ): Target? {
        if (targets.isEmpty()) return null
        val saved = decode(AirPlayPersistence.loadClusterDisplayTarget(context))
        if (saved != null) {
            return targets.firstOrNull {
                it.name == saved.name && it.width == saved.width && it.height == saved.height &&
                    it.displayId == saved.displayId
            } ?: targets.firstOrNull {
                it.name == saved.name && it.width == saved.width && it.height == saved.height
            }
        }
        return if (automaticWhenUnique && targets.size == 1) targets.single() else null
    }

    fun findDisplay(context: Context, automaticWhenUnique: Boolean = true): Display? {
        val target = resolve(context, automaticWhenUnique = automaticWhenUnique) ?: return null
        return context.getSystemService(DisplayManager::class.java)
            ?.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
            .orEmpty()
            .firstOrNull { it.displayId == target.displayId && it.name.orEmpty() == target.name }
    }

    fun saveSelection(context: Context, target: Target?) {
        AirPlayPersistence.saveClusterDisplayTarget(context, target?.stableKey)
    }

    fun encode(target: Target): String = listOf(
        target.name,
        target.width.toString(),
        target.height.toString(),
        target.displayId.toString(),
    ).joinToString(FIELD_SEPARATOR)

    fun decode(value: String?): Target? {
        val fields = value?.split(FIELD_SEPARATOR) ?: return null
        if (fields.size != 4) return null
        val name = fields[0].takeIf { it.isNotBlank() } ?: return null
        val width = fields[1].toIntOrNull()?.takeIf { it > 0 } ?: return null
        val height = fields[2].toIntOrNull()?.takeIf { it > 0 } ?: return null
        val displayId = fields[3].toIntOrNull()?.takeIf { it > 0 } ?: return null
        return Target(displayId, name, width, height)
    }
}
