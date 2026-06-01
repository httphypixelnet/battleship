package doom.despair.client

import com.google.gson.Gson
import doom.despair.core.Packet
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.Closeable
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.concurrent.LinkedBlockingQueue

class ServerHandler(address: String, private val client: Client) : Closeable {
    private val gson = Gson()
    private val socket: Socket = Socket()
    private val reader: BufferedReader
    private val writer: BufferedWriter
    private val responseQueue = LinkedBlockingQueue<Packet>()
    @Volatile
    private var closed = false
    @Volatile
    private var stateUpdateHandler: ((Packet) -> Unit)? = null

    init {
        val a = address.split(":")
        val port: Int = if (a.size != 2) {
            25567
        } else {
            a[1].toInt()
        }
        val socketAddress = InetSocketAddress(InetAddress.getByName(a[0]), port)
        socket.connect(socketAddress)
        println("Client socket local port: ${socket.localPort}")
        reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
        writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))
        Thread {
            readLoop()
        }.apply {
            name = "battleship-client-reader"
            isDaemon = true
            start()
        }
    }

    @Synchronized
    fun request(type: String, payload: Any? = null): Packet {
        try {
            val packet = Packet(type = type, payload = payload?.let { gson.toJson(it) })
            writer.write(packet.serialize())
            writer.newLine()
            writer.flush()

            return responseQueue.take()
        } catch (e: IOException) {
            throw RuntimeException("Network request failed", e)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw RuntimeException("Interrupted while waiting for server response", e)
        }
    }

    fun onStateUpdate(handler: ((Packet) -> Unit)?) {
        stateUpdateHandler = handler
    }

    private fun readLoop() {
        try {
            while (!closed) {
                val line = reader.readLine() ?: break
                val packet = Packet.deserialize(line)
                if (packet.type == "STATE_UPDATE") {
                    stateUpdateHandler?.invoke(packet)
                } else {
                    responseQueue.put(packet)
                }
            }
        } catch (_: Exception) {
            if (!closed) {
                responseQueue.offer(Packet("ERROR", """{"ok":false,"message":"Connection closed"}"""))
            }
        }
    }

    override fun close() {
        try {
            closed = true
            socket.close()
        } catch (_: IOException) {
        }
    }
}
