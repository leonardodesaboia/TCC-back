package com.allset.api.geocoding.provider;

import com.allset.api.geocoding.dto.NormalizedAddress;
import com.allset.api.geocoding.exception.GeocodingProviderUnavailableException;
import com.allset.api.geocoding.exception.GeocodingRateLimitException;
import com.allset.api.geocoding.provider.BrasilApiCepProvider.CepLookup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Encadeia os providers para maximizar o hit rate em endereços brasileiros.
 *
 * <p>Estratégia para query <b>com CEP</b>:
 * <ol>
 *   <li>BrasilAPI v2 retorna endereço + coords → usa direto;</li>
 *   <li>BrasilAPI retorna endereço <b>sem</b> coords (fonte interna ViaCEP/open-cep parcial)
 *       → enriquece a query original com rua/bairro da BrasilAPI e chama Nominatim;</li>
 *   <li>BrasilAPI não tem o CEP ou caiu → Nominatim com a query original.</li>
 * </ol>
 *
 * <p>Para query <b>sem CEP</b>: vai direto para o Nominatim em busca livre.
 *
 * <p>Falhas operacionais (timeout, 5xx, 429) de um provider não derrubam o lookup —
 * caem no próximo. Só lançamos {@link GeocodingProviderUnavailableException} quando
 * <b>todos</b> os providers da chain falharam.
 */
@Primary
@Component
public class CompositeGeocodingProvider implements GeocodingProvider {

    private static final Logger log = LoggerFactory.getLogger(CompositeGeocodingProvider.class);

    private static final String PROVIDER_NAME = "composite";

    private final BrasilApiCepProvider brasilApi;
    private final NominatimGeocodingProvider nominatim;

    public CompositeGeocodingProvider(BrasilApiCepProvider brasilApi,
                                      NominatimGeocodingProvider nominatim) {
        this.brasilApi = brasilApi;
        this.nominatim = nominatim;
    }

    @Override
    public String name() {
        return PROVIDER_NAME;
    }

    @Override
    public Optional<GeocodeResult> geocode(GeocodeQuery query) {
        boolean hasCep = query.zipCode() != null && !query.zipCode().isBlank();

        if (!hasCep) {
            log.info("Query sem CEP, indo direto para Nominatim");
            return nominatim.geocode(query);
        }

        Optional<CepLookup> lookup = Optional.empty();
        boolean brasilApiFailed = false;
        try {
            lookup = brasilApi.lookup(query.zipCode());
        } catch (GeocodingProviderUnavailableException | GeocodingRateLimitException ex) {
            log.warn("BrasilAPI indisponível ({}), seguindo só com Nominatim",
                ex.getClass().getSimpleName());
            brasilApiFailed = true;
        }

        // 1. BrasilAPI tem endereço + coords → resposta direta
        if (lookup.isPresent() && lookup.get().hasCoords()) {
            log.info("Geocoding via BrasilAPI cep={} service={}",
                query.zipCode(), lookup.get().service());
            return brasilApi.geocode(query);
        }

        // 2. BrasilAPI tem endereço sem coords → enriquece query do Nominatim
        GeocodeQuery effectiveQuery = lookup
            .map(l -> {
                log.info("BrasilAPI sem coords (service={}), enriquecendo Nominatim com street='{}', district='{}'",
                    l.service(), l.address().street(), l.address().district());
                return enrichQuery(query, l.address());
            })
            .orElse(query);

        try {
            return nominatim.geocode(effectiveQuery);
        } catch (GeocodingProviderUnavailableException | GeocodingRateLimitException ex) {
            if (brasilApiFailed) {
                log.error("Toda a chain de geocoding está indisponível");
            }
            throw ex;
        }
    }

    /**
     * Preenche apenas os campos que o usuário deixou vazios — nunca sobrescreve
     * o que ele digitou. Para nossa UX (front manda CEP + cidade), isso adiciona
     * rua e bairro que o usuário não preencheu, sem violar a intenção dele.
     */
    private static GeocodeQuery enrichQuery(GeocodeQuery original, NormalizedAddress fromBrasilApi) {
        return new GeocodeQuery(
            original.zipCode(),
            firstNonBlank(original.street(),   fromBrasilApi.street()),
            original.number(),
            firstNonBlank(original.district(), fromBrasilApi.district()),
            firstNonBlank(original.city(),     fromBrasilApi.city()),
            firstNonBlank(original.state(),    fromBrasilApi.state()),
            original.country()
        );
    }

    private static String firstNonBlank(String preferred, String fallback) {
        return (preferred != null && !preferred.isBlank()) ? preferred : fallback;
    }
}
