package cl.ti3042.luzambiente

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import java.util.UUID

// HC-05 usa Bluetooth clásico SPP, no Bluetooth Low Energy.
class Hc05Reader(context: Context, private val onCode: (Int) -> Unit, private val onStatus: (String) -> Unit) {
    val adapter: BluetoothAdapter? = context.getSystemService(BluetoothManager::class.java)?.adapter
    @Volatile private var socket: BluetoothSocket? = null
    @Volatile private var running = true
    @SuppressLint("MissingPermission")
    fun connect(address: String) {
        Thread({
            try {
                val device = requireNotNull(adapter).getRemoteDevice(address)
                val peer = device.createRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"))
                socket = peer
                if (!running) { peer.close(); return@Thread }
                onStatus("Conectando al HC-05…")
                peer.connect()
                onStatus("HC-05 conectado. Esperando un estado 0 a 3.")
                while (running) {
                    val value = peer.inputStream.read()
                    if (value == -1) break
                    if (value in 48..51) onCode(value - 48)
                }
                if (running) onStatus("HC-05 desconectado. Selecciona de nuevo la fuente para reconectar.")
            } catch (e: Exception) { if (running) onStatus("No se pudo leer HC-05. Revisa emparejamiento y modo esclavo.") }
            finally { runCatching { socket?.close() } }
        }, "hc05-reader").start()
    }
    fun close() { running = false; runCatching { socket?.close() } }
}
