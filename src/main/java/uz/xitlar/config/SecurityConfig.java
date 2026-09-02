package uz.xitlar.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import uz.xitlar.filter.JwtAuthenticationFilter;
import uz.xitlar.security.OAuth2AuthenticationFailureHandler;
import uz.xitlar.security.OAuth2AuthenticationSuccessHandler;
import jakarta.servlet.http.HttpServletResponse;

import java.util.Arrays;
import java.util.List;

import static uz.xitlar.enums.Role.ADMIN;
import static uz.xitlar.enums.Role.MODERATOR;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final AuthenticationProvider authenticationProvider;
    private final JwtAuthenticationFilter jwtFilter;
    private final OAuth2AuthenticationSuccessHandler oAuth2AuthenticationSuccessHandler;
    private final OAuth2AuthenticationFailureHandler oAuth2AuthenticationFailureHandler;

    @Value("${spring.security.oauth2.client.registration.google.client-id:google-client-id-placeholder}")
    private String googleClientId;

    @Value("${spring.security.oauth2.client.registration.google.client-secret:google-client-secret-placeholder}")
    private String googleClientSecret;

    @Value("${cors.allowed-origins:http://localhost:1301,http://localhost:4321}")
    private String corsAllowedOrigins;

    @Bean
    public ClientRegistrationRepository clientRegistrationRepository() {
        ClientRegistration googleRegistration = CommonOAuth2Provider.GOOGLE.getBuilder("google")
                .clientId(googleClientId != null && !googleClientId.isBlank() ? googleClientId : "google-client-id-placeholder")
                .clientSecret(googleClientSecret != null && !googleClientSecret.isBlank() ? googleClientSecret : "google-client-secret-placeholder")
                .scope("openid", "profile", "email")
                .build();
        return new InMemoryClientRegistrationRepository(googleRegistration);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests( (authorize)->authorize
                        .requestMatchers(
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/swagger-resources/**",
                                "/webjars/**",
                                "/error"
                        ).permitAll()
                        .requestMatchers("/oauth2/**", "/login/oauth2/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/hello").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/sign-in").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/sign-up").permitAll()
                        .requestMatchers(HttpMethod.PUT, "/api/v1/users/me/password").authenticated()
                        .requestMatchers("/api/v1/moderators", "/api/v1/moderators/**").hasRole(ADMIN.name())
                        .requestMatchers(HttpMethod.PUT, "/api/v1/users/*/role").hasRole(ADMIN.name())
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/users/*").hasRole(ADMIN.name())
                        .requestMatchers(HttpMethod.PUT, "/api/v1/users/*/password").hasRole(ADMIN.name())
                        .requestMatchers(HttpMethod.GET, "/api/v1/users/me").authenticated()
                        .requestMatchers("/api/v1/users", "/api/v1/users/**").hasAnyRole(MODERATOR.name(), ADMIN.name())
                        .requestMatchers(HttpMethod.GET, "/api/v1/images/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/images").hasAnyRole(MODERATOR.name(), ADMIN.name())
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/images/**").hasAnyRole(MODERATOR.name(), ADMIN.name())
                        .requestMatchers(HttpMethod.GET, "/api/v1/artists", "/api/v1/artists/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/artists/*/vote").authenticated()
                        .requestMatchers("/api/v1/artists", "/api/v1/artists/**").hasAnyRole(MODERATOR.name(), ADMIN.name())
                        .requestMatchers(HttpMethod.GET, "/api/v1/musics/liked").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/musics/*/like").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/musics/*/dislike").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/musics", "/api/v1/musics/**").permitAll()
                        .requestMatchers("/api/v1/musics", "/api/v1/musics/**").hasAnyRole(MODERATOR.name(), ADMIN.name())
                        .requestMatchers(HttpMethod.GET, "/api/v1/lyrics", "/api/v1/lyrics/**").permitAll()
                        .requestMatchers("/api/v1/lyrics", "/api/v1/lyrics/**").hasAnyRole(MODERATOR.name(), ADMIN.name())
                        .requestMatchers(HttpMethod.GET, "/api/v1/comments", "/api/v1/comments/**").permitAll()
                        .requestMatchers("/api/v1/comments", "/api/v1/comments/**").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/playlists", "/api/v1/playlists/**").permitAll()
                        .requestMatchers("/api/v1/playlists", "/api/v1/playlists/**").authenticated()
                        .anyRequest().authenticated()
                        )
                .oauth2Login(oauth2 -> oauth2
                        .clientRegistrationRepository(clientRegistrationRepository())
                        .successHandler(oAuth2AuthenticationSuccessHandler)
                        .failureHandler(oAuth2AuthenticationFailureHandler)
                )
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.setContentType("application/json");
                            response.setCharacterEncoding("UTF-8");
                            response.getWriter().write("{\"success\":false,\"message\":\"Authentication required\",\"data\":null}");
                        })
                )
                .sessionManagement((session)->session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authenticationProvider(authenticationProvider)
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .cors(Customizer.withDefaults());

        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        if (corsAllowedOrigins != null && !corsAllowedOrigins.isBlank()) {
            List<String> origins = Arrays.stream(corsAllowedOrigins.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();
            configuration.setAllowedOrigins(origins);
        } else {
            configuration.setAllowedOrigins(List.of("http://localhost:3000", "http://localhost:1301", "http://localhost:4321"));
        }
        configuration.setAllowCredentials(true);
        configuration.setAllowedHeaders(Arrays.asList("Access-Control-Allow-Headers", "Access-Control-Allow-Origin", "Access-Control-Request-Method", "Access-Control-Request-Headers", "Origin", "Cache-Control", "Content-Type", "Authorization"));
        configuration.setAllowedMethods(Arrays.asList("DELETE", "GET", "POST", "PATCH", "PUT"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
