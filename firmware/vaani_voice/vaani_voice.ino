// vaani_voice — P2+P3 wake word + commands. STOCK ESP_SR path (uses the AFE — required for WakeNet).
// Serial-only while tuning. INMP441 read as 32-bit + library 32->16 transform; the AFE does gain.
//
// Mic INMP441 (I2S0): VDD->3V3 GND L/R->GND SCK->GPIO4 WS->GPIO5 SD->GPIO6
// BUILD: FQBN ...,PartitionScheme=esp_sr_16
// Say "Hi ESP" (close, ~10-15cm) then a command.

#include <Arduino.h>
#include "ESP_I2S.h"
#include "ESP_SR.h"

#define MIC_SCK 4
#define MIC_WS  5
#define MIC_SD  6
#define I2S_SAMPLE_RATE 16000

I2SClass i2s;

#define SR_INPUT_FORMAT     "MN"
#define SR_INPUT_CHANNELS   SR_CHANNELS_STEREO
#define I2S_OUTPUT_CHANNELS I2S_SLOT_MODE_STEREO

enum { CMD_START_REC, CMD_STOP_REC, CMD_SYNC, CMD_PENDING, CMD_DELETE_LAST };
static const sr_cmd_t sr_commands[] = {
  {CMD_START_REC,   "Start recording"},
  {CMD_START_REC,   "Begin recording"},
  {CMD_STOP_REC,    "Stop recording"},
  {CMD_STOP_REC,    "End recording"},
  {CMD_SYNC,        "Sync notes"},
  {CMD_SYNC,        "Sync recordings"},
  {CMD_PENDING,     "How many pending"},
  {CMD_DELETE_LAST, "Delete last"},
  {CMD_DELETE_LAST, "Delete last recording"},
};

void onSrEvent(sr_event_t event, int command_id, int phrase_id) {
  switch (event) {
    case SR_EVENT_WAKEWORD:
      Serial.println("\n>>> WAKE WORD: Hi ESP  (say a command now)");
      ESP_SR.setMode(SR_MODE_COMMAND);
      break;
    case SR_EVENT_WAKEWORD_CHANNEL: ESP_SR.setMode(SR_MODE_COMMAND); break;
    case SR_EVENT_TIMEOUT:
      Serial.println(".. command timeout, back to wake word");
      ESP_SR.setMode(SR_MODE_WAKEWORD);
      break;
    case SR_EVENT_COMMAND: {
      const char* n = "?";
      switch (command_id) {
        case CMD_START_REC: n = "START RECORDING"; break;
        case CMD_STOP_REC:  n = "STOP RECORDING";  break;
        case CMD_SYNC:      n = "SYNC NOTES";       break;
        case CMD_PENDING:   n = "HOW MANY PENDING"; break;
        case CMD_DELETE_LAST: n = "DELETE LAST";    break;
      }
      Serial.printf(">>> COMMAND: %s\n", n);
      ESP_SR.setMode(SR_MODE_COMMAND);
      break;
    }
    default: break;
  }
}

void setup() {
  Serial.begin(115200);
  delay(400);
  Serial.println("\n=== Vaani Voice (stock ESP_SR + AFE) ===");

  i2s.setTimeout(1000);
  i2s.setPins(MIC_SCK, MIC_WS, -1, MIC_SD);
  // INMP441 on the LEFT slot of a stereo bus; "MN" = mic + unused right (AFE-friendly for 1 mic).
  if (!i2s.begin(I2S_MODE_STD, I2S_SAMPLE_RATE, I2S_DATA_BIT_WIDTH_32BIT, I2S_SLOT_MODE_STEREO)) {
    Serial.println("I2S begin FAILED"); return;
  }
  i2s.configureRX(I2S_SAMPLE_RATE, I2S_DATA_BIT_WIDTH_32BIT, I2S_SLOT_MODE_STEREO, I2S_RX_TRANSFORM_32_TO_16);
  Serial.println("mic ready");

  ESP_SR.onEvent(onSrEvent);
  if (!ESP_SR.begin(i2s, sr_commands, sizeof(sr_commands) / sizeof(sr_cmd_t),
                    SR_INPUT_CHANNELS, SR_MODE_WAKEWORD, SR_INPUT_FORMAT)) {
    Serial.println("ESP_SR begin FAILED"); return;
  }
  Serial.println("Ready. Say 'Hi ESP' then a command.");
}

void loop() { delay(100); }
