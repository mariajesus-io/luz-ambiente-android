/*
 * NODO A - SENSOR DE LUZ (Arduino Nano #1)
 * Lee un LDR y envía por Bluetooth (HC-05 Maestro) el nivel de luz
 * a otro Arduino que enciende LEDs.
 *
 * Conexiones:
 *  - LDR: 5V -- LDR -- A0 -- R(10k) -- GND
 *  - HC-05: VCC->5V, GND->GND, TXD->D2, RXD->D3 (con divisor 1k/2k)
 */

#include <SoftwareSerial.h>

const int PIN_RX = 2; // conectado al TXD del HC-05
const int PIN_TX = 3; // conectado al RXD del HC-05 (via divisor resistivo)
SoftwareSerial bt(PIN_RX, PIN_TX);

const int PIN_LDR = A0;

// Umbrales de luz (ajustar según tu ambiente real, ver sección de calibración)
const int UMBRAL_MEDIA_ALTA = 700;   // arriba de esto = "mucha luz"
const int UMBRAL_BAJA_MEDIA = 450;   // entre este y el anterior = "luz media"
const int UMBRAL_OSCURO_BAJA = 250;  // entre este y el anterior = "poca luz"
                                      // debajo de este = "oscuridad"

const int HISTERESIS = 20; // margen para evitar parpadeo en los bordes

char estadoActual = 'X'; // estado enviado la última vez, para no reenviar si no cambió

void setup() {
  Serial.begin(9600);   // para depurar por USB
  bt.begin(9600);        // debe coincidir con AT+UART configurado en el HC-05
  pinMode(PIN_LDR, INPUT);
  Serial.println("Nodo sensor iniciado.");
}

char calcularEstado(int lectura) {
  // Se usa el estado actual como referencia para aplicar histéresis
  switch (estadoActual) {
    case '0': // estaba en "mucha luz"
      if (lectura < UMBRAL_MEDIA_ALTA - HISTERESIS) break; // baja de nivel
      return '0';
    case '1': // estaba en "luz media"
      if (lectura >= UMBRAL_MEDIA_ALTA + HISTERESIS) return '0';
      if (lectura < UMBRAL_BAJA_MEDIA - HISTERESIS) break;
      return '1';
    case '2': // estaba en "poca luz"
      if (lectura >= UMBRAL_BAJA_MEDIA + HISTERESIS) return '1';
      if (lectura < UMBRAL_OSCURO_BAJA - HISTERESIS) break;
      return '2';
    case '3': // estaba en "oscuridad"
      if (lectura >= UMBRAL_OSCURO_BAJA + HISTERESIS) return '2';
      return '3';
  }

  // Clasificación normal (primera vez o tras romper la histéresis)
  if (lectura >= UMBRAL_MEDIA_ALTA) return '0';
  if (lectura >= UMBRAL_BAJA_MEDIA) return '1';
  if (lectura >= UMBRAL_OSCURO_BAJA) return '2';
  return '3';
}

void loop() {
  int lectura = analogRead(PIN_LDR);
  char nuevoEstado = calcularEstado(lectura);

  if (nuevoEstado != estadoActual) {
    estadoActual = nuevoEstado;
    bt.write(estadoActual); // envía un solo carácter: '0','1','2' o '3'
    Serial.print("Lectura LDR: ");
    Serial.print(lectura);
    Serial.print(" -> Estado enviado: ");
    Serial.println(estadoActual);
  }

  delay(200); // pequeño muestreo, no hace falta más rápido para luz ambiente
}
