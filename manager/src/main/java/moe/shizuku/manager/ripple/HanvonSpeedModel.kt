package moe.shizuku.manager.ripple

/** Pure speed-state rules shared by the UI and tests. */
object HanvonSpeedModel {
    const val MIN = 30
    const val MAX = 300
    const val STEP = 10
    const val SLOW = 50
    const val MEDIUM = 100
    const val FAST = 200

    fun normalize(value: Int): Int {
        return value.coerceIn(MIN, MAX)
    }

    fun isPreset(value: Int): Boolean {
        return value == SLOW || value == MEDIUM || value == FAST
    }

    fun progressFor(value: Int): Int {
        return ((normalize(value) - MIN) / STEP).coerceIn(0, (MAX - MIN) / STEP)
    }

    fun valueForProgress(progress: Int): Int {
        return normalize(MIN + progress.coerceIn(0, (MAX - MIN) / STEP) * STEP)
    }

    fun presetValue(id: Int): Int? {
        return when (id) {
            41 -> SLOW
            42 -> MEDIUM
            43 -> FAST
            else -> null
        }
    }
}
