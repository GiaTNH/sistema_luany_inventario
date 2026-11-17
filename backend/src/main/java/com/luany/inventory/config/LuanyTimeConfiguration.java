package com.luany.inventory.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
public class LuanyTimeConfiguration {

    public static final ZoneId ZONA_OFICIAL = ZoneId.of("America/Lima");

    @Bean
    public Clock luanyClock() {
        return Clock.system(ZONA_OFICIAL);
    }
}
