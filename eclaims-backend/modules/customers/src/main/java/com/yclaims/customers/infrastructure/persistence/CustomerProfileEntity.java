package com.yclaims.customers.infrastructure.persistence;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "customer_profiles", schema = "customers")
@Getter
@Setter
public class CustomerProfileEntity {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "customer_id", nullable = false, unique = true, length = 100)
    private String customerId;

    @Column(name = "address_line1", length = 200)
    private String addressLine1;

    @Column(name = "address_line2", length = 200)
    private String addressLine2;

    @Column(length = 100)
    private String city;

    @Column(length = 100)
    private String state;

    @Column(name = "zip_code", length = 20)
    private String zipCode;

    @Column(length = 100)
    private String country;

    @Column(name = "billing_cycle", nullable = false, length = 20)
    private String billingCycle;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CustomerProfileEntity x)) return false;
        return id != null && id.equals(x.id);
    }

    @Override
    public int hashCode() { return id != null ? id.hashCode() : 0; }
}
