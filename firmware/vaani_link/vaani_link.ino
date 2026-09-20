// Vaani wearable link firmware — v5: app-link (WiFi SoftAP + BLE GATT + SD + TFT dashboard, secured)
// PLUS on-device wake word (ESP-SR) + hands-free voice-note recording with silence auto-stop.
// Board: ESP32-S3 N16R8.  BUILD FQBN: ...,PartitionScheme=esp_sr_16  (ESP-SR needs the model partition).
//
// Hands-free flow:  idle (listening for "Hi ESP")  --wake-->  record to /vaani/note_<ms>.wav,
//   auto-stop after ~2s of silence (or 30s max)  -->  save  -->  pending++ shown on TFT  -->
//   app pulls it via the existing BLE/WiFi sync into the transcribe->enrich->note pipeline.
//
// Mic INMP441 (I2S0): VDD->3V3 GND L/R->GND SCK->GPIO4 WS->GPIO5 SD->GPIO6  (no SPI/SD/TFT conflict)
// SD: CS=14 SCK=12 MOSI=11 MISO=13 VCC=5V.  TFT: CS=10 DC=17 RST=18 SCK=12 MOSI=11 LED=21.
//
// BLE GATT service 6e40fda0-...: INFO(fda1 read) CMD(fda2 write) DATA(fda3 notify) STATUS(fda4 notify).
// Commands (write UTF-8 to CMD): INFO / LIST <dir> / RN <path> <off> <len> / WRITE <path> (+D:.. +WEND)
//   / DEL <path> / CRED / DISP <conn> <pending>\t<todo1>...  — results opcode-framed on DATA.
#include <WiFi.h>
#include <WebServer.h>
#include <SPI.h>
#include <SD.h>
#include <Adafruit_GFX.h>
#include <Adafruit_ST7735.h>
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLESecurity.h>
#include <BLE2902.h>
#include <Preferences.h>
#include <esp_random.h>
#include "ESP_I2S.h"
#include "ESP_SR.h"
#include "esp32-hal-sr.h"

// ---- shared SPI ----
#define SCLK 12
#define MOSI_PIN 11
#define MISO_PIN 13
#define SD_CS 14
#define TFT_CS 10
#define TFT_DC 17
#define TFT_RST 18
#define TFT_BL 21
SPIClass spiBus(HSPI);
Adafruit_ST7735 tft = Adafruit_ST7735(&spiBus, TFT_CS, TFT_DC, TFT_RST);
WebServer server(80);

// ---- mic + recording (ESP-SR / INMP441) ----
#define MIC_SCK 4
#define MIC_WS  5
#define MIC_SD  6
#define SAMPLE_RATE 16000
#define REC_GAIN_SHIFT 2       // <<2 = 4x, boosts the quiet 32->16 mic samples for an audible note
#define REC_MIN_MS     1500    // record at least this long before silence-stop can fire
#define REC_MAX_MS     30000   // hard cap so a noisy room can't record forever
#define SILENCE_MS     2000    // stop after this much CONTINUOUS silence
#define SILENCE_RMS    900     // window RMS below this (post-gain) counts as silence (tunable)
I2SClass i2s;
bool micOk = false;
volatile bool srReady = false;
volatile bool wakeFired = false;   // set by SR callback (BLE/SR core), handled in loop()
volatile bool recording = false;   // true while capturing a note (shown on TFT)
// Voice UI state shown as a big banner so the user knows exactly what the wearable is doing.
enum VoiceUi { VU_LISTEN, VU_WAKE, VU_REC, VU_SAVED };
volatile VoiceUi voiceUi = VU_LISTEN;
uint32_t voiceUiMs = 0;            // when the transient WAKE/SAVED banner was set (auto-reverts)
extern "C" void on_sr_event_trampoline(void* arg, sr_event_t event, int command_id, int phrase_id);

bool sdOk = false;
Preferences prefs;
String apSsid, apPass, apIp, restToken;
volatile uint32_t reqCount = 0;
SemaphoreHandle_t spiMutex;   // serialises SD (BLE core) vs TFT (loop core) on the shared bus

String randHex(size_t n){
  static const char* h = "0123456789abcdef";
  String s; s.reserve(n);
  while(s.length() < n){ uint32_t r = esp_random(); for(int i=0;i<8 && s.length()<n;i++){ s += h[(r>>(i*4))&0xF]; } }
  return s;
}

void loadOrCreateCreds(){
  prefs.begin("vaani", false);
  apPass    = prefs.getString("psk", "");
  restToken = prefs.getString("tok", "");
  bool changed = false;
  if(apPass.length() < 12){    apPass    = randHex(16); prefs.putString("psk", apPass); changed = true; }
  if(restToken.length() < 24){ restToken = randHex(32); prefs.putString("tok", restToken); changed = true; }
  prefs.end();
  Serial.printf("creds: %s (psk %d chars, token %d chars) [not shown]\n",
                changed?"generated+stored":"loaded from NVS", apPass.length(), restToken.length());
}

