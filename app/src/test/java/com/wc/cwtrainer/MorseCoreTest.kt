package com.wc.cwtrainer

import com.wc.cwtrainer.core.Fist
import com.wc.cwtrainer.core.Morse
import com.wc.cwtrainer.core.MorseDecoder
import com.wc.cwtrainer.core.TextScore
import com.wc.cwtrainer.core.buildSegments
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MorseCoreTest {

    @Test
    fun alphabetCodesAreCorrect() {
        assertEquals("-.-.", Morse.code('C'))
        assertEquals("--..", Morse.code('Z'))
        assertEquals("-----", Morse.code('0'))
        assertEquals(".-.-.-", Morse.code('.'))
        assertEquals('C', Morse.charFor("-.-."))
    }

    @Test
    fun singleCharacterTiming() {
        val segs = buildSegments("E", Fist(wpm = 20.0))
        assertEquals(1, segs.size)
        assertTrue(segs[0].on)
        assertEquals(60, segs[0].ms)
    }

    @Test
    fun decoderRoundTripsSos() {
        assertEquals("SOS", simulationDecode("SOS", 20.0))
    }

    @Test
    fun decoderHandlesWordGaps() {
        assertEquals("CQ TEST", simulationDecode("CQ TEST", 18.0))
    }

    @Test
    fun decoderHandlesDigitsAndCallsign() {
        assertEquals("BG1WC", simulationDecode("BG1WC", 22.0))
    }

    @Test
    fun textScoreIsSane() {
        assertEquals(1f, TextScore.accuracy("ABC", "abc"), 0.001f)
        assertEquals(0f, TextScore.accuracy("ABC", ""), 0.001f)
        assertEquals(1 - 1f / 3f, TextScore.accuracy("ABC", "ABD"), 0.001f)
        assertEquals(2, TextScore.correctCount("ABC", "ABD"))
    }

    private fun simulationDecode(text: String, wpm: Double): String {
        val decoder = MorseDecoder(wpm)
        val segs = buildSegments(text, Fist(wpm = wpm))
        var t = 0L
        for (seg in segs) {
            if (seg.on) {
                decoder.keyDown(t)
                decoder.keyUp(t + seg.ms)
            }
            t += seg.ms
            decoder.tick(t)
        }
        decoder.tick(t + 500)
        decoder.commit()
        return decoder.text.trim()
    }
}
