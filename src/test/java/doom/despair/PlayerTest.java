package doom.despair;

import doom.despair.ships.Carrier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PlayerTest {
    @Test
    void initializesDefaults() {
        Player player = new Player("Alice");

        assertEquals("Alice", player.getDisplayName());
        assertNotNull(player.getUuid());
        player.addShip(new Carrier());
        assertEquals(1, player.getShipsRemaining());
        assertNotNull(player.ships);
        Carrier s = new Carrier();

        player.ships.add(s);
        assertEquals(2, player.ships.size());
    }
}
