package com.hgws.sbp.configurations.cache.writer;

import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.data.redis.cache.BatchStrategy;
import org.springframework.data.redis.cache.CacheStatistics;
import org.springframework.data.redis.cache.CacheStatisticsCollector;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStringCommands;
import org.springframework.data.redis.core.types.Expiration;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * @author zhouhonggang
 * @version 1.0.0
 * @project spring-boot-pro.git
 * @datetime 2022-07-15 14:44
 * @description: 写入动态缓存过期时间
 */
public class DynamicTTLCacheWriter implements RedisCacheWriter {
    private final Duration sleepTime;
    private final BatchStrategy batchStrategy;
    private final Random random = new Random();
    private final CacheStatisticsCollector statistics;
    private final RedisConnectionFactory connectionFactory;

    public DynamicTTLCacheWriter(RedisConnectionFactory connectionFactory, BatchStrategy batchStrategy) {
        this(connectionFactory, Duration.ZERO, batchStrategy);
    }

    DynamicTTLCacheWriter(RedisConnectionFactory connectionFactory, Duration sleepTime, BatchStrategy batchStrategy) {
        this(connectionFactory, sleepTime, CacheStatisticsCollector.none(), batchStrategy);
    }

    DynamicTTLCacheWriter(RedisConnectionFactory connectionFactory, Duration sleepTime, CacheStatisticsCollector cacheStatisticsCollector, BatchStrategy batchStrategy) {
        Assert.notNull(connectionFactory, "ConnectionFactory must not be null!");
        Assert.notNull(sleepTime, "SleepTime must not be null!");
        Assert.notNull(cacheStatisticsCollector, "CacheStatisticsCollector must not be null!");
        Assert.notNull(batchStrategy, "BatchStrategy must not be null!");
        this.connectionFactory = connectionFactory;
        this.sleepTime = sleepTime;
        this.statistics = cacheStatisticsCollector;
        this.batchStrategy = batchStrategy;
    }

    public void put(String name, byte[] key, byte[] value, @Nullable Duration ttl) {
        Assert.notNull(name, "Name must not be null!");
        Assert.notNull(key, "Key must not be null!");
        Assert.notNull(value, "Value must not be null!");
        this.execute(name, (connection) -> {
            if (shouldExpireWithin(ttl)) {
                String nullValue = new String(value);
                if(nullValue.contains("org.springframework.cache.support.NullValue"))
                {
                    //查询数据库没有的记录, 依然写入缓存时长默认五分钟
                    connection.set(key, value, Expiration.from(Duration.ofMinutes(5)), RedisStringCommands.SetOption.upsert());
                }
                else
                {
                    //随机产生一个0-10分钟之间的时间
                    int randomMinutes = random.nextInt(11);
                    //每次向缓存写入时间为随机 10-20 分钟之间
                    connection.set(key, value, Expiration.from(ttl.toMinutes() + randomMinutes, TimeUnit.MINUTES), RedisStringCommands.SetOption.upsert());
                }
            } else {
                connection.set(key, value);
            }

            return "OK";
        });
        this.statistics.incPuts(name);
    }

    public byte[] get(String name, byte[] key) {
        Assert.notNull(name, "Name must not be null!");
        Assert.notNull(key, "Key must not be null!");
        byte[] result = (byte[])this.execute(name, (connection) -> {
            return connection.get(key);
        });
        this.statistics.incGets(name);
        if (result != null) {
            this.statistics.incHits(name);
        } else {
            this.statistics.incMisses(name);
        }

        return result;
    }

    public byte[] putIfAbsent(String name, byte[] key, byte[] value, @Nullable Duration ttl) {
        Assert.notNull(name, "Name must not be null!");
        Assert.notNull(key, "Key must not be null!");
        Assert.notNull(value, "Value must not be null!");
        return (byte[])this.execute(name, (connection) -> {
            if (this.isLockingCacheWriter()) {
                this.doLock(name, connection);
            }

            byte[] var7;
            try {
                boolean put;
                if (shouldExpireWithin(ttl)) {
                    int randomMinutes = random.nextInt(11);
                    put = connection.set(key, value, Expiration.from(ttl.toMinutes()+randomMinutes, TimeUnit.MINUTES), RedisStringCommands.SetOption.ifAbsent());
                } else {
                    put = connection.setNX(key, value);
                }

                if (put) {
                    this.statistics.incPuts(name);
                    Object var11 = null;
                    return (byte[])var11;
                }

                var7 = connection.get(key);
            } finally {
                if (this.isLockingCacheWriter()) {
                    this.doUnlock(name, connection);
                }

            }

            return var7;
        });
    }

