package com.example.apigateway.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/fallback")
public class FallbackController {

    // 원래 api가 장애 발생시 CircuitBreaker가 OPEN 이되어 해당 api를 요청한다.
    @GetMapping("/users")
    public Mono<Map<String, Object>> userFallback() {
        return Mono.just(Map.of("status", "down"));
    }
}
