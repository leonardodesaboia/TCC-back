package com.allset.api.shared.crypto;

import com.allset.api.config.AppProperties;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * Converter JPA transparente para criptografia AES-256/CBC do CPF.
 *
 * Formato armazenado no banco: Base64( IV[16 bytes] || ciphertext )
 * O IV aleatório garante que o mesmo CPF sempre produz ciphertexts distintos.
 *
 * Busca por unicidade deve ser feita via cpf_hash (SHA-256 sem salt),
 * não diretamente pela coluna cpf.
 */
@Converter
@Component
public class CpfConverter implements AttributeConverter<String, String> {

    private static final String ALGORITHM = "AES/CBC/PKCS5Padding";
    private static final int IV_LENGTH = 16;

    private final SecretKeySpec secretKey;

    public CpfConverter(AppProperties appProperties) {
        byte[] keyBytes = hexToBytes(appProperties.cpfEncryptionKey());
        this.secretKey = new SecretKeySpec(keyBytes, "AES");
    }

    @Override
    public String convertToDatabaseColumn(String plainCpf) {
        if (plainCpf == null) return null;
        try {
            byte[] iv = new byte[IV_LENGTH];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new IvParameterSpec(iv));
            byte[] encrypted = cipher.doFinal(plainCpf.getBytes());

            byte[] combined = new byte[IV_LENGTH + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, IV_LENGTH);
            System.arraycopy(encrypted, 0, combined, IV_LENGTH, encrypted.length);

            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao criptografar CPF.", e);
        }
    }

    @Override
    public String convertToEntityAttribute(String encryptedCpf) {
        if (encryptedCpf == null) return null;
        try {
            byte[] combined = Base64.getDecoder().decode(encryptedCpf);
            byte[] iv        = Arrays.copyOfRange(combined, 0, IV_LENGTH);
            byte[] encrypted = Arrays.copyOfRange(combined, IV_LENGTH, combined.length);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new IvParameterSpec(iv));

            return new String(cipher.doFinal(encrypted));
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao descriptografar CPF.", e);
        }
    }

    private static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                                 + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }
}
