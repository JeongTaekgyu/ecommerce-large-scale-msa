package com.example.couponservice.exception;

public class CouponExpiredException extends RuntimeException{
    public CouponExpiredException(String message) {
        super(message);
    }

    public CouponExpiredException(Long couponId) {
        super("이미 사용된 쿠폰입니다: " + couponId);
    }
}