// DATA-characteristic frame opcodes (byte 0 of every notification).
static const uint8_t OP_DATA = 0x01;
static const uint8_t OP_END  = 0x02;
static const uint8_t OP_SZ   = 0x03;
static const uint8_t OP_ERR  = 0x04;

// ---- BLE ----
#define SVC_UUID  "6e40fda0-b5a3-f393-e0a9-e50e24dcca9e"
#define INFO_UUID "6e40fda1-b5a3-f393-e0a9-e50e24dcca9e"
#define CMD_UUID  "6e40fda2-b5a3-f393-e0a9-e50e24dcca9e"
#define DATA_UUID "6e40fda3-b5a3-f393-e0a9-e50e24dcca9e"
#define STAT_UUID "6e40fda4-b5a3-f393-e0a9-e50e24dcca9e"
BLECharacteristic *chInfo=nullptr, *chCmd=nullptr, *chData=nullptr, *chStat=nullptr;
void wifiOn(); void wifiOff();   // fwd decl: BLE "WIFI ON/OFF" command toggles the SoftAP on demand
volatile bool bleConnected = false;
File wrSession;
bool wrOpen = false;
uint32_t bleMtu = 20;

// ---- Dashboard state pushed by the app ----
bool     appLinked   = false;
int      pendingNotes = 0;
String   todos[5];
int      todoCount   = 0;
uint32_t lastAppMs   = 0;
volatile bool dispDirty = false;

String infoJson() {
  uint64_t sizeMB = sdOk ? SD.cardSize()/(1024ULL*1024ULL) : 0;
  uint64_t usedMB = sdOk ? SD.usedBytes()/(1024ULL*1024ULL) : 0;
  const char* tn = "NONE";
  if (sdOk){ uint8_t t=SD.cardType(); tn=t==CARD_MMC?"MMC":t==CARD_SD?"SDSC":t==CARD_SDHC?"SDHC":"UNKNOWN"; }
  String j = "{";
  j += "\"device\":\"Vaani-ESP32S3\",\"chip\":\"" + String(ESP.getChipModel()) + "\",";
  j += "\"freeHeap\":" + String(ESP.getFreeHeap()) + ",\"psramFree\":" + String(ESP.getFreePsram()) + ",";
  j += "\"sdOk\":" + String(sdOk?"true":"false") + ",\"sdType\":\"" + tn + "\",";
  j += "\"sdSizeMB\":" + String((uint32_t)sizeMB) + ",\"sdUsedMB\":" + String((uint32_t)usedMB) + ",";
  j += "\"voice\":" + String(srReady?"true":"false") + ",";
  j += "\"ssid\":\"" + apSsid + "\",\"ip\":\"" + apIp + "\"}";
  return j;
}

// ---- TFT ----
void tftLine(int y, const String& s, uint16_t c){ tft.fillRect(0,y,tft.width(),10,ST77XX_BLACK); tft.setTextColor(c,ST77XX_BLACK); tft.setCursor(2,y); tft.print(s); }
String fit(const String& s, int max){ return s.length() > max ? s.substring(0, max-1) + "~" : s; }

int drawTodo(int idx, const String& text, int y, uint16_t c, bool allowWrap){
  const int W = 26;
  String prefix = String(idx) + ".";
  String full = prefix + text;
  if(full.length() <= W){ tftLine(y, full, c); return 1; }
  if(!allowWrap){ tftLine(y, fit(full, W), c); return 1; }
  int cut = full.lastIndexOf(' ', W);
  if(cut < (int)prefix.length()) cut = W;
  tftLine(y, full.substring(0, cut), c);
  String rest = full.substring(cut); rest.trim();
  tftLine(y + 12, "  " + fit(rest, W - 2), c);
  return 2;
}

