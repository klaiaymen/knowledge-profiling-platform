package dev.knowledgeprofiling.app;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

@Service
public class RefreshTokenService {
    private final JdbcTemplate jdbcTemplate;
    private final SecureRandom secureRandom = new SecureRandom();
    public RefreshTokenService(JdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }

    public String issue(String username) {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        jdbcTemplate.update("INSERT INTO refresh_token (token_hash, username, expires_at) VALUES (?, ?, ?)",
                digest(token), username, OffsetDateTime.now().plusDays(30));
        return token;
    }

    @Transactional
    public RotationResult rotate(String token) {
        if (token == null || token.isBlank()) throw new IllegalArgumentException("Refresh token is required");
        String hash = digest(token);
        List<String> usernames = jdbcTemplate.query("SELECT username FROM refresh_token WHERE token_hash = ? " +
                        "AND revoked_at IS NULL AND expires_at > CURRENT_TIMESTAMP FOR UPDATE",
                (result, row) -> result.getString(1), hash);
        if (usernames.isEmpty()) throw new IllegalArgumentException("Refresh token is invalid or expired");
        int revoked = jdbcTemplate.update("UPDATE refresh_token SET revoked_at = CURRENT_TIMESTAMP " +
                "WHERE token_hash = ? AND revoked_at IS NULL", hash);
        if (revoked != 1) throw new IllegalArgumentException("Refresh token was already used");
        String replacement = issue(usernames.getFirst());
        return new RotationResult(usernames.getFirst(), replacement);
    }

    private String digest(String token) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException("SHA-256 is unavailable", e); }
    }

    public record RotationResult(String username, String refreshToken) { }
}
