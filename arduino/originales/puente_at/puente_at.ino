/*
 * PUENTE AT - Para configurar un HC-05 desde el Monitor Serial
 * Sube esto a CUALQUIER Nano, conecta un solo HC-05 (en modo AT, LED
 * parpadeando lento) y usa el Monitor Serial para mandar comandos AT.
 *
 * Monitor Serial: configurar en "Ambos NL y CR" (o "Newline") y probar
 * primero a 38400 baudios (típico en modo AT), si no responde, probar 9600.
 */

#include <SoftwareSerial.h>

const int PIN_RX = 2; // al TXD del HC-05
const int PIN_TX = 3; // al RXD del HC-05 (con divisor 1k/2k)
SoftwareSerial bt(PIN_RX, PIN_TX);

void setup() {
  Serial.begin(38400);   // baudrate del Monitor Serial (ajustar si no responde)
  bt.begin(38400);        // baudrate de modo AT del HC-05 (ajustar si no responde)
  Serial.println("Puente AT listo. Escribe un comando, ej: AT");
}

void loop() {
  if (Serial.available()) {
    String cmd = Serial.readStringUntil('\n');
    cmd.trim();
    bt.print(cmd);
    bt.print("\r\n");
  }
  if (bt.available()) {
    while (bt.available()) {
      Serial.write(bt.read());
    }
  }
}
