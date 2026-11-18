package cl.ti3042.luzambiente

import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ActivityUiTest {
    @Test fun registerLoginAndSimulatedSensor() {
        val inst = InstrumentationRegistry.getInstrumentation()
        val intent = Intent(inst.targetContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val activity = inst.startActivitySync(intent) as MainActivity
        val name = "prueba" + UUID.randomUUID().toString().take(8)
        val password = "luz123"
        val email = "$name@correo.cl"
        fun allViews(view: View): List<View> = listOf(view) +
            if (view is ViewGroup) (0 until view.childCount).flatMap { allViews(view.getChildAt(it)) } else emptyList()
        fun views(): List<View> = allViews(activity.window.decorView)
        fun button(label: String) = views().filterIsInstance<Button>().firstOrNull { it.text.toString() == label }
        fun tap(label: String) {
            inst.runOnMainSync { button(label)!!.let { it.requestRectangleOnScreen(android.graphics.Rect(0, 0, it.width, it.height), true) } }
            Thread.sleep(300)
            val position = IntArray(2)
            inst.runOnMainSync {
                val b = button(label)!!; b.getLocationOnScreen(position)
                position[0] += b.width / 2; position[1] += b.height / 2
            }
            val now = android.os.SystemClock.uptimeMillis()
            for (action in listOf(android.view.MotionEvent.ACTION_DOWN, android.view.MotionEvent.ACTION_UP)) {
                val event = android.view.MotionEvent.obtain(now, android.os.SystemClock.uptimeMillis(), action, position[0].toFloat(), position[1].toFloat(), 0)
                event.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
                assertTrue(inst.uiAutomation.injectInputEvent(event, true)); event.recycle()
            }
        }
        fun await(label: String) {
            repeat(600) { // El emulador sin aceleración tarda más al derivar contraseñas.
                var found = false
                inst.runOnMainSync { found = views().filterIsInstance<TextView>().any { it.text.toString() == label } }
                if (found) return
                Thread.sleep(500)
            }
            var state = ""
            inst.runOnMainSync { state = views().filterIsInstance<TextView>().joinToString(" | ") { it.text.toString() + " (enabled=" + it.isEnabled + ")" } }
            throw AssertionError("No apareció: $label. Estado: $state")
        }
        fun capture(label: String) {
            inst.runOnMainSync { activity.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE) }
            Thread.sleep(400)
            inst.uiAutomation.takeScreenshot()?.let { bitmap ->
                java.io.File(activity.filesDir, "$label.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
            inst.runOnMainSync { activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE) }
        }
        try {
            capture("acceso_13")
            tap("Crear cuenta")
            await("Registrarme")
            capture("registro_13")
            inst.runOnMainSync {
                val entries = views().filterIsInstance<EditText>()
                assertEquals(3, entries.size)
                entries[0].setText(name); entries[1].setText(email); entries[2].setText(password)
            }
            tap("Registrarme")
            capture("registro_despues_toque")
            await("Usar como sensor")
            tap("Cerrar sesión")
            await("Crear cuenta")
            inst.runOnMainSync {
                val entries = views().filterIsInstance<EditText>()
                entries[0].setText(email); entries[1].setText(password)
            }
            tap("Ingresar")
            await("Usar como sensor")
            tap("Usar como sensor")
            await("Fuente de lectura")
            inst.runOnMainSync { views().filterIsInstance<Spinner>().first().setSelection(1) }
            await("Datos simulados para demostrar la aplicación.")
            inst.runOnMainSync {
                assertTrue(views().filterIsInstance<TextView>().any { it.text.toString().startsWith("Simular ") })
            }
        } finally { inst.runOnMainSync { activity.finish() } }
    }
}
