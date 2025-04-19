package com.example.pointservice.service.v2;

import com.example.pointservice.domain.Point;
import com.example.pointservice.domain.PointBalance;
import com.example.pointservice.domain.PointType;
import com.example.pointservice.repository.PointBalanceRepository;
import com.example.pointservice.repository.PointRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RMap;
import org.redisson.api.RedissonClient;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PointRedisServiceTest {
    @Mock
    private PointBalanceRepository pointBalanceRepository;

    @Mock
    private PointRepository pointRepository;

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RLock rLock;

    @Mock
    private RMap<String, Long> rMap;

    @InjectMocks
    private PointRedisService pointRedisService;

    private static final Long USER_ID = 1L;
    private static final Long POINT_ID = 1L;
    private static final Long AMOUNT = 1000L;
    private static final String DESCRIPTION = "Test description";

    @Test
    @DisplayName("포인트 적립 성공")
    void earnPointsSuccess() throws InterruptedException {
        // given
        setupLockBehavior();
        setupMapBehavior();

        PointBalance pointBalance = PointBalance.builder()
                .userId(USER_ID)
                .balance(0L)
                .build();
        Point expectedPoint = Point.builder()
                .userId(USER_ID)
                .amount(AMOUNT)
                .type(PointType.EARNED)
                .description(DESCRIPTION)
                .balanceSnapshot(AMOUNT)
                .pointBalance(pointBalance)
                .build();

        given(pointBalanceRepository.findByUserId(USER_ID)).willReturn(Optional.of(pointBalance));
        given(pointBalanceRepository.save(any(PointBalance.class))).willReturn(pointBalance);
        given(pointRepository.save(any(Point.class))).willReturn(expectedPoint);

        // when
        Point result = pointRedisService.earnPoints(USER_ID, AMOUNT, DESCRIPTION);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getAmount()).isEqualTo(AMOUNT);
        assertThat(result.getType()).isEqualTo(PointType.EARNED);
        // rMap.fastPut(USER_ID.toString(), AMOUNT) 메서드가 실제로 한 번 호출되었는지 검증, eq는 정확히 저값이어야 함을 명시
        verify(rMap).fastPut(eq(USER_ID.toString()), eq(AMOUNT));
    }

    @Test
    @DisplayName("포인트 사용 성공")
    void usePointsSuccess() throws InterruptedException {
        // given
        setupLockBehavior();
        setupMapBehavior();

        PointBalance pointBalance = PointBalance.builder()
                .userId(USER_ID)
                .balance(AMOUNT)
                .build();
        Point expectedPoint = Point.builder()
                .userId(USER_ID)
                .amount(AMOUNT)
                .type(PointType.USED) // 사용
                .description(DESCRIPTION)
                .balanceSnapshot(0L)
                .pointBalance(pointBalance)
                .build();

        given(rMap.get(USER_ID.toString())).willReturn(AMOUNT);
        given(pointBalanceRepository.findByUserId(USER_ID)).willReturn(Optional.of(pointBalance));
        given(pointBalanceRepository.save(any(PointBalance.class))).willReturn(pointBalance);
        given(pointRepository.save(any(Point.class))).willReturn(expectedPoint);

        // when
        Point result = pointRedisService.usePoints(USER_ID, AMOUNT, DESCRIPTION);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getAmount()).isEqualTo(AMOUNT);
        assertThat(result.getType()).isEqualTo(PointType.USED);
        verify(rMap).fastPut(eq(USER_ID.toString()), eq(0L));
    }

    @Test
    @DisplayName("잔액 부족으로 포인트 사용 실패")
    void usePointsFailInsufficientBalance() throws InterruptedException {
        // given
        setupLockBehavior();
        setupMapBehavior();
        given(rMap.get(USER_ID.toString())).willReturn(500L);

        // 500 포인트일때 1000 포인트 사용 시도
        // when & then
        assertThatThrownBy(() -> pointRedisService.usePoints(USER_ID, AMOUNT, DESCRIPTION))
                // 테스트에서 던져진 에러가 IllegalArgumentException인지 확인
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Insufficient balance");
    }

    @Test
    @DisplayName("포인트 취소 성공 - 적립 취소")
    void cancelEarnedPointsSuccess() throws InterruptedException {
        // given
        setupLockBehavior();
        setupMapBehavior();

        PointBalance pointBalance = PointBalance.builder()
                .userId(USER_ID)
                .balance(AMOUNT)
                .build();
        Point originalPoint = Point.builder()
                .userId(USER_ID)
                .amount(AMOUNT)
                .type(PointType.EARNED)
                .pointBalance(pointBalance)
                .build();
        Point expectedPoint = Point.builder()
                .userId(USER_ID)
                .amount(AMOUNT)
                .type(PointType.CANCELED) // 취소
                .description(DESCRIPTION)
                .balanceSnapshot(0L)
                .pointBalance(pointBalance)
                .build();

        given(pointRepository.findById(POINT_ID)).willReturn(Optional.of(originalPoint));
        given(pointBalanceRepository.save(any(PointBalance.class))).willReturn(pointBalance);
        given(pointRepository.save(any(Point.class))).willReturn(expectedPoint);

        // when
        Point result = pointRedisService.cancelPoints(POINT_ID, DESCRIPTION);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getType()).isEqualTo(PointType.CANCELED);
        verify(rMap).fastPut(eq(USER_ID.toString()), eq(0L));
    }

    @Test
    @DisplayName("이미 취소된 포인트 취소 시도 실패")
    void cancelAlreadyCanceledPointsFail() throws InterruptedException {
        // given
        setupLockBehavior();
        Point originalPoint = Point.builder()
                .userId(USER_ID)
                .amount(AMOUNT)
                .type(PointType.CANCELED)
                .build();

        given(pointRepository.findById(POINT_ID)).willReturn(Optional.of(originalPoint));

        // when & then
        assertThatThrownBy(() -> pointRedisService.cancelPoints(POINT_ID, DESCRIPTION))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Already cancelled point");
    }

    @Test
    @DisplayName("분산 락 획득 실패")
    void lockAcquisitionFailure() throws InterruptedException {
        // given
        given(redissonClient.getLock(anyString())).willReturn(rLock);
        given(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).willReturn(false);

        // when & then
        assertThatThrownBy(() -> pointRedisService.earnPoints(USER_ID, AMOUNT, DESCRIPTION))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Failed to acquire lock for user: " + USER_ID);
    }

    @Test
    @DisplayName("캐시된 잔액 조회 성공")
    void getBalanceFromCache() {
        // given
        setupMapBehavior();
        given(rMap.get(USER_ID.toString())).willReturn(AMOUNT);

        // when
        Long balance = pointRedisService.getBalance(USER_ID);

        // then
        assertThat(balance).isEqualTo(AMOUNT);
        verify(pointBalanceRepository, never()).findByUserId(any());
        // 해당 메서드가 한 번도 호출되지 않았다는 조건을 검증하기 위해 never()를 사용함
    }

    @Test
    @DisplayName("DB에서 잔액 조회 후 캐시 업데이트")
    void getBalanceFromDB() {
        // given
        setupMapBehavior();
        PointBalance pointBalance = PointBalance.builder()
                .userId(USER_ID)
                .balance(AMOUNT)
                .build();

        given(rMap.get(USER_ID.toString())).willReturn(null);
        given(pointBalanceRepository.findByUserId(USER_ID)).willReturn(Optional.of(pointBalance));

        // when
        Long balance = pointRedisService.getBalance(USER_ID);

        // then
        assertThat(balance).isEqualTo(AMOUNT);
        verify(rMap).fastPut(eq(USER_ID.toString()), eq(AMOUNT));
    }

    private void setupLockBehavior() throws InterruptedException {
        // rlock 의 mock 객체를 반환
        given(redissonClient.getLock(anyString())).willReturn(rLock);
        // tryLock 메서드가 true를 반환하도록 가정
        given(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).willReturn(true);
    }

    private void setupMapBehavior() {
        // mock 객체인 rMap을 반환하게 만듦
        given(redissonClient.<String, Long>getMap(anyString())).willReturn(rMap);
    }
}