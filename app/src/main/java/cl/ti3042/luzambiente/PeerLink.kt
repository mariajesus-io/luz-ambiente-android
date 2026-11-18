package cl.ti3042.luzambiente

import org.json.JSONObject
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.security.MessageDigest
import java.util.concurrent.Executors
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket

class PeerLink(
    private val onStatus: (String) -> Unit,
    private val onMessage: (JSONObject) -> Unit,
    private val onError: (Exception) -> Unit = {}
) {
    @Volatile private var running = true
    @Volatile private var server: SSLServerSocket? = null
    @Volatile private var socket: SSLSocket? = null
    @Volatile private var output: DataOutputStream? = null
    private val sender = Executors.newSingleThreadExecutor()
    @Volatile var connected = false
        private set

    private fun configure(socket: SSLSocket) {
        socket.enabledProtocols = socket.supportedProtocols.filter { it == "TLSv1.2" || it == "TLSv1.3" }.toTypedArray()
        socket.soTimeout = 15000
        socket.tcpNoDelay = true
    }

    fun host(context: SSLContext, token: String, port: Int = 8988) {
        Thread({
            try {
                val listener = context.serverSocketFactory.createServerSocket() as SSLServerSocket
                server = listener
                if (!running) { listener.close(); return@Thread }
                listener.reuseAddress = true
                listener.bind(InetSocketAddress(port))
                onStatus("Esperando monitor en el puerto $port")
                while (running) {
                    try {
                        val peer = listener.accept() as SSLSocket
                        socket = peer
                        configure(peer)
                        peer.soTimeout = 5000
                        peer.startHandshake()
                        val input = DataInputStream(peer.inputStream)
                        val out = DataOutputStream(peer.outputStream)
                        val hello = Frames.read(input)
                        require(hello.getInt("v") == 1 && hello.getString("type") == "auth")
                        require(MessageDigest.isEqual(token.toByteArray(), hello.getString("token").toByteArray())) { "Clave de enlace incorrecta" }
                        Frames.write(out, JSONObject().put("type", "ready").put("v", 1))
                        readSession(peer, input, out)
                    } catch (e: Exception) {
                        if (running) onError(e)
                        if (running) onStatus("Enlace finalizado o rechazado. Esperando monitor.")
                    } finally { closePeer() }
                }
            } catch (e: Exception) {
                if (running) onError(e)
                if (running) onStatus("No se pudo iniciar el sensor: ${e.javaClass.simpleName}")
            } finally { server?.close() }
        }, "tls-sensor").start()
    }

    fun connect(pairing: Pairing, port: Int = 8988) {
        Thread({
            try {
                onStatus("Verificando identidad del sensor…")
                val peer = Pairing.clientContext(pairing.fingerprint).socketFactory.createSocket() as SSLSocket
                socket = peer
                if (!running) { peer.close(); return@Thread }
                configure(peer)
                peer.connect(InetSocketAddress(pairing.host, port), 6000)
                peer.startHandshake()
                val out = DataOutputStream(peer.outputStream)
                val input = DataInputStream(peer.inputStream)
                Frames.write(out, JSONObject().put("v", 1).put("type", "auth").put("token", pairing.token))
                val ready = Frames.read(input)
                require(ready.getInt("v") == 1 && ready.getString("type") == "ready")
                readSession(peer, input, out)
            } catch (e: Exception) {
                if (running) onError(e)
                if (running) onStatus("Sin conexión. Revisa Wi-Fi, código, fecha y sensor. ${e.javaClass.simpleName}")
            } finally { closePeer() }
        }, "tls-monitor").start()
    }

    private fun readSession(peer: SSLSocket, input: DataInputStream, out: DataOutputStream) {
        if (!running) return
        output = out
        connected = true
        peer.soTimeout = 15000
        onStatus("Conectado • ${peer.session.protocol} • identidad verificada")
        while (running) {
            val message = Frames.read(input)
            require(message.getInt("v") == 1)
            when (message.getString("type")) {
                "ping" -> send(JSONObject().put("v", 1).put("type", "pong"))
                "pong" -> Unit
                "reading", "command", "ack" -> onMessage(message)
                else -> throw IllegalArgumentException("Mensaje desconocido")
            }
        }
    }

    fun send(message: JSONObject) {
        if (!running || !connected) return
        runCatching { sender.execute {
            try { output?.let { Frames.write(it, message) } }
            catch (e: Exception) { closePeer() }
        } }
    }
    private fun closePeer() {
        connected = false
        output = null
        runCatching { socket?.close() }
        socket = null
    }
    fun close() {
        running = false
        runCatching { server?.close() }
        closePeer()
        sender.shutdownNow()
    }
}
