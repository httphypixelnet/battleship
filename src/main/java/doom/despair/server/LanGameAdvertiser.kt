package doom.despair.server

import java.io.IOException
import javax.jmdns.JmDNS
import javax.jmdns.ServiceInfo

class LanGameAdvertiser(private val port: Int) {
    private companion object {
        const val SERVICE_TYPE = "_battleship._tcp.local."
    }

    private var jmDNS: JmDNS? = null
    private var currentService: ServiceInfo? = null

    @Synchronized
    fun start() {
        if (jmDNS != null) {
            return
        }
        try {
            jmDNS = JmDNS.create()
        } catch (e: IOException) {
            throw RuntimeException("Failed to start JmDNS advertiser", e)
        }
    }

    @Synchronized
    fun stop() {
        clearOpenGame()
        val dns = jmDNS ?: return
        try {
            dns.close()
        } catch (e: IOException) {
            throw RuntimeException("Failed to close JmDNS advertiser", e)
        } finally {
            jmDNS = null
        }
    }

    @Synchronized
    fun advertiseOpenGame(hostName: String) {
        val dns = jmDNS ?: throw IllegalStateException("JmDNS advertiser is not started")
        clearOpenGame()

        val safeHost = hostName.trim().ifBlank { "Host" }
        val serviceName = "Battleship-$safeHost-$port"
        val props = mapOf(
            "open" to "true",
            "host" to safeHost,
            "port" to port.toString()
        )
        val info = ServiceInfo.create(SERVICE_TYPE, serviceName, port, 0, 0, props)
        try {
            dns.registerService(info)
        } catch (e: IOException) {
            throw RuntimeException("Failed to register JmDNS service", e)
        }
        currentService = info
    }

    @Synchronized
    fun clearOpenGame() {
        val dns = jmDNS ?: return
        val info = currentService ?: return
        dns.unregisterService(info)
        currentService = null
    }
}
