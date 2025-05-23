package com.example.couponservice.service.v2;

import com.example.couponservice.domain.Coupon;
import com.example.couponservice.dto.v1.CouponDto;
import com.example.couponservice.exception.CouponNotFoundException;
import com.example.couponservice.repository.CouponRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service("couponServiceV2")
@RequiredArgsConstructor
public class CouponService {
    private final CouponRepository couponRepository;
    private final CouponRedisService couponRedisService;
    private final CouponStateService couponStateService;

    @Transactional
    public CouponDto.Response issueCoupon(CouponDto.IssueRequest request) {
        Coupon coupon = couponRedisService.issueCoupon(request);
        couponStateService.updateCouponState(couponRepository.findById(coupon.getId())
                .orElseThrow(() -> new CouponNotFoundException("쿠폰을 찾을 수 없습니다.")));
        return CouponDto.Response.from(coupon);
    }

    @Transactional
    public CouponDto.Response useCoupon(Long couponId, Long orderId) {
        Coupon coupon = couponRepository.findByIdWithLock(couponId)
                .orElseThrow(() -> new CouponNotFoundException("쿠폰을 찾을 수 없습니다."));

        coupon.use(orderId);
        couponStateService.updateCouponState(coupon);

        return CouponDto.Response.from(coupon);
    }

    @Transactional
    public CouponDto.Response cancelCoupon(Long couponId) {
        Coupon coupon = couponRepository.findByIdWithLock(couponId)
                .orElseThrow(() -> new CouponNotFoundException("쿠폰을 찾을 수 없습니다."));

        coupon.cancel();
        couponStateService.updateCouponState(coupon);

        return CouponDto.Response.from(coupon);
    }

    public CouponDto.Response getCoupon(Long couponId) {
        CouponDto.Response cachedCoupon = couponStateService.getCouponState(couponId);
        if (cachedCoupon != null) {
            return cachedCoupon;
        }

        // cachedCoupon이 없다면 DB에서 쿠폰을 조회
        Coupon coupon = couponRepository.findById(couponId)
                .orElseThrow(() -> new CouponNotFoundException("쿠폰을 찾을 수 없습니다."));

        CouponDto.Response response = CouponDto.Response.from(coupon);
        couponStateService.updateCouponState(coupon);

        return response;
    }
}
