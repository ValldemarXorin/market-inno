package inno.user_service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redis.testcontainers.RedisContainer;
import inno.user_service.dto.request.CreateUserRequest;
import inno.user_service.dto.request.SetActiveRequest;
import inno.user_service.dto.response.UserResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDate;
import java.time.Month;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class SecurityIntegrationTest {

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
    static RedisContainer redis = new RedisContainer(DockerImageName.parse("redis:7"));

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
    void unauthenticatedRequestsShouldBeRejected() throws Exception {
        mockMvc.perform(get("/users/00000000-0000-0000-0000-000000000000"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/users"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminShouldAccessProtectedEndpoints() throws Exception {
        String token = TestTokens.adminToken();
        UserResponse user = createUser();

        mockMvc.perform(get("/users")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/users/" + user.id())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/users/" + user.id() + "/active")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SetActiveRequest(false))))
                .andExpect(status().isNoContent());
    }

    @Test
    void userShouldAccessOwnResources() throws Exception {
        UserResponse user = createUser();
        String token = TestTokens.userToken(user.id());

        mockMvc.perform(get("/users/" + user.id())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void userShouldBeDeniedAccessToAdminEndpoints() throws Exception {
        UserResponse user = createUser();
        String token = TestTokens.userToken(user.id());

        mockMvc.perform(get("/users")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/users/" + user.id() + "/active")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SetActiveRequest(false))))
                .andExpect(status().isForbidden());
    }

    @Test
    void userShouldBeDeniedAccessToAnotherUsersResources() throws Exception {
        UserResponse user = createUser();
        UserResponse other = createUser();
        String token = TestTokens.userToken(user.id());

        mockMvc.perform(get("/users/" + other.id())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    private UserResponse createUser() throws Exception {
        CreateUserRequest request = new CreateUserRequest(
                "Masha",
                "Mashina",
                LocalDate.of(1998, Month.OCTOBER, 12),
                "masha-security-" + UUID.randomUUID() + "@yandex.ru"
        );

        String content = mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return objectMapper.readValue(content, UserResponse.class);
    }
}
