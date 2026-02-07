package doom.despair.core;
import com.google.gson.Gson;

public abstract sealed class GameEvent permits ClientGameEvent, ServerGameEvent {
    public String message;
    public String type;
    public final String serialize() {
        return new Gson().toJson(this);
    }
    public static <T extends GameEvent> T deserialize(String json, Class<T> clazz) {
        return new Gson().fromJson(json, clazz);
    }
    public GameEvent() {
        this.type = this.getClass().getSimpleName();
    }
}
