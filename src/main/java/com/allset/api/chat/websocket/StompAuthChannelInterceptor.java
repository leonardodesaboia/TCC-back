package com.allset.api.chat.websocket;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Intercepta o frame STOMP CONNECT e autentica via JWT Bearer.
 * Token é enviado no header {@code Authorization: Bearer <token>}.
 * Não aceita token por query string — fica em logs de proxy/balancer.
 */
@Component
@RequiredArgsConstructor
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private final JwtDecoder jwtDecoder;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            String bearer = accessor.getFirstNativeHeader("Authorization");
            if (bearer == null || !bearer.startsWith("Bearer ")) {
                throw new MessageDeliveryException("Authorization header ausente no CONNECT");
            }
            try {
                Jwt jwt = jwtDecoder.decode(bearer.substring(7));
                UUID userId = UUID.fromString(jwt.getSubject());
                String role = jwt.getClaimAsString("role");
                if (role == null || role.isBlank()) {
                    throw new MessageDeliveryException("Token sem claim 'role' obrigatório");
                }
                var auth = new UsernamePasswordAuthenticationToken(
                        userId, null, List.of(new SimpleGrantedAuthority(role))
                );
                accessor.setUser(auth);
            } catch (MessageDeliveryException e) {
                throw e;
            } catch (IllegalArgumentException e) {
                // UUID.fromString falhou — sub inválido
                throw new MessageDeliveryException("Token com subject inválido");
            } catch (JwtException e) {
                throw new MessageDeliveryException("Token inválido ou expirado");
            }
        }
        return message;
    }
}
