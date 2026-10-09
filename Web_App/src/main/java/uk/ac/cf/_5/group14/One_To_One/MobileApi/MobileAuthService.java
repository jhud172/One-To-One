package uk.ac.cf._5.group14.One_To_One.MobileApi;

import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.ac.cf._5.group14.One_To_One.Users.Role;
import uk.ac.cf._5.group14.One_To_One.Users.User;
import uk.ac.cf._5.group14.One_To_One.Users.UserRepository;
import uk.ac.cf._5.group14.One_To_One.Users.UserService;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

@Service
public class MobileAuthService {
    private static final int TOKEN_BYTES = 48;
    private static final int TOKEN_DAYS = 30;

    private final UserService userService;
    private final UserRepository userRepository;
    private final JdbcTemplate jdbcTemplate;
    private final SecureRandom secureRandom = new SecureRandom();

    public MobileAuthService(UserService userService, UserRepository userRepository, JdbcTemplate jdbcTemplate) {
        this.userService = userService;
        this.userRepository = userRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    void ensureMobileTokenTable() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS mobile_auth_tokens (
                    token_hash VARCHAR(128) PRIMARY KEY,
                    user_id BIGINT NOT NULL,
                    device_name VARCHAR(120),
                    created_at TIMESTAMP NOT NULL,
                    expires_at TIMESTAMP NOT NULL,
                    revoked_at TIMESTAMP NULL
                )
                """);
    }

    @Transactional
    public MobileSession login(String usernameOrEmail, String password, String deviceName) {
        if (usernameOrEmail == null || usernameOrEmail.isBlank() || password == null || password.isBlank()) {
            throw new MobileApiException(400, "Username/email and password are required.");
        }

        User user = userService.findByUsername(usernameOrEmail);
        if (user == null) {
            user = userService.findByEmail(usernameOrEmail);
        }
        if (user == null || !user.isEnabled() || !userService.verifyPassword(password, user.getPassword())) {
            throw new MobileApiException(401, "Invalid login details.");
        }

        return issueSession(user, deviceName);
    }

    @Transactional
    public MobileSession signup(MobileSignupRequest request, Role role) {
        if (role == Role.GYM_ADMIN) {
            throw new MobileApiException(403, "Gym accounts require review. Apply through the website gym application form.");
        }
        if (request == null) {
            throw new MobileApiException(400, "Signup details are required.");
        }
        String email = clean(request.email());
        String username = clean(request.username()).toLowerCase();
        String firstName = fallback(clean(request.firstName()), role == Role.GYM_ADMIN ? "Gym" : "One");
        String lastName = fallback(clean(request.lastName()), role == Role.GYM_ADMIN ? "Account" : "To One");
        String password = request.password();

        if (email.isBlank() || username.isBlank() || password == null || password.length() < 8) {
            throw new MobileApiException(400, "Email, username and an 8+ character password are required.");
        }
        if (userService.emailExists(email)) {
            throw new MobileApiException(409, "Email is already registered.");
        }
        if (userService.usernameExists(username)) {
            throw new MobileApiException(409, "Username is already taken.");
        }

        User user = new User(email, firstName, lastName, username, password);
        user.setRole(role);
        user.setPhoneNumber(clean(request.phone()));
        if (role == Role.TRAINER) {
            user.setTrainerVerified(false);
        }
        User saved = userService.saveUser(user);
        createRoleProfile(saved, request);
        return issueSession(saved, request.deviceName());
    }

    public Optional<User> authenticate(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            return Optional.empty();
        }
        String token = authorizationHeader.substring("Bearer ".length()).trim();
        if (token.isBlank()) {
            return Optional.empty();
        }
        String hash = hash(token);
        try {
            Long userId = jdbcTemplate.queryForObject(
                    """
                    SELECT user_id FROM mobile_auth_tokens
                    WHERE token_hash = ? AND revoked_at IS NULL AND expires_at > CURRENT_TIMESTAMP
                    """,
                    Long.class,
                    hash
            );
            return userId == null ? Optional.empty() : userRepository.findById(userId).filter(User::isEnabled);
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    public void logout(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            return;
        }
        jdbcTemplate.update(
                "UPDATE mobile_auth_tokens SET revoked_at = CURRENT_TIMESTAMP WHERE token_hash = ?",
                hash(authorizationHeader.substring("Bearer ".length()).trim())
        );
    }

    private MobileSession issueSession(User user, String deviceName) {
        String token = newToken();
        Instant now = Instant.now();
        Instant expiresAt = now.plus(TOKEN_DAYS, ChronoUnit.DAYS);
        jdbcTemplate.update(
                """
                INSERT INTO mobile_auth_tokens (token_hash, user_id, device_name, created_at, expires_at)
                VALUES (?, ?, ?, ?, ?)
                """,
                hash(token),
                user.getId(),
                fallback(clean(deviceName), "Android"),
                Timestamp.from(now),
                Timestamp.from(expiresAt)
        );
        return new MobileSession(token, expiresAt, user);
    }

    private void createRoleProfile(User user, MobileSignupRequest request) {
        if (user.getRole() == Role.TRAINER) {
            jdbcTemplate.update(
                    """
                    INSERT INTO trainer_profiles (user_id, bio, trainer_code, location, created_at, updated_at)
                    VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """,
                    user.getId(),
                    fallback(clean(request.bio()), "Mobile trainer profile"),
                    trainerCode(),
                    clean(request.location())
            );
        } else if (user.getRole() == Role.GYM_ADMIN) {
            jdbcTemplate.update(
                    """
                    INSERT INTO gym_profiles (user_id, gym_name, gym_code, address, city, contact_name, contact_phone, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """,
                    user.getId(),
                    fallback(clean(request.gymName()), user.getFullName()),
                    trainerCode().substring(0, 8),
                    clean(request.address()),
                    clean(request.city()),
                    user.getFullName(),
                    clean(request.phone())
            );
        }
    }

    private String newToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String trainerCode() {
        byte[] bytes = new byte[9];
        secureRandom.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes).substring(0, 12).toUpperCase();
    }

    private String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to hash token", e);
        }
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private String fallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    public record MobileSession(String token, Instant expiresAt, User user) {}

    public record MobileSignupRequest(
            String email,
            String username,
            String password,
            String firstName,
            String lastName,
            String phone,
            String bio,
            String location,
            String gymName,
            String address,
            String city,
            String deviceName
    ) {}
}
