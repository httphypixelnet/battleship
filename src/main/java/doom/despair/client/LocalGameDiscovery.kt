package doom.despair.client

import java.io.Closeable
import java.io.IOException
import javax.jmdns.JmDNS

class LocalGameDiscovery : Closeable {
    data class LocalGame(val name: String, val host: String, val port: Int) {
        val address: String
            get() = "$host:$port"

        override fun toString(): String = "$name ($address)"
    }

    private companion object {
        const val SERVICE_TYPE = "_battleship._tcp.local."
    }

    private val jmDNS: JmDNS = JmDNS.create()

    fun discover(timeoutMs: Long = 800): List<LocalGame> {
        return jmDNS.list(SERVICE_TYPE, timeoutMs).mapNotNull { info ->
            val hostAddress = info.inet4Addresses.firstOrNull()?.hostAddress
                ?: info.inetAddresses.firstOrNull()?.hostAddress
                ?: return@mapNotNull null
            LocalGame(info.name, hostAddress, info.port)
        }.sortedBy { it.name.lowercase() }
    }

    override fun close() {
        try {
            jmDNS.close()
        } catch (e: IOException) {
            throw RuntimeException("Failed to close local game discovery", e)
        }
    }
}
