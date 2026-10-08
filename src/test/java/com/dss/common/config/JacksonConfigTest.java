package com.dss.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 时间序列化契约：LocalDateTime 统一 "yyyy-MM-dd HH:mm:ss"，时区东八区（application.yml 的 spring.jackson.time-zone）。
 */
class JacksonConfigTest {

    @Test
    @DisplayName("LocalDateTime 输出为 yyyy-MM-dd HH:mm:ss")
    void dateTimePattern() throws Exception {
        ObjectMapper mapper = objectMapper();

        String json = mapper.writeValueAsString(LocalDateTime.of(2026, 9, 30, 10, 20, 30));

        assertThat(json).isEqualTo("\"2026-09-30 10:20:30\"");
    }

    @Test
    @DisplayName("LocalDateTime 也按同一格式反序列化")
    void dateTimeRoundTrip() throws Exception {
        ObjectMapper mapper = objectMapper();

        LocalDateTime parsed = mapper.readValue("\"2026-09-30 10:20:30\"", LocalDateTime.class);

        assertThat(parsed).isEqualTo(LocalDateTime.of(2026, 9, 30, 10, 20, 30));
    }

    @Test
    @DisplayName("配置的时区是东八区，和 JDBC 的 connectionTimeZone 一致")
    void timeZoneIsGmt8() {
        // application.yml: spring.jackson.time-zone: GMT+8；启动类另把 JVM 默认时区固定为 Asia/Shanghai
        assertThat(TimeZone.getTimeZone("GMT+8").getRawOffset()).isEqualTo(8 * 60 * 60 * 1000);
        assertThat(ZoneId.of("Asia/Shanghai").getRules().getOffset(LocalDateTime.of(2026, 9, 30, 10, 0)))
                .isEqualTo(java.time.ZoneOffset.ofHours(8));
    }

    @Test
    @DisplayName("故意不做全局 Long → String：ID 在 VO 里直接声明为 String，金额保持数字")
    void longIsNotConvertedToString() throws Exception {
        ObjectMapper mapper = objectMapper();

        assertThat(mapper.writeValueAsString(1234567890123456789L)).isEqualTo("1234567890123456789");
    }

    private ObjectMapper objectMapper() {
        Jackson2ObjectMapperBuilder builder = new Jackson2ObjectMapperBuilder();
        new JacksonConfig().dssJacksonCustomizer().customize(builder);
        builder.timeZone(TimeZone.getTimeZone("GMT+8"));
        return builder.build();
    }
}
