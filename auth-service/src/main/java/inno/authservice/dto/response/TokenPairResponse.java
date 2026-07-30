package inno.authservice.dto.response;

public record TokenPairResponse(
        String accessToken,
        String refreshToken
) {}
