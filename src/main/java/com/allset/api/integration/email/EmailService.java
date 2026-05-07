package com.allset.api.integration.email;

/**
 * Contrato agnóstico de framework para envio de e-mails transacionais.
 * Implementações concretas podem usar SMTP, SendGrid, SES, etc.
 */
public interface EmailService {

    /**
     * Envia um e-mail com o código de redefinição de senha ao destinatário.
     *
     * @param to   endereço de e-mail do destinatário
     * @param code código de 4 dígitos para validação
     * @param ttlMinutes tempo de validade do código em minutos (usado no corpo do e-mail)
     */
    void sendPasswordResetCode(String to, String code, int ttlMinutes);
}
