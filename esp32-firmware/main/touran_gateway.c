#include "touran_gateway.h"

#include <errno.h>
#include <inttypes.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include <unistd.h>

#include "esp_log.h"
#include "esp_timer.h"
#include "freertos/FreeRTOS.h"
#include "freertos/semphr.h"
#include "freertos/task.h"
#include "lwip/inet.h"
#include "lwip/sockets.h"
#include "lwip/tcp.h"

#define GATEWAY_PORT 13569
#define GATEWAY_FW "0.2.0"

static const char *TAG = "touran_gateway";
static SemaphoreHandle_t s_client_mutex;
static int s_client_fd = -1;
static volatile bool s_capture = true;
static volatile uint64_t s_can1_rx = 0;
static volatile uint64_t s_can2_rx = 0;
static volatile uint64_t s_drops = 0;

static void close_client_locked(void)
{
    if (s_client_fd >= 0) {
        shutdown(s_client_fd, SHUT_RDWR);
        close(s_client_fd);
        s_client_fd = -1;
    }
}

static bool send_line_nonblocking(const char *line)
{
    if (!line || !s_client_mutex) return false;
    if (xSemaphoreTake(s_client_mutex, 0) != pdTRUE) {
        s_drops++;
        return false;
    }
    int fd = s_client_fd;
    if (fd < 0) {
        xSemaphoreGive(s_client_mutex);
        return false;
    }
    size_t len = strlen(line);
    int sent = send(fd, line, len, MSG_DONTWAIT);
    if (sent < 0 && errno != EAGAIN && errno != EWOULDBLOCK) {
        close_client_locked();
    }
    bool ok = sent == (int)len;
    if (!ok) s_drops++;
    xSemaphoreGive(s_client_mutex);
    return ok;
}

static void send_hello(void)
{
    char line[256];
    snprintf(line, sizeof(line),
             "{\"type\":\"hello\",\"proto\":1,\"device\":\"135er-touran-gateway\","
             "\"board\":\"ESP32-S3-CAN-2CH-U\",\"fw\":\"%s\",\"readonly\":true}\n",
             GATEWAY_FW);
    send_line_nonblocking(line);
}

static void send_health(void)
{
    char line[320];
    snprintf(line, sizeof(line),
             "{\"type\":\"health\",\"uptime_ms\":%" PRIu64 ",\"can1_rate\":500,"
             "\"can2_rate\":100,\"readonly\":true,\"can1_rx\":%" PRIu64 ","
             "\"can2_rx\":%" PRIu64 ",\"drops\":%" PRIu64 "}\n",
             (uint64_t)(esp_timer_get_time() / 1000), s_can1_rx, s_can2_rx, s_drops);
    send_line_nonblocking(line);
}

void touran_gateway_can_frame(uint8_t channel, uint32_t can_id,
                              const uint8_t *data, uint8_t len, bool extd)
{
    if (channel == 1) s_can1_rx++;
    else if (channel == 2) s_can2_rx++;
    if (!s_capture || s_client_fd < 0) return;

    char hex[17] = {0};
    size_t pos = 0;
    if (len > 8) len = 8;
    for (uint8_t i = 0; i < len && pos + 2 < sizeof(hex); i++) {
        pos += (size_t)snprintf(hex + pos, sizeof(hex) - pos, "%02X", data[i]);
    }

    char line[256];
    snprintf(line, sizeof(line),
             "{\"type\":\"can\",\"ch\":%u,\"ts_us\":%" PRIi64 ",\"id\":%" PRIu32 ","
             "\"ext\":%s,\"data\":\"%s\"}\n",
             channel, esp_timer_get_time(), can_id, extd ? "true" : "false", hex);
    (void)send_line_nonblocking(line);
}

static void handle_command(const char *line)
{
    if (!line) return;
    if (strstr(line, "\"cmd\":\"hello\"")) {
        send_hello();
    } else if (strstr(line, "\"cmd\":\"health\"")) {
        send_health();
    } else if (strstr(line, "\"cmd\":\"capture\"")) {
        s_capture = strstr(line, "\"enable\":false") == NULL;
    } else if (strstr(line, "\"cmd\":\"diag_request\"") ||
               strstr(line, "\"cmd\":\"mfa_send\"") ||
               strstr(line, "\"cmd\":\"set_mode\"")) {
        send_line_nonblocking("{\"type\":\"error\",\"code\":\"READ_ONLY\",\"message\":\"active CAN transmit disabled\"}\n");
    }
}

static void serve_client(int fd)
{
    xSemaphoreTake(s_client_mutex, portMAX_DELAY);
    close_client_locked();
    s_client_fd = fd;
    xSemaphoreGive(s_client_mutex);

    send_hello();
    send_health();

    char rx[384];
    char line[768];
    size_t used = 0;
    while (1) {
        int n = recv(fd, rx, sizeof(rx), 0);
        if (n <= 0) break;
        for (int i = 0; i < n; i++) {
            char c = rx[i];
            if (c == '\n') {
                line[used] = '\0';
                handle_command(line);
                used = 0;
            } else if (c != '\r') {
                if (used + 1 < sizeof(line)) line[used++] = c;
                else used = 0;
            }
        }
    }

    xSemaphoreTake(s_client_mutex, portMAX_DELAY);
    if (s_client_fd == fd) close_client_locked();
    else close(fd);
    xSemaphoreGive(s_client_mutex);
}

static void gateway_task(void *arg)
{
    (void)arg;
    while (1) {
        int server = socket(AF_INET, SOCK_STREAM, IPPROTO_IP);
        if (server < 0) {
            ESP_LOGE(TAG, "socket failed errno=%d", errno);
            vTaskDelay(pdMS_TO_TICKS(1000));
            continue;
        }
        int yes = 1;
        setsockopt(server, SOL_SOCKET, SO_REUSEADDR, &yes, sizeof(yes));

        struct sockaddr_in addr = {0};
        addr.sin_family = AF_INET;
        addr.sin_port = htons(GATEWAY_PORT);
        addr.sin_addr.s_addr = htonl(INADDR_ANY);
        if (bind(server, (struct sockaddr *)&addr, sizeof(addr)) != 0 || listen(server, 1) != 0) {
            ESP_LOGE(TAG, "bind/listen failed errno=%d", errno);
            close(server);
            vTaskDelay(pdMS_TO_TICKS(1000));
            continue;
        }
        ESP_LOGI(TAG, "135er Touran gateway listening on TCP %d", GATEWAY_PORT);

        while (1) {
            struct sockaddr_in peer = {0};
            socklen_t peer_len = sizeof(peer);
            int fd = accept(server, (struct sockaddr *)&peer, &peer_len);
            if (fd < 0) break;
            int yes2 = 1;
            setsockopt(fd, IPPROTO_TCP, TCP_NODELAY, &yes2, sizeof(yes2));
            ESP_LOGI(TAG, "client connected");
            serve_client(fd);
            ESP_LOGI(TAG, "client disconnected");
        }
        close(server);
        vTaskDelay(pdMS_TO_TICKS(500));
    }
}

esp_err_t touran_gateway_start(void)
{
    if (!s_client_mutex) s_client_mutex = xSemaphoreCreateMutex();
    if (!s_client_mutex) return ESP_ERR_NO_MEM;
    BaseType_t ok = xTaskCreatePinnedToCore(gateway_task, "touran_gateway", 6144, NULL, 5, NULL, 1);
    return ok == pdPASS ? ESP_OK : ESP_ERR_NO_MEM;
}
