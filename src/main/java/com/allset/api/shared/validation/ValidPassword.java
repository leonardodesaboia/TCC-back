package com.allset.api.shared.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Valida que a senha atende às regras de complexidade da plataforma:
 * <ul>
 *   <li>Mínimo de 8 caracteres</li>
 *   <li>Pelo menos uma letra maiúscula (A-Z)</li>
 *   <li>Pelo menos uma letra minúscula (a-z)</li>
 *   <li>Pelo menos um número (0-9)</li>
 *   <li>Pelo menos um caractere especial (!@#$%^&amp;*...)</li>
 *   <li>Máximo de 255 caracteres</li>
 * </ul>
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = PasswordValidator.class)
public @interface ValidPassword {

    String message() default "A senha deve ter no mínimo 8 caracteres, uma letra maiúscula, uma minúscula, um número e um caractere especial";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
