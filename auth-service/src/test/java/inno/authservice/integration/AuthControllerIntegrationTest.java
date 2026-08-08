package inno.authservice.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.time.Month;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Transactional
class AuthControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:17")
                    .withDatabaseName("authservice")
                    .withUsername("postgres")
                    .withPassword("postgres");


    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);

        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("auth.jwt.secret",
                () -> "integration-test-secret-at-least-32-bytes-long!!");
    }


    @Test
    void shouldVerifyFullAuthFlowWithTokenRotationAndReuseDetection() throws Exception {

        RegisterRequest register =
                registerRequest("integration_user", "password123");

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(register)))
                .andDo(print())
                .andExpect(status().isCreated());


        LoginRequest login =
                new LoginRequest("integration_user", "password123");


        String loginResponse =
                mockMvc.perform(post("/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(login)))
                        .andDo(print())
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.accessToken",
                                notNullValue()))
                        .andExpect(jsonPath("$.refreshToken",
                                notNullValue()))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();


        TokenPairResponse tokens =
                objectMapper.readValue(loginResponse, TokenPairResponse.class);


        mockMvc.perform(post("/auth/validate")
                        .header("Authorization",
                                "Bearer " + tokens.accessToken()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("USER"));


        String refreshResponse =
                mockMvc.perform(post("/auth/refresh")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(
                                        new RefreshRequest(tokens.refreshToken())
                                )))
                        .andDo(print())
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.accessToken",
                                notNullValue()))
                        .andExpect(jsonPath("$.refreshToken",
                                notNullValue()))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();


        TokenPairResponse rotated =
                objectMapper.readValue(refreshResponse,
                        TokenPairResponse.class);


        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RefreshRequest(tokens.refreshToken())
                        )))
                .andDo(print())
                .andExpect(status().isUnauthorized());


        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RefreshRequest(rotated.refreshToken())
                        )))
                .andDo(print())
                .andExpect(status().isOk());
    }


    @Test
    void shouldReturnConflict_whenRegisteringDuplicateLogin() throws Exception {

        RegisterRequest request =
                registerRequest("duplicate_user", "password123");


        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isCreated());


        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isConflict());
    }


    @Test
    void shouldReturnBadRequestWithFieldErrors_whenPasswordTooShort()
            throws Exception {

        RegisterRequest request =
                registerRequest("short_pwd_user", "1234567");


        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field")
                        .value("password"));
    }


    @Test
    void shouldReturnUnauthorized_whenPasswordIncorrect()
            throws Exception {

        RegisterRequest register =
                registerRequest("wrong_pwd_user", "password123");


        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(register)))
                .andDo(print())
                .andExpect(status().isCreated());


        LoginRequest login =
                new LoginRequest("wrong_pwd_user",
                        "wrong-password");


        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andDo(print())
                .andExpect(status().isUnauthorized());
    }


    @Test
    void shouldReturnUnauthorized_whenAccessTokenIsInvalid()
            throws Exception {

        mockMvc.perform(post("/auth/validate")
                        .header("Authorization",
                                "Bearer not-a-real-token"))
                .andDo(print())
                .andExpect(status().isUnauthorized());
    }

    private RegisterRequest registerRequest(String login, String password) {
        return new RegisterRequest(
                login, password, "Ivan", "Ivanov",
                LocalDate.of(2000, Month.JANUARY, 1), login + "@yandex.ru");
    }
}