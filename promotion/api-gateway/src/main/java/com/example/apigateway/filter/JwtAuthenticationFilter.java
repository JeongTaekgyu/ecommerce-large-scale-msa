package com.example.apigateway.filter;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.cloud.client.loadbalancer.reactive.ReactorLoadBalancerExchangeFilterFunction;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Map;

// JwtAuthenticationFilter를 사용하기 위해서 @Component 을 추가하면 실제 게이트웨이가 뜰 때
// JwtAuthenticationFilter 컴포넌트를 찾아서 자동으로 등록이 된다.
@Component
public class JwtAuthenticationFilter extends AbstractGatewayFilterFactory<JwtAuthenticationFilter.Config> {

    @LoadBalanced
    private final WebClient webClient;

    //  Spring Cloud Gateway에서 JWT 인증을 수행하는 필터
    public JwtAuthenticationFilter(ReactorLoadBalancerExchangeFilterFunction lbFunction) {
        super(Config.class);
        this.webClient = WebClient.builder()
                .filter(lbFunction)
                .baseUrl("http://user-service")
                .build();
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            String authHeader = exchange.getRequest().getHeaders().getFirst("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);
                return validateToken(token)
                        // validateToken 으로 가져온 userId를 가지고 exchange 에 해더를 넣어준다.
                        .flatMap(userId -> proceedWithUserId(userId, exchange, chain))
                        .switchIfEmpty(chain.filter(exchange)) // If token is invalid, continue without setting userId
                        .onErrorResume(e -> handleAuthenticationError(exchange, e)); // Handle errors
            }

            return chain.filter(exchange);
        };
    }

    private Mono<Void> handleAuthenticationError(ServerWebExchange exchange, Throwable e) {
        // 인증의 문제가 있으면 UNAUTHORIZED(401)상태코드를 반환한다.
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }

    private Mono<Void> proceedWithUserId(Long userId, ServerWebExchange exchange, GatewayFilterChain chain) {
        // 백엔드에 있는 컴포넌트들이 X-USER-ID를 전달 받을 수 있게 해당 userId 를 주입해주는 부분 - 그러면 UserController에서 헤더에서 X-USER-ID를 사욜한다.
        exchange.getRequest().mutate().header("X-USER-ID", String.valueOf(userId));
        return chain.filter(exchange); // chain에 filter를 추가한다.
    }

    private Mono<Long> validateToken(String token) {
        return webClient.post()
                .uri("/auth/validate")
                .bodyValue("{\"token\":\"" + token + "\"}")
                .header("Content-Type", "application/json")
                .retrieve()
                .bodyToMono(Map.class)
                .map(response -> Long.valueOf(response.get("id").toString()) );
    }

    public static class Config {
    }
}
