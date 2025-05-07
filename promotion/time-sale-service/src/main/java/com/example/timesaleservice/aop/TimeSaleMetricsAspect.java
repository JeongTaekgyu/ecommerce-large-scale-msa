package com.example.timesaleservice.aop;

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
public class TimeSaleMetricsAspect {
    private final MeterRegistry registry;

    // @TimeSaleMetered 어노테이션이 붙은 메서드를 가로채서 실행 전후에 작업을 수행한다.
    @Around("@annotation(TimeSaleMetered)")
    public Object measureTimeSaleOperation(ProceedingJoinPoint joinPoint) throws Throwable {
        Timer.Sample sample = Timer.start();
        String version = extractVersion(joinPoint);
        String operation = extractOperation(joinPoint);

        try {
            Object result = joinPoint.proceed(); // 이 줄에서 실제로 @TimeSaleMetered 가 붙은 메서드가 실행된다.

            // 타임세일 처리 성공 메트릭
            Counter.builder("time.sale.operation.success")
                    .tag("version", version)
                    .tag("operation", operation)
                    .register(registry)
                    .increment();

            // 메서드의 실행 시간(duration) 을 기록한다.
            sample.stop(Timer.builder("time.sale.operation.duration")
                    .tag("version", version)
                    .tag("operation", operation)
                    .register(registry));

            return result;
        } catch (Exception e) {
            // 타임세일 처리 실패 메트릭
            Counter.builder("time.sale.operation.failure")
                    .tag("version", version)
                    .tag("operation", operation)
                    .tag("error", e.getClass().getSimpleName())
                    .register(registry)
                    .increment();
            throw e;
        }
    }

    private String extractVersion(ProceedingJoinPoint joinPoint) {
        TimeSaleMetered annotation = ((MethodSignature) joinPoint.getSignature())
                .getMethod()
                .getAnnotation(TimeSaleMetered.class);
        return annotation.version();
    }

    private String extractOperation(ProceedingJoinPoint joinPoint) {
        return joinPoint.getSignature().getName();
    }
}
