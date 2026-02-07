package doom.despair.core;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class GameManagedObjectTest {
    private static final class TestObject extends GameManagedObject {
    }

    @Test
    void uniqueIdIsStableAndHashMatches() {
        TestObject obj = new TestObject();

        UUID id = obj.getUUID();
        assertNotNull(id);
        assertEquals(id, obj.getUUID());
        assertEquals(id.hashCode(), obj.hashCode());
    }

    @Test
    void differentInstancesHaveDifferentIds() {
        TestObject a = new TestObject();
        TestObject b = new TestObject();

        assertNotEquals(a.getUUID(), b.getUUID());
    }
}