    public void remove(String name, byte[] key) {
        Assert.notNull(name, "Name must not be null!");
        Assert.notNull(key, "Key must not be null!");
        this.execute(name, (connection) -> {
            return connection.del(new byte[][]{key});
        });
        this.statistics.incDeletes(name);
    }

    public void clean(String name, byte[] pattern) {
        Assert.notNull(name, "Name must not be null!");
        Assert.notNull(pattern, "Pattern must not be null!");
        this.execute(name, (connection) -> {
            boolean wasLocked = false;

            try {
                if (this.isLockingCacheWriter()) {
                    this.doLock(name, connection);
                    wasLocked = true;
                }

                long deleteCount;
                for(deleteCount = this.batchStrategy.cleanCache(connection, name, pattern); deleteCount > 2147483647L; deleteCount -= 2147483647L) {
                    this.statistics.incDeletesBy(name, 2147483647);
                }

                this.statistics.incDeletesBy(name, (int)deleteCount);
                return "OK";
            } finally {
                if (wasLocked && this.isLockingCacheWriter()) {
                    this.doUnlock(name, connection);
                }

            }
        });
    }

    public CacheStatistics getCacheStatistics(String cacheName) {
        return this.statistics.getCacheStatistics(cacheName);
    }

    public void clearStatistics(String name) {
        this.statistics.reset(name);
    }

    public RedisCacheWriter withStatisticsCollector(CacheStatisticsCollector cacheStatisticsCollector) {
        return new DynamicTTLCacheWriter(this.connectionFactory, this.sleepTime, cacheStatisticsCollector, this.batchStrategy);
    }

    void lock(String name) {
        this.execute(name, (connection) -> {
            return this.doLock(name, connection);
        });
    }

    void unlock(String name) {
        this.executeLockFree((connection) -> {
            this.doUnlock(name, connection);
        });
    }

    private Boolean doLock(String name, RedisConnection connection) {
        return connection.setNX(createCacheLockKey(name), new byte[0]);
    }

    private Long doUnlock(String name, RedisConnection connection) {
        return connection.del(new byte[][]{createCacheLockKey(name)});
    }

    boolean doCheckLock(String name, RedisConnection connection) {
        return connection.exists(createCacheLockKey(name));
    }

    private boolean isLockingCacheWriter() {
        return !this.sleepTime.isZero() && !this.sleepTime.isNegative();
    }

    private <T> T execute(String name, Function<RedisConnection, T> callback) {
        RedisConnection connection = this.connectionFactory.getConnection();

        Object var4;
        try {
            this.checkAndPotentiallyWaitUntilUnlocked(name, connection);
            var4 = callback.apply(connection);
        } finally {
            connection.close();
        }

        return (T) var4;
    }

    private void executeLockFree(Consumer<RedisConnection> callback) {
        RedisConnection connection = this.connectionFactory.getConnection();

        try {
            callback.accept(connection);
        } finally {
            connection.close();
        }

    }

    private void checkAndPotentiallyWaitUntilUnlocked(String name, RedisConnection connection) {
        if (this.isLockingCacheWriter()) {
            long lockWaitTimeNs = System.nanoTime();

            try {
                while(this.doCheckLock(name, connection)) {
                    Thread.sleep(this.sleepTime.toMillis());
                }
            } catch (InterruptedException var9) {
                Thread.currentThread().interrupt();
                throw new PessimisticLockingFailureException(String.format("Interrupted while waiting to unlock cache %s", name), var9);
            } finally {
                this.statistics.incLockTime(name, System.nanoTime() - lockWaitTimeNs);
            }

        }
    }

    private static boolean shouldExpireWithin(@Nullable Duration ttl) {
        return ttl != null && !ttl.isZero() && !ttl.isNegative();
    }

    private static byte[] createCacheLockKey(String name) {
        return (name + "~lock").getBytes(StandardCharsets.UTF_8);
    }
}