void drawStatus(){
  tft.fillScreen(ST77XX_BLACK); tft.setTextSize(1);
  bool appFresh = appLinked && (millis() - lastAppMs < 30000);
  tftLine(2, "VAANI", ST77XX_WHITE);
  tft.setCursor(48,2); tft.setTextColor(appFresh?ST77XX_GREEN:ST77XX_YELLOW,ST77XX_BLACK);
  tft.print(appFresh ? "APP LINKED" : (bleConnected?"CONNECTING":"WAITING APP"));

  // Big voice-state banner so the user knows exactly what's happening at a glance.
  if(srReady){
    const char* vt; uint16_t vc;
    switch(voiceUi){
      case VU_WAKE:  vt="> SPEAK NOW"; vc=ST77XX_GREEN;  break;
      case VU_REC:   vt="* RECORDING"; vc=ST77XX_RED;    break;
      case VU_SAVED: vt="SAVED";       vc=ST77XX_GREEN;  break;
      default:       vt="Listening..."; vc=ST77XX_CYAN;  break;
    }
    tft.setTextSize(2); tft.fillRect(0,14,tft.width(),18,ST77XX_BLACK);
    tft.setTextColor(vc,ST77XX_BLACK); tft.setCursor(2,14); tft.print(vt);
    tft.setTextSize(1);
  } else {
    tftLine(14, "voice off", ST77XX_YELLOW);
  }

  uint16_t pc = pendingNotes>0 ? ST77XX_ORANGE : ST77XX_GREEN;
  tftLine(36, String("Notes to sync: ") + String(pendingNotes), pc);

  tftLine(50, "TODAY", ST77XX_CYAN);
  if(todoCount == 0){
    tftLine(62, appFresh ? " (all clear)" : " --", ST77XX_WHITE);
  } else {
    int y = 62;
    for(int i=0; i<todoCount && i<5; i++){
      if(y + 12 > tft.height()) break;
      y += drawTodo(i+1, todos[i], y, ST77XX_WHITE, i < 2) * 12;
    }
  }
}
// Redraw the TFT from the loop core under the SPI mutex (safe from any caller).
void requestRedraw(){ dispDirty = true; }

// ---- BLE notify helpers (opcode-framed) ----
void notifyFrame(uint8_t op, const uint8_t* payload, size_t n){
  uint8_t buf[520];
  if(n > sizeof(buf)-1) n = sizeof(buf)-1;
  buf[0] = op;
  if(n) memcpy(buf+1, payload, n);
  chData->setValue(buf, n+1); chData->notify(); delay(20);
}
void notifyStr(uint8_t op, const String& s){ notifyFrame(op, (const uint8_t*)s.c_str(), s.length()); }
void notifyEnd(){ uint8_t op = OP_END; chData->setValue(&op, 1); chData->notify(); delay(20); }
void notifyErr(const String& msg){ notifyStr(OP_ERR, msg); notifyEnd(); }
void notifyStatus(const String& s){ chStat->setValue((uint8_t*)s.c_str(), s.length()); chStat->notify(); }
void notifyChunked(const String& payload){
  const size_t chunk = 180; size_t i=0;
  while(i < payload.length()){
    size_t n = min(chunk, payload.length()-i);
    notifyFrame(OP_DATA, (const uint8_t*)payload.c_str()+i, n);
    i += n;
  }
}

// Count files under /vaani (excluding dirs) so "pending" is real on boot / after local records.
int countVaaniFiles(){
  if(!sdOk) return 0;
  xSemaphoreTake(spiMutex, portMAX_DELAY);
  int n = 0;
  File d = SD.open("/vaani");
  if(d && d.isDirectory()){ for(File f=d.openNextFile(); f; f=d.openNextFile()){ if(!f.isDirectory()) n++; f.close(); } }
  if(d) d.close();
  xSemaphoreGive(spiMutex);
  return n;
}

void bleList(const String& dir){
  if(!sdOk){ notifyErr("no sd"); return; }
  xSemaphoreTake(spiMutex, portMAX_DELAY);
  File d = SD.open(dir.length()?dir:"/");
  if(!d || !d.isDirectory()){ if(d) d.close(); xSemaphoreGive(spiMutex); notifyErr("not dir"); return; }
  String buf;
  for(File f=d.openNextFile(); f; f=d.openNextFile()){
    buf += String(f.name()) + "\t" + String((uint32_t)f.size()) + "\t" + (f.isDirectory()?"D":"F") + "\n";
    f.close();
  }
  d.close();
  xSemaphoreGive(spiMutex);
  notifyChunked(buf);
  notifyEnd();
}

void bleReadBlock(const String& arg){
  int s1 = arg.lastIndexOf(' ');
  int s0 = arg.lastIndexOf(' ', s1-1);
  if(s0<0||s1<0){ notifyErr("bad RN args"); return; }
  String path = arg.substring(0, s0);
  long off = arg.substring(s0+1, s1).toInt();
  long len = arg.substring(s1+1).toInt();
  if(len < 0 || len > 4096) len = 4096;
  if(!sdOk){ notifyErr("no sd"); return; }
  xSemaphoreTake(spiMutex, portMAX_DELAY);
  if(!SD.exists(path)){ xSemaphoreGive(spiMutex); notifyErr("not found"); return; }
  File f = SD.open(path, FILE_READ);
  if(!f){ xSemaphoreGive(spiMutex); notifyErr("open failed"); return; }
  notifyStr(OP_SZ, String((uint32_t)f.size()));
  f.seek(off);
  uint8_t buf[240];
  long remaining = len;
  while(remaining>0 && f.available()){
    int want = remaining > (long)sizeof(buf) ? sizeof(buf) : remaining;
    int n = f.read(buf, want);
    if(n<=0) break;
    notifyFrame(OP_DATA, buf, n);
    remaining -= n;
  }
  f.close();
  xSemaphoreGive(spiMutex);
  notifyEnd();
}

