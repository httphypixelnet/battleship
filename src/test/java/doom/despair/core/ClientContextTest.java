package doom.despair.core;

import doom.despair.Player;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;

class ClientContextTest {
    @Test
    void storesPlayer() {
        Player player = new Player("Bob");
        ClientContext context = new ClientContext(player);

        assertSame(player, context.getPlayer());
    }
}
