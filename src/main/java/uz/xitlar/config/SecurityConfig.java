package uz.xitlar.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import uz.xitlar.filter.JwtAuthenticationFilter;

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
                                "/webjars/**"
                        ).permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/hello").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/sign-in").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/sign-up").permitAll()
                        .requestMatchers(HttpMethod.PUT, "/api/v1/users/me/password").authenticated()
                        .requestMatchers("/api/v1/moderators", "/api/v1/moderators/**").hasRole(ADMIN.name())
                        .requestMatchers(HttpMethod.PUT, "/api/v1/users/*/role").hasRole(ADMIN.name())
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/users/*").hasRole(ADMIN.name())
                        .requestMatchers(HttpMethod.PUT, "/api/v1/users/*/password").hasRole(ADMIN.name())
                        .requestMatchers("/api/v1/users", "/api/v1/users/**").hasAnyRole(MODERATOR.name(), ADMIN.name())
                        .requestMatchers(HttpMethod.GET, "/api/v1/images/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/images").hasAnyRole(MODERATOR.name(), ADMIN.name())
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/images/**").hasAnyRole(MODERATOR.name(), ADMIN.name())
                        .requestMatchers(HttpMethod.GET, "/api/v1/artists", "/api/v1/artists/**").permitAll()
                        .requestMatchers("/api/v1/artists", "/api/v1/artists/**").hasAnyRole(MODERATOR.name(), ADMIN.name())
                        .requestMatchers(HttpMethod.GET, "/api/v1/musics", "/api/v1/musics/**").permitAll()
                        .requestMatchers("/api/v1/musics", "/api/v1/musics/**").hasAnyRole(MODERATOR.name(), ADMIN.name())
                        .requestMatchers(HttpMethod.GET, "/api/v1/lyrics", "/api/v1/lyrics/**").permitAll()
                        .requestMatchers("/api/v1/lyrics", "/api/v1/lyrics/**").hasAnyRole(MODERATOR.name(), ADMIN.name())
                        .requestMatchers(HttpMethod.GET, "/api/v1/comments", "/api/v1/comments/**").permitAll()
                        .requestMatchers("/api/v1/comments", "/api/v1/comments/**").authenticated()
                        .anyRequest().authenticated()
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
        configuration.setAllowedOrigins(List.of("http://localhost:1301", "http://localhost:4321"));
        configuration.setAllowCredentials(true);
        configuration.setAllowedHeaders(Arrays.asList("Access-Control-Allow-Headers", "Access-Control-Allow-Origin", "Access-Control-Request-Method", "Access-Control-Request-Headers", "Origin", "Cache-Control", "Content-Type", "Authorization"));
        configuration.setAllowedMethods(Arrays.asList("DELETE", "GET", "POST", "PATCH", "PUT"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
