package doom.despair.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BuildConstantsTest {
    @Test
    fun `test build constants values`() {
        assertEquals("1.0.0-SNAPSHOT", BuildConstants.VERSION)
        assertEquals(1, BuildConstants.MAJOR)
        assertEquals(0, BuildConstants.MINOR)
        assertEquals(0, BuildConstants.PATCH)
        assertEquals("SNAPSHOT", BuildConstants.PRERELEASE)
    }

    @Test
    fun `test compareTo function`() {
        assertEquals(2, BuildConstants.sameVersion("1.0.0-SNAPSHOT"))
        assertEquals(1, BuildConstants.sameVersion("1.0.0"))

        assertEquals(0, BuildConstants.sameVersion("0.9.0"))
        assertEquals(0, BuildConstants.sameVersion("0.9.9"))
        assertEquals(1, BuildConstants.sameVersion("1.0.0-RC1"))

        assertEquals(0, BuildConstants.sameVersion("1.0.1"))
        assertEquals(0, BuildConstants.sameVersion("1.1.0"))
        assertEquals(0, BuildConstants.sameVersion("2.0.0"))

        assertEquals(0, BuildConstants.sameVersion("invalid"))
    }
}
