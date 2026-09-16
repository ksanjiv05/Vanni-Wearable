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
#include <BLE2902.h>

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
String apSsid, apPass = "vaani12345", apIp;
volatile uint32_t reqCount = 0;
SemaphoreHandle_t spiMutex;   // serialises SD (BLE-callback core) vs TFT (loop core) on the shared bus

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
void drawStatus(){
  tft.fillScreen(ST77XX_BLACK); tft.setTextSize(1);
  tftLine(2,  "VAANI LINK", ST77XX_WHITE);
  tftLine(16, "AP:" + apSsid, ST77XX_CYAN);
  tftLine(28, "IP:" + apIp, ST77XX_GREEN);
  tftLine(40, String("SD:") + (sdOk?"OK":"FAIL"), sdOk?ST77XX_GREEN:ST77XX_RED);
  tftLine(52, String("BLE:") + (bleConnected?"CONNECTED":"advertising"), bleConnected?ST77XX_GREEN:ST77XX_YELLOW);
  tftLine(64, "wifiClients:" + String(WiFi.softAPgetStationNum()), ST77XX_YELLOW);
  tftLine(76, "reqs:" + String(reqCount), ST77XX_WHITE);
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

// ---- HTTP (WiFi transport) ----
void sendJson(int code,const String&b){ server.sendHeader("Access-Control-Allow-Origin","*"); server.send(code,"application/json",b); }
void hInfo(){ reqCount++; sendJson(200, infoJson()); }
void hFiles(){ reqCount++; if(!sdOk){sendJson(503,"{\"error\":\"no sd\"}");return;} String p=server.hasArg("path")?server.arg("path"):"/"; xSemaphoreTake(spiMutex, portMAX_DELAY); File d=SD.open(p); if(!d||!d.isDirectory()){ if(d) d.close(); xSemaphoreGive(spiMutex); sendJson(404,"{\"error\":\"not dir\"}");return;} String j="{\"path\":\""+p+"\",\"files\":["; bool fst=true; for(File f=d.openNextFile();f;f=d.openNextFile()){ if(!fst)j+=","; fst=false; j+="{\"name\":\""+String(f.name())+"\",\"size\":"+String((uint32_t)f.size())+",\"dir\":"+(f.isDirectory()?"true":"false")+"}"; f.close(); } d.close(); xSemaphoreGive(spiMutex); j+="]}"; sendJson(200,j); }
void hRead(){ reqCount++; if(!sdOk){sendJson(503,"{\"error\":\"no sd\"}");return;} if(!server.hasArg("path")){sendJson(400,"{\"error\":\"path\"}");return;} String p=server.arg("path"); xSemaphoreTake(spiMutex, portMAX_DELAY); if(!SD.exists(p)){ xSemaphoreGive(spiMutex); sendJson(404,"{\"error\":\"not found\"}");return;} File f=SD.open(p,FILE_READ); if(!f){ xSemaphoreGive(spiMutex); sendJson(500,"{\"error\":\"open\"}");return;} server.sendHeader("Access-Control-Allow-Origin","*"); server.streamFile(f,"application/octet-stream"); f.close(); xSemaphoreGive(spiMutex); }
void hDel(){ reqCount++; if(!sdOk){sendJson(503,"{\"error\":\"no sd\"}");return;} if(!server.hasArg("path")){sendJson(400,"{\"error\":\"path\"}");return;} String p=server.arg("path"); xSemaphoreTake(spiMutex, portMAX_DELAY); bool ok=SD.remove(p); xSemaphoreGive(spiMutex); sendJson(ok?200:404, ok?"{\"ok\":true}":"{\"error\":\"del\"}"); }
void hRoot(){ reqCount++; server.send(200,"text/plain","Vaani Link OK"); }

// Streaming raw-body upload: POST /api/upload?path=/recordings/x.wav  (writes as bytes arrive; no RAM buffer)
File uploadFile;
void hUploadData(){
  HTTPUpload& up = server.upload();
  if(up.status == UPLOAD_FILE_START){
    String p = server.hasArg("path") ? server.arg("path") : ("/" + up.filename);
    if(sdOk){ xSemaphoreTake(spiMutex, portMAX_DELAY); if(SD.exists(p)) SD.remove(p); uploadFile = SD.open(p, FILE_WRITE); xSemaphoreGive(spiMutex); }
  } else if(up.status == UPLOAD_FILE_WRITE){
    if(uploadFile){ xSemaphoreTake(spiMutex, portMAX_DELAY); uploadFile.write(up.buf, up.currentSize); xSemaphoreGive(spiMutex); }
  } else if(up.status == UPLOAD_FILE_END){
    if(uploadFile){ xSemaphoreTake(spiMutex, portMAX_DELAY); uploadFile.close(); xSemaphoreGive(spiMutex); }
  }
}
void hUploadDone(){ reqCount++; sendJson(200, "{\"ok\":true}"); }

void setup(){
  Serial.begin(115200); delay(400);
  Serial.println("\n=== Vaani Link v3 (WiFi + BLE + SD, hardened) ===");
  spiMutex = xSemaphoreCreateMutex();
  pinMode(TFT_BL,OUTPUT); digitalWrite(TFT_BL,HIGH);
  spiBus.begin(SCLK,MISO_PIN,MOSI_PIN,-1);
  tft.initR(INITR_BLACKTAB); tft.setSPISpeed(20000000); tft.setRotation(1);
  tft.fillScreen(ST77XX_BLACK); tft.setTextSize(1); tftLine(2,"VAANI booting...",ST77XX_WHITE);

  sdOk = SD.begin(SD_CS, spiBus, 4000000);
  Serial.printf("SD: %s\n", sdOk?"OK":"FAIL");
  if(sdOk && !SD.exists("/recordings")) SD.mkdir("/recordings");

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
  server.begin();

  // BLE
  String bleName = String("Vaani-") + suf;
  BLEDevice::init(bleName.c_str());
  BLEDevice::setMTU(517);
  BLEServer* srv = BLEDevice::createServer();
  srv->setCallbacks(new SrvCb());
  BLEService* svc = srv->createService(SVC_UUID);
  chInfo = svc->createCharacteristic(INFO_UUID, BLECharacteristic::PROPERTY_READ);
  chInfo->setValue(infoJson().c_str());
  chCmd  = svc->createCharacteristic(CMD_UUID, BLECharacteristic::PROPERTY_WRITE | BLECharacteristic::PROPERTY_WRITE_NR);
  chCmd->setCallbacks(new CmdCb());
  chData = svc->createCharacteristic(DATA_UUID, BLECharacteristic::PROPERTY_READ | BLECharacteristic::PROPERTY_NOTIFY);
  chData->addDescriptor(new BLE2902());
  chStat = svc->createCharacteristic(STAT_UUID, BLECharacteristic::PROPERTY_READ | BLECharacteristic::PROPERTY_NOTIFY);
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
  static uint32_t last=0;
  if(millis()-last>3000){ last=millis();
    xSemaphoreTake(spiMutex, portMAX_DELAY);
    chInfo->setValue(infoJson().c_str());
    drawStatus();
    xSemaphoreGive(spiMutex);
    Serial.printf("alive heap=%u wifi=%d ble=%d reqs=%u\n", ESP.getFreeHeap(), WiFi.softAPgetStationNum(), bleConnected?1:0, reqCount);
  }
}
