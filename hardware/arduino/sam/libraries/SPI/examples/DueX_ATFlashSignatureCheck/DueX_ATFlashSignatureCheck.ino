
#include <SPI.h>

// Flash memory is connected on SPI pin SS3
#define FLASH PIN_SPI_SS3

void setup() {
  Serial.begin(9600);

  // Start SPI with FLASH device
  SPI.begin(FLASH);
  // Half clock speed: we are too fast with 1
  SPI.setClockDivider(FLASH, 2);
}

void loop() {
  // Send "identify" command (9f) and receive response 
  // on the same SPI transaction. Parameter SPI_CONTINUE 
  // keeps the SS pin active.
  Serial.println("Sending 'Identify' cmd to flash => 9F");
  SPI.transfer(FLASH, 0x9f, SPI_CONTINUE);
  char a1 = SPI.transfer(FLASH, 0x00, SPI_CONTINUE);
  char a2 = SPI.transfer(FLASH, 0x00, SPI_CONTINUE);
  char a3 = SPI.transfer(FLASH, 0x00, SPI_CONTINUE);
  char a4 = SPI.transfer(FLASH, 0x00, SPI_CONTINUE);
  char a5 = SPI.transfer(FLASH, 0x00);

  // Print response over serial port
  Serial.print("Received signature: ");
  Serial.print(a1, HEX);
  Serial.print(a2, HEX);
  Serial.print(a3, HEX);
  Serial.print(a4, HEX);
  Serial.println(a5, HEX);
  
  delay(1000);
}
