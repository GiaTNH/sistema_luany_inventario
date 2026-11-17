package com.luany.inventory.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IntentosAutenticacionServiceTests {

    @Test
    void bloqueoDuraQuinceMinutosDesdeElQuintoIntento() {
        MutableClock clock = new MutableClock(Instant.parse("2026-03-10T12:00:00Z"));
        IntentosAutenticacionService service = new IntentosAutenticacionService(clock);
        for (int intento = 0; intento < 5; intento++) {
            service.registrarLoginFallido("ana@luany.com", "192.0.2.12");
        }

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, assertThrows(ResponseStatusException.class,
                () -> service.verificarLogin("ana@luany.com", "192.0.2.12")).getStatusCode());

        clock.avanzar(Duration.ofMinutes(14).plusSeconds(59));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, assertThrows(ResponseStatusException.class,
                () -> service.verificarLogin("ana@luany.com", "192.0.2.12")).getStatusCode());

        clock.avanzar(Duration.ofSeconds(1));
        assertDoesNotThrow(() -> service.verificarLogin("ana@luany.com", "192.0.2.12"));
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("America/Lima");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return new Clock() {
                @Override
                public ZoneId getZone() {
                    return zone;
                }

                @Override
                public Clock withZone(ZoneId requestedZone) {
                    return this;
                }

                @Override
                public Instant instant() {
                    return MutableClock.this.instant;
                }
            };
        }

        @Override
        public Instant instant() {
            return instant;
        }

        private void avanzar(Duration duracion) {
            instant = instant.plus(duracion);
        }
    }
}
