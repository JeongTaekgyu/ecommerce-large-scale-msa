package com.example.couponservice.service.v1;

import com.example.couponservice.domain.CouponPolicy;
import com.example.couponservice.dto.v1.CouponPolicyDto;
import com.example.couponservice.exception.CouponPolicyNotFoundException;
import com.example.couponservice.repository.CouponPolicyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CouponPolicyService {
    private final CouponPolicyRepository couponPolicyRepository;

    @Transactional
    public CouponPolicy createCouponPolicy(CouponPolicyDto.CreateRequest request) {
        CouponPolicy couponPolicy = request.toEntity();
        return couponPolicyRepository.save(couponPolicy);
    }

    @Transactional(readOnly = true)
    public CouponPolicy getCouponPolicy(Long id) {
        return couponPolicyRepository.findById(id)
                .orElseThrow(() -> new CouponPolicyNotFoundException("쿠폰 정책을 찾을 수 없습니다."));
    }

    @Transactional(readOnly = true)
    public List<CouponPolicy> getAllCouponPolicies() {
        return couponPolicyRepository.findAll();
    }

}
