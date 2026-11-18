package cl.ti3042.luzambiente

import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.Base64
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

data class Pairing(val host: String, val fingerprint: String, val token: String) {
    fun encode() = "$host|$fingerprint|$token"
    companion object {
        fun token() = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also { SecureRandom().nextBytes(it) })
        fun fingerprint(cert: X509Certificate) = MessageDigest.getInstance("SHA-256")
            .digest(cert.encoded).joinToString("") { "%02x".format(it) }
        fun parse(text: String): Pairing {
            val parts = text.trim().split('|')
            require(parts.size == 3) { "Copia el código completo del sensor." }
            require(parts[0].split('.').let { p -> p.size == 4 && p.all { it.toIntOrNull() in 0..255 } }) { "Se requiere una IPv4." }
            require(parts[1].matches(Regex("[a-f0-9]{64}"))) { "Huella SHA-256 inválida." }
            require(parts[2].matches(Regex("[A-Za-z0-9_-]{43}"))) { "Clave de enlace inválida." }
            return Pairing(parts[0], parts[1], parts[2])
        }
        fun clientContext(pin: String): SSLContext {
            require(pin.matches(Regex("[a-f0-9]{64}")))
            val trust = object : X509TrustManager {
                override fun getAcceptedIssuers() = emptyArray<X509Certificate>()
                override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {
                    throw java.security.cert.CertificateException("Cliente no admitido en este contexto")
                }
                override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
                    if (chain.isEmpty()) throw java.security.cert.CertificateException("Sin certificado")
                    chain[0].checkValidity()
                    if (!MessageDigest.isEqual(fingerprint(chain[0]).toByteArray(), pin.toByteArray()))
                        throw java.security.cert.CertificateException("La huella del sensor no coincide")
                }
            }
            // La identidad se verifica con la huella intercambiada por un canal de confianza.
            return SSLContext.getInstance("TLS").apply { init(null, arrayOf<TrustManager>(trust), SecureRandom()) }
        }
    }
}
