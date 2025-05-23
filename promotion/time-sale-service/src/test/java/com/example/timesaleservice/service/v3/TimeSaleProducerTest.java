package com.example.timesaleservice.service.v3;

import com.example.timesaleservice.dto.PurchaseRequestMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.kafka.core.KafkaTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class TimeSaleProducerTest {

    @InjectMocks
    private TimeSaleProducer timeSaleProducer;

    @Mock
    private KafkaTemplate<String, PurchaseRequestMessage> kafkaTemplate;

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RBucket<String> resultBucket;

    @Mock
    private RBucket<String> queueBucket;

    @Mock
    private RAtomicLong totalCounter;

    @Test
    @DisplayName("구매 요청 전송 성공")
    void sendPurchaseRequest_Success() {
        // given
        Long timeSaleId = 1L;
        Long userId = 1L;
        Long quantity = 2L;
        when(redissonClient.<String>getBucket(matches("purchase-result:.*"))).thenReturn(resultBucket);
        when(redissonClient.<String>getBucket(matches("time-sale-queue:.*"))).thenReturn(queueBucket);
        when(redissonClient.getAtomicLong(anyString())).thenReturn(totalCounter);

        // when
        String requestId = timeSaleProducer.sendPurchaseRequest(timeSaleId, userId, quantity);

        // then
        verify(resultBucket).set("PENDING"); // consumer에서는 success로 변경 producer니까 pending 상태여야한다.
        verify(queueBucket).set(requestId);
        verify(totalCounter).incrementAndGet();
        verify(kafkaTemplate).send(eq("time-sale-requests"), eq(requestId), any(PurchaseRequestMessage.class));
        assertThat(requestId).isNotNull();
    }

    @Test
    @DisplayName("대기열 위치 조회 성공")
    void getQueuePosition_Success() {
        // given
        Long timeSaleId = 1L;
        String requestId = "test-request-id";
        when(redissonClient.<String>getBucket(matches("time-sale-queue:.*"))).thenReturn(queueBucket);
        when(queueBucket.get()).thenReturn("request-1,test-request-id,request-3"); // 대기열 위치가 2번째에 있음

        // when
        Integer position = timeSaleProducer.getQueuePosition(timeSaleId, requestId);

        // then
        assertThat(position).isEqualTo(2); // 대기열 위치가 2번째 임을 검증
    }

    @Test
    @DisplayName("대기열 위치 조회 실패 - 요청 ID가 대기열에 없음")
    void getQueuePosition_NotInQueue() {
        // given
        Long timeSaleId = 1L;
        String requestId = "test-request-id";
        when(redissonClient.<String>getBucket(matches("time-sale-queue:.*"))).thenReturn(queueBucket);
        when(queueBucket.get()).thenReturn("request-1,request-2,request-3");

        // when
        Integer position = timeSaleProducer.getQueuePosition(timeSaleId, requestId);

        // then
        assertThat(position).isNull();
    }

    @Test
    @DisplayName("총 대기 수 조회 성공")
    void getTotalWaiting_Success() {
        // given
        Long timeSaleId = 1L;
        when(redissonClient.getAtomicLong(anyString())).thenReturn(totalCounter);
        when(totalCounter.get()).thenReturn(5L); // 5개의 대기 수가 있다고 가정

        // when
        Long total = timeSaleProducer.getTotalWaiting(timeSaleId);

        // then
        assertThat(total).isEqualTo(5L); // 총 대기 수가 5임을 검증
        verify(totalCounter).get();
    }
}