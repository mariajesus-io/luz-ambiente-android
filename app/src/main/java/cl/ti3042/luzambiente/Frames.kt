package cl.ti3042.luzambiente

import org.json.JSONObject
import java.io.DataInputStream
import java.io.DataOutputStream

object Frames {
    const val MAX_BYTES = 4096
    fun write(output: DataOutputStream, json: JSONObject) {
        val bytes = json.toString().toByteArray(Charsets.UTF_8)
        require(bytes.size in 1..MAX_BYTES)
        output.writeInt(bytes.size)
        output.write(bytes)
        output.flush()
    }
    fun read(input: DataInputStream): JSONObject {
        val size = input.readInt()
        require(size in 1..MAX_BYTES) { "Trama fuera de rango" }
        val bytes = ByteArray(size)
        input.readFully(bytes)
        return JSONObject(bytes.toString(Charsets.UTF_8))
    }
}
