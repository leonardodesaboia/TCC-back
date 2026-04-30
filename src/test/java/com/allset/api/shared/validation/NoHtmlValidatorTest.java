package com.allset.api.shared.validation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NoHtmlValidatorTest {

    private final NoHtmlValidator validator = new NoHtmlValidator();

    @Test
    void shouldAllowPlainText() {
        assertThat(validator.isValid("Servico feito com qualidade 2 < 3", null)).isTrue();
    }

    @Test
    void shouldRejectHtmlAndScriptPayloads() {
        assertThat(validator.isValid("<script>alert(1)</script>", null)).isFalse();
        assertThat(validator.isValid("<img src=x onerror=alert(1)>", null)).isFalse();
        assertThat(validator.isValid("javascript:alert(1)", null)).isFalse();
        assertThat(validator.isValid("&lt;script&gt;alert(1)&lt;/script&gt;", null)).isFalse();
    }
}
