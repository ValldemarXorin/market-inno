package inno.authservice.controller;

import inno.authservice.dto.request.LoginRequest;
import inno.authservice.dto.request.RefreshRequest;
import inno.authservice.dto.request.RegisterRequest;
import inno.authservice.dto.response.RegisterResponse;
import inno.authservice.dto.response.TokenPairResponse;
import inno.authservice.dto.response.TokenValidationResponse;
import inno.authservice.entity.UserCredentials;
import inno.authservice.exception.custom_exception.InvalidTokenException;
import inno.authservice.service.AuthService;
import inno.authservice.service.UserCredentialsService;
import inno.authservice.util.TokenExtractor;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String BEARER_PREFIX = "Bearer ";

    private final AuthService authService;
    private final UserCredentialsService userCredentialsService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public RegisterResponse register(@Valid @RequestBody RegisterRequest request) {
        RegisterResponse created = userCredentialsService.register(request.login(), request.password());
        return new RegisterResponse(created.id(), created.login());
    }

    @PostMapping("/login")
    public TokenPairResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request.login(), request.password());
    }

    @PostMapping("/refresh")
    public TokenPairResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request.refreshToken());
    }

    @PostMapping("/validate")
    public TokenValidationResponse validate(@RequestHeader("Authorization") String authorizationHeader) {
        return authService.validate(TokenExtractor.extractBearerToken(authorizationHeader));
    }
}
