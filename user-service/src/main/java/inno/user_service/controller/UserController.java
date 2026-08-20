package inno.user_service.controller;

import inno.user_service.dto.request.CreateUserRequest;
import inno.user_service.dto.request.SetActiveRequest;
import inno.user_service.dto.request.UpdateUserRequest;
import inno.user_service.dto.response.UserResponse;
import inno.user_service.security.util.ResourceSecurityService;
import inno.user_service.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;
    private final ResourceSecurityService resourceSecurity;

    @PostMapping
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody CreateUserRequest createUserRequest) {
        UserResponse userResponseCreated = userService.createUser(createUserRequest);
        return ResponseEntity
                .created(URI.create("/api/v1/users/" + userResponseCreated.id()))
                .body(userResponseCreated);
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> getUserById(@PathVariable UUID id) {
        resourceSecurity.requireAdminOrSelf(id);
        return ResponseEntity.ok(userService.getUserById(id));
    }

    @GetMapping("/by-email/{email}")
    public ResponseEntity<UserResponse> getUserByEmail(@PathVariable String email) {
        resourceSecurity.requireAdmin();
        return ResponseEntity.ok(userService.getUserByEmail(email));
    }

    @GetMapping
    public ResponseEntity<Page<UserResponse>> getAllUsers(
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String surname,
            Pageable pageable) {
        resourceSecurity.requireAdmin();
        return ResponseEntity.ok(userService.getAllUsers(username, surname, pageable));
    }

    @PutMapping("/{id}")
    public ResponseEntity<UserResponse> updateUser(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateUserRequest updateUserRequest) {
        resourceSecurity.requireAdminOrSelf(id);
        return ResponseEntity.ok(userService.updateUser(id, updateUserRequest));
    }

    @PatchMapping("/{id}/active")
    public ResponseEntity<Void> setUserActive(
            @PathVariable UUID id,
            @Valid @RequestBody SetActiveRequest setActiveRequest) {
        resourceSecurity.requireAdmin();
        userService.setUserActive(id, setActiveRequest.active());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable UUID id) {
        resourceSecurity.requireAdmin();
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }
}
