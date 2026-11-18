# Luz Ambiente

Aplicación Android en **Kotlin**, versión **1.3**, para monitorear la luz ambiental y compartir lecturas entre dos teléfonos por Wi-Fi. Proyecto académico de María Jesús Erices Ferrada para TI3042, Aplicaciones Móviles para IoT.

## Funciones

- Registro local con usuario, correo y contraseña de **6 a 12 caracteres**.
- Ingreso con usuario o correo; cada cuenta se guarda en ese teléfono.
- Sensor de luz Android en lux, simulación identificada y entrada opcional desde HC-05.
- Roles sensor y monitor en el mismo APK, con comunicación TLS por Wi-Fi local.
- Gráfico de lecturas y LED virtual según el nivel de iluminación.
- Pausa y reanudación del envío, historial por usuario y exportación CSV.
- SQLite, PBKDF2, cifrado AES-GCM y Android Keystore.

La app muestra un **LED virtual**. No enciende luces ni controla directamente los LEDs físicos del Arduino. El correo identifica la cuenta local; no se envían mensajes de verificación ni se sincronizan cuentas entre teléfonos.

## Abrir en Android Studio

1. Clona o descarga este repositorio.
2. En Android Studio elige **File → Open** y abre la carpeta que contiene `settings.gradle.kts`.
3. Usa **JDK 17**, **Android SDK Platform 35** y **Build Tools 35.0.0**. El proyecto utiliza Gradle 8.11.1, AGP 8.9.2 y Kotlin 2.1.20.
4. Espera la sincronización. Selecciona un teléfono o emulador Android 8.0 o posterior y pulsa **Run**.

La interfaz está programada con Views en Kotlin y se observa al ejecutar la aplicación.

## Probar sin Arduino

1. Pulsa **Crear cuenta**, completa usuario, correo y contraseña y selecciona **Registrarme**.
2. Elige **Usar como sensor** y selecciona **Simulación identificada**.
3. Mueve la barra para observar los niveles y consulta **Historial y exportación**.

Para el monitoreo remoto, instala el mismo APK en dos Android conectados al mismo Wi-Fi. En el sensor inicia el enlace seguro y comparte el código completo con el monitor por un canal privado. El monitor permite pausar y reanudar el envío. El procedimiento está en [docs/PRUEBAS.md](docs/PRUEBAS.md).

## Compilar y verificar

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

En Windows utiliza `gradlew.bat`. El APK se genera en `app/build/outputs/apk/debug/app-debug.apk`.

Las pruebas de Android se ejecutan con un dispositivo o emulador conectado:

```bash
./gradlew connectedDebugAndroidTest
```

Se aprobaron 16 pruebas JVM y ocho comprobaciones Android en un emulador Android 12 API 31. Incluyen registro por correo, migración de cuentas, cifrado, comunicación TLS y un recorrido de interfaz mediante toques. Consulta [los resultados](docs/RESULTADOS_V1.3.txt). La prueba de interfaz permite más tiempo en el emulador sin aceleración.

La comprobación en el Redmi 9T, la comunicación entre dos teléfonos físicos y el circuito HC-05 deben realizarse en los equipos reales.

## Código principal

| Archivo | Responsabilidad |
|---|---|
| `MainActivity.kt` | Pantallas, acceso, sensor, roles y navegación |
| `AppDatabase.kt` | Usuarios, correo, migración e historial |
| `PasswordHasher.kt` | Reglas y protección de contraseñas |
| `PeerLink.kt` y `Pairing.kt` | Comunicación y verificación TLS |
| `Vault.kt` | Cifrado de lecturas |
| `Hc05Reader.kt` | Recepción de estados Arduino |

Los archivos Kotlin están en `app/src/main/java/cl/ti3042/luzambiente/`. La carpeta [arduino](arduino/INTEGRACION.md) conserva los sketches originales y una variante opcional del sensor. El certificado de `app/src/test/resources` es una identidad de prueba para los tests JVM; la app genera su identidad propia en Android Keystore.

El informe y el APK de entrega se incluyen en la carpeta de entrega completa, fuera de este repositorio de código.
