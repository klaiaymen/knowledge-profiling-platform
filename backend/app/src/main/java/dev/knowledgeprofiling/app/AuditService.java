package dev.knowledgeprofiling.app;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

@Service
public class AuditService {
    private final JdbcTemplate jdbcTemplate;
    private final byte[] pseudonymKey;
    public AuditService(JdbcTemplate jdbcTemplate, @Value("${KPF_PSEUDONYM_SECRET:}") String secret) {
        this.jdbcTemplate = jdbcTemplate;
        if (secret == null || secret.isBlank()) { pseudonymKey = new byte[32]; new SecureRandom().nextBytes(pseudonymKey); }
        else pseudonymKey = secret.getBytes(StandardCharsets.UTF_8);
    }

    public void record(String username, String action, String resource) {
        jdbcTemplate.update("INSERT INTO audit_log (username, action, occurred_at, resource) VALUES (?, ?, CURRENT_TIMESTAMP, ?)",
                pseudonym(username), action, resource);
    }

    private String pseudonym(String username) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(pseudonymKey, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(username.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) { throw new IllegalStateException("Could not pseudonymize audit subject", e); }
    }
}
