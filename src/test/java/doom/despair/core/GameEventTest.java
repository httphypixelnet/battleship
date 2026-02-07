package doom.despair.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GameEventTest {
    private static final class TestClientEvent extends ClientGameEvent {
        public int value;

        public TestClientEvent() {
        }

        @Override
        public void handle(ClientContext context) {
            // no-op for test
        }
    }

    @Test
    void serializeAndDeserializeRoundTrip() {
        TestClientEvent event = new TestClientEvent();
        event.message = "hello";
        event.value = 42;

        assertEquals("TestClientEvent", event.type);

        String json = event.serialize();
        TestClientEvent restored = GameEvent.deserialize(json, TestClientEvent.class);

        assertNotNull(restored);
        assertEquals("hello", restored.message);
        assertEquals(42, restored.value);
        assertEquals("TestClientEvent", restored.type);
    }
}
