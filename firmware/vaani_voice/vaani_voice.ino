// vaani_voice v2 — wake word + voice-note recording + TFT dashboard with live feedback.
// Standalone (does NOT touch vaani_link.ino). INMP441 mic + ST7735 TFT + microSD, ESP-SR wake word.
//
// Flow:  idle ("Say: Hi ESP")  --wake-->  "DETECTED!"  -->  "Listening..." (records ~6s to SD)
//        -->  "Saved note_N.wav"  -->  back to idle.
//
// Screen shows: engine state (what it's doing / listening), SD status, notes-recorded count,
// and big wake-word feedback (WAITING / DETECTED / LISTENING / SAVED).
//
// DISPLAY SAFETY: the TFT is redrawn ONLY when state changes (dispDirty flag, rendered from loop),
// never every tick — continuous redraw while the SR audio task runs causes white-screen contention.
// SD + TFT share the HSPI bus (spiMutex); SR is paused during recording so it doesn't fight the mic.
//
// Mic INMP441 (I2S0): VDD->3V3 GND L/R->GND SCK->GPIO4 WS->GPIO5 SD->GPIO6
// TFT ST7735: SCLK12 MOSI11 MISO13 CS10 DC17 RST18 BL21    SD: CS14 (shared 12/11/13)
// BUILD: FQBN ...,PartitionScheme=esp_sr_16
// Wake word: say "Hi. Eee-Ess-Pee" clearly, ~10-15cm.

#include <Arduino.h>
#include "ESP_I2S.h"
#include "ESP_SR.h"
#include "esp32-hal-sr.h"
#include <SPI.h>
#include <SD.h>
#include <Adafruit_GFX.h>
#include <Adafruit_ST7735.h>
#include "freertos/FreeRTOS.h"
#include "freertos/semphr.h"

// ---- mic ----
#define MIC_SCK 4
#define MIC_WS  5
#define MIC_SD  6
#define SAMPLE_RATE 16000
#define REC_SECONDS 6
#define REC_GAIN_SHIFT 2   // <<2 = 4x, boosts the quiet 32->16 mic samples for an audible note

// ---- TFT + SD (shared HSPI) ----
#define TFT_SCLK 12
#define TFT_MOSI 11
#define TFT_MISO 13
#define TFT_CS   10
#define TFT_DC   17
#define TFT_RST  18
#define TFT_BL   21
#define SD_CS    14
SPIClass spiBus(HSPI);
Adafruit_ST7735 tft = Adafruit_ST7735(&spiBus, TFT_CS, TFT_DC, TFT_RST);
SemaphoreHandle_t spiMutex;

I2SClass i2s;
extern "C" void on_sr_event_trampoline(void* arg, sr_event_t event, int command_id, int phrase_id);

// ---- state machine ----
enum VState { ST_INIT, ST_WAITING, ST_DETECTED, ST_RECORDING, ST_SAVED, ST_ERROR };
volatile VState g_state = ST_INIT;
volatile bool   g_dispDirty = true;
volatile bool   g_wakeFired = false;   // set by SR callback, handled in loop()
String  g_detail = "";
bool    g_sdOk = false;
int     g_notesRecorded = 0;

void setState(VState s, const String& detail = "") { g_state = s; g_detail = detail; g_dispDirty = true; }

// ---- TFT (all draws guarded by spiMutex; called only from loop) ----
void tline(int y, const String& s, uint16_t c) {
  tft.fillRect(0, y, tft.width(), 16, ST77XX_BLACK);
  tft.setTextColor(c, ST77XX_BLACK); tft.setCursor(2, y); tft.print(s);
}
void renderScreen() {
  const char* stateTxt; uint16_t stateCol;
  switch (g_state) {
    case ST_INIT:      stateTxt = "Starting...";   stateCol = ST77XX_WHITE;  break;
    case ST_WAITING:   stateTxt = "Say: Hi ESP";   stateCol = ST77XX_CYAN;   break;
    case ST_DETECTED:  stateTxt = "DETECTED!";     stateCol = ST77XX_GREEN;  break;
    case ST_RECORDING: stateTxt = "Listening...";  stateCol = ST77XX_ORANGE; break;
    case ST_SAVED:     stateTxt = "Saved";         stateCol = ST77XX_GREEN;  break;
    default:           stateTxt = "Error";         stateCol = ST77XX_RED;    break;
  }
  if (xSemaphoreTake(spiMutex, pdMS_TO_TICKS(500)) != pdTRUE) return;
  tft.setTextSize(1);
  tline(2,  "VAANI VOICE", ST77XX_WHITE);
  tft.setTextSize(2); tline(22, stateTxt, stateCol); tft.setTextSize(1);
  tline(46, g_detail, ST77XX_YELLOW);
  tline(88,  String("SD: ") + (g_sdOk ? "ready" : "FAIL"), g_sdOk ? ST77XX_GREEN : ST77XX_RED);
  tline(104, String("Notes recorded: ") + String(g_notesRecorded), ST77XX_WHITE);
  xSemaphoreGive(spiMutex);
}

