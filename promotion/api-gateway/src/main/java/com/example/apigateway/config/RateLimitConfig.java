package com.example.apigateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

@Configuration
public class RateLimitConfig {
    // Redis 기반 요청 제한 설정
    @Bean
    public RedisRateLimiter redisRateLimiter() {
        // replenishRate: 초당 허용되는 요청 수
        // burstCapacity: 최대 누적 가능한 요청 수
        return new RedisRateLimiter(10, 20);
    }

    // 요청을 제한할 기준 설정 (사용자 ID 있으면 ID, 없으면 IP 기준)
    @Bean
    public KeyResolver userKeyResolver() {
        // 여기서 사용되는 KeyResolver는 요청이 들어왔을 때 어떤 값을 키값으로 잡고 요청을 카운트하는지에 대한 부분
        return exchange -> Mono.just(
                exchange.getRequest().getHeaders().getFirst("X-User-ID") != null ?
                        exchange.getRequest().getHeaders().getFirst("X-User-ID") : // 있으면 사용
                        exchange.getRequest().getRemoteAddress().getAddress().getHostAddress() // 없으면 IP 주소 사용
        );
    }
}
