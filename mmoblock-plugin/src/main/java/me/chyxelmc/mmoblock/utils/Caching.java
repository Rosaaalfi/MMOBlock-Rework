package me.chyxelmc.mmoblock.utils;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

public final class Caching {

    private Caching() {
    }

    public static <K, V> Cache<K, V> simpleCache(final long maxSize, final Duration expireAfterWrite) {
        return Caffeine.newBuilder()
                .maximumSize(maxSize)
                .expireAfterWrite(expireAfterWrite)
                .recordStats()
                .build();
    }

    public static <K, V> Cache<K, V> smallCache() {
        return Caffeine.newBuilder()
                .maximumSize(512)
                .expireAfterWrite(Duration.ofMinutes(2))
                .recordStats()
                .build();
    }

    public static <K, V> Cache<K, V> mediumCache() {
        return Caffeine.newBuilder()
                .maximumSize(2048)
                .expireAfterWrite(Duration.ofMinutes(5))
                .recordStats()
                .build();
    }

    public static <K, V> Cache<K, V> largeCache() {
        return Caffeine.newBuilder()
                .maximumSize(16384)
                .expireAfterWrite(Duration.ofMinutes(15))
                .recordStats()
                .build();
    }

    public static <K, V> Cache<K, V> neverExpireCache(final long maxSize) {
        return Caffeine.newBuilder()
                .maximumSize(maxSize)
                .recordStats()
                .build();
    }

    public static <K, V> V getOrLoad(final Cache<K, V> cache, final K key, final Function<K, V> loader) {
        final V existing = cache.getIfPresent(key);
        if (existing != null) {
            return existing;
        }
        final V loaded = loader.apply(key);
        if (loaded != null) {
            cache.put(key, loaded);
        }
        return loaded;
    }

    public static <K, V> Optional<V> getOptional(final Cache<K, V> cache, final K key) {
        return Optional.ofNullable(cache.getIfPresent(key));
    }
}