// ---- WAV note recording (SR paused; reads mic i2s directly) ----
void writeLE32(File& f, uint32_t v) { f.write((uint8_t)(v)); f.write((uint8_t)(v>>8)); f.write((uint8_t)(v>>16)); f.write((uint8_t)(v>>24)); }
void writeLE16(File& f, uint16_t v) { f.write((uint8_t)(v)); f.write((uint8_t)(v>>8)); }
void writeWavHeader(File& f, uint32_t dataLen) {
  f.write((const uint8_t*)"RIFF", 4); writeLE32(f, 36 + dataLen); f.write((const uint8_t*)"WAVE", 4);
  f.write((const uint8_t*)"fmt ", 4); writeLE32(f, 16); writeLE16(f, 1); writeLE16(f, 1);
  writeLE32(f, SAMPLE_RATE); writeLE32(f, SAMPLE_RATE * 2); writeLE16(f, 2); writeLE16(f, 16);
  f.write((const uint8_t*)"data", 4); writeLE32(f, dataLen);
}

void recordNote() {
  if (!g_sdOk) { setState(ST_ERROR, "No SD card"); return; }
  sr_pause();  // stop ESP-SR reading the mic so we can capture it ourselves

  char path[40]; snprintf(path, sizeof(path), "/vaani/note_%lu.wav", (unsigned long)millis());
  xSemaphoreTake(spiMutex, portMAX_DELAY);
  File f = SD.open(path, FILE_WRITE);
  if (!f) { xSemaphoreGive(spiMutex); sr_resume(); setState(ST_ERROR, "SD open fail"); return; }
  writeWavHeader(f, 0);  // placeholder, patched after
  xSemaphoreGive(spiMutex);

  static int16_t buf[512];
  uint32_t totalSamples = 0, targetSamples = (uint32_t)SAMPLE_RATE * REC_SECONDS;
  uint32_t startMs = millis(); int lastShown = -1;
  while (totalSamples < targetSamples) {
    size_t got = i2s.readBytes((char*)buf, sizeof(buf));   // 16-bit samples (32->16 transform)
    size_t n = got / sizeof(int16_t);
    for (size_t i = 0; i < n; i++) {
      int32_t v = (int32_t)buf[i] << REC_GAIN_SHIFT;
      if (v > 32767) v = 32767; else if (v < -32768) v = -32768;
      buf[i] = (int16_t)v;
    }
    xSemaphoreTake(spiMutex, portMAX_DELAY);
    f.write((uint8_t*)buf, n * sizeof(int16_t));
    xSemaphoreGive(spiMutex);
    totalSamples += n;
    int secLeft = REC_SECONDS - (int)((millis() - startMs) / 1000);
    if (secLeft != lastShown) { lastShown = secLeft; setState(ST_RECORDING, String("recording ") + String(secLeft) + "s"); renderScreen(); g_dispDirty = false; }
  }

  uint32_t dataLen = totalSamples * sizeof(int16_t);
  xSemaphoreTake(spiMutex, portMAX_DELAY);
  f.seek(0); writeWavHeader(f, dataLen); f.close();
  xSemaphoreGive(spiMutex);

  g_notesRecorded++;
  const char* base = strrchr(path, '/'); base = base ? base + 1 : path;
  Serial.printf(">> saved %s (%u bytes)\n", path, (unsigned)(dataLen + 44));
  setState(ST_SAVED, String(base));
  sr_resume();  // back to listening for the wake word
}

