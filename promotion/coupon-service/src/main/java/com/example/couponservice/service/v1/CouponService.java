package com.example.couponservice.service.v1;

import com.example.couponservice.aop.CouponMetered;
import com.example.couponservice.config.UserIdInterceptor;
import com.example.couponservice.domain.Coupon;
import com.example.couponservice.domain.CouponPolicy;
import com.example.couponservice.dto.v1.CouponDto;
import com.example.couponservice.exception.CouponIssueException;
import com.example.couponservice.exception.CouponNotFoundException;
import com.example.couponservice.repository.CouponPolicyRepository;
import com.example.couponservice.repository.CouponRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CouponService {
    private final CouponRepository couponRepository;
    private final CouponPolicyRepository couponPolicyRepository;

    /**
     * 1. Race Condition 발생 가능성
     * findByIdWithLock 으로 쿠폰 정책에 대해 락을 걸지만, countByCouponPolicyId 와 실제 쿠폰 저장 사이에 갭이 존재
     * 현재는 여러 트랜잭션이 동시에 카운트를 조회하고 조건을 통과한 후 쿠폰을 저장할 수 있음
     * 결과적으로 totalQuantity 보다 더 많은 쿠폰이 발급될 수 있음
     *
     * 2. 성능 이슈
     * 매 요청마다 발급된 쿠폰 수를 카운트하는 쿼리 실행
     * 쿠폰 수가 많아질수록 카운트 쿼리의 성능이 저하될 수 있음
     * PESSIMISTIC_LOCK 으로 인한 병목 현상 발생 가능
     *
     * 3. Dead Lock 가능성 (둘 이상의 프로세스가 다른 프로세스가 점유하고 있는 자원을 서로 기다릴 때 무한 대기에 빠지는 상황)
     * 여러 트랜잭션이 동시에 같은 쿠폰 정책에 대해 락을 획득하려 할 때
     * 트랜잭션 타임아웃이 발생할 수 있음
     *
     * 4. 정확한 수량 보장의 어려움
     * 분산 환경에서 여러 서버가 동시에 쿠폰을 발급할 경우
     * DB 레벨의 락만으로는 정확한 수량 제어가 어려움
     */
    @Transactional
    @CouponMetered(version = "v1")
    public Coupon issueCoupon(CouponDto.IssueRequest request) {
        CouponPolicy couponPolicy = couponPolicyRepository.findByIdWithLock(request.getCouponPolicyId())
                .orElseThrow(() -> new CouponIssueException("쿠폰 정책을 찾을 수 없습니다."));

        LocalDateTime now = LocalDateTime.now();
        log.info("v1~~~ start : " + couponPolicy.getStartTime());
        log.info("v1~~~ now : " + now);
        log.info("v1~~~ endtime : " + couponPolicy.getEndTime());
        if (now.isBefore(couponPolicy.getStartTime()) || now.isAfter(couponPolicy.getEndTime())) {
            throw new CouponIssueException("쿠폰 발급 기간이 아닙니다.");
        }

        long issuedCouponCount = couponRepository.countByCouponPolicyId(couponPolicy.getId());
        if (issuedCouponCount >= couponPolicy.getTotalQuantity()) {
            throw new CouponIssueException("쿠폰이 모두 소진되었습니다.");
        }

        Coupon coupon = Coupon.builder()
                .couponPolicy(couponPolicy)
                .userId(UserIdInterceptor.getCurrentUserId())
                .couponCode(generateCouponCode())
                .build();

        return couponRepository.save(coupon);
        // countByCouponPolicyId 에 락을 거는건 의미가 없고 coupon 자체가 save할 때까지 락이 걸리는게 중요하다
        // 물론 v1 에서는 findByIdWithLock을 제외하고는 락을 걸지 않는다.
    }

    private String generateCouponCode() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    @Transactional
    public Coupon useCoupon(Long couponId, Long orderId) {
        Long currentUserId = UserIdInterceptor.getCurrentUserId();

        Coupon coupon = couponRepository.findByIdAndUserId(couponId, currentUserId)
                .orElseThrow(() -> new CouponNotFoundException("쿠폰을 찾을 수 없거나 접근 권한이 없습니다."));

        coupon.use(orderId);

        return coupon;
    }

    @Transactional
    public Coupon cancelCoupon(Long couponId) {
        Long currentUserId = UserIdInterceptor.getCurrentUserId();

        Coupon coupon = couponRepository.findByIdAndUserId(couponId, currentUserId)
                .orElseThrow(() -> new CouponNotFoundException("쿠폰을 찾을 수 없거나 접근 권한이 없습니다."));

        coupon.cancel();
        return coupon;
    }

    @Transactional(readOnly = true)
    public Page<Coupon> getCoupons(CouponDto.ListRequest request) {
        Long currentUserId = UserIdInterceptor.getCurrentUserId();

        return couponRepository.findByUserIdAndStatusOrderByCreatedAtDesc(
            currentUserId,
            request.getStatus(),
            PageRequest.of(
                    request.getPage() != null ? request.getPage() : 0,
                    request.getSize() != null ? request.getSize() : 10
            )
        );
    }
}

