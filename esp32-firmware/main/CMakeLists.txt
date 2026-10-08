idf_component_register(
    SRCS "main.c" "touran_gateway.c" "waveshare_twai_port.c"
    INCLUDE_DIRS "."
    REQUIRES esp_bsp esp_timer lwip
)
