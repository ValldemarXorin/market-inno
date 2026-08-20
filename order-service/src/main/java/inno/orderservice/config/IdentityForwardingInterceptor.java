package inno.orderservice.config;

import inno.orderservice.security.CurrentUser;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.IOException;

public class IdentityForwardingInterceptor implements ClientHttpRequestInterceptor {

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletRequestAttributes) {
            var incoming = servletRequestAttributes.getRequest();
            if (!request.getHeaders().containsKey(CurrentUser.USER_ID_HEADER)) {
                String userId = incoming.getHeader(CurrentUser.USER_ID_HEADER);
                if (userId != null) {
                    request.getHeaders().set(CurrentUser.USER_ID_HEADER, userId);
                }
            }
            if (!request.getHeaders().containsKey(CurrentUser.USER_ROLE_HEADER)) {
                String role = incoming.getHeader(CurrentUser.USER_ROLE_HEADER);
                if (role != null) {
                    request.getHeaders().set(CurrentUser.USER_ROLE_HEADER, role);
                }
            }
        }
        return execution.execute(request, body);
    }
}