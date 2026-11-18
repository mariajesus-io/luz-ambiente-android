# Integración Arduino y Android

## Circuito recibido

Los originales implementan Nano A con LDR y HC-05 maestro, y Nano B con HC-05 esclavo y tres LEDs. El nodo A transmite un único carácter cuando cambia de nivel. `puente_at.ino` se utiliza únicamente para configurar un módulo por vez.

| Carácter | Estado del LDR | Acción original en nodo B |
|---|---|---|
| 0 | Mucha luz | Apaga los LEDs |
| 1 | Luz media | Enciende amarillo |
| 2 | Poca luz | Enciende azul |
| 3 | Oscuridad | Enciende rojo |

Los límites ADC originales son 250, 450 y 700, con histéresis de 20. Se conservan sin convertirlos a lux.

## Topología para la app

`LDR + Nano A → HC-05 esclavo → Android A sensor → Wi-Fi TLS → Android B monitor`

El monitor simula el LED. El nodo B original sigue disponible para la demostración Arduino a Arduino, pero no se controla desde esta versión de la app. Las órdenes PAUSE/RESUME controlan el envío desde la app sensor.

Un HC-05 no mantiene al mismo tiempo el enlace punto a punto con otro HC-05 y con el teléfono. Para esta topología hay que desconectar el par original y configurar **el HC-05 del sensor como esclavo**, conservando el cableado serial y 9600 baudios de datos. Los comandos y el procedimiento AT pueden variar según firmware: consultar el módulo antes de modificarlo. Comandos habituales de consulta/configuración son `AT`, `AT+ROLE?`, `AT+ROLE=0`, `AT+UART?` y `AT+UART=9600,0,0`. El puente original usa 38400 baudios para AT; la app no envía comandos AT.

1. Conserva una copia de la configuración anterior.
2. Conecta un solo HC-05 al puente y entra en modo AT conforme a tu placa.
3. Ajusta el rol de ese módulo a esclavo y los baudios de datos a 9600.
4. Carga el sketch original del sensor o `nodo_sensor_android/nodo_sensor_android.ino` en Nano A.
5. Empareja desde Ajustes Android usando el PIN configurado en tu módulo; no asumas una clave universal.
6. Dentro de la app sensor elige Arduino con HC-05, concede el permiso de dispositivos cercanos si se solicita y selecciona el módulo emparejado.
7. Cambia la luz para recibir un estado. La variante opcional repite el estado cada dos segundos; el original sólo transmite cambios.

## Cableado conservado

- LDR: 5 V → LDR → A0 → resistencia de 10 kΩ → GND.
- HC-05 TXD → D2 de Nano. D3 de Nano → RXD del módulo mediante el divisor de tensión indicado en los sketches.
- Masa común. Alimentación según la placa adaptadora específica del HC-05; la lógica RX del módulo debe protegerse a 3,3 V.
- Los LEDs del nodo B mantienen resistencias y pines D5, D6 y D7 indicados en su original.

No se verificaron el montaje, el firmware AT ni la calibración física en este entorno. El enlace HC-05 no implementa el cifrado TLS de la red entre Android.