class CmdCb : public BLECharacteristicCallbacks {
  void onWrite(BLECharacteristic* c) override {
    uint8_t* data = c->getData();
    size_t rlen = c->getLength();
    if(rlen >= 2 && data[0]=='D' && data[1]==':'){
      if(wrOpen){ xSemaphoreTake(spiMutex, portMAX_DELAY); wrSession.write(data+2, rlen-2); xSemaphoreGive(spiMutex); }
      return;
    }
    if(rlen == 4 && memcmp(data, "WEND", 4)==0){
      if(wrOpen){
        xSemaphoreTake(spiMutex, portMAX_DELAY);
        size_t n = wrSession.size(); wrSession.close(); wrOpen=false;
        xSemaphoreGive(spiMutex);
        notifyStr(OP_DATA, "OK " + String((uint32_t)n)); notifyEnd();
      } else notifyErr("no write session");
      return;
    }
    String v; for(size_t i=0;i<rlen;i++) v += (char)data[i];
    int sp = v.indexOf(' ');
    String cmd = sp<0? v : v.substring(0,sp);
    String arg = sp<0? ""  : v.substring(sp+1);
    if(cmd=="INFO"){ notifyChunked(infoJson()); notifyEnd(); }
    else if(cmd=="CRED"){
      String j = "{\"ssid\":\"" + apSsid + "\",\"psk\":\"" + apPass + "\",\"token\":\"" + restToken + "\"}";
      notifyChunked(j); notifyEnd();
    }
    else if(cmd=="LIST"){ bleList(arg); }
    else if(cmd=="RN"){ bleReadBlock(arg); }
    else if(cmd=="WRITE"){
      if(!sdOk){ notifyErr("no sd"); return; }
      if(wrOpen){ notifyErr("write busy"); return; }
      xSemaphoreTake(spiMutex, portMAX_DELAY);
      wrSession=SD.open(arg, FILE_WRITE); wrOpen=(bool)wrSession;
      xSemaphoreGive(spiMutex);
      if(wrOpen){ notifyStr(OP_DATA, "READY"); notifyEnd(); } else notifyErr("open failed");
    }
    else if(cmd=="WIFI"){
      // "WIFI ON" brings the SoftAP up for a fast pull; "WIFI OFF" frees heap back to ESP-SR.
      if(arg.indexOf("ON")>=0){ wifiOn(); notifyStr(OP_DATA, "{\"ssid\":\""+apSsid+"\",\"ip\":\""+apIp+"\"}"); }
      else { wifiOff(); notifyStr(OP_DATA, "OFF"); }
      notifyEnd();
    }
    else if(cmd=="DEL"){
      xSemaphoreTake(spiMutex, portMAX_DELAY);
      bool ok=SD.remove(arg);
      xSemaphoreGive(spiMutex);
      // Keep the local pending count honest when the app deletes/consumes a note.
      if(ok){ pendingNotes = countVaaniFiles(); requestRedraw(); }
      notifyStr(OP_DATA, ok?"OK":"ERR"); notifyEnd();
    }
    else if(cmd=="DISP"){
      int t0 = arg.indexOf('\t');
      String head = t0<0 ? arg : arg.substring(0, t0);
      int hs = head.indexOf(' ');
      appLinked    = (hs>0 ? head.substring(0,hs) : head).toInt() != 0;
      pendingNotes = (hs>0 ? head.substring(hs+1) : String("0")).toInt();
      todoCount = 0;
      int pos = t0;
      while(pos >= 0 && todoCount < 5){
        int next = arg.indexOf('\t', pos+1);
        String item = next<0 ? arg.substring(pos+1) : arg.substring(pos+1, next);
        item.trim();
        if(item.length()) todos[todoCount++] = item;
        pos = next;
      }
      lastAppMs = millis();
      notifyStr(OP_DATA, "OK"); notifyEnd();
      dispDirty = true;
    }
    else { notifyErr("unknown cmd"); }
  }
};
class SrvCb : public BLEServerCallbacks {
  void onConnect(BLEServer* s) override { bleConnected=true; }
  void onDisconnect(BLEServer* s) override {
    bleConnected=false;
    if(wrOpen){ xSemaphoreTake(spiMutex, portMAX_DELAY); wrSession.close(); wrOpen=false; xSemaphoreGive(spiMutex); }
    s->getAdvertising()->start();
  }
};
class SecCb : public BLESecurityCallbacks {
  uint32_t onPassKeyRequest() override { return 0; }
  void onPassKeyNotify(uint32_t pass) override {}
  bool onConfirmPIN(uint32_t pass) override { return true; }
  bool onSecurityRequest() override { return true; }
  bool onAuthorizationRequest(uint16_t, uint16_t, bool) override { return true; }
};

