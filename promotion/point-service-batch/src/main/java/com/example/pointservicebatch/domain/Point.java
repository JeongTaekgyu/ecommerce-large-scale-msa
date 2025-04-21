package com.example.pointservicebatch.domain;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "points")
@Getter
@NoArgsConstructor
@EntityListeners(AuditingEntityListener.class) // AuditingEntityListener를 사용해 엔티티의 생성 및 수정 시간을 자동으로 관리
public class Point { // 적립금 내역 엔터티

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long amount; // 적립/차감 금액

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PointType type;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private Long balanceSnapshot;

    @Version
    private Long version; // Optimistic Lock을 위한 버전 관리

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "point_balance_id")
    private PointBalance pointBalance;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    public Point(Long userId, Long amount, PointType type, String description, Long balanceSnapshot, PointBalance pointBalance) {
        this.userId = userId;
        this.amount = amount;
        this.type = type;
        this.description = description;
        this.balanceSnapshot = balanceSnapshot;
        this.pointBalance = pointBalance;
        this.version = 0L;
    }
}