void onSrEvent(sr_event_t event, int command_id, int phrase_id) {
  if (event == SR_EVENT_WAKEWORD) {
    Serial.println(">> WAKE: Hi ESP");
    g_wakeFired = true;             // handle the (blocking) recording in loop(), not here
    setState(ST_DETECTED);
  }
}
extern "C" void on_sr_event_trampoline(void* arg, sr_event_t event, int command_id, int phrase_id) {
  onSrEvent(event, command_id, phrase_id);
}

void setup() {
  Serial.begin(115200);
  delay(400);
  Serial.println("\n=== Vaani Voice v2 (wake + record + TFT) ===");

  spiMutex = xSemaphoreCreateMutex();

  // TFT (MISO=13 REQUIRED or the panel inits white)
  pinMode(TFT_BL, OUTPUT); digitalWrite(TFT_BL, HIGH);
  // Explicit hardware reset pulse — a warm reset (after flashing) sometimes leaves the ST7735
  // uninitialized (white screen) unless it gets a full RST low->high cycle + settle time.
  pinMode(TFT_RST, OUTPUT);
  digitalWrite(TFT_RST, HIGH); delay(20);
  digitalWrite(TFT_RST, LOW);  delay(20);
  digitalWrite(TFT_RST, HIGH); delay(150);
  spiBus.begin(TFT_SCLK, TFT_MISO, TFT_MOSI, -1);
  tft.initR(INITR_BLACKTAB); tft.setSPISpeed(20000000); tft.setRotation(1);
  tft.fillScreen(ST77XX_BLACK);
  setState(ST_INIT, "mic + SD..."); renderScreen(); g_dispDirty = false;

  // SD on the shared bus
  g_sdOk = SD.begin(SD_CS, spiBus, 4000000);
  if (g_sdOk && !SD.exists("/vaani")) SD.mkdir("/vaani");
  Serial.printf("SD: %s\n", g_sdOk ? "ready" : "FAIL");

  // Mic: 32-bit + library 32->16 transform (AFE handles wake-word gain)
  i2s.setTimeout(1000);
  i2s.setPins(MIC_SCK, MIC_WS, -1, MIC_SD);
  if (!i2s.begin(I2S_MODE_STD, SAMPLE_RATE, I2S_DATA_BIT_WIDTH_32BIT, I2S_SLOT_MODE_MONO, I2S_STD_SLOT_LEFT)) {
    Serial.println("I2S FAILED"); setState(ST_ERROR, "mic fail"); renderScreen(); return;
  }
  i2s.configureRX(SAMPLE_RATE, I2S_DATA_BIT_WIDTH_32BIT, I2S_SLOT_MODE_MONO, I2S_RX_TRANSFORM_32_TO_16);

  // ESP-SR wake word. MultiNet still builds an FST from these, so they MUST be real English
  // phrases in its dictionary (a made-up placeholder null-derefs build_fsts). We only act on the
  // wake word here (record a note), but keep valid commands so the engine initializes cleanly.
  static const sr_cmd_t cmds[] = {
    {0, "Start recording"},
    {1, "Stop recording"},
    {2, "Sync notes"},
  };
  ESP_SR.onEvent(onSrEvent);
  if (!ESP_SR.begin(i2s, cmds, sizeof(cmds) / sizeof(sr_cmd_t), SR_CHANNELS_MONO, SR_MODE_WAKEWORD, "M")) {
    Serial.println("ESP_SR FAILED"); setState(ST_ERROR, "SR fail"); renderScreen(); return;
  }
  Serial.println("Ready. Say 'Hi ESP' to record a note.");
  // Panel self-test AFTER ESP-SR is running — tells us if the SR task corrupts the TFT.
  xSemaphoreTake(spiMutex, portMAX_DELAY);
  tft.fillScreen(ST77XX_RED);   delay(400);
  tft.fillScreen(ST77XX_GREEN); delay(400);
  tft.fillScreen(ST77XX_BLUE);  delay(400);
  tft.fillScreen(ST77XX_BLACK);
  xSemaphoreGive(spiMutex);
  setState(ST_WAITING);
}

void loop() {
  if (g_wakeFired) {
    g_wakeFired = false;
    renderScreen(); g_dispDirty = false;   // show DETECTED
    delay(400);
    setState(ST_RECORDING, "recording...");
    recordNote();                          // blocks ~6s, updates screen itself
    renderScreen(); g_dispDirty = false;   // show SAVED
    delay(1200);
    setState(ST_WAITING);
  }
  if (g_dispDirty) { renderScreen(); g_dispDirty = false; }
  delay(30);
}
