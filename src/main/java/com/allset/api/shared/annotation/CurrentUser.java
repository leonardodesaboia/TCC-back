package com.allset.api.shared.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Resolves the authenticated user's UUID from the JWT {@code sub} claim.
 *
 * <p>Use como parâmetro em métodos de controller para obter o {@link java.util.UUID}
 * do usuário autenticado sem acessar o {@code SecurityContext} manualmente:</p>
 *
 * <pre>{@code
 * @GetMapping("/me")
 * public ResponseEntity<UserResponse> me(@CurrentUser UUID currentUserId) { ... }
 * }</pre>
 *
 * <p>Requer que o endpoint esteja protegido (usuário autenticado). Se o contexto de
 * autenticação estiver ausente, o resolver lança {@code IllegalStateException}.</p>
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentUser {
}
