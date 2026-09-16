package com.allset.api.address.exception;

import com.allset.api.address.domain.CoordinateSource;

import java.util.UUID;

/**
 * Lançada quando um fluxo que depende de precisão geográfica (hoje só o Express)
 * recebe um endereço cuja coordenada não é confiável.
 *
 * <p>Carrega um código estável para o cliente distinguir os dois motivos sem
 * fazer parsing de mensagem — os dois respondem 422:
 *
 * <ul>
 *   <li>{@code ADDRESS_COORDINATE_MISSING} — o endereço nunca teve ponto no mapa;</li>
 *   <li>{@code ADDRESS_COORDINATE_NOT_TRUSTED} — tem ponto, mas de procedência
 *       insuficiente (gravado antes da correção, ou sugestão aproximada do provider).</li>
 * </ul>
 *
 * <p>Em ambos os casos a saída é a mesma para quem usa o app: abrir o endereço e
 * confirmar o pin. A mensagem diz isso.
 */
public class AddressCoordinateNotTrustedException extends RuntimeException {

    public static final String CODE_MISSING = "ADDRESS_COORDINATE_MISSING";
    public static final String CODE_NOT_TRUSTED = "ADDRESS_COORDINATE_NOT_TRUSTED";

    private final String code;
    private final UUID addressId;
    private final CoordinateSource source;

    private AddressCoordinateNotTrustedException(String code, UUID addressId,
                                                 CoordinateSource source, String message) {
        super(message);
        this.code = code;
        this.addressId = addressId;
        this.source = source;
    }

    public static AddressCoordinateNotTrustedException missing(UUID addressId) {
        return new AddressCoordinateNotTrustedException(CODE_MISSING, addressId, null,
            "Este endereço ainda não tem um ponto marcado no mapa. "
            + "Abra o endereço, confirme o pin no local do atendimento e tente de novo.");
    }

    public static AddressCoordinateNotTrustedException notTrusted(UUID addressId, CoordinateSource source) {
        return new AddressCoordinateNotTrustedException(CODE_NOT_TRUSTED, addressId, source,
            "O ponto salvo neste endereço é aproximado e não serve para o modo Express, "
            + "que avisa apenas profissionais bem próximos. "
            + "Abra o endereço e confirme o pin no local do atendimento.");
    }

    public String getCode() {
        return code;
    }

    public UUID getAddressId() {
        return addressId;
    }

    /** Procedência recusada. {@code null} quando o endereço não tinha coordenada. */
    public CoordinateSource getSource() {
        return source;
    }
}
