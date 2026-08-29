package uz.xitlar.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.xitlar.entity.OAuthAccount;
import uz.xitlar.entity.User;
import uz.xitlar.enums.OAuthProvider;
import uz.xitlar.enums.Role;
import uz.xitlar.exception.OAuthAuthenticationException;
import uz.xitlar.repository.OAuthAccountRepository;
import uz.xitlar.repository.UserRepository;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OAuthAuthenticationService {

    private static final Logger log = LoggerFactory.getLogger(OAuthAuthenticationService.class);

    private final OAuthAccountRepository oauthAccountRepository;
    private final UserRepository userRepository;
    private final JwtService jwtService;

    @Transactional
    public String authenticateGoogle(OidcUser oidcUser) {
        if (oidcUser == null) {
            throw new OAuthAuthenticationException("OIDC user principal cannot be null");
        }

        String sub = extractSub(oidcUser);
        if (sub == null || sub.isBlank()) {
            throw new OAuthAuthenticationException("Missing Google subject identifier");
        }

        String email = extractEmail(oidcUser);
        if (email == null || email.isBlank()) {
            throw new OAuthAuthenticationException("Missing Google email");
        }
        email = email.trim().toLowerCase(java.util.Locale.ROOT);

        Boolean emailVerified = extractEmailVerified(oidcUser);
        if (!Boolean.TRUE.equals(emailVerified)) {
            throw new OAuthAuthenticationException("Google email is not verified");
        }

        // 1. Check if OAuthAccount already exists for (GOOGLE, sub)
        Optional<OAuthAccount> existingAccount = oauthAccountRepository.findByProviderAndProviderUserId(OAuthProvider.GOOGLE, sub);
        if (existingAccount.isPresent()) {
            User user = existingAccount.get().getUser();
            log.info("Google authentication succeeded for existing user id={}", user.getId());
            return jwtService.generateToken(user);
        }

        // 2. Prevent account takeover if email already belongs to a local/existing user
        if (userRepository.existsByEmail(email)) {
            throw new OAuthAuthenticationException("An account with this email already exists. Automatic linking is not permitted.");
        }

        // 3. Create new User + OAuthAccount
        try {
            User newUser = createNewUser(oidcUser, email);
            User savedUser = userRepository.save(newUser);

            OAuthAccount oauthAccount = OAuthAccount.builder()
                    .user(savedUser)
                    .provider(OAuthProvider.GOOGLE)
                    .providerUserId(sub)
                    .build();

            oauthAccountRepository.save(oauthAccount);

            log.info("Google registration and authentication succeeded for new user id={}", savedUser.getId());
            return jwtService.generateToken(savedUser);
        } catch (DataIntegrityViolationException e) {
            log.warn("Concurrent OAuth account creation detected for sub={}", sub);
            // Handle race condition: check if another thread completed the registration
            Optional<OAuthAccount> concurrentAccount = oauthAccountRepository.findByProviderAndProviderUserId(OAuthProvider.GOOGLE, sub);
            if (concurrentAccount.isPresent()) {
                return jwtService.generateToken(concurrentAccount.get().getUser());
            }
            throw new OAuthAuthenticationException("Concurrent account creation failed. Please try again.");
        }
    }

    private User createNewUser(OidcUser oidcUser, String email) {
        String givenName = oidcUser.getGivenName();
        String familyName = oidcUser.getFamilyName();
        String fullName = oidcUser.getFullName();

        String firstName = sanitizeName(givenName != null && !givenName.isBlank() ? givenName : fullName, "Google");
        String lastName = sanitizeName(familyName, "User");

        String username = generateUniqueUsername(oidcUser, email);

        return User.builder()
                .firstName(firstName)
                .lastName(lastName)
                .username(username)
                .email(email)
                .password(null)
                .role(Role.USER) // Always Role.USER - prevents any privilege escalation
                .build();
    }

    private String generateUniqueUsername(OidcUser oidcUser, String email) {
        String baseCandidate = null;
        if (email != null && email.contains("@")) {
            baseCandidate = email.substring(0, email.indexOf('@'));
        }

        if (baseCandidate == null || baseCandidate.isBlank()) {
            baseCandidate = oidcUser.getGivenName();
        }

        if (baseCandidate == null || baseCandidate.isBlank()) {
            baseCandidate = "user";
        }

        // Sanitize to only allow valid characters: [a-zA-Z0-9_.]
        String sanitized = baseCandidate.replaceAll("[^a-zA-Z0-9_.]", "");
        if (sanitized.length() < 3) {
            sanitized = "user_" + sanitized;
        }
        if (sanitized.length() > 35) {
            sanitized = sanitized.substring(0, 35);
        }

        String candidate = sanitized;
        while (userRepository.existsByUsername(candidate)) {
            String suffix = "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 6);
            int maxBaseLen = 50 - suffix.length();
            String truncatedBase = sanitized.length() > maxBaseLen ? sanitized.substring(0, maxBaseLen) : sanitized;
            candidate = truncatedBase + suffix;
        }

        return candidate;
    }

    private String sanitizeName(String name, String fallback) {
        if (name == null || name.trim().isBlank()) {
            return fallback;
        }
        String trimmed = name.trim();
        return trimmed.length() > 100 ? trimmed.substring(0, 100) : trimmed;
    }

    private String extractSub(OidcUser oidcUser) {
        if (oidcUser.getSubject() != null && !oidcUser.getSubject().isBlank()) {
            return oidcUser.getSubject();
        }
        Object subAttr = oidcUser.getAttribute("sub");
        return subAttr != null ? subAttr.toString() : null;
    }

    private String extractEmail(OidcUser oidcUser) {
        if (oidcUser.getEmail() != null && !oidcUser.getEmail().isBlank()) {
            return oidcUser.getEmail();
        }
        Object emailAttr = oidcUser.getAttribute("email");
        return emailAttr != null ? emailAttr.toString() : null;
    }

    private Boolean extractEmailVerified(OidcUser oidcUser) {
        if (oidcUser.getEmailVerified() != null) {
            return oidcUser.getEmailVerified();
        }
        Object verifiedAttr = oidcUser.getAttribute("email_verified");
        if (verifiedAttr instanceof Boolean b) {
            return b;
        }
        if (verifiedAttr instanceof String s) {
            return Boolean.parseBoolean(s);
        }
        return false;
    }
}
