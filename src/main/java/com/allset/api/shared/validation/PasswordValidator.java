package com.allset.api.shared.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Implementação de {@link ValidPassword}.
 * Verifica cada regra individualmente para fornecer mensagens de erro precisas.
 */
public class PasswordValidator implements ConstraintValidator<ValidPassword, String> {

    private static final int MIN_LENGTH = 8;
    private static final int MAX_LENGTH = 255;
    private static final String SPECIAL_CHARS = "!@#$%^&*()_+\\-=\\[\\]{}|;':\",./<>?";

    @Override
    public boolean isValid(String password, ConstraintValidatorContext context) {
        if (password == null || password.isBlank()) {
            return true; // @NotBlank é responsável por esta validação
        }

        boolean valid = true;
        context.disableDefaultConstraintViolation();

        if (password.length() < MIN_LENGTH || password.length() > MAX_LENGTH) {
            addViolation(context, "A senha deve ter entre " + MIN_LENGTH + " e " + MAX_LENGTH + " caracteres");
            valid = false;
        }
        if (!password.chars().anyMatch(Character::isUpperCase)) {
            addViolation(context, "A senha deve conter pelo menos uma letra maiúscula");
            valid = false;
        }
        if (!password.chars().anyMatch(Character::isLowerCase)) {
            addViolation(context, "A senha deve conter pelo menos uma letra minúscula");
            valid = false;
        }
        if (!password.chars().anyMatch(Character::isDigit)) {
            addViolation(context, "A senha deve conter pelo menos um número");
            valid = false;
        }
        if (!password.chars().anyMatch(c -> !Character.isLetterOrDigit(c))) {
            addViolation(context, "A senha deve conter pelo menos um caractere especial (!@#$%^&*...)");
            valid = false;
        }

        return valid;
    }

    private void addViolation(ConstraintValidatorContext context, String message) {
        context.buildConstraintViolationWithTemplate(message).addConstraintViolation();
    }
}
