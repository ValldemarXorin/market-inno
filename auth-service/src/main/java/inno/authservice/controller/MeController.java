package inno.authservice.controller;

import inno.authservice.dto.response.UserCredentialsResponse;
import inno.authservice.service.UserCredentialsService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class MeController {

    private final UserCredentialsService userCredentialsService;

    @GetMapping("/me")
    public UserCredentialsResponse me(@AuthenticationPrincipal UUID userId) {
        return userCredentialsService.getById(userId);
    }
}
