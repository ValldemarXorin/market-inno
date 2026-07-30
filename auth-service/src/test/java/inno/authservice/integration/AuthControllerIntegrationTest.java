package inno.authservice.integration;

import inno.authservice.dto.request.LoginRequest;
import inno.authservice.dto.request.RefreshRequest;
import inno.authservice.dto.request.RegisterRequest;
import inno.authservice.dto.response.TokenPairResponse;
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
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
public class AuthControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Container
    static PostgreSQLContainer<?> myPostgres = new PostgreSQLContainer<>("postgres:17")
            .withDatabaseName("authservice")
            .withUsername("postgres")
            .withPassword("postgres");

    @DynamicPropertySource
    static void setupProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", myPostgres::getJdbcUrl);
        registry.add("spring.datasource.username", myPostgres::getUsername);
        registry.add("spring.datasource.password", myPostgres::getPassword);

        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");

        registry.add("auth.jwt.secret", () -> "integration-test-secret-at-least-32-bytes-long!!");
    }

    @Test
    public void shouldVerifyFullAuthFlowWithTokenRotationAndReuseDetection() throws Exception {
        RegisterRequest registerRequest = new RegisterRequest("integration_user", "password123");

        String registerResponseContent = mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.login").value("integration_user"))
                .andReturn().getResponse().getContentAsString();

        LoginRequest loginRequest = new LoginRequest("integration_user", "password123");

        String loginResponseContent = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", notNullValue()))
                .andExpect(jsonPath("$.refreshToken", notNullValue()))
                .andReturn().getResponse().getContentAsString();

        TokenPairResponse tokens = objectMapper.readValue(loginResponseContent, TokenPairResponse.class);

        mockMvc.perform(post("/auth/validate")
                        .header("Authorization", "Bearer " + tokens.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("USER"));

        String refreshResponseContent = mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshRequest(tokens.refreshToken()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", notNullValue()))
                .andReturn().getResponse().getContentAsString();

        TokenPairResponse rotatedTokens = objectMapper.readValue(refreshResponseContent, TokenPairResponse.class);

        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshRequest(tokens.refreshToken()))))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshRequest(rotatedTokens.refreshToken()))))
                .andExpect(status().isOk());
    }

    @Test
    public void shouldReturnConflict_whenRegisteringDuplicateLogin() throws Exception {
        RegisterRequest request = new RegisterRequest("duplicate_user", "password123");

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Login already taken: duplicate_user"));
    }

    @Test
    public void shouldReturnBadRequestWithFieldErrors_whenPasswordTooShort() throws Exception {
        RegisterRequest request = new RegisterRequest("short_pwd_user", "1234567");

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("password"));
    }

    @Test
    public void shouldReturnUnauthorized_whenPasswordIncorrect() throws Exception {
        RegisterRequest registerRequest = new RegisterRequest("wrong_pwd_user", "password123");
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated());

        LoginRequest loginRequest = new LoginRequest("wrong_pwd_user", "wrong-password");
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    public void shouldReturnUnauthorized_whenAccessTokenIsInvalid() throws Exception {
        mockMvc.perform(post("/auth/validate")
                        .header("Authorization", "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized());
    }
}
