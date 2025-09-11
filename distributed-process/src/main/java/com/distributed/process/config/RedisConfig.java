package com.distributed.process.config;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


 //จัดการการเชื่อมต่อ Redis Pub/Sub communication
public class RedisConfig {
    private static final Logger logger = LoggerFactory.getLogger(RedisConfig.class);

    // Redis connection settings
    private static final String REDIS_HOST = "localhost";
    private static final int REDIS_PORT = 6379;

    // Singleton instance
    private static JedisPool jedisPool;

    //สร้าง Redis connection pool
    public static void initialize() {
        if (jedisPool == null) {
            jedisPool = new JedisPool(REDIS_HOST, REDIS_PORT);
            logger.info("Redis connected at {}:{}", REDIS_HOST, REDIS_PORT);
        }
    }
    
    public static Jedis getJedis() {
        if (jedisPool == null) {
            initialize();
        }
        return jedisPool.getResource();
    }

     //ปิด connection pool
    public static void shutdown() {
        if (jedisPool != null) {
            jedisPool.close();
            logger.info("Redis connection closed");
        }
    }
}