// ---- HTTP (WiFi transport) ----
void sendJson(int code,const String&b){ server.sendHeader("Access-Control-Allow-Origin","*"); server.send(code,"application/json",b); }
bool authed(){
  String want = "Bearer " + restToken;
  if(server.hasHeader("Authorization") && server.header("Authorization") == want) return true;
  if(server.hasArg("token") && server.arg("token") == restToken) return true;
  reqCount++; sendJson(401, "{\"error\":\"unauthorized\"}");
  return false;
}
void hInfo(){ if(!authed()) return; reqCount++; sendJson(200, infoJson()); }
void hFiles(){ if(!authed()) return; reqCount++; if(!sdOk){sendJson(503,"{\"error\":\"no sd\"}");return;} String p=server.hasArg("path")?server.arg("path"):"/"; xSemaphoreTake(spiMutex, portMAX_DELAY); File d=SD.open(p); if(!d||!d.isDirectory()){ if(d) d.close(); xSemaphoreGive(spiMutex); sendJson(404,"{\"error\":\"not dir\"}");return;} String j="{\"path\":\""+p+"\",\"files\":["; bool fst=true; for(File f=d.openNextFile();f;f=d.openNextFile()){ if(!fst)j+=","; fst=false; j+="{\"name\":\""+String(f.name())+"\",\"size\":"+String((uint32_t)f.size())+",\"dir\":"+(f.isDirectory()?"true":"false")+"}"; f.close(); } d.close(); xSemaphoreGive(spiMutex); j+="]}"; sendJson(200,j); }
void hRead(){ if(!authed()) return; reqCount++; if(!sdOk){sendJson(503,"{\"error\":\"no sd\"}");return;} if(!server.hasArg("path")){sendJson(400,"{\"error\":\"path\"}");return;} String p=server.arg("path"); xSemaphoreTake(spiMutex, portMAX_DELAY); if(!SD.exists(p)){ xSemaphoreGive(spiMutex); sendJson(404,"{\"error\":\"not found\"}");return;} File f=SD.open(p,FILE_READ); if(!f){ xSemaphoreGive(spiMutex); sendJson(500,"{\"error\":\"open\"}");return;} server.sendHeader("Access-Control-Allow-Origin","*"); server.streamFile(f,"application/octet-stream"); f.close(); xSemaphoreGive(spiMutex); }
void hDel(){ if(!authed()) return; reqCount++; if(!sdOk){sendJson(503,"{\"error\":\"no sd\"}");return;} if(!server.hasArg("path")){sendJson(400,"{\"error\":\"path\"}");return;} String p=server.arg("path"); xSemaphoreTake(spiMutex, portMAX_DELAY); bool ok=SD.remove(p); xSemaphoreGive(spiMutex); if(ok){ pendingNotes=countVaaniFiles(); requestRedraw(); } sendJson(ok?200:404, ok?"{\"ok\":true}":"{\"error\":\"del\"}"); }
void hRoot(){ reqCount++; server.send(200,"text/plain","Vaani Link OK"); }
File uploadFile;
bool uploadAuthed = false;
void hUploadData(){
  HTTPUpload& up = server.upload();
  if(up.status == UPLOAD_FILE_START){
    String want = "Bearer " + restToken;
    uploadAuthed = (server.hasHeader("Authorization") && server.header("Authorization") == want) ||
                   (server.hasArg("token") && server.arg("token") == restToken);
    if(!uploadAuthed) return;
    String p = server.hasArg("path") ? server.arg("path") : ("/" + up.filename);
    if(sdOk){ xSemaphoreTake(spiMutex, portMAX_DELAY); if(SD.exists(p)) SD.remove(p); uploadFile = SD.open(p, FILE_WRITE); xSemaphoreGive(spiMutex); }
  } else if(up.status == UPLOAD_FILE_WRITE){
    if(uploadAuthed && uploadFile){ xSemaphoreTake(spiMutex, portMAX_DELAY); uploadFile.write(up.buf, up.currentSize); xSemaphoreGive(spiMutex); }
  } else if(up.status == UPLOAD_FILE_END){
    if(uploadFile){ xSemaphoreTake(spiMutex, portMAX_DELAY); uploadFile.close(); xSemaphoreGive(spiMutex); }
  }
}
void hUploadDone(){ reqCount++; if(!uploadAuthed){ sendJson(401,"{\"error\":\"unauthorized\"}"); return; } sendJson(200, "{\"ok\":true}"); }

