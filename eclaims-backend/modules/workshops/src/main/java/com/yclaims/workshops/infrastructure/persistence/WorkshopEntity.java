package com.yclaims.workshops.infrastructure.persistence;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.util.UUID;

@Entity
@Table(name = "workshops", schema = "workshops")
@Getter @Setter
public class WorkshopEntity {
    @Id @Column(columnDefinition = "uuid")
    private UUID id;
    @Column(nullable = false, length = 150) private String name;
    @Column(length = 300) private String address;
    @Column(length = 100) private String city;
    @Column(name = "zip_code", length = 20) private String zipCode;
    @Column(length = 20) private String phone;
    @Column(length = 255) private String email;
    private double rating;
    private boolean active;
    @Column(name = "provider_type", nullable = false, length = 30) private String providerType;
    @Column(name = "keycloak_user_id", length = 36, unique = true) private String keycloakUserId;

    @Override public boolean equals(Object o) { if (this == o) return true; if (!(o instanceof WorkshopEntity x)) return false; return id != null && id.equals(x.id); }
    @Override public int hashCode() { return id != null ? id.hashCode() : 0; }
}
