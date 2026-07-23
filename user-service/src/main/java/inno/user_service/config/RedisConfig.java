package inno.user_service.config;

import org.springframework.boot.cache.autoconfigure.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;
import java.util.Map;

@Configuration
@EnableCaching
public class RedisConfig {

    public static final String USERS_CACHE = "users";
    public static final String USER_CARDS_CACHE = "userCards";
    private static final Duration DEFAULT_TTL = Duration.ofMinutes(15);

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        var jsonSerializer = RedisSerializationContext.SerializationPair
                .fromSerializer(GenericJacksonJsonRedisSerializer.builder().build());

        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(DEFAULT_TTL)
                .disableCachingNullValues()
                .serializeValuesWith(jsonSerializer);

        Map<String, RedisCacheConfiguration> initialConfigs = Map.of(
                USERS_CACHE, defaultConfig,
                USER_CARDS_CACHE, defaultConfig
        );

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(initialConfigs)
                .build();
    }
}