package uz.xitlar.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import uz.xitlar.entity.OAuthAccount;
import uz.xitlar.entity.User;
import uz.xitlar.enums.OAuthProvider;
import uz.xitlar.enums.Role;
import uz.xitlar.exception.OAuthAuthenticationException;
import uz.xitlar.repository.OAuthAccountRepository;
import uz.xitlar.repository.UserRepository;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OAuthAuthenticationServiceTest {

    @Mock
    private OAuthAccountRepository oauthAccountRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private OAuthAuthenticationService oAuthAuthenticationService;

    private OidcUser createMockOidcUser(String sub, String email, Boolean emailVerified, String givenName, String familyName) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", sub);
        if (email != null) claims.put("email", email);
        if (emailVerified != null) claims.put("email_verified", emailVerified);
        if (givenName != null) claims.put("given_name", givenName);
        if (familyName != null) claims.put("family_name", familyName);
        claims.put("name", (givenName != null ? givenName : "") + " " + (familyName != null ? familyName : ""));

        OidcIdToken idToken = new OidcIdToken("token-val", Instant.now(), Instant.now().plusSeconds(3600), claims);
        OidcUserInfo userInfo = new OidcUserInfo(claims);

        return new DefaultOidcUser(Collections.emptyList(), idToken, userInfo, "sub");
    }

    @Test
    @DisplayName("Existing Google OAuthAccount logs in successfully and preserves user role")
    void authenticateGoogle_ExistingAccount_Success() {
        String sub = "google-sub-12345";
        String email = "existing@gmail.com";
        OidcUser oidcUser = createMockOidcUser(sub, email, true, "Existing", "User");

        User existingUser = User.builder()
                .username("existing_user")
                .email(email)
                .role(Role.MODERATOR)
                .password("hashed_password")
                .firstName("Existing")
                .lastName("User")
                .build();

        OAuthAccount oauthAccount = OAuthAccount.builder()
                .user(existingUser)
                .provider(OAuthProvider.GOOGLE)
                .providerUserId(sub)
                .build();

        when(oauthAccountRepository.findByProviderAndProviderUserId(OAuthProvider.GOOGLE, sub))
                .thenReturn(Optional.of(oauthAccount));
        when(jwtService.generateToken(existingUser)).thenReturn("jwt.token.here");

        String token = oAuthAuthenticationService.authenticateGoogle(oidcUser);

        assertEquals("jwt.token.here", token);
        assertEquals(Role.MODERATOR, existingUser.getRole(), "Existing user role must be preserved");
        assertEquals("existing_user", existingUser.getUsername(), "Existing username must not be modified");
        assertEquals("hashed_password", existingUser.getPassword(), "Existing password must not be modified");
        verify(userRepository, never()).save(any());
        verify(oauthAccountRepository, never()).save(any());
    }

    @Test
    @DisplayName("New Google User creates User (role USER) and OAuthAccount, returns JWT")
    void authenticateGoogle_NewUser_Success() {
        String sub = "google-sub-99999";
        String email = "newuser@gmail.com";
        OidcUser oidcUser = createMockOidcUser(sub, email, true, "Alice", "Smith");

        when(oauthAccountRepository.findByProviderAndProviderUserId(OAuthProvider.GOOGLE, sub))
                .thenReturn(Optional.empty());
        when(userRepository.existsByEmail(email)).thenReturn(false);
        when(userRepository.existsByUsername("newuser")).thenReturn(false);

        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            return user;
        });

        when(jwtService.generateToken(any(User.class))).thenReturn("new.user.jwt.token");

        String token = oAuthAuthenticationService.authenticateGoogle(oidcUser);

        assertEquals("new.user.jwt.token", token);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();

        assertEquals("Alice", savedUser.getFirstName());
        assertEquals("Smith", savedUser.getLastName());
        assertEquals("newuser", savedUser.getUsername());
        assertEquals(email, savedUser.getEmail());
        assertNull(savedUser.getPassword(), "Password should be null for OAuth-only accounts");
        assertEquals(Role.USER, savedUser.getRole(), "New OAuth users must receive Role.USER");

        ArgumentCaptor<OAuthAccount> accountCaptor = ArgumentCaptor.forClass(OAuthAccount.class);
        verify(oauthAccountRepository).save(accountCaptor.capture());
        OAuthAccount savedAccount = accountCaptor.getValue();

        assertEquals(OAuthProvider.GOOGLE, savedAccount.getProvider());
        assertEquals(sub, savedAccount.getProviderUserId());
        assertEquals(savedUser, savedAccount.getUser());
    }

    @Test
    @DisplayName("Missing Google sub throws OAuthAuthenticationException")
    void authenticateGoogle_MissingSub_ThrowsException() {
        OidcUser oidcUser = mock(OidcUser.class);
        when(oidcUser.getSubject()).thenReturn(null);
        when(oidcUser.getAttribute("sub")).thenReturn(null);

        OAuthAuthenticationException exception = assertThrows(
                OAuthAuthenticationException.class,
                () -> oAuthAuthenticationService.authenticateGoogle(oidcUser)
        );

        assertTrue(exception.getMessage().contains("Missing Google subject identifier"));
        verifyNoInteractions(userRepository);
        verifyNoInteractions(oauthAccountRepository);
    }

    @Test
    @DisplayName("Missing Google email throws OAuthAuthenticationException")
    void authenticateGoogle_MissingEmail_ThrowsException() {
        OidcUser oidcUser = createMockOidcUser("sub-123", null, true, "No", "Email");

        OAuthAuthenticationException exception = assertThrows(
                OAuthAuthenticationException.class,
                () -> oAuthAuthenticationService.authenticateGoogle(oidcUser)
        );

        assertTrue(exception.getMessage().contains("Missing Google email"));
        verifyNoInteractions(userRepository);
    }

    @Test
    @DisplayName("Unverified Google email throws OAuthAuthenticationException")
    void authenticateGoogle_UnverifiedEmail_ThrowsException() {
        OidcUser oidcUser = createMockOidcUser("sub-123", "unverified@gmail.com", false, "Unverified", "Email");

        OAuthAuthenticationException exception = assertThrows(
                OAuthAuthenticationException.class,
                () -> oAuthAuthenticationService.authenticateGoogle(oidcUser)
        );

        assertTrue(exception.getMessage().contains("Google email is not verified"));
        verifyNoInteractions(userRepository);
    }

    @Test
    @DisplayName("Existing local account with matching email prevents automatic takeover")
    void authenticateGoogle_ExistingEmail_PreventsAccountTakeover() {
        String sub = "sub-attacker-123";
        String email = "victim@gmail.com";
        OidcUser oidcUser = createMockOidcUser(sub, email, true, "Attacker", "Name");

        when(oauthAccountRepository.findByProviderAndProviderUserId(OAuthProvider.GOOGLE, sub))
                .thenReturn(Optional.empty());
        when(userRepository.existsByEmail(email)).thenReturn(true);

        OAuthAuthenticationException exception = assertThrows(
                OAuthAuthenticationException.class,
                () -> oAuthAuthenticationService.authenticateGoogle(oidcUser)
        );

        assertTrue(exception.getMessage().contains("Automatic linking is not permitted"));
        verify(userRepository, never()).save(any());
        verify(oauthAccountRepository, never()).save(any());
    }

    @Test
    @DisplayName("Username collisions are resolved with safe unique suffix")
    void authenticateGoogle_UsernameCollision_GeneratesUniqueUsername() {
        String sub = "sub-collision-123";
        String email = "john@gmail.com";
        OidcUser oidcUser = createMockOidcUser(sub, email, true, "John", "Doe");

        when(oauthAccountRepository.findByProviderAndProviderUserId(OAuthProvider.GOOGLE, sub))
                .thenReturn(Optional.empty());
        when(userRepository.existsByEmail(email)).thenReturn(false);
        when(userRepository.existsByUsername("john")).thenReturn(true);
        when(userRepository.existsByUsername(startsWith("john_"))).thenReturn(false);

        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
        when(jwtService.generateToken(any())).thenReturn("token");

        oAuthAuthenticationService.authenticateGoogle(oidcUser);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User user = userCaptor.getValue();

        assertTrue(user.getUsername().startsWith("john_"));
        assertTrue(user.getUsername().length() <= 50);
    }

    @Test
    @DisplayName("Concurrent duplicate registration recovers gracefully without crash")
    void authenticateGoogle_ConcurrentRegistration_HandlesRaceCondition() {
        String sub = "sub-race-123";
        String email = "race@gmail.com";
        OidcUser oidcUser = createMockOidcUser(sub, email, true, "Race", "Condition");

        when(oauthAccountRepository.findByProviderAndProviderUserId(OAuthProvider.GOOGLE, sub))
                .thenReturn(Optional.empty());
        when(userRepository.existsByEmail(email)).thenReturn(false);
        when(userRepository.existsByUsername("race")).thenReturn(false);

        User raceUser = User.builder().username("race").role(Role.USER).build();
        OAuthAccount existingAfterRace = OAuthAccount.builder()
                .user(raceUser)
                .provider(OAuthProvider.GOOGLE)
                .providerUserId(sub)
                .build();

        when(userRepository.save(any(User.class))).thenReturn(raceUser);
        when(oauthAccountRepository.save(any(OAuthAccount.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key value"));

        // During catch block, subsequent lookup returns the saved entity
        when(oauthAccountRepository.findByProviderAndProviderUserId(OAuthProvider.GOOGLE, sub))
                .thenReturn(Optional.empty()) // first call
                .thenReturn(Optional.of(existingAfterRace)); // second call in catch block

        when(jwtService.generateToken(raceUser)).thenReturn("race.recovered.jwt");

        String token = oAuthAuthenticationService.authenticateGoogle(oidcUser);

        assertEquals("race.recovered.jwt", token);
    }

    @Test
    @DisplayName("Email with mixed casing is normalized to lowercase")
    void authenticateGoogle_MixedCaseEmail_NormalizedToLowercase() {
        String sub = "sub-case-123";
        String email = "MixedCase.User@GMAIL.com";
        OidcUser oidcUser = createMockOidcUser(sub, email, true, "Case", "User");

        when(oauthAccountRepository.findByProviderAndProviderUserId(OAuthProvider.GOOGLE, sub))
                .thenReturn(Optional.empty());
        when(userRepository.existsByEmail("mixedcase.user@gmail.com")).thenReturn(false);
        when(userRepository.existsByUsername(anyString())).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
        when(jwtService.generateToken(any())).thenReturn("token.jwt");

        oAuthAuthenticationService.authenticateGoogle(oidcUser);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertEquals("mixedcase.user@gmail.com", userCaptor.getValue().getEmail());
    }
}
