package cl.ti3042.luzambiente

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.*

class CoreTest {
    @Test fun luxBoundaries() {
        assertEquals(LightLevel.DARK, LightLevel.fromLux(249.99f))
        assertEquals(LightLevel.LOW, LightLevel.fromLux(250f))
        assertEquals(LightLevel.MEDIUM, LightLevel.fromLux(450f))
        assertEquals(LightLevel.HIGH, LightLevel.fromLux(700f))
    }
    @Test fun invalidLuxRejected() {
        listOf(-1f, Float.NaN, Float.POSITIVE_INFINITY, 200001f).forEach {
            assertThrows(IllegalArgumentException::class.java) { LightLevel.fromLux(it) }
        }
    }
    @Test fun arduinoNeverInventsLux() {
        val reading = Reading(source = "HC05", lux = null, level = LightLevel.DARK, origin = "Arduino HC-05")
        assertEquals(reading, Reading.parse(reading.toJson()))
        assertTrue(reading.toJson().isNull("lux"))
    }
    @Test fun contradictoryMeasurementRejected() {
        val reading = Reading(source = "PHONE", lux = 500f, level = LightLevel.DARK, origin = "Test")
        assertThrows(IllegalArgumentException::class.java) { Reading.parse(reading.toJson()) }
    }
    @Test fun unknownSourceRejected() {
        val json = Reading(source = "SIMULATION", lux = 20f, level = LightLevel.DARK, origin = "Test").toJson()
        json.put("source", "UNKNOWN")
        assertThrows(IllegalArgumentException::class.java) { Reading.parse(json) }
    }
    @Test fun missingLuxRejectedForPhone() {
        val json = Reading(source = "PHONE", lux = null, level = LightLevel.DARK, origin = "Test").toJson()
        assertThrows(IllegalArgumentException::class.java) { Reading.parse(json) }
    }
    @Test fun binaryFramesRoundTrip() {
        val bytes = ByteArrayOutputStream()
        Frames.write(DataOutputStream(bytes), JSONObject().put("mensaje", "iluminación"))
        val result = Frames.read(DataInputStream(ByteArrayInputStream(bytes.toByteArray())))
        assertEquals("iluminación", result.getString("mensaje"))
    }
    @Test fun oversizedIncomingFrameRejectedBeforeAllocation() {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).writeInt(Int.MAX_VALUE)
        assertThrows(IllegalArgumentException::class.java) { Frames.read(DataInputStream(ByteArrayInputStream(bytes.toByteArray()))) }
    }
    @Test fun truncatedFrameRejected() {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).apply { writeInt(8); writeByte(1) }
        assertThrows(EOFException::class.java) { Frames.read(DataInputStream(ByteArrayInputStream(bytes.toByteArray()))) }
    }
    @Test fun oversizedOutgoingFrameRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            Frames.write(DataOutputStream(ByteArrayOutputStream()), JSONObject().put("x", "x".repeat(5000)))
        }
    }
    @Test fun pairingValidation() {
        val pairing = Pairing("192.168.1.20", "a".repeat(64), Pairing.token())
        assertEquals(pairing, Pairing.parse(pairing.encode()))
        assertThrows(IllegalArgumentException::class.java) { Pairing.parse(pairing.copy(host = "999.1.1.1").encode()) }
        assertThrows(IllegalArgumentException::class.java) { Pairing.parse(pairing.copy(fingerprint = "a").encode()) }
    }
    @Test fun saltedPasswordsAndWrongPassword() {
        val pass = "Clave de prueba 2026".toCharArray()
        val a = PasswordHasher.salt(); val b = PasswordHasher.salt()
        val hash = PasswordHasher.derive(pass, a)
        assertFalse(hash.contentEquals(PasswordHasher.derive(pass, b)))
        assertTrue(PasswordHasher.verify(pass, a, hash))
        assertFalse(PasswordHasher.verify("Otra clave de prueba".toCharArray(), a, hash))
    }
    @Test fun credentialRules() {
        assertTrue(PasswordHasher.validUser("maria.erices"))
        assertTrue(PasswordHasher.validUser("maría jesús"))
        assertFalse(PasswordHasher.validUser("' OR 1=1"))
        assertFalse(PasswordHasher.validPassword("1234".toCharArray()))
        assertTrue(PasswordHasher.validPassword("luz123".toCharArray()))
        assertTrue(PasswordHasher.validPassword("123456789012".toCharArray()))
        assertFalse(PasswordHasher.validPassword("1234567890123".toCharArray()))
        assertFalse(PasswordHasher.validPassword("12345".toCharArray()))
        assertTrue(PasswordHasher.validEmail("maria@correo.cl"))
        assertFalse(PasswordHasher.validEmail("maria@"))
        assertEquals("maría", PasswordHasher.normalize(" María "))
    }
}
