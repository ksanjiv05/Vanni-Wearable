// Vaani wearable link firmware — v2: WiFi SoftAP + BLE GATT, both over microSD, TFT status.
// Board: ESP32-S3 N16R8.  SD: CS=14 SCK=12 MOSI=11 MISO=13 VCC=5V.  TFT: CS=10 DC=17 RST=18 SCK=12 MOSI=11 LED=21.
//
// BLE GATT service (primary transport for the phone):
//   Service  6e40fda0-b5a3-f393-e0a9-e50e24dcca9e
//   INFO      ...fda1  READ            -> JSON device+SD info
//   CMD       ...fda2  WRITE           -> text command (see below)
//   DATA      ...fda3  READ | NOTIFY   -> command result / file chunks (notified)
//   STATUS    ...fda4  READ | NOTIFY   -> short status string
//
// Command protocol (write UTF-8 to CMD, result streamed on DATA notify):
//   INFO                      -> one DATA notify: JSON info
//   LIST <dir>                -> DATA notifies: one line per entry "name\t size \t D|F", then "<END>"
//   READ <path>               -> DATA notifies: raw file bytes in MTU-sized chunks, then "<END>"
//   WRITE <path>              -> begins a write session (truncates file); subsequent CMD writes with
//                                prefix "D:" append that payload's bytes; "WEND" closes the file.
//   DEL <path>                -> DATA notify "OK" / "ERR"
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

bool sdOk = false;
// Per-device secrets — generated randomly at first boot, persisted in NVS. NEVER hardcoded and
// NEVER printed to serial/TFT. Handed to the phone only over the encrypted+bonded BLE link (CRED).
Preferences prefs;
String apSsid, apPass, apIp, restToken;
volatile uint32_t reqCount = 0;
SemaphoreHandle_t spiMutex;   // serialises SD (BLE-callback core) vs TFT (loop core) on the shared bus

// Random lowercase-hex string of n chars, sourced from the hardware RNG.
String randHex(size_t n){
  static const char* h = "0123456789abcdef";
  String s; s.reserve(n);
  while(s.length() < n){ uint32_t r = esp_random(); for(int i=0;i<8 && s.length()<n;i++){ s += h[(r>>(i*4))&0xF]; } }
  return s;
}

// Load PSK + REST token from NVS; generate+persist on first boot (or if wiped/too short).
void loadOrCreateCreds(){
  prefs.begin("vaani", false);
  apPass    = prefs.getString("psk", "");
  restToken = prefs.getString("tok", "");
  bool changed = false;
  if(apPass.length() < 12){    apPass    = randHex(16); prefs.putString("psk", apPass); changed = true; }  // WPA2 PSK (>=8)
  if(restToken.length() < 24){ restToken = randHex(32); prefs.putString("tok", restToken); changed = true; }
  prefs.end();
  Serial.printf("creds: %s (psk %d chars, token %d chars) [not shown]\n",
                changed?"generated+stored":"loaded from NVS", apPass.length(), restToken.length());
}

// DATA-characteristic frame opcodes (byte 0 of every notification). Structural, so raw
// binary payload can NEVER be mistaken for a control marker (fixes marker/binary collision).
static const uint8_t OP_DATA = 0x01;  // payload = raw file/text bytes to append
static const uint8_t OP_END  = 0x02;  // end of the current stream/block
static const uint8_t OP_SZ   = 0x03;  // payload = ASCII decimal total file size
static const uint8_t OP_ERR  = 0x04;  // payload = ASCII error message

// ---- BLE ----
#define SVC_UUID  "6e40fda0-b5a3-f393-e0a9-e50e24dcca9e"
#define INFO_UUID "6e40fda1-b5a3-f393-e0a9-e50e24dcca9e"
#define CMD_UUID  "6e40fda2-b5a3-f393-e0a9-e50e24dcca9e"
#define DATA_UUID "6e40fda3-b5a3-f393-e0a9-e50e24dcca9e"
#define STAT_UUID "6e40fda4-b5a3-f393-e0a9-e50e24dcca9e"
BLECharacteristic *chInfo, *chCmd, *chData, *chStat;
volatile bool bleConnected = false;
File wrSession;              // active WRITE session
bool wrOpen = false;
uint32_t bleMtu = 20;        // conservative default payload; grows on MTU negotiate

// ---- Dashboard state pushed by the app (so the wearable is informative without the phone) ----
bool     appLinked   = false;      // app said it is actively connected/foregrounded
int      pendingNotes = 0;         // recordings on SD not yet transferred to the app
String   todos[5];                 // today's todos, first 5 (already prioritised app-side)
int      todoCount   = 0;
uint32_t lastAppMs   = 0;          // millis() of the last DISP push (staleness -> "waiting")
volatile bool dispDirty = false;   // set by BLE-core DISP handler; loop core redraws the TFT

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
  j += "\"ssid\":\"" + apSsid + "\",\"ip\":\"" + apIp + "\"}";
  return j;
}

