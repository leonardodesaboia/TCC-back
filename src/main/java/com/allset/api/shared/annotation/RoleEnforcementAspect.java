package com.allset.api.shared.annotation;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * Aspecto que intercepta métodos e classes anotados com {@link RequiresAnyRole}
 * e verifica se o usuário autenticado possui pelo menos um dos roles exigidos.
 *
 * <p>Lança {@link AccessDeniedException} (convertida para HTTP 403 pelo Spring Security)
 * se nenhum role do token bater com os roles declarados na anotação.</p>
 */
@Aspect
@Component
public class RoleEnforcementAspect {

    @Before("@annotation(requiresAnyRole) || @within(requiresAnyRole)")
    public void enforce(JoinPoint joinPoint, RequiresAnyRole requiresAnyRole) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new AccessDeniedException("Acesso negado: usuário não autenticado");
        }

        boolean hasRequiredRole = Arrays.stream(requiresAnyRole.value())
            .map(Enum::name)
            .anyMatch(role -> authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals(role)));

        if (!hasRequiredRole) {
            throw new AccessDeniedException("Acesso negado: permissão insuficiente");
        }
    }
}
