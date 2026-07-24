package inno.user_service.service;

import inno.user_service.dao.repository.UserRepository;
import inno.user_service.dto.request.CreateUserRequest;
import inno.user_service.dto.request.UpdateUserRequest;
import inno.user_service.dto.response.UserResponse;
import inno.user_service.entity.User;
import inno.user_service.exception.custom_exception.EmailAlreadyExistsException;
import inno.user_service.exception.custom_exception.UserNotFoundException;
import inno.user_service.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.time.Month;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class UserServiceTest {
    @Mock
    private UserRepository userRepository;

    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private UserService userService;

    private UUID testId;
    private User testUser;
    private UserResponse testUserResponse;
    private String testNameVova = "vova";
    private String testSurnameKhorin = "khorin";
    private String testEmailVova = "vova@gmail.com";
    private LocalDate testDateVova = LocalDate.of(2006, Month.JANUARY, 20);

    @BeforeEach
    public void initData() {
        testId = UUID.randomUUID();

        testUser = new User();
        testUser.setId(testId);
        testUser.setUsername(testNameVova);
        testUser.setSurname(testSurnameKhorin);
        testUser.setEmail(testEmailVova);

        testUserResponse = new UserResponse(testId,
                testNameVova, testSurnameKhorin,
                testDateVova, testEmailVova,
                true, null, null);
    }

    @Test
    public void shouldCreateUserSuccessfully() {
        CreateUserRequest createUserRequest = new CreateUserRequest(
                testNameVova, testSurnameKhorin,
                testDateVova, testEmailVova);

        when(userRepository.existsByEmail(testEmailVova)).thenReturn(false);
        when(userMapper.toEntity(createUserRequest)).thenReturn(testUser);
        when(userRepository.save(testUser)).thenReturn(testUser);
        when(userMapper.toResponse(testUser)).thenReturn(testUserResponse);

        UserResponse result = userService.createUser(createUserRequest);

        assertNotNull(result);
        assertEquals(testNameVova, result.username());
        assertEquals(testId, result.id());
    }

    @Test
    public void shouldThrowExceptionWhenUserEmailAlreadyExists() {
        CreateUserRequest createUserRequest = new CreateUserRequest(
                testNameVova, testSurnameKhorin,
                testDateVova, testEmailVova);

        doReturn(true).when(userRepository).existsByEmail(testEmailVova);
        assertThrows(EmailAlreadyExistsException.class, () -> {
            userService.createUser(createUserRequest);
        });

        verify(userRepository, never()).save(any());
    }

    @Test
    public void shouldGetUserByIdSuccessfully() {
        doReturn(Optional.of(testUser)).when(userRepository).findById(testId);
        doReturn(testUserResponse).when(userMapper).toResponse(testUser);

        UserResponse result = userService.getUserById(testId);

        assertNotNull(result);
        assertEquals(testId, result.id());
    }

    @Test
    public void shouldThrowExceptionWhenUserNotFoundById() {
        doReturn(Optional.empty()).when(userRepository).findById(testId);

        assertThrows(UserNotFoundException.class, () -> {
            userService.getUserById(testId);
        });
    }

    @Test
    public void shouldGetAllUsersWithFiltersAndPagination() {
        List<User> userList = new ArrayList<>();
        userList.add(testUser);
        Page<User> userPage = new PageImpl<>(userList);

        doReturn(userPage).when(userRepository).findAll(any(Specification.class), any(Pageable.class));
        doReturn(testUserResponse).when(userMapper).toResponse(testUser);

        Page<UserResponse> resultPage = userService.getAllUsers(testNameVova, testSurnameKhorin, Pageable.unpaged());

        assertNotNull(resultPage);
        assertEquals(1, resultPage.getTotalElements());
    }

    @Test
    public void shouldUpdateUserFieldsSuccessfully() {
        UpdateUserRequest updateUserRequest = new UpdateUserRequest(
                "updatedName", "updatedSurname", testDateVova, "updated@gmail.com");

        doReturn(1).when(userRepository).updateUserDetails(any(), any(), any(), any(), any());
        doReturn(Optional.of(testUser)).when(userRepository).findById(testId);
        doReturn(testUserResponse).when(userMapper).toResponse(testUser);

        UserResponse result = userService.updateUser(testId, updateUserRequest);

        assertNotNull(result);
    }

    @Test
    public void shouldThrowExceptionWhenUpdatingNonExistentUser() {
        UpdateUserRequest updateUserRequest = new UpdateUserRequest(
                "updatedName", "updatedSurname", testDateVova, "updated@gmail.com");

        doReturn(0).when(userRepository).updateUserDetails(any(), any(), any(), any(), any());

        assertThrows(UserNotFoundException.class, () -> {
            userService.updateUser(testId, updateUserRequest);
        });
    }

    @Test
    public void shouldSetUserActiveStatusSuccessfully() {
        doReturn(1).when(userRepository).setActiveNative(testId, false);

        assertDoesNotThrow(() -> {
            userService.setUserActive(testId, false);
        });
    }

    @Test
    public void shouldDeleteUserSuccessfully() {
        doReturn(true).when(userRepository).existsById(testId);

        assertDoesNotThrow(() -> {
            userService.deleteUser(testId);
        });

        verify(userRepository, times(1)).deleteById(testId);
    }

    @Test
    public void shouldCheckUserCacheWorkSuccessfully() {
        doReturn(Optional.of(testUser)).when(userRepository).findById(testId);
        doReturn(testUserResponse).when(userMapper).toResponse(testUser);

        UserResponse firstCall = userService.getUserById(testId);

        verify(userRepository, times(1)).findById(testId);
        assertNotNull(firstCall);
    }
}
