package com.example.gateway.config;

import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Bucket4j distributed rate limiting via Lettuce (Redis).
 * Redis key prefix: "gateway:rate-limit:{ip}"
 */
@Configuration
public class RateLimitConfig {

    @Value("${spring.data.redis.host}")
    private String redisHost;

    @Value("${spring.data.redis.port}")
    private int redisPort;

    @Bean
    public StatefulRedisConnection<byte[], byte[]> rateLimitRedisConnection() {
        RedisURI redisUri = RedisURI.builder()
                .withHost(redisHost)
                .withPort(redisPort)
                .build();
        RedisClient client = RedisClient.create(redisUri);
        return client.connect(ByteArrayCodec.INSTANCE);
    }

    @Bean
    public ProxyManager<byte[]> bucketProxyManager(
            StatefulRedisConnection<byte[], byte[]> rateLimitRedisConnection
    ) {
        return LettuceBasedProxyManager
                .builderFor(rateLimitRedisConnection)
                .build();
    }
}
