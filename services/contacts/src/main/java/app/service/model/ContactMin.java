package app.service.model;

import lombok.*;
import java.time.Instant;
import java.util.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ContactMin {
    private Long id;
    private String appId;
    private String adminId;
    private String deviceId;
    private String lookupKey;
    private String displayName;
    private Instant collectedAt;
    private Instant createdAt;
    private Instant updatedAt;
    @lombok.Builder.Default
    private List<String> numbers = new ArrayList<>();
}
