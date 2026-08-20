package inno.authservice.controller;

import inno.authservice.dto.response.UserCredentialsResponse;
import inno.authservice.security.CurrentUser;
import inno.authservice.service.UserCredentialsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class MeController {

    private final UserCredentialsService userCredentialsService;
    private final CurrentUser currentUser;

    @GetMapping("/me")
    public UserCredentialsResponse me() {
        return userCredentialsService.getById(currentUser.id());
    }
}