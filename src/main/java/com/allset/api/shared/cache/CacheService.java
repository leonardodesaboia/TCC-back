package com.allset.api.shared.cache;

import java.util.Optional;

/**
 * Contrato agnóstico de framework para operações de cache chave-valor com TTL.
 * Implementações concretas podem usar Redis, Memcached, etc.
 */
public interface CacheService {

    /**
     * Armazena um valor associado à chave com tempo de expiração.
     *
     * @param key        chave única de armazenamento
     * @param value      valor a ser armazenado
     * @param ttlSeconds tempo de vida em segundos
     */
    void set(String key, String value, long ttlSeconds);

    /**
     * Recupera o valor associado à chave, se existir e não tiver expirado.
     *
     * @param key chave de busca
     * @return Optional com o valor, ou empty se ausente/expirado
     */
    Optional<String> get(String key);

    /**
     * Remove a chave e seu valor do cache.
     *
     * @param key chave a ser removida
     */
    void delete(String key);
}
