package com.example.timesaleservice.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(name = "time_sale_orders")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class) // AuditingEntityListener를 사용해 엔티티의 생성 및 수정 시간을 자동으로 관리
public class TimeSaleOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "time_sale_id", nullable = false)
    private TimeSale timeSale;

    @Column(nullable = false)
    private Long quantity; // 주문 수량

    @Column(nullable = false)
    private Long discountPrice; // 할인가

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    public TimeSaleOrder(Long id, Long userId, TimeSale timeSale, Long quantity, Long discountPrice) {
        this.id = id;
        this.userId = userId;
        this.timeSale = timeSale;
        this.quantity = quantity;
        this.discountPrice = discountPrice;
        this.status = OrderStatus.PENDING;
    }
}
