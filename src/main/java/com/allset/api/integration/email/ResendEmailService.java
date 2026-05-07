package com.allset.api.integration.email;

import com.allset.api.config.AppProperties;
import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.model.CreateEmailOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Implementação de {@link EmailService} usando a API do Resend.
 * Configuração via variáveis de ambiente: RESEND_API_KEY e EMAIL_FROM.
 */
@Slf4j
@Service
public class ResendEmailService implements EmailService {

    private final Resend resend;
    private final String emailFrom;

    public ResendEmailService(AppProperties appProperties) {
        this.resend = new Resend(appProperties.resendApiKey());
        this.emailFrom = appProperties.emailFrom();
    }

    @Override
    public void sendPasswordResetCode(String to, String code, int ttlMinutes) {
        CreateEmailOptions params = CreateEmailOptions.builder()
            .from(emailFrom)
            .to(to)
            .subject("Código de redefinição de senha — All Set")
            .html(buildHtmlBody(code, ttlMinutes))
            .build();

        try {
            resend.emails().send(params);
            log.info("Email de reset de senha enviado para {}", to);
        } catch (ResendException e) {
            log.error("Falha ao enviar e-mail de reset para {}: {}", to, e.getMessage(), e);
            throw new EmailSendException("Falha ao enviar e-mail de redefinição de senha", e);
        }
    }

    private String buildHtmlBody(String code, int ttlMinutes) {
        return """
            <!DOCTYPE html>
            <html lang="pt-BR">
            <head><meta charset="UTF-8"></head>
            <body style="font-family: Arial, sans-serif; color: #333; max-width: 480px; margin: 0 auto; padding: 32px;">
              <h2 style="color: #1a1a1a;">Redefinição de senha</h2>
              <p>Recebemos uma solicitação para redefinir a senha da sua conta no <strong>All Set</strong>.</p>
              <p>Use o código abaixo para continuar:</p>
              <div style="text-align: center; margin: 32px 0;">
                <span style="font-size: 40px; font-weight: bold; letter-spacing: 12px; color: #2563EB;">%s</span>
              </div>
              <p style="color: #666; font-size: 14px;">Este código expira em <strong>%d minutos</strong>.</p>
              <p style="color: #666; font-size: 14px;">Se você não solicitou a redefinição de senha, ignore este e-mail. Sua senha permanece a mesma.</p>
              <hr style="border: none; border-top: 1px solid #e5e7eb; margin: 24px 0;">
              <p style="color: #9ca3af; font-size: 12px;">All Set — Marketplace de Serviços Autônomos</p>
            </body>
            </html>
            """.formatted(code, ttlMinutes);
    }
}
