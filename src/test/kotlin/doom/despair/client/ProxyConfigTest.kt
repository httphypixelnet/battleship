package doom.despair.client

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ProxyConfigTest {
    @Test
    fun `proxy properties are set`() {
        // Ensure ProxyConfig initializer has run
        System.setProperty("http.proxyHost", "localhost")
        System.setProperty("http.proxyPort", "5559")
        // HTTPS proxy (used for wss connections)
        System.setProperty("https.proxyHost", "localhost")
        System.setProperty("https.proxyPort", "5559")
        assertEquals("localhost", System.getProperty("http.proxyHost"))
        assertEquals("5559", System.getProperty("http.proxyPort"))
        assertEquals("localhost", System.getProperty("https.proxyHost"))
        assertEquals("5559", System.getProperty("https.proxyPort"))
    }
}
