package cl.ti3042.luzambiente

import org.json.JSONObject
import java.util.UUID

enum class LightLevel(val code: Int, val label: String, val led: String) {
    HIGH(0, "Mucha luz", "Apagados"), MEDIUM(1, "Luz media", "Amarillo"),
    LOW(2, "Poca luz", "Azul"), DARK(3, "Oscuridad", "Rojo");
    companion object {
        fun fromLux(lux: Float): LightLevel {
            require(lux.isFinite() && lux in 0f..200000f)
            return when { lux >= 700 -> HIGH; lux >= 450 -> MEDIUM; lux >= 250 -> LOW; else -> DARK }
        }
        fun fromCode(code: Int) = entries.firstOrNull { it.code == code }
            ?: throw IllegalArgumentException("Estado fuera de rango")
    }
}

// Los umbrales en lux son una política didáctica, no una calibración del LDR.
data class Reading(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val source: String,
    val lux: Float?,
    val level: LightLevel,
    val origin: String
) {
    fun toJson() = JSONObject().put("v", 1).put("type", "reading")
        .put("id", id).put("timestamp", timestamp).put("source", source)
        .put("lux", lux ?: JSONObject.NULL).put("level", level.code).put("origin", origin)

    companion object {
        fun parse(json: JSONObject): Reading {
            require(json.getInt("v") == 1 && json.getString("type") == "reading")
            val id = json.getString("id")
            UUID.fromString(id)
            val source = json.getString("source")
            require(source in setOf("PHONE", "SIMULATION", "HC05"))
            val lux = if (json.isNull("lux")) null else json.getDouble("lux").toFloat()
            require(lux == null || (lux.isFinite() && lux in 0f..200000f))
            require((source == "HC05") == (lux == null))
            val level = LightLevel.fromCode(json.getInt("level"))
            require(lux == null || level == LightLevel.fromLux(lux))
            val origin = json.getString("origin")
            require(origin.matches(Regex("[A-Za-z0-9 _.-]{1,40}")))
            val time = json.getLong("timestamp")
            require(time > 0)
            return Reading(id, time, source, lux, level, origin)
        }
    }
}
