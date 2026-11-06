package app.socket.auth.service;

import app.socket.auth.model.AppRegistration;
import app.socket.auth.repo.AppRegistrationRepository;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;

@Service
public class AppService {
    private final AppRegistrationRepository repo;
    private final SecureRandom rnd = new SecureRandom();

    public AppService(AppRegistrationRepository repo) { this.repo = repo; }

    public List<AppRegistration> list() { return repo.findAll(); }

    public AppRegistration create(String packageName, String signatureSha, String appName) {
        // Generate 64-bit random appId as 16-hex-chars (alphanumeric)
        String appId;
        int attempts = 0;
        do {
            appId = genAppId64();
            attempts++;
            if (attempts > 5) throw new IllegalStateException("failed to generate unique appId");
        } while (repo.existsByAppId(appId));

        AppRegistration ar = new AppRegistration();
        ar.setAppId(appId);
        ar.setPackageName(packageName);
        ar.setSignatureSha(signatureSha);
        ar.setAppName(appName);
        ar.setApiKey(genKey());
        return repo.save(ar);
    }

    public AppRegistration update(String appId, String packageName, String signatureSha, String appName) {
        AppRegistration ar = repo.findByAppId(appId).orElseThrow();
        ar.setPackageName(packageName);
        ar.setSignatureSha(signatureSha);
        ar.setAppName(appName);
        return repo.save(ar);
    }

    public void delete(String appId) {
        AppRegistration ar = repo.findByAppId(appId).orElseThrow();
        repo.delete(ar);
    }

    public AppRegistration resetApiKey(String appId) {
        AppRegistration ar = repo.findByAppId(appId).orElseThrow();
        ar.setApiKey(genKey());
        return repo.save(ar);
    }

    private String genKey() {
        byte[] b = new byte[24];
        rnd.nextBytes(b);
        return HexFormat.of().formatHex(b);
    }

    private String genAppId64() {
        byte[] b = new byte[8]; // 64 bits
        rnd.nextBytes(b);
        return HexFormat.of().formatHex(b); // 16 hex chars
    }
}
