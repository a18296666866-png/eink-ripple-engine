package moe.shizuku.manager.ripple

/** Ignore only duplicate cross-source reporting of one physical page-turn action. */
class PageEventGate(private val mirrorWindowMs: Long = 120L) {
    private var lastSource = ""
    private var lastDirectionNext = false
    private var lastTimeMs = Long.MIN_VALUE

    @Synchronized
    fun accept(source: String, next: Boolean, atMs: Long): Boolean {
        val delta = atMs - lastTimeMs
        val mirrored = source != lastSource && next == lastDirectionNext &&
            delta >= 0L && delta < mirrorWindowMs
        if (mirrored) return false
        lastSource = source
        lastDirectionNext = next
        lastTimeMs = atMs
        return true
    }

    @Synchronized
    fun reset() {
        lastSource = ""
        lastDirectionNext = false
        lastTimeMs = Long.MIN_VALUE
    }
}
