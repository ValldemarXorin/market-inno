package inno.apigateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.netflix.eureka.EurekaDiscoveryClient;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class GatewayRoutesTest {

    @Autowired
    private RouteDefinitionLocator routeDefinitionLocator;

    @Autowired
    private RouteLocator routeLocator;

    @Autowired
    private List<DiscoveryClient> discoveryClients;

    @Test
    void exposesRoutesForAllBackendServices() {
        List<RouteDefinition> definitions = routeDefinitionLocator.getRouteDefinitions().collectList().block();

        assertThat(definitions).isNotNull().hasSize(4);

        Map<String, RouteDefinition> byId = definitions.stream()
                .collect(Collectors.toMap(RouteDefinition::getId, Function.identity()));

        assertThat(byId)
                .containsKeys("auth-service", "user-service", "order-service", "order-service-user-orders");

        assertThat(byId.get("auth-service").getUri().toString()).isEqualTo("lb://auth-service");
        assertThat(byId.get("user-service").getUri().toString()).isEqualTo("lb://user-service");
        assertThat(byId.get("order-service").getUri().toString()).isEqualTo("lb://order-service");
        assertThat(byId.get("order-service-user-orders").getUri().toString()).isEqualTo("lb://order-service");

        assertThat(byId.get("order-service-user-orders").getOrder())
                .isLessThan(byId.get("user-service").getOrder());
    }

    @Test
    void forwardsRequestsToTheBackendServiceOwningThePath() {
        assertThat(routeFor("/api/v1/auth/login").getUri().toString()).isEqualTo("lb://auth-service");
        assertThat(routeFor("/api/v1/users/123").getUri().toString()).isEqualTo("lb://user-service");
        assertThat(routeFor("/api/v1/users/123/cards").getUri().toString()).isEqualTo("lb://user-service");
        assertThat(routeFor("/api/v1/cards/456").getUri().toString()).isEqualTo("lb://user-service");
        assertThat(routeFor("/api/v1/users/123/orders").getUri().toString()).isEqualTo("lb://order-service");
        assertThat(routeFor("/api/v1/orders/456").getUri().toString()).isEqualTo("lb://order-service");
    }

    @Test
    void eurekaDiscoveryClientIsActiveWithoutEurekaServer() {
        assertThat(discoveryClients)
                .anyMatch(client -> client instanceof EurekaDiscoveryClient);
    }

    private Route routeFor(String path) {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get(path).build());
        Route route = routeLocator.getRoutes()
                .filter(candidate -> Boolean.TRUE.equals(Mono.from(candidate.getPredicate().apply(exchange)).block()))
                .next()
                .block();
        assertThat(route).as("no gateway route matched %s", path).isNotNull();
        return route;
    }
}