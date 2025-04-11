package com.example.couponservice.service.v3;

import com.example.couponservice.dto.v3.CouponDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CouponConsumer {
    // 해당 메시지를 consume해서 실제 쿠폰을 발급하는 로직을 구현

    private final CouponService couponService;

    // CouponProducer에서 쿠폰 발급 요청에 대한 메시지를 쓰게 되고
    // 이 메시지가 쓰여지면 CouponConsumer에서 해당 메시지를 읽어서(consume해서) issueCoupon을 호출한다.
    @KafkaListener(topics = "coupon-issue-requests", groupId = "coupon-service", containerFactory = "couponKafkaListenerContainerFactory")
    public void consumeCouponIssueRequest(CouponDto.IssueMessage message) {
        try {
            log.info("Received coupon issue request: {}", message);
            couponService.issueCoupon(message);
        } catch (Exception e) {
            log.error("Failed to process coupon issue request: {}", e.getMessage(), e);
        }
    }
}
