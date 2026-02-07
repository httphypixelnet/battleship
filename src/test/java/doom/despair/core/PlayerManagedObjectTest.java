package doom.despair.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;

class PlayerManagedObjectTest {
    private static final class TestPlayerManagedObject extends PlayerManagedObject {
    }

    @Test
    void playerDefaultsToNull() {
        TestPlayerManagedObject obj = new TestPlayerManagedObject();
        assertNull(obj.getPlayer());
    }
}
