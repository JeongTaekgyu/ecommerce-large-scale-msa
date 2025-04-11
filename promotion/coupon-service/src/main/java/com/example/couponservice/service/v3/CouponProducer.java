package com.example.couponservice.service.v3;

import com.example.couponservice.dto.v3.CouponDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class CouponProducer {
    private static final String TOPIC = "coupon-issue-requests";
    private final KafkaTemplate<String, CouponDto.IssueMessage> kafkaTemplate;

    public void sendCouponIssueRequest(CouponDto.IssueMessage message) {
        // send 는 비동기로 Kafka 메시지를 보내고 결과를 확인하기 위해 whenComplete를 사용
        // whenComplete 메서드는 비동기 작업이 성공하든 실패하든 상관없이, 작업이 끝나면 실행되는 콜백 함수
        kafkaTemplate.send(TOPIC, String.valueOf(message.getPolicyId()), message)
                .whenComplete((result, ex) -> {
                    if (ex == null) { // 예외가 없다면 (성공했다면)
                        log.info("Sent message=[{}] with offset=[{}]", message, result.getRecordMetadata().offset());
                    } else { // 예외가 발생하면
                        log.error("Unable to send message=[{}] due to : {}", message, ex.getMessage());
                    }
                });
    }
}
