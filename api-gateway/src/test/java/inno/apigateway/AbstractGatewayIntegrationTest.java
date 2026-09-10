package inno.apigateway;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;

/**
 * Base class for gateway integration tests that exercise the real routing pipeline.
 *
 * <p>The gateway routes defined in application.yaml point at {@code lb://} services discovered
 * through Eureka. During tests there is no Eureka-registered backend, so the routes are
 * re-pointed at a local WireMock server and Eureka discovery is disabled. This keeps the tests
 * deterministic and independent of any locally running Docker services.
 */
abstract class AbstractGatewayIntegrationTest {

    private static final WireMockServer WIRE_MOCK = createAndStartWireMock();

    @DynamicPropertySource
    static void backends(DynamicPropertyRegistry registry) {
        String baseUrl = "http://localhost:" + WIRE_MOCK.port();

        registry.add("spring.cloud.gateway.routes[0].id", () -> "auth-service");
        registry.add("spring.cloud.gateway.routes[0].uri", () -> baseUrl);
        registry.add("spring.cloud.gateway.routes[0].order", () -> "0");
        registry.add("spring.cloud.gateway.routes[0].predicates[0]", () -> "Path=/api/v1/auth/**");

        registry.add("spring.cloud.gateway.routes[1].id", () -> "order-service-user-orders");
        registry.add("spring.cloud.gateway.routes[1].uri", () -> baseUrl);
        registry.add("spring.cloud.gateway.routes[1].order", () -> "1");
        registry.add("spring.cloud.gateway.routes[1].predicates[0]", () -> "Path=/api/v1/users/*/orders");

        registry.add("spring.cloud.gateway.routes[2].id", () -> "user-service");
        registry.add("spring.cloud.gateway.routes[2].uri", () -> baseUrl);
        registry.add("spring.cloud.gateway.routes[2].order", () -> "2");
        registry.add("spring.cloud.gateway.routes[2].predicates[0]", () -> "Path=/api/v1/users/**,/api/v1/cards/**");

        registry.add("spring.cloud.gateway.routes[3].id", () -> "order-service");
        registry.add("spring.cloud.gateway.routes[3].uri", () -> baseUrl);
        registry.add("spring.cloud.gateway.routes[3].order", () -> "3");
        registry.add("spring.cloud.gateway.routes[3].predicates[0]", () -> "Path=/api/v1/orders/**");

        registry.add("eureka.client.enabled", () -> "false");
        registry.add("eureka.client.register-with-eureka", () -> "false");
        registry.add("eureka.client.fetch-registry", () -> "false");
    }

    private static WireMockServer createAndStartWireMock() {
        WireMockServer server = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        server.start();
        server.stubFor(any(anyUrl()).willReturn(ok()));
        return server;
    }
}