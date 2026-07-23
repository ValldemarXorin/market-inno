package inno.user_service.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;

import java.time.Duration;

@Configuration
@EnableCaching
public class RedisConfig {

    public static final String USERS_CACHE = "users";
    public static final String USER_CARDS_CACHE = "userCards";

    private static final long CACHE_EXPIRATION_MINUTES = 20;

    @Bean
    public RedisTemplate<String, Object> redisTemplate(LettuceConnectionFactory lettuceFactory) {
        var txTemplate = new RedisTemplate<String, Object>();

        txTemplate.setConnectionFactory(lettuceFactory);
        txTemplate.setKeySerializer(RedisSerializer.string());
        txTemplate.setValueSerializer(RedisSerializer.json());
        txTemplate.setHashKeySerializer(RedisSerializer.string());
        txTemplate.setHashValueSerializer(RedisSerializer.json());

        txTemplate.setEnableTransactionSupport(true);

        return txTemplate;
    }

    @Bean
    public RedisCacheManager redisCacheManager(LettuceConnectionFactory lettuceFactory) {
        RedisCacheConfiguration cacheSettings = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(CACHE_EXPIRATION_MINUTES))
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(RedisSerializer.string()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(RedisSerializer.json()))
                .disableCachingNullValues();

        return RedisCacheManager.RedisCacheManagerBuilder
                .fromConnectionFactory(lettuceFactory)
                .cacheDefaults(cacheSettings)
                .transactionAware()
                .build();
    }
}