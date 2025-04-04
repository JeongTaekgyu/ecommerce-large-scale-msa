package com.example.couponservice.service.v1;

import com.example.couponservice.config.UserIdInterceptor;
import com.example.couponservice.domain.Coupon;
import com.example.couponservice.domain.CouponPolicy;
import com.example.couponservice.dto.v1.CouponDto;
import com.example.couponservice.exception.CouponNotFoundException;
import com.example.couponservice.repository.CouponPolicyRepository;
import com.example.couponservice.repository.CouponRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CouponServiceTest {
    @InjectMocks
    private CouponService couponService;

    @Mock
    private CouponRepository couponRepository;

    @Mock
    private CouponPolicyRepository couponPolicyRepository;

    private CouponPolicy couponPolicy;
    private Coupon coupon;
    private static final Long TEST_USER_ID = 1L;
    private static final Long TEST_COUPON_ID = 1L;
    private static final Long TEST_ORDER_ID = 1L;

    @BeforeEach
    void setUp() {
        couponPolicy = CouponPolicy.builder()
                .id(1L)
                .name("테스트 쿠폰")
                .discountType(CouponPolicy.DiscountType.FIXED_AMOUNT)
                .discountValue(1000)
                .minimumOrderAmount(10000)
                .maximumDiscountAmount(1000)
                .totalQuantity(100)
                .startTime(LocalDateTime.now().minusDays(1))
                .endTime(LocalDateTime.now().plusDays(1))
                .build();

        coupon = Coupon.builder()
                .id(TEST_COUPON_ID)
                .userId(TEST_USER_ID)
                .couponPolicy(couponPolicy)
                .couponCode("TEST123")
                .build();
    }

    @Test
    @DisplayName("쿠폰 발급 성공")
    void issueCoupon_Success() {
        // Given
        CouponDto.IssueRequest request = CouponDto.IssueRequest.builder()
                .couponPolicyId(1L)
                .build();

        when(couponPolicyRepository.findByIdWithLock(any())).thenReturn(Optional.of(couponPolicy));
        when(couponRepository.countByCouponPolicyId(any())).thenReturn(0L);
        when(couponRepository.save(any())).thenReturn(coupon);

        try (MockedStatic<UserIdInterceptor> mockedStatic = mockStatic(UserIdInterceptor.class)) {
            mockedStatic.when(UserIdInterceptor::getCurrentUserId).thenReturn(TEST_USER_ID);

            // When
            CouponDto.Response response = CouponDto.Response.from(couponService.issueCoupon(request));

            // Then
            assertThat(response.getId()).isEqualTo(TEST_COUPON_ID);
            assertThat(response.getUserId()).isEqualTo(TEST_USER_ID);
            verify(couponRepository).save(any());
        }
    }

    @Test
    @DisplayName("쿠폰 사용 성공")
    void useCoupon_Success() {
        // Given
        when(couponRepository.findByIdAndUserId(TEST_COUPON_ID, TEST_USER_ID))
                .thenReturn(Optional.of(coupon));

        try (MockedStatic<UserIdInterceptor> mockedStatic = mockStatic(UserIdInterceptor.class)) {
            mockedStatic.when(UserIdInterceptor::getCurrentUserId).thenReturn(TEST_USER_ID);

            // When
            CouponDto.Response response = CouponDto.Response.from(couponService.useCoupon(TEST_COUPON_ID, TEST_ORDER_ID));

            // Then
            assertThat(response.getId()).isEqualTo(TEST_COUPON_ID);
            assertThat(response.getOrderId()).isEqualTo(TEST_ORDER_ID);
            assertThat(response.getStatus()).isEqualTo(Coupon.Status.USED);
        }
    }

    @Test
    @DisplayName("쿠폰 사용 실패 - 쿠폰이 존재하지 않거나 권한 없음")
    void useCoupon_Fail_NotFoundOrUnauthorized() {
        // Given
        when(couponRepository.findByIdAndUserId(TEST_COUPON_ID, TEST_USER_ID))
                .thenReturn(Optional.empty());

        try (MockedStatic<UserIdInterceptor> mockedStatic = mockStatic(UserIdInterceptor.class)) {
            mockedStatic.when(UserIdInterceptor::getCurrentUserId).thenReturn(TEST_USER_ID);

            // When & Then
            assertThatThrownBy(() -> couponService.useCoupon(TEST_COUPON_ID, TEST_ORDER_ID))
                    .isInstanceOf(CouponNotFoundException.class)
                    .hasMessage("쿠폰을 찾을 수 없거나 접근 권한이 없습니다.");
        }
    }

    @Test
    @DisplayName("쿠폰 취소 성공")
    void cancelCoupon_Success() {
        // Given
        coupon.use(TEST_ORDER_ID); // Set coupon as USED first
        when(couponRepository.findByIdAndUserId(TEST_COUPON_ID, TEST_USER_ID))
                .thenReturn(Optional.of(coupon));

        try (MockedStatic<UserIdInterceptor> mockedStatic = mockStatic(UserIdInterceptor.class)) {
            mockedStatic.when(UserIdInterceptor::getCurrentUserId).thenReturn(TEST_USER_ID);

            // When
            CouponDto.Response response = CouponDto.Response.from(couponService.cancelCoupon(TEST_COUPON_ID));

            // Then
            assertThat(response.getId()).isEqualTo(TEST_COUPON_ID);
            assertThat(response.getStatus()).isEqualTo(Coupon.Status.CANCELLED);
        }
    }

    @Test
    @DisplayName("쿠폰 취소 실패 - 쿠폰이 존재하지 않거나 권한 없음")
    void cancelCoupon_Fail_NotFoundOrUnauthorized() {
        // Given
        when(couponRepository.findByIdAndUserId(TEST_COUPON_ID, TEST_USER_ID))
                .thenReturn(Optional.empty());

        try (MockedStatic<UserIdInterceptor> mockedStatic = mockStatic(UserIdInterceptor.class)) {
            mockedStatic.when(UserIdInterceptor::getCurrentUserId).thenReturn(TEST_USER_ID);

            // When & Then
            assertThatThrownBy(() -> couponService.cancelCoupon(TEST_COUPON_ID))
                    .isInstanceOf(CouponNotFoundException.class)
                    .hasMessage("쿠폰을 찾을 수 없거나 접근 권한이 없습니다.");
        }
    }
}