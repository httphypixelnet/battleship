package doom.despair.core;

import java.util.UUID;

public abstract class GameManagedObject {
    private final UUID uniqueId = UUID.randomUUID();
    public final UUID getUUID() { return uniqueId; }
    @Override
    public final int hashCode() { return uniqueId.hashCode(); }
}
