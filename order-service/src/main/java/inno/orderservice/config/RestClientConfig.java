package inno.orderservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    @Bean
    public RestClient userServiceRestClient(
            @Value("${app.user-service.base-url:http://localhost:8080/api/v1}") String baseUrl,
            IdentityForwardingInterceptor identityForwardingInterceptor) {
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestInterceptor(identityForwardingInterceptor)
                .build();
    }

    @Bean
    public IdentityForwardingInterceptor identityForwardingInterceptor() {
        return new IdentityForwardingInterceptor();
    }
}