// ---- TFT ----
void tftLine(int y, const String& s, uint16_t c){ tft.fillRect(0,y,tft.width(),10,ST77XX_BLACK); tft.setTextColor(c,ST77XX_BLACK); tft.setCursor(2,y); tft.print(s); }

// Truncate a string to fit the 160px-wide screen (~26 chars at textSize 1).
String fit(const String& s, int max){ return s.length() > max ? s.substring(0, max-1) + "~" : s; }

// Render a todo starting at row y. [allowWrap] true (first 2 todos) lets a long todo spill onto an
// indented 2nd line; otherwise it's a one-liner truncated with "~". Returns rows used (1 or 2).
int drawTodo(int idx, const String& text, int y, uint16_t c, bool allowWrap){
  const int W = 26;                      // chars that fit on one 160px line at textSize 1
  String prefix = String(idx) + ".";
  String full = prefix + text;
  if(full.length() <= W){ tftLine(y, full, c); return 1; }
  if(!allowWrap){ tftLine(y, fit(full, W), c); return 1; }   // one-liner: truncate
  // Break line 1 on the last space that fits (fall back to a hard cut).
  int cut = full.lastIndexOf(' ', W);
  if(cut < prefix.length()) cut = W;     // no usable space -> hard wrap
  tftLine(y, full.substring(0, cut), c);
  String rest = full.substring(cut);
  rest.trim();
  // Indent line 2 under the text and truncate if still too long.
  tftLine(y + 12, "  " + fit(rest, W - 2), c);
  return 2;
}

void drawStatus(){
  tft.fillScreen(ST77XX_BLACK); tft.setTextSize(1);
  // App-connection banner: the wearable knows if the phone app is actively linked. A push older
  // than 30s (or none yet) means the app isn't currently talking to us -> "waiting for app".
  bool appFresh = appLinked && (millis() - lastAppMs < 30000);
  tftLine(2, "VAANI", ST77XX_WHITE);
  tft.setCursor(48,2); tft.setTextColor(appFresh?ST77XX_GREEN:ST77XX_YELLOW,ST77XX_BLACK);
  tft.print(appFresh ? "APP LINKED" : (bleConnected?"CONNECTING":"WAITING APP"));

  // Pending notes to transfer — the headline number the user wants at a glance.
  uint16_t pc = pendingNotes>0 ? ST77XX_ORANGE : ST77XX_GREEN;
  tftLine(16, String("Notes to sync: ") + String(pendingNotes), pc);

  // Today's todos — each may wrap to 2 lines; render as many as fit (screen is 128px tall).
  tftLine(30, "TODAY", ST77XX_CYAN);
  if(todoCount == 0){
    tftLine(42, appFresh ? " (all clear)" : " --", ST77XX_WHITE);
  } else {
    int y = 42;
    for(int i=0; i<todoCount && i<5; i++){
      if(y + 12 > tft.height()) break;                 // out of vertical space
      y += drawTodo(i+1, todos[i], y, ST77XX_WHITE, i < 2) * 12;   // first 2 may wrap to 2 lines
    }
  }
}

// ---- BLE notify helpers (opcode-framed: byte 0 = opcode, rest = payload) ----
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

