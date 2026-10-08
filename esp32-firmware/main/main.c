#include "touran_gateway.h"
#include "waveshare_twai_port.h"

#include "esp_check.h"
#include "esp_log.h"

static const char *TAG = "135er_touran";

void app_main(void)
{
    ESP_ERROR_CHECK(waveshare_main_wifi_ap_init());
    ESP_ERROR_CHECK(touran_gateway_start());
    ESP_LOGI(TAG, "ESP32-S3-CAN-2CH-U ready: CAN1=500k CAN2=100k, listen-only, TCP/13569");
}