// Bring the WiFi SoftAP up on demand (app asks via BLE "WIFI ON" when it wants the faster pull).
// Kept off by default so ESP-SR's AFE has the internal heap it needs to detect the wake word.
bool wifiUp = false;
void wifiOn(){
  if(wifiUp) return;
  WiFi.mode(WIFI_AP);
  WiFi.softAP(apSsid.c_str(), apPass.c_str()); delay(300);
  apIp = WiFi.softAPIP().toString();
  server.begin();
  wifiUp = true;
  Serial.printf("SoftAP ON '%s' IP %s heap=%u\n", apSsid.c_str(), apIp.c_str(), ESP.getFreeHeap());
}
void wifiOff(){
  if(!wifiUp) return;
  server.stop();
  WiFi.softAPdisconnect(true); WiFi.mode(WIFI_OFF);
  wifiUp = false;
  Serial.printf("SoftAP OFF heap=%u\n", ESP.getFreeHeap());
}

// ---- WAV note recording (wake-triggered; SR paused; silence auto-stop) ----
void writeLE32(File& f, uint32_t v){ f.write((uint8_t)v); f.write((uint8_t)(v>>8)); f.write((uint8_t)(v>>16)); f.write((uint8_t)(v>>24)); }
void writeLE16(File& f, uint16_t v){ f.write((uint8_t)v); f.write((uint8_t)(v>>8)); }
void writeWavHeader(File& f, uint32_t dataLen){
  f.write((const uint8_t*)"RIFF",4); writeLE32(f,36+dataLen); f.write((const uint8_t*)"WAVE",4);
  f.write((const uint8_t*)"fmt ",4); writeLE32(f,16); writeLE16(f,1); writeLE16(f,1);
  writeLE32(f,SAMPLE_RATE); writeLE32(f,SAMPLE_RATE*2); writeLE16(f,2); writeLE16(f,16);
  f.write((const uint8_t*)"data",4); writeLE32(f,dataLen);
}

// Records a note after the wake word: captures until ~SILENCE_MS of continuous quiet (after a
// REC_MIN_MS floor) or REC_MAX_MS, applies gain, writes a real WAV, bumps pendingNotes.
void recordNote(){
  if(!sdOk || !srReady) return;
  recording = true; voiceUi = VU_REC;
  xSemaphoreTake(spiMutex, portMAX_DELAY); drawStatus(); xSemaphoreGive(spiMutex);   // show RECORDING now
  sr_pause();   // release the mic from ESP-SR so we can read it ourselves

  char path[40]; snprintf(path,sizeof(path),"/vaani/note_%lu.wav",(unsigned long)millis());
  xSemaphoreTake(spiMutex, portMAX_DELAY);
  File f = SD.open(path, FILE_WRITE);
  if(!f){ xSemaphoreGive(spiMutex); sr_resume(); recording=false; requestRedraw(); Serial.println("rec: SD open fail"); return; }
  writeWavHeader(f, 0);
  xSemaphoreGive(spiMutex);

  static int16_t buf[512];
  uint32_t totalSamples = 0;
  uint32_t startMs = millis(), silenceStart = 0;
  while(true){
    size_t got = i2s.readBytes((char*)buf, sizeof(buf));
    size_t n = got / sizeof(int16_t);
    uint64_t sq = 0;
    for(size_t i=0;i<n;i++){
      int32_t v = (int32_t)buf[i] << REC_GAIN_SHIFT;
      if(v>32767) v=32767; else if(v<-32768) v=-32768;
      buf[i] = (int16_t)v;
      sq += (uint64_t)((int32_t)v * v);
    }
    int rms = n ? (int)sqrt((double)(sq/n)) : 0;
    xSemaphoreTake(spiMutex, portMAX_DELAY);
    f.write((uint8_t*)buf, n*sizeof(int16_t));
    xSemaphoreGive(spiMutex);
    totalSamples += n;

    uint32_t elapsed = millis() - startMs;
    // Silence tracking only counts after the minimum length, so it won't cut off an early pause.
    if(elapsed >= REC_MIN_MS){
      if(rms < SILENCE_RMS){ if(silenceStart==0) silenceStart = millis(); }
      else silenceStart = 0;
      if(silenceStart && (millis()-silenceStart) >= SILENCE_MS) break;
    }
    if(elapsed >= REC_MAX_MS) break;
  }

  uint32_t dataLen = totalSamples * sizeof(int16_t);
  xSemaphoreTake(spiMutex, portMAX_DELAY);
  f.seek(0); writeWavHeader(f, dataLen); f.close();
  xSemaphoreGive(spiMutex);

  pendingNotes = countVaaniFiles();
  Serial.printf(">> saved %s (%u bytes, %.1fs) pending=%d\n", path, (unsigned)(dataLen+44), dataLen/(float)(SAMPLE_RATE*2), pendingNotes);
  notifyStatus(String("recorded ") + String((uint32_t)(dataLen+44)) + "B");
  sr_resume();
  recording = false; voiceUi = VU_SAVED; voiceUiMs = millis(); requestRedraw();
}

