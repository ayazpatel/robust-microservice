package app.socket.auth.repo;

import app.socket.auth.model.AppRegistration;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AppRegistrationRepository extends JpaRepository<AppRegistration, Long> {
    Optional<AppRegistration> findByAppId(String appId);
    boolean existsByAppId(String appId);
}
