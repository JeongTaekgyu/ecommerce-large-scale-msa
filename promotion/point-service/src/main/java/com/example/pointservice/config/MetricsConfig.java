package com.example.pointservice.config;

import com.example.pointservice.aop.PointMetricsAspect;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration
@EnableAspectJAutoProxy // 해당 어노테이션을 통해 @Aspect 가 붙은 클래스가 동작할 수 있게 된다. (프록시 생성).
public class MetricsConfig {
    // pointMetricsAspect 라는 AOP 클래스를 빈으로 등록한다.
    @Bean
    public PointMetricsAspect pointMetricsAspect(MeterRegistry registry) {
        return new PointMetricsAspect(registry);
    }
}
