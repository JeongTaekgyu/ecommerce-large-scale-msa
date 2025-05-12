package com.example.couponservice.service.v3;

import com.example.couponservice.aop.CouponMetered;
import com.example.couponservice.config.UserIdInterceptor;
import com.example.couponservice.domain.Coupon;
import com.example.couponservice.domain.CouponPolicy;
import com.example.couponservice.dto.v3.CouponDto;
import com.example.couponservice.exception.CouponIssueException;
import com.example.couponservice.exception.CouponNotFoundException;
import com.example.couponservice.repository.CouponRepository;
import com.example.couponservice.service.v2.CouponPolicyService;
import com.example.couponservice.service.v2.CouponStateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service("couponServiceV3")
@RequiredArgsConstructor
@Slf4j
public class CouponService {
    private static final String COUPON_QUANTITY_KEY = "coupon:quantity:";
    private static final String COUPON_LOCK_KEY = "coupon:lock:";
    private static final long LOCK_WAIT_TIME = 3L;
    private static final long LOCK_LEASE_TIME = 5L;

    private final RedissonClient redissonClient;
    private final CouponRepository couponRepository;
    private final CouponProducer couponProducer;
    private final CouponStateService couponStateService;
    private final CouponPolicyService couponPolicyService;

    /*
    실제 사용자가 쿠폰 서비스에서 엄청나게 많은 requestCouponIssue를 요청을 받더라도
    실제 발급하는 로직에 대해서는 redis에서 빠르게 비즈니스로직을 처리하고
    실제 db에 발급 처리하는 부분은 Consumer로 위임을 해서 순차적으로 db에 쌓이는 구조를 비동기 구조르 만들 수 있다.
    그러므로 더 안정적인 순차 처리가 가능하다.
    */
    @Transactional(readOnly = true)
    @CouponMetered(version = "v3")
    public void requestCouponIssue(CouponDto.IssueRequest request) {
        String quantityKey = COUPON_QUANTITY_KEY + request.getCouponPolicyId();
        String lockKey = COUPON_LOCK_KEY + request.getCouponPolicyId();
        RLock lock = redissonClient.getLock(lockKey);

        try {
            boolean isLocked = lock.tryLock(LOCK_WAIT_TIME, LOCK_LEASE_TIME, TimeUnit.SECONDS);
            if (!isLocked) {
                throw new CouponIssueException("쿠폰 발급 요청이 많아 처리할 수 없습니다. 잠시 후 다시 시도해주세요.");
            }

            log.info("~~~ v3 requestCouponIssue getCouponPolicy 직전");
            CouponPolicy couponPolicy = couponPolicyService.getCouponPolicy(request.getCouponPolicyId());
            if (couponPolicy == null) {
                throw new IllegalArgumentException("쿠폰 정책을 찾을 수 없습니다.");
            }

            LocalDateTime now = LocalDateTime.now();
            log.info("v3~~~ start : " + couponPolicy.getStartTime());
            log.info("v3~~~ now : " + now);
            log.info("v3~~~ endtime : " + couponPolicy.getEndTime());
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

            // Kafka로 쿠폰 발급 요청 전송 - 비동기 이기 때문에 결과를 기다리지 않고 다음 로직으로 넘어간다. 그래서 finally에서 락을 해제한다.
            couponProducer.sendCouponIssueRequest(
                    CouponDto.IssueMessage.builder()
                            .policyId(request.getCouponPolicyId())
                            .userId(UserIdInterceptor.getCurrentUserId())
                            .build()
            );

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CouponIssueException("쿠폰 발급 중 오류가 발생했습니다.");
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    @Transactional
    public void issueCoupon(CouponDto.IssueMessage message) {
        try {
            log.info("~~~ v3 issueCoupon getCouponPolicy 직전");
            CouponPolicy policy = couponPolicyService.getCouponPolicy(message.getPolicyId());
            if (policy == null) {
                throw new IllegalArgumentException("쿠폰 정책을 찾을 수 없습니다.");
            }

            Coupon coupon = couponRepository.save(Coupon.builder()
                    .couponPolicy(policy)
                    .userId(message.getUserId())
                    .couponCode(generateCouponCode())
                    .build());

            log.info("Coupon issued successfully: policyId={}, userId={}", message.getPolicyId(), message.getUserId());

        } catch (Exception e) {
            log.error("Failed to issue coupon: {}", e.getMessage());
            throw e;
        }
    }

    @Transactional
    public Coupon useCoupon(Long couponId, Long orderId) {
        Coupon coupon = couponRepository.findByIdWithLock(couponId)
                .orElseThrow(() -> new CouponNotFoundException("쿠폰을 찾을 수 없습니다."));

        coupon.use(orderId);
        couponStateService.updateCouponState(coupon);

        return coupon;
    }

    @Transactional
    public Coupon cancelCoupon(Long couponId) {
        Coupon coupon = couponRepository.findByIdAndUserId(couponId, UserIdInterceptor.getCurrentUserId())
                .orElseThrow(() -> new IllegalArgumentException("쿠폰을 찾을 수 없습니다."));

        if (!coupon.isUsed()) {
            throw new IllegalStateException("사용되지 않은 쿠폰은 취소할 수 없습니다.");
        }

        coupon.cancel();
        couponStateService.updateCouponState(coupon);

        return coupon;
    }

    private String generateCouponCode() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    }
}