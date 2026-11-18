package cl.ti3042.luzambiente

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.net.ServerSocket
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class AndroidIntegrationTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun registerAccentedNameAndShortPasswordThenLogin() {
        val name = "maría jesús " + UUID.randomUUID().toString().take(8)
        val password = "luz123".toCharArray()
        AppDatabase(context).use { db ->
            db.register(name, password)
            assertTrue(db.login(name, password))
            assertFalse(db.login(name, "nope12".toCharArray()))
            assertThrows(IllegalStateException::class.java) { db.register(name, password) }
        }
        password.fill('\u0000')
    }
    @Test fun sqliteLoginEncryptionAndUserIsolation() {
        val db = AppDatabase(context)
        val owner = "test_" + UUID.randomUUID().toString().take(8)
        val other = "test_" + UUID.randomUUID().toString().take(8)
        val password = "Clave2026".toCharArray()
        try {
            db.register(owner, password); db.register(other, password)
            assertTrue(db.login(owner, password))
            assertFalse(db.login(owner, "Clave incorrecta 2026".toCharArray()))
            val reading = Reading(source = "SIMULATION", lux = 500f, level = LightLevel.MEDIUM, origin = "Android test")
            db.save(owner, reading); db.save(owner, reading)
            assertEquals(listOf(reading), db.history(owner)) // UUID evita duplicados.
            assertTrue(db.history(other).isEmpty())
            db.readableDatabase.rawQuery("SELECT payload FROM readings WHERE owner=?", arrayOf(owner)).use {
                assertTrue(it.moveToFirst())
                assertFalse(it.getBlob(0).toString(Charsets.UTF_8).contains("SIMULATION"))
            }
            db.clear(owner); assertTrue(db.history(owner).isEmpty())
        } finally { db.close(); password.fill('\u0000') }
    }
    @Test fun failedLoginLockPersistsAcrossDatabaseInstances() {
        val name = "test_" + UUID.randomUUID().toString().take(8)
        val password = "Clave2026".toCharArray()
        AppDatabase(context).use { db ->
            db.register(name, password)
            repeat(5) { assertFalse(db.login(name, "Otra clave de prueba".toCharArray())) }
        }
        AppDatabase(context).use { db -> assertFalse(db.login(name, password)) }
        password.fill('\u0000')
    }
    @Test fun keystoreCipherRejectsTamperingAndWrongOwner() {
        val vault = Vault(); val encrypted = vault.encrypt("500 lux", "maria:123")
        assertEquals("500 lux", vault.decrypt(encrypted, "maria:123"))
        assertThrows(Exception::class.java) { vault.decrypt(encrypted, "otro:123") }
        encrypted[encrypted.lastIndex] = (encrypted.last().toInt() xor 1).toByte()
        assertThrows(Exception::class.java) { vault.decrypt(encrypted, "maria:123") }
    }
    @Test fun androidKeystoreTlsAndBidirectionalMessages() {
        val identity = TlsIdentity(); val token = Pairing.token()
        val port = ServerSocket(0).use { it.localPort }
        val ready = CountDownLatch(1); val connected = CountDownLatch(2)
        val received = CountDownLatch(1); val ack = CountDownLatch(1)
        val events = StringBuffer()
        val errors: (Exception) -> Unit = { events.append(it.stackTraceToString()); android.util.Log.e("TLS_TEST", "Fallo TLS de prueba", it) }
        lateinit var sensor: PeerLink
        sensor = PeerLink({ if (it.startsWith("Esperando")) ready.countDown(); if (it.startsWith("Conectado")) connected.countDown() }, {
            if (it.optString("type") == "command") sensor.send(JSONObject().put("v", 1).put("type", "ack").put("action", "PAUSE"))
        }, errors)
        val monitor = PeerLink({ if (it.startsWith("Conectado")) connected.countDown() }, {
            if (it.optString("type") == "reading" && Reading.parse(it).lux == 500f) received.countDown()
            if (it.optString("type") == "ack") ack.countDown()
        }, errors)
        try {
            sensor.host(identity.serverContext(), token, port)
            assertTrue("Servidor no inició", ready.await(10, TimeUnit.SECONDS))
            monitor.connect(Pairing("127.0.0.1", identity.fingerprint, token), port)
            val isConnected = connected.await(40, TimeUnit.SECONDS)
            assertTrue("Falló TLS Android Keystore: $events", isConnected)
            sensor.send(Reading(source = "SIMULATION", lux = 500f, level = LightLevel.MEDIUM, origin = "Android test").toJson())
            assertTrue(received.await(10, TimeUnit.SECONDS))
            monitor.send(JSONObject().put("v", 1).put("type", "command").put("action", "PAUSE"))
            assertTrue(ack.await(10, TimeUnit.SECONDS))
        } finally { monitor.close(); sensor.close() }
    }
}
