package cl.ti3042.luzambiente

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.net.ServerSocket
import java.security.KeyStore
import java.security.cert.X509Certificate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext

class PeerLinkTest {
    private fun fixture(): Pair<SSLContext, String> {
        // Certificado exclusivamente de pruebas. No se empaqueta en el APK.
        val store = KeyStore.getInstance("PKCS12")
        javaClass.getResourceAsStream("/test-identity.p12").use { store.load(it, "test-only".toCharArray()) }
        val km = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        km.init(store, "test-only".toCharArray())
        val context = SSLContext.getInstance("TLS").apply { init(km.keyManagers, null, null) }
        return context to Pairing.fingerprint(store.getCertificate("test") as X509Certificate)
    }
    private fun port() = ServerSocket(0).use { it.localPort }

    @Test fun twoPeersExchangeReadingCommandAndAcknowledgment() {
        val (context, pin) = fixture()
        val ready = CountDownLatch(1); val connected = CountDownLatch(2)
        val readingReceived = CountDownLatch(1); val commandReceived = CountDownLatch(1); val ackReceived = CountDownLatch(1)
        val token = Pairing.token(); val port = port()
        lateinit var sensor: PeerLink
        sensor = PeerLink({ if (it.startsWith("Esperando")) ready.countDown(); if (it.startsWith("Conectado")) connected.countDown() }, {
            if (it.getString("type") == "command" && it.getString("action") == "PAUSE") {
                commandReceived.countDown()
                sensor.send(JSONObject().put("v", 1).put("type", "ack").put("action", "PAUSE"))
            }
        })
        val monitor = PeerLink({ if (it.startsWith("Conectado")) connected.countDown() }, {
            if (it.getString("type") == "reading" && Reading.parse(it).lux == 500f) readingReceived.countDown()
            if (it.getString("type") == "ack") ackReceived.countDown()
        })
        try {
            sensor.host(context, token, port)
            assertTrue(ready.await(5, TimeUnit.SECONDS))
            monitor.connect(Pairing("127.0.0.1", pin, token), port)
            assertTrue(connected.await(8, TimeUnit.SECONDS))
            sensor.send(Reading(source = "SIMULATION", lux = 500f, level = LightLevel.MEDIUM, origin = "Test").toJson())
            assertTrue(readingReceived.await(5, TimeUnit.SECONDS))
            monitor.send(JSONObject().put("v", 1).put("type", "command").put("action", "PAUSE"))
            assertTrue(commandReceived.await(5, TimeUnit.SECONDS))
            assertTrue(ackReceived.await(5, TimeUnit.SECONDS))
        } finally { monitor.close(); sensor.close() }
    }
    @Test fun wrongCertificatePinIsRejected() { rejected(wrongPin = true) }
    @Test fun wrongPairingTokenIsRejected() { rejected(wrongPin = false) }
    private fun rejected(wrongPin: Boolean) {
        val (context, pin) = fixture(); val token = Pairing.token(); val port = port()
        val ready = CountDownLatch(1); val rejected = CountDownLatch(1)
        val sensor = PeerLink({ if (it.startsWith("Esperando")) ready.countDown() }, {})
        val monitor = PeerLink({ if (it.startsWith("Sin conexión")) rejected.countDown() }, {})
        try {
            sensor.host(context, token, port); assertTrue(ready.await(5, TimeUnit.SECONDS))
            monitor.connect(Pairing("127.0.0.1", if (wrongPin) "0".repeat(64) else pin, if (wrongPin) token else Pairing.token()), port)
            assertTrue(rejected.await(8, TimeUnit.SECONDS))
            assertFalse(monitor.connected)
        } finally { monitor.close(); sensor.close() }
    }
}
