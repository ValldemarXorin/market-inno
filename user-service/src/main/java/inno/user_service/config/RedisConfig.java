package inno.user_service.config;

import org.springframework.boot.cache.autoconfigure.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;

@Configuration
@EnableCaching
public class RedisConfig {

    private static final String USERS_CACHE = "users";
    private static final Duration DEFAULT_TTL = Duration.ofMinutes(15);

    @Bean
    public RedisCacheManagerBuilderCustomizer redisCacheManagerBuilderCustomizer() {
        var jsonSerializer = RedisSerializationContext.SerializationPair
                .fromSerializer(GenericJacksonJsonRedisSerializer.builder().build());

        RedisCacheConfiguration cacheConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(DEFAULT_TTL)
                .disableCachingNullValues()
                .serializeValuesWith(jsonSerializer);

        return builder -> builder.withCacheConfiguration(USERS_CACHE, cacheConfig);
    }
}