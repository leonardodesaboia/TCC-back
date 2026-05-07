package com.allset.api.shared.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.regex.Pattern;

public class NoHtmlValidator implements ConstraintValidator<NoHtml, String> {

    private static final Pattern DANGEROUS_CONTENT = Pattern.compile(
            "(?i)(<\\s*/?\\s*[a-z][^>]*>|javascript\\s*:|data\\s*:\\s*text/html|on[a-z]+\\s*=|&lt;|&#x?0*3c;)"
    );

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value == null || !DANGEROUS_CONTENT.matcher(value).find();
    }
}
