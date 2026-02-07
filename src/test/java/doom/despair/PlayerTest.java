package doom.despair;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PlayerTest {
    @Test
    void initializesDefaults() {
        Player player = new Player("Alice");

        assertEquals("Alice", player.displayName);
        assertNotNull(player.uuid);
        assertEquals(5, player.shipsRemaining);
        assertNotNull(player.ships);
        assertTrue(player.ships.isEmpty());
        Ship s = new Ship();

        player.ships.put(s.getUUID(), s);
        assertEquals(1, player.ships.size());
        assertEquals(player.ships.get(s.getUUID()), s);
    }
}
