package com.allset.api.config;

import com.allset.api.chat.websocket.StompAuthChannelInterceptor;
import com.allset.api.chat.websocket.StompSubscriptionInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import com.allset.api.config.AppProperties;

/**
 * Configuração do broker STOMP em memória (MVP de instância única).
 * Para deploy multi-instância, substituir {@code enableSimpleBroker} por
 * {@code enableStompBrokerRelay} (RabbitMQ) sem mudança no código de domínio.
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final StompAuthChannelInterceptor authInterceptor;
    private final StompSubscriptionInterceptor subscriptionInterceptor;
    private final AppProperties appProperties;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // MVP: broker simples em memória.
        config.enableSimpleBroker("/topic", "/queue");
        config.setApplicationDestinationPrefixes("/app");
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Em produção, FRONTEND_URL deve ser o domínio exato — nunca manter "*" em prod.
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(appProperties.frontendUrl())
                .withSockJS();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        // Ordem importa: auth antes de subscription
        registration.interceptors(authInterceptor, subscriptionInterceptor);
    }
}