// Send a large text payload as OP_DATA frames (used for INFO/LIST), then OP_END.
void notifyChunked(const String& payload){
  const size_t chunk = 180;
  size_t i=0;
  while(i < payload.length()){
    size_t n = min(chunk, payload.length()-i);
    notifyFrame(OP_DATA, (const uint8_t*)payload.c_str()+i, n);
    i += n;
  }
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

// Reliable block read: "RN <path> <offset> <len>" -> OP_SZ(total) + OP_DATA(bytes) + OP_END.
// Opcode framing means audio bytes can never collide with a control marker. App re-requests
// a block whose received size != requested (dropped notification) — no data lost.
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
    // Raw ATT bytes — never route binary through a NUL-terminated string (it truncates at the
    // first 0x00, which corrupts WAV/PCM audio that is full of 0x00 bytes). getData()/getLength()
    // give the true byte buffer regardless of embedded NULs.
    uint8_t* data = c->getData();
    size_t rlen = c->getLength();
    // Write-session data chunk: "D:" + raw bytes (may contain NULs).
    if(rlen >= 2 && data[0]=='D' && data[1]==':'){
      if(wrOpen){
        xSemaphoreTake(spiMutex, portMAX_DELAY);
        wrSession.write(data+2, rlen-2);
        xSemaphoreGive(spiMutex);
      }
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
    // Control commands are pure ASCII.
    String v; for(size_t i=0;i<rlen;i++) v += (char)data[i];
    int sp = v.indexOf(' ');
    String cmd = sp<0? v : v.substring(0,sp);
    String arg = sp<0? ""  : v.substring(sp+1);
    if(cmd=="INFO"){ notifyChunked(infoJson()); notifyEnd(); }
    else if(cmd=="CRED"){
      // Provision the phone with per-device WiFi + REST secrets. Only reachable over the
      // encryption-required CMD characteristic, so the peer is already bonded/encrypted.
      String j = "{\"ssid\":\"" + apSsid + "\",\"psk\":\"" + apPass + "\",\"token\":\"" + restToken + "\"}";
      notifyChunked(j); notifyEnd();
    }
    else if(cmd=="LIST"){ bleList(arg); }
    else if(cmd=="RN"){ bleReadBlock(arg); }
    else if(cmd=="WRITE"){
      if(!sdOk){ notifyErr("no sd"); return; }
      if(wrOpen){ notifyErr("write busy"); return; }   // reject overlapping session (was a handle leak)
      xSemaphoreTake(spiMutex, portMAX_DELAY);
      wrSession=SD.open(arg, FILE_WRITE); wrOpen=(bool)wrSession;
      xSemaphoreGive(spiMutex);
      if(wrOpen){ notifyStr(OP_DATA, "READY"); notifyEnd(); } else notifyErr("open failed");
    }
    else if(cmd=="DEL"){
      xSemaphoreTake(spiMutex, portMAX_DELAY);
      bool ok=SD.remove(arg);
      xSemaphoreGive(spiMutex);
      notifyStr(OP_DATA, ok?"OK":"ERR"); notifyEnd();
    }
    else if(cmd=="DISP"){
      // App pushes dashboard state so the wearable is informative without the phone in hand.
      // Format: "DISP <conn> <pending>\t<todo1>\t<todo2>..."  (tab-delimited; todos already
      // prioritised + trimmed to the first 5 app-side). Redraws the TFT immediately.
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
      // TFT draw must happen on the loop core (shared SPI); flag a redraw instead of drawing here.
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

// SMP/pairing responder. Bluedroid will NOT answer the phone's pairing request (-> SMP_RSP_TIMEOUT,
// bond fails) unless security callbacks are registered — even for Just Works. IO_CAP_NONE means no
// passkey, so we just auto-confirm and accept the security request.
class SecCb : public BLESecurityCallbacks {
  uint32_t onPassKeyRequest() override { return 0; }
  void onPassKeyNotify(uint32_t pass) override {}
  bool onConfirmPIN(uint32_t pass) override { return true; }
  bool onSecurityRequest() override { return true; }
  bool onAuthorizationRequest(uint16_t, uint16_t, bool) override { return true; }
};

// ---- HTTP (WiFi transport) ----
void sendJson(int code,const String&b){ server.sendHeader("Access-Control-Allow-Origin","*"); server.send(code,"application/json",b); }
// Bearer-token gate: every /api/* call must present the per-device REST token (provisioned to the
// phone over encrypted BLE) as `Authorization: Bearer <token>` or `?token=<token>`. Without it the
// AP is useless to a stranger even if they somehow join. Returns true if the request may proceed.
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
void hDel(){ if(!authed()) return; reqCount++; if(!sdOk){sendJson(503,"{\"error\":\"no sd\"}");return;} if(!server.hasArg("path")){sendJson(400,"{\"error\":\"path\"}");return;} String p=server.arg("path"); xSemaphoreTake(spiMutex, portMAX_DELAY); bool ok=SD.remove(p); xSemaphoreGive(spiMutex); sendJson(ok?200:404, ok?"{\"ok\":true}":"{\"error\":\"del\"}"); }
void hRoot(){ reqCount++; server.send(200,"text/plain","Vaani Link OK"); }

// Streaming raw-body upload: POST /api/upload?path=/recordings/x.wav  (writes as bytes arrive; no RAM buffer)
File uploadFile;
bool uploadAuthed = false;
void hUploadData(){
  HTTPUpload& up = server.upload();
  if(up.status == UPLOAD_FILE_START){
    // Auth is checked here (headers are already parsed) so an unauthorized body never touches SD.
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

void setup(){
  Serial.begin(115200); delay(400);
  Serial.println("\n=== Vaani Link v4 (WiFi + BLE + SD, secured) ===");
  spiMutex = xSemaphoreCreateMutex();
  loadOrCreateCreds();                        // per-device PSK + REST token (NVS)
  pinMode(TFT_BL,OUTPUT); digitalWrite(TFT_BL,HIGH);
  spiBus.begin(SCLK,MISO_PIN,MOSI_PIN,-1);
  tft.initR(INITR_BLACKTAB); tft.setSPISpeed(20000000); tft.setRotation(1);
  tft.fillScreen(ST77XX_BLACK); tft.setTextSize(1); tftLine(2,"VAANI booting...",ST77XX_WHITE);

  sdOk = SD.begin(SD_CS, spiBus, 4000000);
  Serial.printf("SD: %s\n", sdOk?"OK":"FAIL");
  // Single Vaani folder — ALL Vaani media/data lives under /vaani. The app browses only this dir,
  // never the whole card. Migrate recordings from the legacy /recordings dir if present.
  if(sdOk){
    if(!SD.exists("/vaani")) SD.mkdir("/vaani");
    if(SD.exists("/recordings")){
      File d = SD.open("/recordings");
      if(d && d.isDirectory()){
        for(File f=d.openNextFile(); f; f=d.openNextFile()){
          if(!f.isDirectory()){
            String base = String(f.name()); int sl = base.lastIndexOf('/'); if(sl>=0) base = base.substring(sl+1);
            String dst = "/vaani/" + base;
            if(!SD.exists(dst)){ File out = SD.open(dst, FILE_WRITE); if(out){ uint8_t b[512]; int n; while((n=f.read(b,sizeof(b)))>0) out.write(b,n); out.close(); } }
          }
          f.close();
        }
        d.close();
      }
    }
  }

  // WiFi SoftAP — start it first, THEN read the SoftAP MAC for a real per-device suffix.
  WiFi.mode(WIFI_AP);
  WiFi.softAP("Vaani-setup", apPass.c_str()); delay(300);
  uint8_t mac[6]; WiFi.softAPmacAddress(mac);
  char suf[5]; snprintf(suf,sizeof(suf),"%02X%02X",mac[4],mac[5]);
  apSsid = String("Vaani-") + suf;
  WiFi.softAP(apSsid.c_str(), apPass.c_str()); delay(300);
  apIp = WiFi.softAPIP().toString();
  Serial.printf("SoftAP '%s' IP %s\n", apSsid.c_str(), apIp.c_str());
  server.on("/",HTTP_GET,hRoot); server.on("/api/info",HTTP_GET,hInfo);
  server.on("/api/files",HTTP_GET,hFiles); server.on("/api/file",HTTP_GET,hRead);
  server.on("/api/delete",HTTP_GET,hDel);
  server.on("/api/upload",HTTP_POST,hUploadDone,hUploadData);
  { const char* hdrs[] = {"Authorization"}; server.collectHeaders(hdrs, 1); }   // needed for Bearer auth
  server.begin();

  // BLE
  String bleName = String("Vaani-") + suf;
  BLEDevice::init(bleName.c_str());
  BLEDevice::setMTU(517);
  // Security: bonding + LE encryption (Just Works — the wearable has no keyboard/display for a
  // passkey). First connection bonds; keys persist in NVS so re-pairing is automatic. This makes
  // the encrypted BLE link the root of trust that hands out the WiFi PSK + REST token (CRED).
  BLEDevice::setSecurityCallbacks(new SecCb());   // MUST be set or Bluedroid ignores the pairing req
  {
    BLESecurity* sec = new BLESecurity();
    sec->setAuthenticationMode(ESP_LE_AUTH_REQ_SC_BOND);   // secure-connections + bonding
    sec->setCapability(ESP_IO_CAP_NONE);                   // Just Works (no I/O for a passkey)
    sec->setInitEncryptionKey(ESP_BLE_ENC_KEY_MASK | ESP_BLE_ID_KEY_MASK);
  }
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
  adv->addServiceUUID(SVC_UUID);
  adv->setScanResponse(true);
  BLEDevice::startAdvertising();
  Serial.printf("BLE advertising as '%s' svc %s\n", bleName.c_str(), SVC_UUID);

  drawStatus();
}

void loop(){
  server.handleClient();
  // Immediate redraw when the app pushes new dashboard data (DISP), on the loop core.
  if(dispDirty){
    dispDirty = false;
    xSemaphoreTake(spiMutex, portMAX_DELAY);
    drawStatus();
    xSemaphoreGive(spiMutex);
  }
  static uint32_t last=0;
  if(millis()-last>3000){ last=millis();
    xSemaphoreTake(spiMutex, portMAX_DELAY);
    chInfo->setValue(infoJson().c_str());
    drawStatus();
    xSemaphoreGive(spiMutex);
    Serial.printf("alive heap=%u wifi=%d ble=%d reqs=%u pend=%d todos=%d\n", ESP.getFreeHeap(), WiFi.softAPgetStationNum(), bleConnected?1:0, reqCount, pendingNotes, todoCount);
  }
}
