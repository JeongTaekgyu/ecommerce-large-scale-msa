package com.example.timesaleservice.service.v2;

import com.example.timesaleservice.domain.Product;
import com.example.timesaleservice.domain.TimeSale;
import com.example.timesaleservice.domain.TimeSaleOrder;
import com.example.timesaleservice.domain.TimeSaleStatus;
import com.example.timesaleservice.dto.TimeSaleDto;
import com.example.timesaleservice.exception.TimeSaleException;
import com.example.timesaleservice.repository.ProductRepository;
import com.example.timesaleservice.repository.TimeSaleOrderRepository;
import com.example.timesaleservice.repository.TimeSaleRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class TimeSaleRedisService {
    private static final String TIME_SALE_KEY = "time-sale:";
    private static final String TIME_SALE_LOCK = "time-sale-lock:";
    private static final long WAIT_TIME = 3L;
    private static final long LEASE_TIME = 3L;

    private final TimeSaleRepository timeSaleRepository;
    private final ProductRepository productRepository;
    private final TimeSaleOrderRepository timeSaleOrderRepository;
    private final RedissonClient redissonClient;
    private final ObjectMapper objectMapper;

    @Transactional
    public TimeSale createTimeSale(TimeSaleDto.CreateRequest request) {
        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new IllegalArgumentException("Product not found"));

        TimeSale timeSale = TimeSale.builder()
                .product(product)
                .quantity(request.getQuantity())
                .remainingQuantity(request.getQuantity())
                .discountPrice(request.getDiscountPrice())
                .startAt(request.getStartAt())
                .endAt(request.getEndAt())
                .status(TimeSaleStatus.ACTIVE)
                .build();

        TimeSale savedTimeSale = timeSaleRepository.save(timeSale);
        saveToRedis(savedTimeSale);
        return savedTimeSale;
    }

    // redis를 사용하지 않고 db에서 가져옴
    @Transactional(readOnly = true)
    public Page<TimeSale> getOngoingTimeSales(Pageable pageable) {
        LocalDateTime now = LocalDateTime.now();
        return timeSaleRepository.findAllByStartAtBeforeAndEndAtAfterAndStatus(now, TimeSaleStatus.ACTIVE, pageable);
    }

    @Transactional(readOnly = true)
    public TimeSale getTimeSale(Long timeSaleId) {
        return getFromRedis(timeSaleId);
    }

    @Transactional
    public TimeSale purchaseTimeSale(Long timeSaleId, TimeSaleDto.PurchaseRequest request) {
        // 분산 락 획득 - // 분산 락을 획득하기 위한 준비 작업
        RLock lock = redissonClient.getLock(TIME_SALE_LOCK + timeSaleId);
        if (lock == null) {
            throw new TimeSaleException("Failed to create lock");
        }

        boolean isLocked = false;
        try {
            isLocked = lock.tryLock(WAIT_TIME, LEASE_TIME, TimeUnit.SECONDS);
            if (!isLocked) {
                throw new TimeSaleException("Failed to acquire lock");
            }

            // Redis에서 타임세일 정보 가져와서 속도가 개선됨
            TimeSale timeSale = getFromRedis(timeSaleId);
            timeSale.purchase(request.getQuantity());

            // Save changes to DB
            timeSale = timeSaleRepository.save(timeSale);

            TimeSaleOrder order = TimeSaleOrder.builder()
                    .userId(request.getUserId())
                    .timeSale(timeSale)
                    .quantity(request.getQuantity())
                    .discountPrice(timeSale.getDiscountPrice())
                    .build();

            // status는 변경 안함?
            timeSaleOrderRepository.save(order);
            saveToRedis(timeSale);

            return timeSale;

        } catch (InterruptedException e) {
            // 락 획득 중 인터럽트 발생
            Thread.currentThread().interrupt();
            throw new TimeSaleException("Lock interrupted");
        } finally {
            if (isLocked) {
                try {
                    lock.unlock();
                } catch (Exception e) {
                    log.error("Failed to unlock", e);
                }
            }
        }
    }

    public void saveToRedis(TimeSale timeSale) {
        try {
            String json = objectMapper.writeValueAsString(timeSale);
            RBucket<String> bucket = redissonClient.getBucket(TIME_SALE_KEY + timeSale.getId());
            bucket.set(json);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize TimeSale: {}", timeSale.getId(), e);
        } catch (Exception e) {
            log.error("Failed to save TimeSale to Redis: {}", timeSale.getId(), e);
        }
    }

    private TimeSale getFromRedis(Long timeSaleId) {
        RBucket<String> bucket = redissonClient.getBucket(TIME_SALE_KEY + timeSaleId);
        String json = bucket.get();

        try {
            if (json != null) {
                return objectMapper.readValue(json, TimeSale.class);
            }

            // Redis에 없으면 DB에서 조회
            TimeSale timeSale = timeSaleRepository.findById(timeSaleId)
                    .orElseThrow(() -> new IllegalArgumentException("TimeSale not found"));

            // Redis에 저장
            saveToRedis(timeSale);

            return timeSale;
        } catch (JsonProcessingException e) {
            throw new TimeSaleException("Failed to parse TimeSale from Redis", e);
        }
    }
}
