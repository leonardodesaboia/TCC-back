package com.allset.api.shared.annotation;

import com.allset.api.user.domain.UserRole;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Restringe o acesso ao endpoint a usuários com pelo menos um dos roles informados.
 * Lança {@code AccessDeniedException} (HTTP 403) se nenhum role bater.
 *
 * <p>Suporta um ou múltiplos roles:</p>
 * <pre>{@code
 * // Apenas administradores
 * @RequiresAnyRole(UserRole.admin)
 * public ResponseEntity<?> deleteUser(...) { ... }
 *
 * // Administradores OU profissionais
 * @RequiresAnyRole({UserRole.admin, UserRole.professional})
 * public ResponseEntity<?> viewEarnings(...) { ... }
 * }</pre>
 *
 * <p>Pode ser aplicado a métodos ou classes (neste caso, aplica-se a todos os métodos).</p>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresAnyRole {

    /**
     * Um ou mais roles que têm acesso ao recurso.
     */
    UserRole[] value();
}
