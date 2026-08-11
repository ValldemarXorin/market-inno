package inno.user_service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redis.testcontainers.RedisContainer;
import inno.user_service.dto.request.CreatePaymentCardRequest;
import inno.user_service.dto.request.CreateUserRequest;
import inno.user_service.dto.request.SetActiveRequest;
import inno.user_service.dto.request.UpdatePaymentCardRequest;
import inno.user_service.dto.response.PaymentCardResponse;
import inno.user_service.dto.response.UserResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDate;
import java.time.Month;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}")
@EmbeddedKafka(partitions = 1, topics = {"user-created-events", "user-status-events"})
@AutoConfigureMockMvc
@Testcontainers
class PaymentCardControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .findAndRegisterModules();

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17")
            .withDatabaseName("userservice")
            .withUsername("postgres")
            .withPassword("postgres");

    @Container
    static RedisContainer redis =
            new RedisContainer(DockerImageName.parse("redis:7"));

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);

        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));

        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");

        registry.add("auth.jwt.secret", () -> TestTokens.SECRET);
    }

    @Test
    void shouldVerifyFullPaymentCardFlow() throws Exception {

        CreateUserRequest userRequest = new CreateUserRequest(
                "Masha",
                "Mashina",
                LocalDate.of(1998, Month.OCTOBER, 12),
                "masha@yandex.ru"
        );

        String createdUserJson =
                mockMvc.perform(post("/users")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(userRequest)))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        UserResponse user =
                objectMapper.readValue(createdUserJson, UserResponse.class);

        String adminToken = TestTokens.adminToken();

        CreatePaymentCardRequest cardRequest =
                new CreatePaymentCardRequest(
                        "1111222233334444",
                        "Masha Mashina",
                        LocalDate.of(2030, Month.DECEMBER, 31)
                );

        String createdCardJson =
                mockMvc.perform(post("/users/" + user.id() + "/cards")
                                .header("Authorization", "Bearer " + adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(cardRequest)))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.holder").value("Masha Mashina"))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        PaymentCardResponse card =
                objectMapper.readValue(createdCardJson, PaymentCardResponse.class);

        mockMvc.perform(get("/cards/" + card.id())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(card.id().toString()))
                .andExpect(jsonPath("$.holder").value("Masha Mashina"));

        mockMvc.perform(get("/users/" + user.id() + "/cards")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1));

        mockMvc.perform(get("/cards")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1));

        UpdatePaymentCardRequest updateRequest =
                new UpdatePaymentCardRequest(
                        "5555666677778888",
                        "Ivan Ivanov",
                        LocalDate.of(2032, Month.JANUARY, 1)
                );

        mockMvc.perform(put("/cards/" + card.id())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.holder").value("Ivan Ivanov"))
                .andExpect(jsonPath("$.number").value("5555666677778888"));

        mockMvc.perform(patch("/cards/" + card.id() + "/active")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new SetActiveRequest(false))))
                .andExpect(status().isNoContent());

        mockMvc.perform(delete("/cards/" + card.id())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/cards/" + card.id())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }
}
