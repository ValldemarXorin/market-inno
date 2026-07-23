package inno.user_service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redis.testcontainers.RedisContainer;
import inno.user_service.dto.request.CreateUserRequest;
import inno.user_service.dto.response.UserResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
public class UserControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());

    @Container
    static PostgreSQLContainer<?> myPostgres = new PostgreSQLContainer<>("postgres:17")
            .withDatabaseName("userservice")
            .withUsername("postgres")
            .withPassword("postgres");

    @Container
    static RedisContainer myRedis = new RedisContainer(DockerImageName.parse("redis:7"));

    @DynamicPropertySource
    static void setupProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", myPostgres::getJdbcUrl);
        registry.add("spring.datasource.username", myPostgres::getUsername);
        registry.add("spring.datasource.password", myPostgres::getPassword);

        registry.add("spring.data.redis.host", myRedis::getHost);
        registry.add("spring.data.redis.port", () -> myRedis.getMappedPort(6379));

        registry.add("spring.jpa.hibernate.ddl-auto", () -> "update");
    }

    @Test
    public void shouldVerifyFullUserFlowWithRedisCaching() throws Exception {
        CreateUserRequest requestDto = new CreateUserRequest(
                "Masha",
                "Mashina",
                LocalDate.of(1998, 10, 12),
                "masha@yandex.ru"
        );

        String responseContent = mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isCreated()) // Ждем статус 201 Created
                .andExpect(jsonPath("$.username").value("Masha"))
                .andExpect(jsonPath("$.email").value("masha@yandex.ru"))
                .andReturn().getResponse().getContentAsString();

        UserResponse createdUser = objectMapper.readValue(responseContent, UserResponse.class);
        String idFromDb = createdUser.id().toString();

        mockMvc.perform(get("/users/" + idFromDb))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(idFromDb))
                .andExpect(jsonPath("$.username").value("Masha"));

        mockMvc.perform(get("/users/" + idFromDb))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(idFromDb));

        mockMvc.perform(get("/users/00000000-0000-0000-0000-000000000000"))
                .andExpect(status().isNotFound());
    }
}
