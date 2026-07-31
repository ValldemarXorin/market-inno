package inno.authservice.mapper;

import inno.authservice.dto.response.RegisterResponse;
import inno.authservice.dto.response.UserCredentialsResponse;
import inno.authservice.entity.UserCredentials;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserCredentialsMapper {

    UserCredentialsResponse toResponse(UserCredentials entity);

    RegisterResponse toRegisterResponse(UserCredentials entity);
}