void onSrEvent(sr_event_t event, int command_id, int phrase_id){
  if(event == SR_EVENT_WAKEWORD){ Serial.println(">> WAKE: Hi ESP"); wakeFired = true; voiceUi = VU_WAKE; voiceUiMs = millis(); dispDirty = true; }
}
extern "C" void on_sr_event_trampoline(void* arg, sr_event_t event, int command_id, int phrase_id){
  onSrEvent(event, command_id, phrase_id);
}

void setup(){
  Serial.begin(115200); delay(400);
  Serial.println("\n=== Vaani Link v5 (WiFi + BLE + SD + TFT + voice) ===");
  spiMutex = xSemaphoreCreateMutex();
  loadOrCreateCreds();

  pinMode(TFT_BL,OUTPUT); digitalWrite(TFT_BL,HIGH);
  pinMode(TFT_RST,OUTPUT);
  digitalWrite(TFT_RST,HIGH); delay(50); digitalWrite(TFT_RST,LOW); delay(200); digitalWrite(TFT_RST,HIGH); delay(300);
  spiBus.begin(SCLK,MISO_PIN,MOSI_PIN,-1);
  tft.initR(INITR_BLACKTAB); tft.setSPISpeed(20000000); tft.setRotation(1);
  tft.fillScreen(ST77XX_BLACK); tft.setTextSize(1); tftLine(2,"VAANI booting...",ST77XX_WHITE);

  sdOk = SD.begin(SD_CS, spiBus, 4000000);
  Serial.printf("SD: %s\n", sdOk?"OK":"FAIL");
  if(sdOk){
    if(!SD.exists("/vaani")) SD.mkdir("/vaani");
    if(SD.exists("/recordings")){
      File d = SD.open("/recordings");
      if(d && d.isDirectory()){
        for(File f=d.openNextFile(); f; f=d.openNextFile()){
          if(!f.isDirectory()){
            String base = String(f.name()); int sl = base.lastIndexOf('/'); if(sl>=0) base = base.substring(sl+1);
            String dst = "/vaani/" + base;
            if(!SD.exists(dst)){ File out = SD.open(dst, FILE_WRITE); if(out){ uint8_t b[512]; int nn; while((nn=f.read(b,sizeof(b)))>0) out.write(b,nn); out.close(); } }
          }
          f.close();
        }
        d.close();
      }
    }
  }

  // WiFi SoftAP — DEFERRED. BLE is the primary transport and works without WiFi; the app brings
  // the AP up on demand via BLE "WIFI ON" only for a faster pull. Deferring keeps heap free.
  WiFi.mode(WIFI_AP);
  WiFi.softAP("Vaani-setup", apPass.c_str()); delay(200);
  uint8_t mac[6]; WiFi.softAPmacAddress(mac);
  char suf[5]; snprintf(suf,sizeof(suf),"%02X%02X",mac[4],mac[5]);
  apSsid = String("Vaani-") + suf;
  WiFi.softAPdisconnect(true); WiFi.mode(WIFI_OFF); delay(100);
  apIp = "192.168.4.1";
  Serial.printf("SoftAP deferred (SSID will be '%s' when enabled)\n", apSsid.c_str());
  server.on("/",HTTP_GET,hRoot); server.on("/api/info",HTTP_GET,hInfo);
  server.on("/api/files",HTTP_GET,hFiles); server.on("/api/file",HTTP_GET,hRead);
  server.on("/api/delete",HTTP_GET,hDel);
  server.on("/api/upload",HTTP_POST,hUploadDone,hUploadData);
  { const char* hdrs[] = {"Authorization"}; server.collectHeaders(hdrs, 1); }
  // server.begin() is called by wifiOn() when the AP is actually enabled.

  // ---- BLE ----
  String bleName = String("Vaani-") + suf;
  BLEDevice::init(bleName.c_str());
  BLEDevice::setMTU(517);
  BLEDevice::setSecurityCallbacks(new SecCb());
  { BLESecurity* sec = new BLESecurity();
    sec->setAuthenticationMode(ESP_LE_AUTH_REQ_SC_BOND);
    sec->setCapability(ESP_IO_CAP_NONE);
    sec->setInitEncryptionKey(ESP_BLE_ENC_KEY_MASK | ESP_BLE_ID_KEY_MASK); }
  BLEServer* srv = BLEDevice::createServer();
  srv->setCallbacks(new SrvCb());
  BLEService* svc = srv->createService(SVC_UUID);
  chInfo = svc->createCharacteristic(INFO_UUID, BLECharacteristic::PROPERTY_READ);
  chInfo->setAccessPermissions(ESP_GATT_PERM_READ_ENCRYPTED);
  chInfo->setValue(infoJson().c_str());
  chCmd  = svc->createCharacteristic(CMD_UUID, BLECharacteristic::PROPERTY_WRITE | BLECharacteristic::PROPERTY_WRITE_NR);
  chCmd->setAccessPermissions(ESP_GATT_PERM_WRITE_ENCRYPTED);
  chCmd->setCallbacks(new CmdCb());
  chData = svc->createCharacteristic(DATA_UUID, BLECharacteristic::PROPERTY_READ | BLECharacteristic::PROPERTY_NOTIFY);
  chData->setAccessPermissions(ESP_GATT_PERM_READ_ENCRYPTED);
  chData->addDescriptor(new BLE2902());
  chStat = svc->createCharacteristic(STAT_UUID, BLECharacteristic::PROPERTY_READ | BLECharacteristic::PROPERTY_NOTIFY);
  chStat->setAccessPermissions(ESP_GATT_PERM_READ_ENCRYPTED);
  chStat->addDescriptor(new BLE2902());
  chStat->setValue("ready");
  svc->start();
  BLEAdvertising* adv = BLEDevice::getAdvertising();
  adv->addServiceUUID(SVC_UUID); adv->setScanResponse(true);
  BLEDevice::startAdvertising();
  Serial.printf("BLE advertising as '%s'\n", bleName.c_str());

  // ---- Voice: INMP441 mic + ESP-SR wake word (init AFTER BLE/WiFi so we see if RAM is tight) ----
  i2s.setTimeout(1000);
  i2s.setPins(MIC_SCK, MIC_WS, -1, MIC_SD);
  micOk = i2s.begin(I2S_MODE_STD, SAMPLE_RATE, I2S_DATA_BIT_WIDTH_32BIT, I2S_SLOT_MODE_MONO, I2S_STD_SLOT_LEFT);
  if(micOk){
    i2s.configureRX(SAMPLE_RATE, I2S_DATA_BIT_WIDTH_32BIT, I2S_SLOT_MODE_MONO, I2S_RX_TRANSFORM_32_TO_16);
    static const sr_cmd_t cmds[] = { {0,"Start recording"}, {1,"Stop recording"}, {2,"Sync notes"} };
    ESP_SR.onEvent(onSrEvent);
    srReady = ESP_SR.begin(i2s, cmds, sizeof(cmds)/sizeof(sr_cmd_t), SR_CHANNELS_MONO, SR_MODE_WAKEWORD, "M");
    Serial.printf("voice: mic=%d sr=%d heap=%u\n", micOk?1:0, srReady?1:0, ESP.getFreeHeap());
  } else {
    Serial.println("voice: I2S mic init FAILED (app-link still works)");
  }

  pendingNotes = countVaaniFiles();
  drawStatus();
  Serial.printf("ready. pending=%d voice=%d\n", pendingNotes, srReady?1:0);
}

