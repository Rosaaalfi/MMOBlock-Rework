package me.chyxelmc.mmoblock.utils;

import com.github.benmanes.caffeine.cache.AsyncLoadingCache;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.github.benmanes.caffeine.cache.stats.CacheStats;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;

/**
 * Factory terpusat untuk membuat dan mengelola instance {@link Cache} berbasis Caffeine.
 * <p>
 * Semua cache yang dibuat lewat class ini otomatis mencatat statistik ({@code recordStats()})
 * sehingga bisa dipantau lewat {@link #stats(Cache)}.
 */
public final class CacheFactory {

    /**
     * Preset ukuran & waktu expire yang umum dipakai.
     * Menggantikan pasangan konstanta SIZE/EXPIRE terpisah agar tidak mudah desync.
     */
    public enum Size {
        SMALL(512, Duration.ofMinutes(2)),
        MEDIUM(2_048, Duration.ofMinutes(5)),
        LARGE(16_384, Duration.ofMinutes(15));

        private final long maxSize;
        private final Duration expireAfterWrite;

        Size(long maxSize, Duration expireAfterWrite) {
            this.maxSize = maxSize;
            this.expireAfterWrite = expireAfterWrite;
        }

        public long maxSize() {
            return maxSize;
        }

        public Duration expireAfterWrite() {
            return expireAfterWrite;
        }
    }

    private CacheFactory() {
        throw new UnsupportedOperationException("Utility class");
    }

    // ---------------------------------------------------------------------
    // Builder dasar
    // ---------------------------------------------------------------------

    private static Caffeine<Object, Object> builder() {
        return Caffeine.newBuilder().recordStats();
    }

    private static Caffeine<Object, Object> builder(long maxSize) {
        return builder().maximumSize(maxSize);
    }

    private static Caffeine<Object, Object> builder(long maxSize, Duration expireAfterWrite) {
        return builder(maxSize).expireAfterWrite(expireAfterWrite);
    }

    // ---------------------------------------------------------------------
    // Cache biasa
    // ---------------------------------------------------------------------

    /** Cache dengan expiration tetap sejak write. */
    public static <K, V> Cache<K, V> create(long maxSize, Duration expireAfterWrite) {
        return builder(maxSize, expireAfterWrite).build();
    }

    /** Cache dengan expiration tetap, plus opsi weakKeys/softValues untuk hemat memori. */
    public static <K, V> Cache<K, V> create(
            long maxSize,
            Duration expireAfterWrite,
            boolean weakKeys,
            boolean softValues
    ) {
        Caffeine<Object, Object> b = builder(maxSize, expireAfterWrite);
        if (weakKeys) b = b.weakKeys();
        if (softValues) b = b.softValues();
        return b.build();
    }

    /** Cache tanpa expiration waktu; entry dievict hanya saat maximumSize tercapai. */
    public static <K, V> Cache<K, V> persistent(long maxSize) {
        return builder(maxSize).build();
    }

    /** Cache dengan expiration per-entry (custom {@link Expiry}). */
    public static <K, V> Cache<K, V> withExpiry(long maxSize, Expiry<K, V> expiry) {
        return builder(maxSize).expireAfter(expiry).build();
    }

    // ---------------------------------------------------------------------
    // Loading cache (sync)
    // ---------------------------------------------------------------------

    /** Loading cache dasar dengan expireAfterWrite. */
    public static <K, V> LoadingCache<K, V> loading(
            long maxSize,
            Duration expireAfterWrite,
            Function<? super K, ? extends V> loader
    ) {
        return builder(maxSize, expireAfterWrite).build(loader::apply);
    }

    /**
     * Loading cache dengan refreshAfterWrite (stale-while-revalidate).
     * Entry lama tetap dikembalikan ke caller selagi reload berjalan di background,
     * sehingga tidak ada latency spike saat data expire.
     */
    public static <K, V> LoadingCache<K, V> loadingWithRefresh(
            long maxSize,
            Duration expireAfterWrite,
            Duration refreshAfterWrite,
            Function<? super K, ? extends V> loader
    ) {
        return builder(maxSize, expireAfterWrite)
                .refreshAfterWrite(refreshAfterWrite)
                .build(loader::apply);
    }

    // ---------------------------------------------------------------------
    // Loading cache (async) — disarankan untuk operasi I/O (DB, HTTP, dll)
    // agar tidak memblokir main thread server.
    // ---------------------------------------------------------------------

    /** Async loading cache; loader dijalankan di common ForkJoinPool. */
    public static <K, V> AsyncLoadingCache<K, V> asyncLoading(
            long maxSize,
            Duration expireAfterWrite,
            Function<? super K, ? extends V> loader
    ) {
        return builder(maxSize, expireAfterWrite)
                .buildAsync((key, executor) -> CompletableFuture.supplyAsync(() -> loader.apply(key), executor));
    }

    /** Async loading cache dengan executor kustom (misal scheduler plugin Bukkit). */
    public static <K, V> AsyncLoadingCache<K, V> asyncLoading(
            long maxSize,
            Duration expireAfterWrite,
            Function<? super K, ? extends V> loader,
            Executor executor
    ) {
        return builder(maxSize, expireAfterWrite)
                .executor(executor)
                .buildAsync((key, exec) -> CompletableFuture.supplyAsync(() -> loader.apply(key), exec));
    }

    // ---------------------------------------------------------------------
    // Preset
    // ---------------------------------------------------------------------

    /** Membuat cache berdasarkan {@link Size} preset. */
    public static <K, V> Cache<K, V> of(Size size) {
        return create(size.maxSize(), size.expireAfterWrite());
    }

    public static <K, V> Cache<K, V> small() {
        return of(Size.SMALL);
    }

    public static <K, V> Cache<K, V> medium() {
        return of(Size.MEDIUM);
    }

    public static <K, V> Cache<K, V> large() {
        return of(Size.LARGE);
    }

    // ---------------------------------------------------------------------
    // Operasi umum
    // ---------------------------------------------------------------------

    /** Ambil nilai dari cache atau load secara atomik jika belum ada. */
    public static <K, V> V getOrLoad(Cache<K, V> cache, K key, Function<? super K, ? extends V> loader) {
        return cache.get(key, loader);
    }

    /** Ambil nilai cache sebagai {@link Optional}, tanpa memicu load. */
    public static <K, V> Optional<V> getOptional(Cache<K, V> cache, K key) {
        return Optional.ofNullable(cache.getIfPresent(key));
    }

    /** Statistik cache (hit rate, eviction count, dll) — berguna untuk command debug/monitoring. */
    public static CacheStats stats(Cache<?, ?> cache) {
        return cache.stats();
    }

    /** Invalidasi satu entry. */
    public static <K, V> void invalidate(Cache<K, V> cache, K key) {
        cache.invalidate(key);
    }

    /** Invalidasi seluruh entry. */
    public static void invalidateAll(Cache<?, ?> cache) {
        cache.invalidateAll();
    }

    /** Jalankan maintenance tertunda (eviction, expiration cleanup) secara manual. */
    public static void cleanUp(Cache<?, ?> cache) {
        cache.cleanUp();
    }
}