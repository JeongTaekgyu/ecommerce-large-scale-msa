package com.example.pointservice.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class UserIdInterceptor implements HandlerInterceptor {
    private static final String USER_ID_HEADER = "X-USER-ID";
    private static final ThreadLocal<Long> currentUserId = new ThreadLocal<>(); //
    // 참고로 api-gateway로 부터 x-user-Id를 받는다.

    // 클라이언트 → 인터셉터 실행 (preHandle) → 컨트롤러 실행
    //
    // 컨트롤러 실행 후 → 인터셉터 실행 (postHandle)
    //
    // 응답이 만들어진 후 → 인터셉터 실행 (afterCompletion) → 클라이언트

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String userIdStr = request.getHeader(USER_ID_HEADER);
        System.out.println("X-USER-ID: [" + userIdStr + "]");
        if (userIdStr == null || userIdStr.isEmpty()) {
            throw new IllegalStateException("X-USER-ID header is required");
        }
        try { // userIdStr 가 있다면 ThreadLocal의 currentUserId에 저장하나다.
            currentUserId.set(Long.parseLong(userIdStr));
            return true;
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Invalid X-USER-ID format");
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        currentUserId.remove();
    }

    public static Long getCurrentUserId() {
        Long userId = currentUserId.get();
        if (userId == null) {
            throw new IllegalStateException("User ID not found in current context");
        }
        return userId;
    }
}
