package inno.user_service.controller;

import inno.user_service.dto.request.CreateUserRequest;
import inno.user_service.dto.request.SetActiveRequest;
import inno.user_service.dto.request.UpdateUserRequest;
import inno.user_service.dto.response.UserResponse;
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

    @PostMapping
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody CreateUserRequest createUserRequest) {
        UserResponse userResponseCreated = userService.createUser(createUserRequest);
        return ResponseEntity
                .created(URI.create("/api/v1/users/" + userResponseCreated.id()))
                .body(userResponseCreated);
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> getUserById(@PathVariable UUID id) {
        return ResponseEntity.ok(userService.getUserById(id));
    }

    @GetMapping
    public ResponseEntity<Page<UserResponse>> getAllUsers(
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String surname,
            Pageable pageable) {
        return ResponseEntity.ok(userService.getAllUsers(username, surname, pageable));
    }

    @PutMapping("/{id}")
    public ResponseEntity<UserResponse> updateUser(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateUserRequest updateUserRequest) {
        return ResponseEntity.ok(userService.updateUser(id, updateUserRequest));
    }

    @PatchMapping("/{id}/active")
    public ResponseEntity<Void> setUserActive(
            @PathVariable UUID id,
            @Valid @RequestBody SetActiveRequest setActiveRequest) {
        userService.setUserActive(id, setActiveRequest.active());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable UUID id) {
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }
}
