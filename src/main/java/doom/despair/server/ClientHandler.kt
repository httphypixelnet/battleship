package doom.despair.server

import doom.despair.core.Packet
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.Socket
import java.nio.charset.StandardCharsets

class ClientHandler(private val server: BattleshipServer, private val socket: Socket) {
    fun handle() {
        socket.use { clientSocket ->
            BufferedReader(
                InputStreamReader(
                    clientSocket.getInputStream(), StandardCharsets.UTF_8
                )
            ).use { reader ->
                BufferedWriter(
                    OutputStreamWriter(clientSocket.getOutputStream(), StandardCharsets.UTF_8)
                ).use { writer ->
                    processClient(reader, writer)
                }
            }
        }
    }

    private fun processClient(reader: BufferedReader, writer: BufferedWriter) {
        var registeredPlayerId: String? = null
        val sendPacket: (Packet) -> Unit = { packet ->
            synchronized(writer) {
                writer.write(packet.serialize())
                writer.newLine()
                writer.flush()
            }
        }
        try {
            var line: String?
            while ((reader.readLine().also { line = it }) != null) {
                val inputPacket = try {
                    Packet.deserialize(line!!)
                } catch (_: Exception) {
                    Packet(type = "ERROR", payload = """{"ok":false,"message":"Malformed packet"}""")
                }
                val response = server.processPacket(inputPacket, sendPacket)
                if (registeredPlayerId == null) {
                    registeredPlayerId = server.extractPlayerId(response)
                }
                sendPacket(response)
            }
        } catch (e: IOException) {
            if (server.isRunning) {
                throw RuntimeException(e)
            }
        } finally {
            registeredPlayerId?.let { server.unregisterSubscriber(it) }
        }
    }
}
