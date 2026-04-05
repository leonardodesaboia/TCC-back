package com.allset.api.shared.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Valida CPF pelo algoritmo dos dígitos verificadores (Receita Federal).
 * Aceita CPF com ou sem máscara (ex: "123.456.789-09" ou "12345678909").
 */
public class CpfValidator implements ConstraintValidator<ValidCPF, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) return false;

        String digits = value.replaceAll("[^\\d]", "");

        if (digits.length() != 11) return false;
        if (digits.chars().distinct().count() == 1) return false;

        return validatesFirstDigit(digits) && validatesSecondDigit(digits);
    }

    private boolean validatesFirstDigit(String digits) {
        int sum = 0;
        for (int i = 0; i < 9; i++) {
            sum += Character.getNumericValue(digits.charAt(i)) * (10 - i);
        }
        int remainder = sum % 11;
        int expected = remainder < 2 ? 0 : 11 - remainder;
        return Character.getNumericValue(digits.charAt(9)) == expected;
    }

    private boolean validatesSecondDigit(String digits) {
        int sum = 0;
        for (int i = 0; i < 10; i++) {
            sum += Character.getNumericValue(digits.charAt(i)) * (11 - i);
        }
        int remainder = sum % 11;
        int expected = remainder < 2 ? 0 : 11 - remainder;
        return Character.getNumericValue(digits.charAt(10)) == expected;
    }
}
