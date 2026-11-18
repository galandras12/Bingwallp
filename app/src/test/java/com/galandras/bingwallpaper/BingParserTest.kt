package com.galandras.bingwallpaper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class BingParserTest {
    private val sample = """
        {"images":[{"startdate":"20261118","fullstartdate":"202611180700","enddate":"20261119",
        "url":"/th?id=OHR.TestLake_HU-HU1234567890_1920x1080.jpg&rf=LaDigue_1920x1080.jpg&pid=hp",
        "urlbase":"/th?id=OHR.TestLake_HU-HU1234567890",
        "copyright":"Teszt-tó, Magyarország (© Valaki/Getty Images)","title":"Tükörsima tó","hsh":"abc123"}],
        "tooltips":{"loading":"Betöltés..."}}
    """.trimIndent()

    @Test fun parsesFields() {
        val img = BingParser.parse(sample)
        assertEquals("20261118-abc123", img.id)
        assertEquals("Tükörsima tó", img.title)
        assertEquals("Teszt-tó, Magyarország (© Valaki/Getty Images)", img.copyright)
    }

    @Test fun candidatesPreferUhdThenFullHd() {
        val c = BingParser.parse(sample).candidateUrls
        assertEquals(
            "https://www.bing.com/th?id=OHR.TestLake_HU-HU1234567890_UHD.jpg&rf=LaDigue_UHD.jpg&pid=hp", c[0],
        )
        assertEquals(
            "https://www.bing.com/th?id=OHR.TestLake_HU-HU1234567890_1920x1080.jpg&rf=LaDigue_1920x1080.jpg&pid=hp", c[1],
        )
        assertTrue(c.all { it.startsWith("https://www.bing.com/") })
    }

    @Test fun titleFallsBackToCopyrightText() {
        val img = BingParser.parse("""{"images":[{"url":"/th?id=A_1920x1080.jpg","copyright":"Hegy (© X)"}]}""")
        assertEquals("Hegy", img.title)
    }

    @Test fun genericInfoTitleIsReplacedByCopyrightHead() {
        val img = BingParser.parse("""{"images":[{"url":"/th?id=A_1920x1080.jpg","title":"Info","copyright":"Pair of barn owls (© Zahoor Salmi/Getty Images)"}]}""")
        assertEquals("Pair of barn owls", img.title)
    }

    @Test fun usesUrlbaseWhenUrlMissing() {
        val img = BingParser.parse("""{"images":[{"urlbase":"/th?id=OHR.X_HU1","startdate":"20260101"}]}""")
        assertEquals("https://www.bing.com/th?id=OHR.X_HU1_UHD.jpg", img.candidateUrls.first())
        assertEquals("20260101", img.id)
    }

    @Test fun emptyResponseIsTransient() {
        try { BingParser.parse("  "); fail() } catch (e: BingException) { assertTrue(e.transient) }
    }

    @Test fun emptyImagesIsTransient() {
        try { BingParser.parse("""{"images":[]}"""); fail() } catch (e: BingException) { assertTrue(e.transient) }
    }

    @Test fun malformedJsonIsPermanent() {
        try { BingParser.parse("<html>nope</html>"); fail() } catch (e: BingException) { assertFalse(e.transient) }
    }

    @Test fun missingUrlIsPermanent() {
        try { BingParser.parse("""{"images":[{"title":"x"}]}"""); fail() } catch (e: BingException) { assertFalse(e.transient) }
    }
}

class BingUrlsTest {
    @Test fun metadataUrl() {
        assertEquals(
            "https://www.bing.com/HPImageArchive.aspx?format=js&idx=0&n=1&mkt=hu-HU",
            BingUrls.METADATA_URL,
        )
    }

    @Test fun absoluteUrls() {
        assertEquals("https://www.bing.com/th?id=a", BingUrls.absolute("/th?id=a"))
        assertEquals("https://cdn.example.com/a.jpg", BingUrls.absolute("https://cdn.example.com/a.jpg"))
        assertEquals("https://www.bing.com/th?id=a", BingUrls.absolute("th?id=a"))
    }

    @Test fun urlWithoutSizeMarkerIsKeptAsIs() {
        assertEquals(listOf("https://www.bing.com/th?id=a.png"), BingUrls.candidates("/th?id=a.png"))
    }

    @Test fun alreadyUhdYieldsFallbackToo() {
        val c = BingUrls.candidates("/th?id=X_UHD.jpg&pid=hp")
        assertEquals("https://www.bing.com/th?id=X_UHD.jpg&pid=hp", c[0])
        assertEquals("https://www.bing.com/th?id=X_1920x1080.jpg&pid=hp", c[1])
    }
}
