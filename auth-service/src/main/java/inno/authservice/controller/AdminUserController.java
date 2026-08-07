package inno.authservice.controller;

import inno.authservice.dto.response.UserCredentialsResponse;
import inno.authservice.service.UserCredentialsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final UserCredentialsService userCredentialsService;

    @GetMapping
    public List<UserCredentialsResponse> getAll() {
        return userCredentialsService.getAll();
    }
}
