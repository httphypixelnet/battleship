package doom.despair.core

import com.google.gson.Gson

data class Packet(val type: String, val payload: String? = null) {
    fun serialize(): String = gson.toJson(this)

    companion object {
        private val gson = Gson()

        fun deserialize(serialized: String): Packet {
            return gson.fromJson(serialized, Packet::class.java)
        }
    }
}
