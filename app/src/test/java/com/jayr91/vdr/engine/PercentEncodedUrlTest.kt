package com.jayr91.vdr.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Percent-encoded paths, using the shapes archive.org actually serves.
 *
 * A details page there links the same file twice — once with the directory
 * separator encoded as %2F and once literal — so both defects below fired on
 * the entry the picker selects by default.
 */
class PercentEncodedUrlTest {

    private val encoded =
        "https://archive.org/download/BigBuckBunny_124/Content%2Fbig_buck_bunny_720p_surround.mp4"
    private val plain =
        "https://archive.org/download/BigBuckBunny_124/Content/big_buck_bunny_720p_surround.mp4"

    @Test
    fun encodedSlashDoesNotLeakIntoTheFilename() {
        // Slicing the raw URL took "Content%2Fbig_buck_bunny_720p_surround.mp4"
        // as the last segment; stripping '%' as unsafe then saved the file as
        // "Content2Fbig_buck_bunny_720p_surround.mp4".
        assertEquals("big_buck_bunny_720p_surround.mp4", Organizer.filenameFromUrl(encoded))
        assertEquals("big_buck_bunny_720p_surround.mp4", Organizer.filenameFromUrl(plain))
    }

    @Test
    fun encodedAndPlainFormsAreTheSameFile() {
        assertEquals(MediaGrabber.canonicalize(plain), MediaGrabber.canonicalize(encoded))
    }

    @Test
    fun pickerListsOneRowPerFile() {
        // Both forms appear on the page; the user should be offered one.
        val picked = MediaGrabber.preferMediaOrder(listOf(encoded, plain))
        assertEquals(1, picked.size)
    }

    @Test
    fun categoryStillResolvesForEncodedPaths() {
        // Category comes from the extension, which the mangled name happened
        // to preserve -- but only by luck. Pin it.
        assertEquals("Videos", Organizer.categoryFor(Organizer.filenameFromUrl(encoded)))
    }

    @Test
    fun ordinaryUrlsAreUnaffected() {
        assertEquals("mov_bbb.mp4", Organizer.filenameFromUrl("https://www.w3schools.com/html/mov_bbb.mp4"))
        assertEquals("clip.mp4", Organizer.filenameFromUrl("https://x.example/a/b/clip.mp4?token=abc"))
        // A space encoded as %20 is a legitimate part of a name, not a separator.
        assertEquals("my clip.mp4", Organizer.filenameFromUrl("https://x.example/a/my%20clip.mp4"))
    }

    /**
     * The picker and the organiser must agree on what a video is.
     *
     * ".ogv" was in MediaGrabber's video set but missing from Organizer's
     * Videos category, so archive.org's Ogg copy was listed under
     * "3 videos on page" and then saved to Downloads/VDR/Other -- the app
     * contradicting itself between one screen and the next.
     */
    @Test
    fun everyScannerVideoExtensionFilesAsVideo() {
        val wrong = MediaGrabber.videoExtensions.filter { ext ->
            Organizer.categoryFor("clip.$ext") != "Videos"
        }
        assertTrue("filed outside Videos: $wrong", wrong.isEmpty())
    }

    @Test
    fun oggAudioStillFilesAsAudio() {
        // .ogg is the audio-only container and must not follow .ogv.
        assertEquals("Audio", Organizer.categoryFor("track.ogg"))
        assertEquals("Videos", Organizer.categoryFor("clip.ogv"))
    }

    @Test
    fun queryAndFragmentAreNotPartOfTheName() {
        assertEquals("clip.mp4", Organizer.filenameFromUrl("https://x.example/v/clip.mp4#t=10"))
        assertTrue(Organizer.filenameFromUrl("https://x.example/v/clip.mp4?a=1&b=2").endsWith(".mp4"))
    }
}
