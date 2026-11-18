# Verificación y demostración

## Pruebas automatizadas

Comando: `./gradlew testDebugUnitTest lintDebug assembleDebug`.

`CoreTest` verifica clasificación y límites, rechazo de datos contradictorios, ausencia de lux inventados en HC-05, tamaño máximo de tramas, UTF-8, tramas truncadas, formato de enlace y contraseñas con sal.

`PeerLinkTest` conecta por loopback dos extremos TLS reales de `PeerLink`: envía lectura, orden PAUSE y acuse; además comprueba rechazo de una huella o clave incorrecta. El certificado de prueba está sólo en `src/test/resources` y no se incluye en el APK.

Consultar `docs/RESULTADOS_COMPILACION.txt` para el resultado efectivamente obtenido en este entorno.

## Integración ejecutada en Android emulado

Se ejecutaron y aprobaron cuatro pruebas en Android 10 API 29 x86_64: autenticación y persistencia SQLite cifrada con aislamiento por usuario y deduplicación; bloqueo de login persistente; rechazo de manipulación o contexto incorrecto en AES-GCM; y comunicación TLS bidireccional con una clave EC en Android Keystore. Los dos extremos se ejecutaron en el mismo emulador mediante loopback, sin representar dos teléfonos físicos ni una prueba de radio.

El registro está en `docs/PRUEBAS_ANDROID_EJECUTADAS.txt`. Para repetirlas con un dispositivo o emulador conectado: `./gradlew connectedDebugAndroidTest`.

## Pruebas manuales pendientes en dos Android

Anotar modelo, versión Android, fecha, resultado y evidencia real de cada prueba. No marcar como aprobado sin ejecutarla.

| ID | Acción | Resultado esperado | Resultado físico |
|---|---|---|---|
| M01 | Crear una cuenta y volver a iniciar sesión | Se acepta contraseña correcta; se rechaza incorrecta | Pendiente |
| M02 | Ingresar cinco contraseñas erróneas y reiniciar app | Bloqueo persiste 30 segundos para ese usuario | Pendiente |
| M03 | Registrar usuario repetido o entrada SQL | Rechazo validado; no se modifica otra cuenta | Pendiente |
| M04 | Crear segundo usuario y abrir historial | No consulta lecturas de la primera cuenta | Pendiente |
| M05 | Conectar dos Android según README | Huella y clave verificadas; se muestra TLS | Pendiente |
| M06 | Alterar huella o clave del código de enlace | Conexión rechazada sin aceptar lecturas | Pendiente |
| M07 | Cubrir sensor real o usar simulación 100, 300, 500 y 900 lx | Ambos muestran estados 3, 2, 1 y 0 y origen correcto | Pendiente |
| M08 | PAUSE y RESUME desde monitor | Sensor cambia envío y monitor recibe confirmación | Pendiente |
| M09 | Mantener fuente sin cambios por 30 s | El sensor mantiene lecturas y la conexión sigue activa | Pendiente |
| M10 | Apagar Wi-Fi o cerrar sensor | Se informa pérdida del enlace o datos antiguos | Pendiente |
| M11 | Abrir historial y reiniciar app | Persisten lecturas del usuario; reconexión explícita | Pendiente |
| M12 | Exportar y abrir CSV | Filas y unidad coinciden; HC05 no inventa lux | Pendiente |
| M13 | Rechazar permiso Bluetooth | App informa y permite usar otra fuente | Pendiente |
| M14 | Conectar HC-05 libre, esclavo, con estados 0 a 3 | Se reciben códigos sin conversión ADC a lux | Pendiente |
| M15 | Fondo, giro o cierre de app | Se cierran recursos; se puede volver a iniciar sesión o rol | Pendiente |
| M16 | Mantener lectura y recepción durante 10 minutos | Sin cierre, duplicados por UUID ni crecimiento por encima de 5000 lecturas por usuario | Pendiente |
| M17 | Ejecutar en Android sin TYPE_LIGHT | Se informa ausencia y se ofrece simulación o HC-05 | Pendiente |

## Guion de exposición

1. Explicar el problema y la relación con el circuito Arduino anterior.
2. Mostrar registro e inicio de sesión locales en los dos teléfonos.
3. Mostrar el sensor y el monitor enlazados. Transferir el código fuera de la proyección pública.
4. Variar la luz o usar simulación identificada y mostrar los cuatro niveles.
5. Pausar y reanudar desde el monitor; mostrar el acuse en la pantalla.
6. Mostrar historial por usuario y exportación CSV.
7. Explicar hash de contraseñas, cifrado de lecturas, huella TLS y permisos mínimos.
8. Explicar que HC-05 entrega estados discretos y su enlace no tiene TLS.
9. Presentar pruebas realizadas y pendientes sin sustituir la demostración física por resultados JVM.
