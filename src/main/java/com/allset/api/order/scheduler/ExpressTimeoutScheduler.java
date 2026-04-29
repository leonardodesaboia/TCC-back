package com.allset.api.order.scheduler;

import com.allset.api.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Processa janelas expiradas do fluxo Express a cada minuto.
 *
 * <p>Responsabilidades delegadas ao service:
 * <ul>
 *   <li>Marcar timeout de profissionais que não responderam em {@code expressProTimeoutMinutes}</li>
 *   <li>Expandir raio ou cancelar pedidos sem nenhuma proposta após o prazo</li>
 *   <li>Cancelar pedidos onde o cliente não escolheu uma proposta no prazo de {@code expressClientWindowMinutes} (30min)</li>
 * </ul>
 *
 * <p>Cada falha individual é tratada internamente no service — uma entrada com erro
 * não interrompe o processamento das demais.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExpressTimeoutScheduler {

    private final OrderService orderService;

    @Scheduled(fixedDelay = 60_000) // a cada 60 segundos
    public void processTimeouts() {
        log.debug("event=express_timeout_check");
        orderService.processExpiredWindows();
    }
}
