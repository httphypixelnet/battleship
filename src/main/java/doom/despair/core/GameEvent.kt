package doom.despair.core

import com.google.gson.*;

abstract class GameEvent {
    @JvmField
    var message: String? = null
    @JvmField
    var type: String = this.javaClass.getSimpleName()
    fun serialize(): String {
        return Gson().toJson(this)
    }

    companion object {
        fun <T : GameEvent?> deserialize(json: String?, clazz: Class<T>): T {
            return Gson().fromJson(json, clazz)
        }
    }
}