void loop(){
  if(wifiUp) server.handleClient();

  // Wake word detected -> show "SPEAK NOW" instantly, then record (blocking a few seconds).
  if(wakeFired){
    wakeFired = false;
    xSemaphoreTake(spiMutex, portMAX_DELAY); drawStatus(); xSemaphoreGive(spiMutex);  // show SPEAK NOW
    recordNote();
  }
  // Auto-revert the transient SAVED banner back to Listening after ~2s.
  if(voiceUi == VU_SAVED && millis()-voiceUiMs > 2000){ voiceUi = VU_LISTEN; requestRedraw(); }

  if(dispDirty){
    dispDirty = false;
    xSemaphoreTake(spiMutex, portMAX_DELAY);
    drawStatus();
    xSemaphoreGive(spiMutex);
  }
  static uint32_t last=0;
  if(millis()-last>3000){ last=millis();
    xSemaphoreTake(spiMutex, portMAX_DELAY);
    if(chInfo) chInfo->setValue(infoJson().c_str());
    if(!recording) drawStatus();
    xSemaphoreGive(spiMutex);
    Serial.printf("alive heap=%u wifi=%d ble=%d voice=%d rec=%d reqs=%u pend=%d\n",
                  ESP.getFreeHeap(), WiFi.softAPgetStationNum(), bleConnected?1:0, srReady?1:0, recording?1:0, reqCount, pendingNotes);
  }
  delay(20);   // yield the core so ESP-SR's audio/AFE task gets CPU (busy-loop starves WakeNet)
}
