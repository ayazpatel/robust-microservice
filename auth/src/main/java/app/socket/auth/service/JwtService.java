package app.socket.auth.service;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Map;

@Service
public class JwtService {
    private final PrivateKey privateKey;

    public JwtService(
            @Value("${jwt.privateKeyPem:}") String privateKeyPem,
            @Value("${jwt.privateKeyPath:}") String privateKeyPath
    ) {
        try {
            String pem = privateKeyPem;
            if ((pem == null || pem.isBlank()) && privateKeyPath != null && !privateKeyPath.isBlank()) {
                pem = Files.readString(Path.of(privateKeyPath));
            }
            if (pem == null || pem.isBlank()) {
                throw new IllegalStateException("Missing RSA private key: set jwt.privateKeyPem or jwt.privateKeyPath");
            }
            this.privateKey = loadPrivateKeyFromPem(pem);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load RSA private key", e);
        }
    }

    public String issue(String appId, String deviceId, String ip, long ttlSeconds) {
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(ttlSeconds);
        return Jwts.builder()
                .setSubject(appId)
                .addClaims(Map.of("appId", appId, "deviceId", deviceId, "ip", ip))
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(exp))
                .signWith(privateKey, SignatureAlgorithm.RS256)
                .compact();
    }

    private static PrivateKey loadPrivateKeyFromPem(String pem) throws Exception {
        String clean = pem.replace("\r", "").replace("-----BEGIN PRIVATE KEY-----", "").replace("-----END PRIVATE KEY-----", "").replace("\n", "");
        byte[] der = Base64.getDecoder().decode(clean);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(der);
        return KeyFactory.getInstance("RSA").generatePrivate(spec);
    }
}
