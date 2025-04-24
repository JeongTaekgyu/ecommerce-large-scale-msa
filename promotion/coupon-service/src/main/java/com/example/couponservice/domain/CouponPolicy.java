package com.example.couponservice.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "coupon_policies")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CouponPolicy {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column
    private String description;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private DiscountType discountType;

    @Column(nullable = false)
    private Integer discountValue;

    @Column(nullable = false)
    private Integer minimumOrderAmount;

    @Column(nullable = false)
    private Integer maximumDiscountAmount;

    @Column(nullable = false)
    private Integer totalQuantity; // 이 쿠폰 정책으로 발급할 수 있는 총 쿠폰 수량

    @Column(nullable = false)
    private LocalDateTime startTime;

    @Column(nullable = false)
    @Setter
    private LocalDateTime endTime;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public enum DiscountType {
        FIXED_AMOUNT,    // 정액 할인
        PERCENTAGE      // 정률 할인
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public boolean isValidPeriod() {
        LocalDateTime now = LocalDateTime.now();
        return !now.isBefore(startTime) && !now.isAfter(endTime);
    }
}
