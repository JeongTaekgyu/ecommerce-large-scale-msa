package com.example.couponservice.controller.v1;

import com.example.couponservice.dto.v1.CouponPolicyDto;
import com.example.couponservice.service.v1.CouponPolicyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/coupon-policies")
@RequiredArgsConstructor
public class CouponPolicyController {
    private final CouponPolicyService couponPolicyService;

    @PostMapping
    public ResponseEntity<CouponPolicyDto.Response> createCouponPolicy(
            @RequestBody CouponPolicyDto.CreateRequest request) {
        return ResponseEntity.ok()
                .body(CouponPolicyDto.Response.from(couponPolicyService.createCouponPolicy(request)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CouponPolicyDto.Response> getCouponPolicy(@PathVariable Long id) {
        return ResponseEntity.ok(CouponPolicyDto.Response.from(couponPolicyService.getCouponPolicy(id)));
    }

    @GetMapping
    public ResponseEntity<List<CouponPolicyDto.Response>> getAllCouponPolicies() {
        return ResponseEntity.ok(couponPolicyService.getAllCouponPolicies().stream()
                .map(CouponPolicyDto.Response::from)
                .collect(Collectors.toList()));
    }
}
