package com.allset.api.geocoding.provider;

import com.allset.api.config.AppProperties;
import com.allset.api.geocoding.exception.GeocodingRateLimitException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * A política do Nominatim público é 1 req/s por IP. Durante a auditoria, 20 buscas
 * em 7 segundos bloquearam o IP do container por mais de 25 minutos — e como cada
 * busca dispara até 3 requisições em cascata, o limite estoura com pouco tráfego.
 */
@ExtendWith(MockitoExtension.class)
class NominatimRateLimiterTest {

    @Mock
    private AppProperties appProperties;

    @Test
    void shouldSpaceConsecutiveCalls() {
        NominatimRateLimiter limiter = limiter(120, 5_000);

        long start = System.nanoTime();
        limiter.acquire();
        limiter.acquire();
        limiter.acquire();
        long elapsedMs = (System.nanoTime() - start) / 1_000_000L;

        // Primeira sai na hora; as outras duas esperam o intervalo cada.
        assertThat(elapsedMs).isGreaterThanOrEqualTo(200L);
    }

    @Test
    void firstCallShouldNotWait() {
        NominatimRateLimiter limiter = limiter(1_000, 5_000);

        long start = System.nanoTime();
        limiter.acquire();
        long elapsedMs = (System.nanoTime() - start) / 1_000_000L;

        assertThat(elapsedMs).isLessThan(200L);
    }

    /**
     * Melhor devolver 429 rápido do que segurar a thread do request numa fila longa.
     */
    @Test
    void shouldRejectWhenQueueWouldExceedMaxWait() {
        NominatimRateLimiter limiter = limiter(1_000, 0);

        limiter.acquire();

        assertThatThrownBy(limiter::acquire)
                .isInstanceOf(GeocodingRateLimitException.class);
    }

    private NominatimRateLimiter limiter(int minIntervalMs, int maxWaitMs) {
        when(appProperties.geocodingMinIntervalMs()).thenReturn(minIntervalMs);
        when(appProperties.geocodingMaxWaitMs()).thenReturn(maxWaitMs);
        return new NominatimRateLimiter(appProperties);
    }
}
