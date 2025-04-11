package com.example.couponservice.dto.v3;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

public class CouponDto {

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IssueRequest {
        private Long couponPolicyId;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IssueMessage {
        private Long policyId;
        private Long userId;
    }
}
