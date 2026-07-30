package inno.user_service.config;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;

import java.time.Duration;

@Configuration
@EnableCaching
@EnableConfigurationProperties(CacheProperties.class)
public class RedisConfig {

    private static RedisSerializer<Object> jsonRedisSerializer() {
        var objectMapper = JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
        objectMapper.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                com.fasterxml.jackson.databind.ObjectMapper.DefaultTyping.EVERYTHING
        );

        return new GenericJackson2JsonRedisSerializer(objectMapper);
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate(LettuceConnectionFactory lettuceFactory) {
        var txTemplate = new RedisTemplate<String, Object>();
        var jsonSerializer = jsonRedisSerializer();

        txTemplate.setConnectionFactory(lettuceFactory);
        txTemplate.setKeySerializer(RedisSerializer.string());
        txTemplate.setValueSerializer(jsonSerializer);
        txTemplate.setHashKeySerializer(RedisSerializer.string());
        txTemplate.setHashValueSerializer(jsonSerializer);

        txTemplate.setEnableTransactionSupport(true);

        return txTemplate;
    }

    @Bean
    public RedisCacheManager redisCacheManager(LettuceConnectionFactory lettuceFactory, CacheProperties cacheProperties) {
        var jsonSerializer = jsonRedisSerializer();
        RedisCacheConfiguration cacheSettings = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(cacheProperties.expirationMinutes()))
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(RedisSerializer.string()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(jsonSerializer))
                .disableCachingNullValues();

        return RedisCacheManager.RedisCacheManagerBuilder
                .fromConnectionFactory(lettuceFactory)
                .cacheDefaults(cacheSettings)
                .transactionAware()
                .build();
    }
}