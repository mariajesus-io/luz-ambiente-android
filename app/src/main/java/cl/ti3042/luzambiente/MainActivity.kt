package cl.ti3042.luzambiente

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.*
import org.json.JSONObject
import java.net.Inet4Address
import java.net.NetworkInterface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : Activity(), SensorEventListener {
    private val db by lazy { AppDatabase(this) }
    private val worker = Executors.newSingleThreadExecutor()
    private val authWorker = Executors.newSingleThreadExecutor()
    private var accessGeneration = 0
    private var accessRegister = false
    private var authBusy = false
    private val handler = Handler(Looper.getMainLooper())
    private val sensors by lazy { getSystemService(SensorManager::class.java) }
    private var user: String? = null
    private var role = ""
    private var generation = 0
    private var sourceGeneration = 0
    private var link: PeerLink? = null
    private var hc05: Hc05Reader? = null
    private var source = "PHONE"
    private var collecting = true
    private var latestLux: Float? = null
    private var hcCode: Int? = null
    private var hcUpdated = 0L
    private var simulation = 500f
    private var pendingExport = ""
    private lateinit var root: LinearLayout
    private lateinit var status: TextView
    private lateinit var value: TextView
    private lateinit var level: TextView
    private lateinit var detail: TextView
    private lateinit var chart: LightChart
    private lateinit var sourceStatus: TextView
    private var simulationControls: View? = null
    private var btControls: View? = null
    private var lastReceived = 0L

    private val tick = object : Runnable {
        override fun run() {
            if (user == null || role.isEmpty()) return
            if (role == "SENSOR" && collecting) {
                val reading = when (source) {
                    "PHONE" -> latestLux?.let { Reading(source = "PHONE", lux = it, level = LightLevel.fromLux(it), origin = "Sensor Android") }
                    "SIMULATION" -> Reading(source = "SIMULATION", lux = simulation, level = LightLevel.fromLux(simulation), origin = "Simulacion Android")
                    else -> null // El HC-05 se guarda sólo cuando entrega un estado.
                }
                reading?.let { acceptReading(it, true) }
            }
            if (role == "MONITOR" && lastReceived > 0 && System.currentTimeMillis() - lastReceived > 6000)
                detail.text = "Última lectura hace más de 6 s. Puede estar pausado o sin datos."
            link?.send(JSONObject().put("v", 1).put("type", "ping"))
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        showLogin()
    }
    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    private fun surface(color: Int, radius: Int, stroke: Int? = null) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat()
        if (stroke != null) setStroke(dp(1), stroke)
    }
    private fun page(title: String, subtitle: String) {
        val scroll = ScrollView(this).apply { setBackgroundColor(Color.rgb(244, 248, 246)); isFillViewport = true }
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(24), dp(24), dp(32)) }
        scroll.addView(root)
        scroll.setOnApplyWindowInsetsListener { v, insets ->
            v.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop, insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
            insets
        }
        setContentView(scroll)
        text("LUZ AMBIENTE", 12, Color.rgb(8, 110, 94)).apply { setTypeface(null, Typeface.BOLD); letterSpacing = 0.12f }
        text(title, 30).apply { setTypeface(null, Typeface.BOLD); setPadding(0, dp(14), 0, dp(4)) }
        if (subtitle.isNotBlank()) text(subtitle, 15, Color.rgb(80, 104, 97))
    }
    private fun text(s: String, size: Int = 16, color: Int = Color.rgb(21, 43, 38), parent: LinearLayout = root): TextView =
        TextView(this).apply {
            text = s; textSize = size.toFloat(); setTextColor(color); setPadding(0, dp(7), 0, dp(7))
            parent.addView(this, LinearLayout.LayoutParams(-1, -2))
        }
    private fun button(label: String, parent: LinearLayout = root, secondary: Boolean = false, action: () -> Unit): Button = Button(this).apply {
        text = label; isAllCaps = false; textSize = 15f; setTypeface(null, Typeface.BOLD)
        setTextColor(if (secondary) Color.rgb(8, 110, 94) else Color.WHITE)
        background = surface(if (secondary) Color.WHITE else Color.rgb(8, 110, 94), 14, if (secondary) Color.rgb(150, 189, 178) else null)
        parent.addView(this, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(10); bottomMargin = dp(2) })
        setOnClickListener { action() }
    }
    private fun input(hint: String, secret: Boolean = false): EditText = EditText(this).apply {
        this.hint = hint; textSize = 16f; setSingleLine(true)
        inputType = if (secret) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD else InputType.TYPE_CLASS_TEXT
        setTextColor(Color.rgb(24, 43, 37)); setHintTextColor(Color.rgb(100, 120, 113))
        background = surface(Color.WHITE, 12, Color.rgb(203, 219, 212))
        setPadding(dp(16), 0, dp(16), 0)
        importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        root.addView(this, LinearLayout.LayoutParams(-1, dp(54)).apply { topMargin = dp(10) })
    }
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()

    private fun showLogin() = showAccess(false)

    private fun showAccess(register: Boolean) {
        accessRegister = register
        val screen = ++accessGeneration
        page(if (register) "Crear cuenta" else "Bienvenida", if (register) "Completa tus datos para comenzar." else "Tu luz, tus lecturas, tu espacio.")
        text(if (register) "Usuario" else "Usuario o correo", 14).setTypeface(null, Typeface.BOLD)
        val identifier = input(if (register) "Ej. maria" else "Tu usuario o correo")
        val email = if (register) {
            text("Correo electrónico", 14).setTypeface(null, Typeface.BOLD)
            input("nombre@correo.cl").apply { inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS }
        } else null
        text("Contraseña", 14).setTypeface(null, Typeface.BOLD)
        val password = input("De 6 a 12 caracteres", true)
        val showPassword = CheckBox(this).apply {
            text = "Mostrar contraseña"; textSize = 14f; setTextColor(Color.rgb(80, 104, 97))
            setOnCheckedChangeListener { _, checked ->
                password.transformationMethod = if (checked) null else android.text.method.PasswordTransformationMethod.getInstance()
                password.setSelection(password.text.length)
            }
        }
        root.addView(showPassword)
        val result = text("", 14, Color.rgb(176, 38, 38)).apply { accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE }
        val loading = ProgressBar(this).apply { visibility = View.GONE }
        root.addView(loading, LinearLayout.LayoutParams(dp(28), dp(28)).apply { gravity = android.view.Gravity.CENTER_HORIZONTAL })
        val buttons = mutableListOf<Button>()
        fun error(field: EditText, message: String) {
            field.error = message; field.requestFocus(); result.text = message
            AlertDialog.Builder(this).setTitle("Revisa tus datos").setMessage(message).setPositiveButton("Entendido", null).show()
        }
        fun submit() {
            if (authBusy) return
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(password.windowToken, 0)
            identifier.error = null; email?.error = null; password.error = null
            val name = PasswordHasher.normalize(identifier.text.toString())
            val address = email?.let { PasswordHasher.normalize(it.text.toString()) }
            if (if (register) !PasswordHasher.validUser(name) else !(PasswordHasher.validUser(name) || PasswordHasher.validEmail(name))) {
                error(identifier, if (register) "Elige un usuario de 3 a 32 letras o números. También puedes usar espacios, puntos y guiones." else "Escribe tu usuario o un correo válido."); return
            }
            if (email != null && !PasswordHasher.validEmail(address.orEmpty())) {
                error(email, "Escribe un correo válido, por ejemplo maria@correo.cl."); return
            }
            val pass = password.text.toString().toCharArray()
            if (!PasswordHasher.validPassword(pass)) {
                pass.fill('\u0000'); error(password, "La contraseña debe tener entre 6 y 12 caracteres."); return
            }
            authBusy = true
            buttons.forEach { it.isEnabled = false; it.alpha = 0.55f }
            loading.visibility = View.VISIBLE; result.setTextColor(Color.rgb(8, 110, 94))
            result.text = if (register) "Creando tu cuenta…" else "Ingresando…"
            authWorker.execute {
                val outcome = runCatching {
                    if (register) { db.register(name, pass, address); name }
                    else db.loginUser(name, pass) ?: throw IllegalArgumentException("Usuario, correo o contraseña incorrectos. Tras cinco intentos fallidos, espera 30 segundos.")
                }
                pass.fill('\u0000')
                runOnUiThread {
                    authBusy = false
                    if (isDestroyed || screen != accessGeneration) return@runOnUiThread
                    loading.visibility = View.GONE
                    buttons.forEach { it.isEnabled = true; it.alpha = 1f }
                    outcome.onSuccess {
                        password.text.clear(); user = it; showRoles()
                        if (register) toast("Cuenta creada. Ya puedes usar la aplicación.")
                    }.onFailure {
                        result.setTextColor(Color.rgb(176, 38, 38)); result.text = it.message ?: "No se pudo completar el acceso. Intenta otra vez."
                        AlertDialog.Builder(this).setTitle(if (register) "No se pudo crear la cuenta" else "No se pudo ingresar")
                            .setMessage(result.text).setPositiveButton("Entendido", null).show()
                    }
                }
            }
        }
        buttons += button(if (register) "Registrarme" else "Ingresar") { submit() }
        buttons += button(if (register) "Ya tengo cuenta" else "Crear cuenta", secondary = true) { showAccess(!register) }
        text("Cuenta guardada en este dispositivo.", 12, Color.rgb(80, 104, 97))
        text("Versión 1.3", 12, Color.rgb(80, 104, 97))
    }

    private fun showRoles() {
        stopSession()
        page("Hola, ${user.orEmpty()}", "Elige una función para este teléfono.")
        text("Sensor de luz", 21).setTypeface(null, Typeface.BOLD)
        text("Mide luz, simula niveles o recibe estados de un Arduino HC-05. Comparte las lecturas por Wi-Fi.")
        button("Usar como sensor") { dashboard("SENSOR") }
        text("Monitor remoto", 21).setTypeface(null, Typeface.BOLD)
        text("Recibe las lecturas y envía órdenes para pausar o reanudar el envío del sensor.")
        button("Usar como monitor") { dashboard("MONITOR") }
        button("Consultar historial", secondary = true) { history() }
        button("Cerrar sesión", secondary = true) { logout() }
    }

    private fun dashboard(newRole: String) {
        stopSession(); role = newRole; collecting = true; lastReceived = 0
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        page(if (role == "SENSOR") "Estación de luz" else "Monitor remoto", "Usuario ${user.orEmpty()} • red Wi-Fi local")
        status = text("Sin conexión entre teléfonos", 14)
        value = text("—", 48).apply { setTypeface(null, Typeface.BOLD) }
        level = text("Esperando una lectura", 22)
        detail = text("Las lecturas de prueba se identifican como simulación.", 13)
        chart = LightChart(this).also { root.addView(it, LinearLayout.LayoutParams(-1, dp(150))) }
        if (role == "SENSOR") sensorPanel() else monitorPanel()
        button("Historial y exportación") { history() }
        button("Volver a elegir función") { showRoles() }
        button("Cerrar sesión") { logout() }
        handler.post(tick)
    }

    private fun sensorPanel() {
        text("Fuente de lectura", 20).setTypeface(null, Typeface.BOLD)
        val select = Spinner(this)
        select.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item,
            listOf("Sensor del teléfono", "Simulación identificada", "Arduino con HC-05"))
        root.addView(select)
        sourceStatus = text("", 13)
        val sim = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
        root.addView(sim); simulationControls = sim
        val simText = text("Simular %.0f lx".format(simulation), 16, parent = sim)
        sim.addView(SeekBar(this).apply {
            max = 1500; progress = simulation.toInt(); contentDescription = "Iluminancia simulada en lux"
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    simulation = progress.toFloat(); simText.text = "Simular $progress lx"
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        })
        val bt = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
        root.addView(bt); btControls = bt
        text("Empareja primero el HC-05 desde Ajustes. Debe estar libre y configurado como esclavo.", 13, parent = bt)
        button("Seleccionar HC-05 emparejado", bt) { chooseHc05() }
        select.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                setSource(listOf("PHONE", "SIMULATION", "HC05")[position])
            }
        }
        button("Iniciar enlace Wi-Fi seguro") { startHost() }
        button("Pausar o reanudar envío local") { collecting = !collecting; sourceStatus.text = if (collecting) "Envío reanudado." else "Envío pausado." }
        text("Los umbrales de 250, 450 y 700 lx son configuraciones didácticas. Los estados HC-05 no se convierten a lux.", 13)
    }

    private fun setSource(newSource: String) {
        sensors.unregisterListener(this); hc05?.close(); hc05 = null; latestLux = null; hcCode = null
        sourceGeneration++; source = newSource
        simulationControls?.visibility = if (source == "SIMULATION") View.VISIBLE else View.GONE
        btControls?.visibility = if (source == "HC05") View.VISIBLE else View.GONE
        value.text = "—"; level.text = "Esperando fuente"; chart.clear()
        sourceStatus.text = when (source) {
            "PHONE" -> {
                val sensor = sensors.getDefaultSensor(Sensor.TYPE_LIGHT)
                if (sensor == null) "Este teléfono no tiene sensor de luz. Selecciona simulación o HC-05."
                else if (sensors.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)) "Sensor del teléfono activo. Unidad: lux."
                else "No se pudo activar el sensor. Selecciona otra fuente."
            }
            "SIMULATION" -> "Datos simulados para demostrar la aplicación."
            else -> "Sin HC-05 conectado. El protocolo original sólo envía cambios de estado."
        }
    }

    private fun addresses(): List<String> = runCatching {
        NetworkInterface.getNetworkInterfaces().toList().filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }.filterIsInstance<Inet4Address>()
            .mapNotNull { it.hostAddress }.distinct()
    }.getOrDefault(emptyList())

    private fun startHost() {
        val ips = addresses()
        if (ips.isEmpty()) { toast("Conecta ambos teléfonos al mismo Wi-Fi o a un punto de acceso."); return }
        AlertDialog.Builder(this).setTitle("Selecciona la IP de tu red local")
            .setItems(ips.toTypedArray()) { _, which ->
                link?.close(); val g = ++generation
                status.text = "Preparando identidad TLS…"
                worker.execute {
                    val result = runCatching { TlsIdentity() }
                    runOnUiThread {
                        if (g != generation || user == null) return@runOnUiThread
                        val identity = result.getOrElse { toast("No se pudo crear la identidad TLS."); return@runOnUiThread }
                        val token = Pairing.token()
                        val peer = createLink(g)
                        link = peer; peer.host(identity.serverContext(), token)
                        val code = Pairing(ips[which], identity.fingerprint, token).encode()
                        val entry = EditText(this).apply { setText(code); textSize = 13f; setSelectAllOnFocus(true); inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE }
                        AlertDialog.Builder(this).setTitle("Código privado para el monitor")
                            .setMessage("Comparte este código completo por un canal de confianza. Mantén abierto el sensor. Se renueva al iniciar otro enlace.")
                            .setView(entry).setPositiveButton("Copiar") { _, _ ->
                                val clip = getSystemService(ClipboardManager::class.java)
                                clip.setPrimaryClip(ClipData.newPlainText("Enlace Luz Ambiente", code))
                                handler.postDelayed({
                                    if (clip.primaryClip?.getItemAt(0)?.text?.toString() == code) {
                                        if (Build.VERSION.SDK_INT >= 28) clip.clearPrimaryClip()
                                        else clip.setPrimaryClip(ClipData.newPlainText("", ""))
                                    }
                                }, 60000)
                                toast("Código copiado. Se borrará del portapapeles en 60 s.")
                            }.setNegativeButton("Cerrar", null).show()
                    }
                }
            }.show()
    }

    private fun monitorPanel() {
        text("Conectar con el sensor", 20).setTypeface(null, Typeface.BOLD)
        text("Conecta ambos Android al mismo Wi-Fi. En el sensor inicia el enlace y copia su código privado.", 14)
        val code = input("Pega aquí IP|huella|clave", true)
        button("Verificar y conectar") {
            val pairing = runCatching { Pairing.parse(code.text.toString()) }.getOrElse { toast(it.message ?: "Código inválido"); return@button }
            code.text.clear(); link?.close(); val g = ++generation
            link = createLink(g).also { it.connect(pairing) }
        }
        button("Pausar envío del sensor") { sendCommand("PAUSE") }
        button("Reanudar envío del sensor") { sendCommand("RESUME") }
        text("El indicador LED se simula en esta pantalla. Las órdenes controlan el envío de la app; no accionan los LEDs físicos del nodo B.", 13)
    }
    private fun createLink(g: Int) = PeerLink(
        { message -> runOnUiThread { if (g == generation && user != null) status.text = message } },
        { message -> runOnUiThread { if (g == generation && user != null) receive(message) } }
    )
    private fun sendCommand(action: String) {
        if (link?.connected != true) { toast("Conecta primero con el sensor."); return }
        link?.send(JSONObject().put("v", 1).put("type", "command").put("action", action))
        status.text = "Orden enviada. Esperando confirmación del sensor…"
    }
    private fun receive(message: JSONObject) {
        runCatching {
            when (message.getString("type")) {
                "reading" -> { require(role == "MONITOR"); lastReceived = System.currentTimeMillis(); acceptReading(Reading.parse(message), false) }
                "command" -> {
                    require(role == "SENSOR")
                    val action = message.getString("action")
                    require(action in setOf("PAUSE", "RESUME"))
                    collecting = action == "RESUME"
                    sourceStatus.text = if (collecting) "Envío reanudado por el monitor." else "Envío pausado por el monitor."
                    link?.send(JSONObject().put("v", 1).put("type", "ack").put("action", action))
                }
                "ack" -> {
                    require(role == "MONITOR")
                    val action = message.getString("action"); require(action in setOf("PAUSE", "RESUME"))
                    status.text = if (action == "PAUSE") "Sensor confirmó la pausa." else "Sensor confirmó la reanudación."
                }
                else -> throw IllegalArgumentException("Tipo de mensaje no admitido")
            }
        }.onFailure { link?.close(); status.text = "Se cerró el enlace por datos inválidos." }
    }

    private fun acceptReading(reading: Reading, send: Boolean) {
        val owner = user ?: return
        value.text = reading.lux?.let { "%.1f lx".format(it) } ?: "Estado ${reading.level.code}"
        level.text = "${reading.level.label} • LED ${reading.level.led.lowercase(Locale.ROOT)}"
        level.setTextColor(when (reading.level) {
            LightLevel.HIGH -> Color.rgb(8, 127, 117); LightLevel.MEDIUM -> Color.rgb(135, 95, 0)
            LightLevel.LOW -> Color.rgb(32, 91, 175); LightLevel.DARK -> Color.rgb(172, 44, 44)
        })
        detail.text = "${sourceLabel(reading.source)} • ${time(reading.timestamp)}" + if (reading.lux == null) "\nEstado discreto del LDR, sin lux calibrados." else ""
        reading.lux?.let { chart.add(it) }
        worker.execute { runCatching { db.save(owner, reading) }.onFailure { runOnUiThread { toast("No se pudo guardar la lectura.") } } }
        if (send) link?.send(reading.toJson())
    }

    @SuppressLint("MissingPermission")
    private fun chooseHc05() {
        if (Build.VERSION.SDK_INT >= 31 && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT), 10); return
        }
        val s = sourceGeneration
        hc05?.close()
        val reader = Hc05Reader(this, { code -> runOnUiThread {
            if (s == sourceGeneration && role == "SENSOR" && source == "HC05") {
                val now = System.currentTimeMillis()
                if (code != hcCode || now - hcUpdated >= 1000) {
                    hcCode = code; hcUpdated = now
                    if (collecting) acceptReading(Reading(source = "HC05", lux = null, level = LightLevel.fromCode(code), origin = "Arduino HC-05"), true)
                }
            }
        } }, { message -> runOnUiThread { if (s == sourceGeneration && role == "SENSOR") sourceStatus.text = message } })
        hc05 = reader
        val adapter = reader.adapter
        if (adapter == null || !adapter.isEnabled) { toast("Activa Bluetooth desde Ajustes y empareja el HC-05."); return }
        val devices = adapter.bondedDevices.toList()
        if (devices.isEmpty()) { toast("No hay dispositivos emparejados. Empareja el HC-05 desde Ajustes."); return }
        AlertDialog.Builder(this).setTitle("Dispositivos Bluetooth emparejados")
            .setItems(devices.map { "${it.name ?: "Sin nombre"} • ${it.address}" }.toTypedArray()) { _, which -> reader.connect(devices[which].address) }
            .setNegativeButton("Cancelar", null).show()
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 10 && role == "SENSOR" && source == "HC05") {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) chooseHc05()
            else toast("Permiso denegado. Puedes usar el sensor del teléfono o la simulación.")
        }
    }
    override fun onSensorChanged(event: SensorEvent) {
        if (source == "PHONE") event.values.firstOrNull()?.let { if (it.isFinite() && it in 0f..200000f) latestLux = it }
    }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun sourceLabel(code: String) = when (code) { "PHONE" -> "Sensor Android"; "SIMULATION" -> "SIMULACIÓN"; else -> "Arduino HC-05" }
    private fun time(timestamp: Long) = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
    private fun history() {
        val owner = user ?: return
        stopSession()
        page("Historial de luz", "Últimas 100 lecturas de $owner • máximo 5000 por usuario")
        val loading = text("Cargando tus lecturas…")
        button("Exportar hasta 5000 lecturas a CSV") {
            worker.execute {
                val result = runCatching { db.history(owner, 5000) }
                runOnUiThread {
                    if (user != owner) return@runOnUiThread
                    result.onSuccess { rows ->
                        pendingExport = "id,fecha_epoch_ms,fuente,lux,estado,led\n" + rows.joinToString("\n") { "${it.id},${it.timestamp},${it.source},${it.lux ?: ""},${it.level.code},${it.level.led}" }
                        startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE); type = "text/csv"; putExtra(Intent.EXTRA_TITLE, "lecturas_luz.csv")
                        }, 20)
                    }.onFailure { toast("No se pudo leer el historial cifrado.") }
                }
            }
        }
        text("El CSV es una copia sin cifrado en la ubicación que elijas. Comparte sólo los datos necesarios.", 13)
        button("Eliminar mi historial") {
            AlertDialog.Builder(this).setTitle("¿Eliminar tus lecturas?").setMessage("Esta acción borra sólo el historial de $owner en este teléfono.")
                .setPositiveButton("Eliminar") { _, _ -> worker.execute { db.clear(owner); runOnUiThread { if (user == owner) history() } } }
                .setNegativeButton("Cancelar", null).show()
        }
        button("Volver") { showRoles() }
        worker.execute {
            val result = runCatching { db.history(owner) }
            runOnUiThread {
                if (user != owner || role.isNotEmpty() || isDestroyed) return@runOnUiThread
                result.onSuccess { rows ->
                    loading.text = if (rows.isEmpty()) "Aún no hay lecturas. Inicia el sensor o conecta el monitor." else "${rows.size} lecturas disponibles"
                    rows.forEach { r -> text("${time(r.timestamp)}\n${r.lux?.let { "%.1f lx".format(it) } ?: "Estado ${r.level.code}"} • ${r.level.label}\n${sourceLabel(r.source)}", 14) }
                }.onFailure { loading.text = "No se pudo leer el historial cifrado." }
            }
        }
    }
    @Deprecated("Activity result API de plataforma")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 20) {
            val csv = pendingExport; pendingExport = ""
            if (resultCode == RESULT_OK) data?.data?.let { uri -> worker.execute {
                val result = runCatching { contentResolver.openOutputStream(uri)?.use { it.write(csv.toByteArray(Charsets.UTF_8)) } ?: error("Archivo no disponible") }
                runOnUiThread { toast(if (result.isSuccess) "CSV exportado." else "No se pudo exportar el CSV.") }
            } }
        }
    }

    private fun stopSession() {
        generation++; sourceGeneration++; role = ""; handler.removeCallbacks(tick)
        link?.close(); link = null; hc05?.close(); hc05 = null
        sensors.unregisterListener(this); latestLux = null; hcCode = null
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
    private fun logout() { stopSession(); user = null; showLogin() }
    override fun onStop() {
        super.onStop()
        if (role.isNotEmpty()) {
            stopSession()
            if (user != null) showRoles()
        }
    }
    override fun onDestroy() { stopSession(); worker.shutdown(); authWorker.shutdown(); super.onDestroy() }
    @Deprecated("Navegación de plataforma")
    override fun onBackPressed() {
        if (authBusy) { toast("Espera a que termine el acceso."); return }
        if (user != null) showRoles() else if (accessRegister) showLogin() else super.onBackPressed()
    }
}
