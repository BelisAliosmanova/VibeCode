package com.vide.vibe.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.Currency;

@Entity
@Table(name = "pricing")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Pricing extends SoftDeletableEntity {

    public enum BillingCycle {
        ONE_TIME, MONTHLY, YEARLY, USAGE_BASED
    }

    public enum PricingType {
        FREE, FREEMIUM, PAID, CONTACT_US
    }

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30)
    private PricingType type;

    @Column(name = "amount", precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_cycle", length = 30)
    private BillingCycle billingCycle;

    @Column(name = "description", length = 500)
    private String description;
}