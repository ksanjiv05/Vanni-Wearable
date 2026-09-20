// mic_test — P1 INMP441 I2S bring-up test (standalone; does NOT touch vaani_link firmware).
// Reads mono 16-bit audio from the INMP441 on I2S0 and prints per-window RMS + peak so we can
// confirm the mic + wiring work before adding any voice-recognition ML.
//
// Wiring (INMP441 -> ESP32-S3):
//   VDD -> 3V3 (NOT 5V)   GND -> GND   L/R -> GND (left channel)
//   SCK -> GPIO4 (BCLK)   WS  -> GPIO5 (WS)   SD -> GPIO6 (data in)
//
// Uses the ESP-IDF I2S std driver via the Arduino-ESP32 3.x wrapper.
#include <driver/i2s_std.h>

#define I2S_SCK   4
#define I2S_WS    5
#define I2S_SD    6
#define SAMPLE_RATE 16000

static i2s_chan_handle_t rx = nullptr;

void setup() {
  Serial.begin(115200);
  delay(500);
  Serial.println("\n=== INMP441 mic test (P1) ===");

  i2s_chan_config_t chan_cfg = I2S_CHANNEL_DEFAULT_CONFIG(I2S_NUM_0, I2S_ROLE_MASTER);
  if (i2s_new_channel(&chan_cfg, nullptr, &rx) != ESP_OK) {
    Serial.println("i2s_new_channel FAILED");
    return;
  }

  i2s_std_config_t std_cfg = {
    .clk_cfg  = I2S_STD_CLK_DEFAULT_CONFIG(SAMPLE_RATE),
    .slot_cfg = I2S_STD_PHILIPS_SLOT_DEFAULT_CONFIG(I2S_DATA_BIT_WIDTH_32BIT, I2S_SLOT_MODE_MONO),
    .gpio_cfg = {
      .mclk = I2S_GPIO_UNUSED,
      .bclk = (gpio_num_t)I2S_SCK,
      .ws   = (gpio_num_t)I2S_WS,
      .dout = I2S_GPIO_UNUSED,
      .din  = (gpio_num_t)I2S_SD,
      .invert_flags = { .mclk_inv = false, .bclk_inv = false, .ws_inv = false },
    },
  };
  // INMP441 with L/R tied to GND drives the LEFT slot.
  std_cfg.slot_cfg.slot_mask = I2S_STD_SLOT_LEFT;

  if (i2s_channel_init_std_mode(rx, &std_cfg) != ESP_OK) { Serial.println("init_std FAILED"); return; }
  if (i2s_channel_enable(rx) != ESP_OK) { Serial.println("enable FAILED"); return; }
  Serial.println("I2S RX ready. Talk/clap near the mic — RMS & peak should jump.");
}

void loop() {
  static int32_t buf[512];
  size_t got = 0;
  if (i2s_channel_read(rx, buf, sizeof(buf), &got, 200 / portTICK_PERIOD_MS) != ESP_OK) {
    Serial.println("read err");
    delay(200);
    return;
  }
  int n = got / sizeof(int32_t);
  if (n == 0) { Serial.println("no samples"); delay(200); return; }

  // INMP441 gives 24-bit data left-justified in a 32-bit slot -> shift down to 16-bit.
  double sumsq = 0; int32_t peak = 0;
  for (int i = 0; i < n; i++) {
    int32_t s = buf[i] >> 14;       // scale into ~16-bit range
    sumsq += (double)s * s;
    int32_t a = s < 0 ? -s : s;
    if (a > peak) peak = a;
  }
  int rms = (int)sqrt(sumsq / n);

  // Simple ASCII VU meter so it's obvious the mic responds to sound.
  int bars = rms / 200; if (bars > 40) bars = 40;
  char meter[41]; for (int i = 0; i < 40; i++) meter[i] = i < bars ? '#' : '.'; meter[40] = 0;
  Serial.printf("rms=%5d peak=%6d |%s|\n", rms, (int)peak, meter);
  delay(100);
}
