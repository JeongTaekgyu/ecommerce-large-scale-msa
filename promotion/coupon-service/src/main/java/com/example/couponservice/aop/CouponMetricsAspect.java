package com.example.couponservice.aop;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

@Aspect
@Component
@RequiredArgsConstructor
public class CouponMetricsAspect {
    // MeterRegistry 는 Spring Boot Actuator + Micrometer + Prometheus 조합이 자동으로 제공하는 메트릭 수집 도구이다.
    private final MeterRegistry registry;

    // @CouponMetered 어노테이션이 붙은 메서드를 가로채서 실행 전후에 작업을 수행한다.
    @Around("@annotation(CouponMetered)")
    public Object measureCouponOperation(ProceedingJoinPoint joinPoint) throws Throwable {
        // 메트릭을 수집하는 작업을 진행한다.
        Timer.Sample sample = Timer.start();
        String version = extractVersion(joinPoint);
        String operation = extractOperation(joinPoint);

        try {
            Object result = joinPoint.proceed(); // 이 줄에서 실제로 @CouponMetered 가 붙은 메서드가 실행된다.

            // 쿠폰 발급 성공 메트릭
            // 성공 메트릭 카운터를 1 증가시킨다
            Counter.builder("coupon.operation.success")
                    .tag("version", version)
                    .tag("operation", operation)
                    .register(registry)
                    .increment();

            // 메서드의 실행 시간(duration) 을 기록한다.
            sample.stop(Timer.builder("coupon.operation.duration")
                    .tag("version", version)
                    .tag("operation", operation)
                    .register(registry));

            return result;
        } catch (Exception e) {
            // 쿠폰 발급 실패 메트릭
            Counter.builder("coupon.operation.failure")
                    .tag("version", version)
                    .tag("operation", operation)
                    .tag("error", e.getClass().getSimpleName())
                    .register(registry)
                    .increment();
            throw e;
        }
    }

    // 현재 실행 중인 메서드에서 @CouponMetered 어노테이션을 가져와서
    // 해당 어노테이션의 version 값을 반환한다.
    private String extractVersion(ProceedingJoinPoint joinPoint) {
        CouponMetered annotation = ((MethodSignature) joinPoint.getSignature())
                .getMethod()
                .getAnnotation(CouponMetered.class);
        return annotation.version();
    }

    // 현재 실행 중인 메서드의 이름(예: issueCoupon 등)을 문자열로 반환한다.
    private String extractOperation(ProceedingJoinPoint joinPoint) {
        return joinPoint.getSignature().getName();
    }
}
