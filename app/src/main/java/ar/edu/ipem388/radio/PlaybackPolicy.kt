package ar.edu.ipem388.radio

enum class AudioSource(val key: String, val title: String) {
    RECORDING("recording", "El Molle de beber"), LIVE("live", "Radio Escolar 388 en vivo");
    companion object {
        fun fromKey(key: String?): AudioSource = entries.firstOrNull { it.key == key } ?: RECORDING
        const val PREFS = "radio_prefs"
        const val SELECTED = "selected_source"
        const val EPISODE_PAGE = "https://www.ivoox.com/molle-beber-audios-mp3_rf_57184735_1.html"
    }
}

enum class PlaybackPhase(val label: String) {
    STOPPED("Detenido"), CONNECTING("Conectando…"), PLAYING("Reproduciendo"),
    PAUSED("En pausa"), ERROR("No se pudo reproducir"), FINISHED("Programa finalizado");
    companion object {
        fun fromKey(key: String?): PlaybackPhase = entries.firstOrNull { it.name == key } ?: STOPPED
    }
}

/** Three retries per interruption, reset only after audio actually plays. */
class ReconnectPolicy {
    private var attempt = 0
    fun nextDelayMs(): Long? = longArrayOf(2_000, 5_000, 10_000).getOrNull(attempt++)
    fun reset() { attempt = 0 }
}
