package com.kangli.qms.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Redis 工具类。
 * <p>封装常用 String/Object 操作，供认证模块使用。</p>
 */
@Slf4j
@Component
public class RedisUtil {

    private final RedisTemplate<String, Object> redisTemplate;

    public RedisUtil(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 写入带 TTL 的值。
     */
    public void set(String key, Object value, long timeout, TimeUnit unit) {
        try {
            redisTemplate.opsForValue().set(key, value, timeout, unit);
        } catch (Exception e) {
            log.error("[Redis] 写入失败 key={}", key, e);
            throw e;
        }
    }

    /**
     * 写入带 TTL（秒）的值。
     */
    public void set(String key, Object value, long timeoutSeconds) {
        set(key, value, timeoutSeconds, TimeUnit.SECONDS);
    }

    /**
     * 读取值。
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        return (T) redisTemplate.opsForValue().get(key);
    }

    /**
     * 读取字符串值。
     */
    public String getString(String key) {
        Object val = redisTemplate.opsForValue().get(key);
        return val == null ? null : val.toString();
    }

    /**
     * 读取整数值（失败返回 0）。
     */
    public int getInt(String key) {
        Object val = redisTemplate.opsForValue().get(key);
        if (val == null) {
            return 0;
        }
        if (val instanceof Number) {
            return ((Number) val).intValue();
        }
        try {
            return Integer.parseInt(val.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * 递增（原子操作），返回递增后的值。
     */
    public long increment(String key) {
        return redisTemplate.opsForValue().increment(key);
    }

    /**
     * 递增并设置 TTL（首次递增时设置过期时间）。
     *
     * @param key             Redis Key
     * @param timeoutSeconds   TTL（秒）
     * @return 递增后的值
     */
    public long incrementWithTtl(String key, long timeoutSeconds) {
        long count = redisTemplate.opsForValue().increment(key);
        if (count == 1L) {
            redisTemplate.expire(key, timeoutSeconds, TimeUnit.SECONDS);
        }
        return count;
    }

    /**
     * 删除 Key。
     */
    public Boolean delete(String key) {
        return redisTemplate.delete(key);
    }

    /**
     * 判断 Key 是否存在。
     */
    public Boolean hasKey(String key) {
        return redisTemplate.hasKey(key);
    }

    /**
     * 设置 TTL（秒）。
     */
    public Boolean expire(String key, long timeoutSeconds) {
        return redisTemplate.expire(key, timeoutSeconds, TimeUnit.SECONDS);
    }

    /**
     * 获取 Key 的剩余 TTL（秒）。
     *
     * @return 剩余秒数；key 不存在返回 -2；key 无过期时间返回 -1；异常返回 0
     */
    public long getExpireSeconds(String key) {
        try {
            return redisTemplate.getExpire(key, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("[Redis] 获取TTL失败 key={}", key, e);
            return 0L;
        }
    }
}
