package cl.ti3042.luzambiente

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Locale
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object PasswordHasher {
    const val ITERATIONS = 210000
    fun salt() = ByteArray(16).also { SecureRandom().nextBytes(it) }
    fun derive(password: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password, salt, ITERATIONS, 256)
        return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded }
        finally { spec.clearPassword() }
    }
    fun verify(password: CharArray, salt: ByteArray, expected: ByteArray) =
        MessageDigest.isEqual(derive(password, salt), expected)
    fun validUser(user: String) = user.length in 3..32 &&
        user.matches(Regex("[\\p{L}\\p{M}\\p{N} _.-]+"))
    fun normalize(value: String) = value.trim().lowercase(Locale.ROOT)
    fun validEmail(email: String) = email.length in 3..254 &&
        email.matches(Regex("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))
    fun validPassword(password: CharArray) = password.size in 6..12
}
