package uz.xitlar.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import uz.xitlar.dto.common.ResponseApi;
import uz.xitlar.exception.OAuthAuthenticationException;
import uz.xitlar.service.OAuthAuthenticationService;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2AuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private final OAuthAuthenticationService oAuthAuthenticationService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        if (authentication.getPrincipal() instanceof OidcUser oidcUser) {
            try {
                String token = oAuthAuthenticationService.authenticateGoogle(oidcUser);

                response.setStatus(HttpStatus.OK.value());
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.setCharacterEncoding("UTF-8");

                ResponseApi<String> responseApi = ResponseApi.<String>builder()
                        .success(true)
                        .message("Successfully authenticated with Google")
                        .data(token)
                        .build();

                response.getWriter().write(objectMapper.writeValueAsString(responseApi));
                response.getWriter().flush();
            } catch (OAuthAuthenticationException e) {
                log.warn("OAuth authentication rejected: {}", e.getMessage());
                response.setStatus(HttpStatus.BAD_REQUEST.value());
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.setCharacterEncoding("UTF-8");

                ResponseApi<Void> responseApi = ResponseApi.<Void>builder()
                        .success(false)
                        .message(e.getMessage())
                        .build();

                response.getWriter().write(objectMapper.writeValueAsString(responseApi));
                response.getWriter().flush();
            } catch (Exception e) {
                log.error("Unexpected error during OAuth authentication", e);
                response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.setCharacterEncoding("UTF-8");

                ResponseApi<Void> responseApi = ResponseApi.<Void>builder()
                        .success(false)
                        .message("OAuth authentication processing error")
                        .build();

                response.getWriter().write(objectMapper.writeValueAsString(responseApi));
                response.getWriter().flush();
            }
        } else {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");

            ResponseApi<Void> responseApi = ResponseApi.<Void>builder()
                    .success(false)
                    .message("Unsupported authentication principal")
                    .build();

            response.getWriter().write(objectMapper.writeValueAsString(responseApi));
            response.getWriter().flush();
        }
    }
}
