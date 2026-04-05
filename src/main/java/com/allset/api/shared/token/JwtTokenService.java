package com.allset.api.shared.token;

import com.allset.api.config.AppProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * Implementação de {@link TokenService} usando JWT HS256 via Spring OAuth2 Resource Server.
 * Os TTLs são lidos de {@link AppProperties}, configurados via variáveis de ambiente.
 */
@Service
@RequiredArgsConstructor
public class JwtTokenService implements TokenService {

    private final JwtEncoder jwtEncoder;
    private final JwtDecoder jwtDecoder;
    private final AppProperties appProperties;

    @Override
    public String generateAccessToken(UUID userId, String role) {
        Instant now = Instant.now();
        long ttlSeconds = appProperties.accessTokenTtlMinutes() * 60L;

        JwtClaimsSet claims = JwtClaimsSet.builder()
            .subject(userId.toString())
            .issuedAt(now)
            .expiresAt(now.plusSeconds(ttlSeconds))
            .claim("role", role)
            .build();

        return encode(claims);
    }

    @Override
    public String generateRefreshToken(UUID userId) {
        Instant now = Instant.now();
        long ttlSeconds = appProperties.refreshTokenTtlDays() * 86_400L;

        JwtClaimsSet claims = JwtClaimsSet.builder()
            .subject(userId.toString())
            .issuedAt(now)
            .expiresAt(now.plusSeconds(ttlSeconds))
            .claim("type", "refresh")
            .build();

        return encode(claims);
    }

    @Override
    public UUID extractRefreshUserId(String token) {
        Jwt jwt = decode(token);

        if (!"refresh".equals(jwt.getClaimAsString("type"))) {
            throw new TokenParseException("Token não é do tipo refresh");
        }

        try {
            return UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException e) {
            throw new TokenParseException("Claim 'sub' não contém um UUID válido", e);
        }
    }

    @Override
    public long accessTokenTtlSeconds() {
        return appProperties.accessTokenTtlMinutes() * 60L;
    }

    private String encode(JwtClaimsSet claims) {
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    private Jwt decode(String token) {
        try {
            return jwtDecoder.decode(token);
        } catch (JwtException e) {
            throw new TokenParseException("Token inválido ou expirado", e);
        }
    }
}
