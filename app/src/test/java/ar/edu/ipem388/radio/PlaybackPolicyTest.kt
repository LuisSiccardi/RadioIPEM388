package ar.edu.ipem388.radio

import org.junit.Assert.*
import org.junit.Test

class PlaybackPolicyTest {
    @Test fun failedLiveStreamHasAFiniteRetryBudget() {
        val policy = ReconnectPolicy()
        assertEquals(listOf(2000L, 5000L, 10000L, null, null), (1..5).map { policy.nextDelayMs() })
        policy.reset()
        assertEquals(2000L, policy.nextDelayMs())
    }
    @Test fun newOrInvalidSelectionDefaultsToTheExplicitRecording() {
        assertEquals(AudioSource.RECORDING, AudioSource.fromKey(null))
        assertEquals(AudioSource.RECORDING, AudioSource.fromKey("obsolete"))
        assertEquals(AudioSource.LIVE, AudioSource.fromKey("live"))
        assertEquals(PlaybackPhase.STOPPED, PlaybackPhase.fromKey("obsolete"))
    }
    @Test fun htmlWithAnMp3SuffixIsNotAudio() {
        val html = "<!doctype html><html>Stream unavailable</html>".toByteArray()
        assertFalse(StreamResolver.isAudioPrefix(html, html.size, "audio/mpeg"))
        assertFalse(StreamResolver.isAudioPrefix("ID3test".toByteArray(), 7, "text/html"))
        assertFalse(StreamResolver.isAudioPrefix(byteArrayOf(), 0, "audio/mpeg"))
    }
    @Test fun acceptsRecognizableAudioAndHlsHeaders() {
        assertTrue(StreamResolver.isAudioPrefix("ID3test".toByteArray(), 7, "application/octet-stream"))
        assertTrue(StreamResolver.isAudioPrefix(byteArrayOf(0xff.toByte(), 0xfb.toByte()), 2, "audio/mpeg"))
        assertTrue(StreamResolver.isAudioPrefix("#EXTM3U\n".toByteArray(), 8, "application/vnd.apple.mpegurl"))
    }
    @Test fun decodesPortalLinksAndRejectsLookalikeDomains() {
        val html = """https:\/\/uk26.listen2myradio.com\/live.mp3?a=1&amp;b=2
            https://listen2myradio.com.attacker.invalid/fake.mp3
            https://uk26.listen2myradio.com/live.mp3?a=1&b=2"""
        assertEquals(listOf("https://uk26.listen2myradio.com/live.mp3?a=1&b=2"), StreamResolver.extractCandidates(html))
    }
}
