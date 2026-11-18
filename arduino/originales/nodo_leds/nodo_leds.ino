/*
 * NODO B - CONTROL DE LEDs (Arduino Nano #2)
 * Recibe por Bluetooth (HC-05 Esclavo) el nivel de luz detectado
 * por el otro Arduino y enciende el LED correspondiente.
 *
 * Conexiones:
 *  - LED amarillo: D5 -- R(330) -- LED -- GND
 *  - LED azul:     D6 -- R(220) -- LED -- GND
 *  - LED rojo:     D7 -- R(330) -- LED -- GND
 *  - HC-05: VCC->5V, GND->GND, TXD->D2, RXD->D3 (con divisor 1k/2k)
 */

#include <SoftwareSerial.h>

const int PIN_RX = 2; // conectado al TXD del HC-05
const int PIN_TX = 3; // conectado al RXD del HC-05 (via divisor resistivo)
SoftwareSerial bt(PIN_RX, PIN_TX);

const int PIN_LED_AMARILLO = 5;
const int PIN_LED_AZUL = 6;
const int PIN_LED_ROJO = 7;

void apagarTodos() {
  digitalWrite(PIN_LED_AMARILLO, LOW);
  digitalWrite(PIN_LED_AZUL, LOW);
  digitalWrite(PIN_LED_ROJO, LOW);
}

void aplicarEstado(char estado) {
  apagarTodos();
  switch (estado) {
    case '0': // mucha luz -> todo apagado
      break;
    case '1': // luz media -> amarillo
      digitalWrite(PIN_LED_AMARILLO, HIGH);
      break;
    case '2': // poca luz -> azul
      digitalWrite(PIN_LED_AZUL, HIGH);
      break;
    case '3': // oscuridad -> rojo
      digitalWrite(PIN_LED_ROJO, HIGH);
      break;
    default:
      // carácter no reconocido, no hacer nada
      break;
  }
}

void setup() {
  Serial.begin(9600);
  bt.begin(9600); // debe coincidir con AT+UART del HC-05 esclavo

  pinMode(PIN_LED_AMARILLO, OUTPUT);
  pinMode(PIN_LED_AZUL, OUTPUT);
  pinMode(PIN_LED_ROJO, OUTPUT);

  apagarTodos();
  Serial.println("Nodo LEDs iniciado, esperando datos...");
}

void loop() {
  if (bt.available()) {
    char c = bt.read();
    if (c >= '0' && c <= '3') {
      aplicarEstado(c);
      Serial.print("Estado recibido: ");
      Serial.println(c);
    }
  }
}
