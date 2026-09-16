package com.allset.api.geocoding.provider;

import com.allset.api.config.AppProperties;
import com.allset.api.geocoding.exception.GeocodingRateLimitException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.locks.ReentrantLock;

/**
 * Espaça as chamadas ao Nominatim público, que exige no máximo 1 requisição por
 * segundo por IP.
 *
 * <p>Não é um detalhe de etiqueta: durante a auditoria, 20 buscas em 7 segundos
 * bloquearam o IP do container por mais de 25 minutos. E como cada busca dispara
 * até três requisições em cascata, o limite estoura com pouquíssimo tráfego real.
 *
 * <p>O limitador é <b>por instância</b>. Com várias réplicas atrás do mesmo IP de
 * saída seria preciso um contador distribuído (Redis); para o cenário atual, de
 * instância única, o custo extra não se paga.
 *
 * <p>Quem chega quando a fila já passaria do tempo máximo de espera recebe
 * {@link GeocodingRateLimitException} (429) em vez de ficar pendurado — melhor
 * devolver "tente de novo" rápido do que segurar a thread do request.
 */
@Component
public class NominatimRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(NominatimRateLimiter.class);

    private final ReentrantLock lock = new ReentrantLock(true);
    private final long minIntervalNanos;
    private final long maxWaitNanos;

    /** Instante (nanoTime) em que a próxima chamada pode sair. */
    private long nextSlotNanos = System.nanoTime();

    public NominatimRateLimiter(AppProperties appProperties) {
        this.minIntervalNanos = appProperties.geocodingMinIntervalMs() * 1_000_000L;
        this.maxWaitNanos = appProperties.geocodingMaxWaitMs() * 1_000_000L;
    }

    /**
     * Bloqueia até que a chamada possa ser feita dentro da política.
     *
     * @throws GeocodingRateLimitException quando a espera necessária passa do teto configurado
     */
    public void acquire() {
        long waitNanos;

        lock.lock();
        try {
            long now = System.nanoTime();
            long slot = Math.max(now, nextSlotNanos);
            waitNanos = slot - now;

            if (waitNanos > maxWaitNanos) {
                log.warn("Fila do Nominatim acima do teto ({} ms de espera), devolvendo 429",
                    waitNanos / 1_000_000L);
                throw new GeocodingRateLimitException();
            }

            // Reserva o slot antes de dormir para que quem chegar depois se enfileire
            // atrás dele, e não em cima.
            nextSlotNanos = slot + minIntervalNanos;
        } finally {
            lock.unlock();
        }

        if (waitNanos <= 0) {
            return;
        }

        try {
            Thread.sleep(waitNanos / 1_000_000L, (int) (waitNanos % 1_000_000L));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new GeocodingRateLimitException();
        }
    }
}
