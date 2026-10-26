package io.github.sibmaks.app.dose.data

/**
 * User-selectable vibration presets for dose reminders. Timings alternate off/on starting with the
 * initial delay; amplitudes pair one-to-one with timings, where 0 keeps the vibrator off for that
 * step (1..255 is the strength). The chosen preset drives both the pop-up haptic and the
 * notification-feed channel pattern.
 */
enum class ReminderVibrationStyle(
    val preference: String,
    val timings: LongArray,
    val amplitudes: IntArray,
    val repeat: Int = -1
) {
    // The original double buzz kept for existing users.
    DEFAULT("default", longArrayOf(0, 180, 100, 180), intArrayOf(0, 255, 0, 255)),
    SHORT("short", longArrayOf(0, 60), intArrayOf(0, 168)),
    DOUBLE("double", longArrayOf(0, 90, 120, 90), intArrayOf(0, 208, 0, 208)),
    PULSE("pulse", longArrayOf(0, 80, 160, 80, 160, 80), intArrayOf(0, 128, 0, 128, 0, 128)),
    SOS(
        "sos",
        longArrayOf(
            0, 80, 70, 80, 70, 80, 200, 240, 70, 240, 70, 240, 200, 80, 70, 80, 70, 80
        ),
        intArrayOf(
            0, 255, 0, 255, 0, 255, 0, 255, 0, 255, 0, 255, 0, 255, 0, 255, 0, 255
        )
    ),

    // Arcade power-up: quick taps rising in strength, resolved by one long confident buzz.
    GAME(
        "game",
        longArrayOf(0, 45, 55, 45, 55, 45, 55, 45, 55, 45, 55, 45, 55, 320),
        intArrayOf(0, 56, 0, 88, 0, 120, 0, 152, 0, 184, 0, 216, 0, 255)
    ),

    // Doom E1M1: the galloping riff, fading like the descending notes, with a heavy final hit.
    DOOM(
        "doom",
        longArrayOf(0, 45, 45, 45, 45, 140, 110, 45, 45, 45, 45, 140, 110, 45, 45, 45, 45, 140, 110, 240),
        intArrayOf(0, 255, 0, 255, 0, 255, 0, 216, 0, 216, 0, 216, 0, 184, 0, 184, 0, 184, 0, 255)
    ),

    // Minecraft XP orbs: a quick run of bright "pling" ticks.
    MINECRAFT(
        "minecraft",
        longArrayOf(0, 30, 45, 30, 45, 30, 45, 30, 45, 30, 45, 30, 45, 30, 45, 60),
        intArrayOf(0, 160, 0, 208, 0, 160, 0, 208, 0, 160, 0, 208, 0, 160, 0, 255)
    ),

    // The GTA mission-passed jingle: two rising "ta-ta-daa" phrases and a sustained resolve.
    GTA(
        "gta",
        longArrayOf(0, 55, 55, 55, 55, 110, 130, 55, 55, 55, 55, 110, 130, 280),
        intArrayOf(0, 150, 0, 195, 0, 240, 0, 150, 0, 195, 0, 240, 0, 255)
    ),

    // Red Alert 2's Hell March: a chainsaw rev accelerating into heavy marching stomps.
    HELL_MARCH(
        "hell_march",
        longArrayOf(0, 30, 45, 30, 40, 30, 35, 35, 30, 35, 70, 190, 140, 190, 140, 190, 140, 280),
        intArrayOf(0, 80, 0, 112, 0, 144, 0, 176, 0, 208, 0, 255, 0, 255, 0, 255, 0, 255)
    ),
    ;

    companion object {
        fun fromPreference(value: String?): ReminderVibrationStyle =
            entries.firstOrNull { it.preference == value } ?: DEFAULT
    }
}
