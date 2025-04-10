package com.example.couponservice.service.v2;

import com.example.couponservice.config.UserIdInterceptor;
import com.example.couponservice.domain.Coupon;
import com.example.couponservice.domain.CouponPolicy;
import com.example.couponservice.dto.v1.CouponDto;
import com.example.couponservice.exception.CouponIssueException;
import com.example.couponservice.repository.CouponRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class CouponRedisService {
    private final RedissonClient redissonClient;
    private final CouponRepository couponRepository;
    private final CouponPolicyService couponPolicyService;

    private static final String COUPON_QUANTITY_KEY = "coupon:quantity:";
    private static final String COUPON_LOCK_KEY = "coupon:lock:";
    private static final long LOCK_WAIT_TIME = 3;
    private static final long LOCK_LEASE_TIME = 5;

    @Transactional
    public Coupon issueCoupon(CouponDto.IssueRequest request) {
        String quantityKey = COUPON_QUANTITY_KEY + request.getCouponPolicyId();
        String lockKey = COUPON_LOCK_KEY + request.getCouponPolicyId();
        RLock lock = redissonClient.getLock(lockKey);

        try {
            // 동시성 충돌, 중복 차감, 잘못된 수량 감소 방지를 위해 RLock 을 사용한다.
            // lock.tryLock(...)은 다른 누군가가 락을 잡고 있으면 최대 LOCK_WAIT_TIME 초 만큼 기다렸다가 락을 얻으려는 시도를 하고
            // 그 시간 내에 락을 못 얻으면 false가 리턴되고, 락을 얻으면 true가 리턴된다
            boolean isLocked = lock.tryLock(LOCK_WAIT_TIME, LOCK_LEASE_TIME, TimeUnit.SECONDS);
            if (!isLocked) {
                throw new CouponIssueException("쿠폰 발급 요청이 많아 처리할 수 없습니다. 잠시 후 다시 시도해주세요.");
            }

            // redis에서 쿠폰 정책을 가져온다.
            CouponPolicy couponPolicy = couponPolicyService.getCouponPolicy(request.getCouponPolicyId());

            LocalDateTime now = LocalDateTime.now();
            if (now.isBefore(couponPolicy.getStartTime()) || now.isAfter(couponPolicy.getEndTime())) {
                throw new IllegalStateException("쿠폰 발급 기간이 아닙니다.");
            }

            // 수량 체크 및 감소
            RAtomicLong atomicQuantity = redissonClient.getAtomicLong(quantityKey);
            long remainingQuantity = atomicQuantity.decrementAndGet();

            if (remainingQuantity < 0) {
                atomicQuantity.incrementAndGet();
                throw new CouponIssueException("쿠폰이 모두 소진되었습니다.");
            }

            // 쿠폰 발급
            return couponRepository.save(Coupon.builder()
                    .couponPolicy(couponPolicy)
                    .userId(UserIdInterceptor.getCurrentUserId())
                    .couponCode(generateCouponCode())
                    .build());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CouponIssueException("쿠폰 발급 중 오류가 발생했습니다.", e);
        } finally {
            if (lock.isHeldByCurrentThread()) { // 현재 스레드가 락을 보유하고 있으면 락을 해제한다
                lock.unlock();
            }
        }
    }

    private String generateCouponCode() {
        return java.util.UUID.randomUUID().toString().substring(0, 8);
    }
}
