package com.example.pointservice.service.v2;

import com.example.pointservice.aop.PointMetered;
import com.example.pointservice.domain.Point;
import com.example.pointservice.domain.PointBalance;
import com.example.pointservice.domain.PointType;
import com.example.pointservice.repository.PointBalanceRepository;
import com.example.pointservice.repository.PointRepository;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RMap;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

/**
 * Redis를 활용한 포인트 서비스 V2 구현
 * - Redisson 분산 락을 통한 동시성 제어
 * - Redis 캐시를 통한 성능 최적화
 */
@Service
@RequiredArgsConstructor
public class PointRedisService {
    // Redis key prefix 및 설정값
    private static final String POINT_BALANCE_MAP = "point:balance"; // 포인트 잔액을 저장하기 위한 키 값
    private static final String POINT_LOCK_PREFIX = "point:lock:";
    private static final long LOCK_WAIT_TIME = 3L;
    private static final long LOCK_LEASE_TIME = 3L;

    private final PointBalanceRepository pointBalanceRepository;
    private final PointRepository pointRepository;
    private final RedissonClient redissonClient;

    /**
     * 포인트 적립 처리
     * 1. 분산 락 획득
     * 2. 캐시된 잔액 조회 (없으면 DB에서 조회)
     * 3. 포인트 잔액 증가
     * 4. DB 저장 및 캐시 업데이트
     * 5. 포인트 이력 저장
     */
    @Transactional
    @PointMetered(version = "v2")
    public Point earnPoints(Long userId, Long amount, String description) {
        // 분산 락 획득 - // 분산 락을 획득하기 위한 준비 작업
        RLock lock = redissonClient.getLock(POINT_LOCK_PREFIX + userId);
        try {
            // 락 획득 시도
            boolean locked = lock.tryLock(LOCK_WAIT_TIME, LOCK_LEASE_TIME, TimeUnit.SECONDS);
            if (!locked) {
                // 락 획득 실패
                throw new IllegalStateException("Failed to acquire lock for user: " + userId);
            }

            // 캐시된 잔액 조회
            Long currentBalance = getBalanceFromCache(userId);
            if (currentBalance == null) {
                // 캐시된 잔액이 없으면 DB에서 조회
                currentBalance = getBalanceFromDB(userId);
                // 캐시 업데이트
                updateBalanceCache(userId, currentBalance);
            }

            // 포인트 잔액 증가
            // 만약에 db에 없으면 포인트 잔액을 0으로 초기화
            PointBalance pointBalance = pointBalanceRepository.findByUserId(userId)
                    .orElseGet(() -> PointBalance.builder()
                            .userId(userId)
                            .balance(0L)
                            .build());

            // db에 업데이트
            pointBalance.addBalance(amount);
            pointBalance = pointBalanceRepository.save(pointBalance);
            // 캐시 업데이트
            updateBalanceCache(userId, pointBalance.getBalance());

            // 포인트 이력 저장
            Point point = Point.builder()
                    .userId(userId)
                    .amount(amount)
                    .type(PointType.EARNED)
                    .description(description)
                    .balanceSnapshot(pointBalance.getBalance())
                    .pointBalance(pointBalance)
                    .build();
            return pointRepository.save(point);
        } catch (InterruptedException e) {
            // 락 획득 중 인터럽트 발생
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Lock acquisition was interrupted", e);
        } finally {
            // 락 해제 - 현재 스레드가 락을 보유하고 있으면 락을 해제한다
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 포인트 사용 처리
     * 1. 분산 락 획득
     * 2. 캐시된 잔액 조회 (없으면 DB에서 조회)
     * 3. 잔액 체크
     * 4. 포인트 잔액 감소
     * 5. DB 저장 및 캐시 업데이트
     * 6. 포인트 이력 저장
     */
    @Transactional
    @PointMetered(version = "v2")
    public Point usePoints(Long userId, Long amount, String description) {
        // 분산 락 획득 - // 분산 락을 획득하기 위한 준비 작업
        RLock lock = redissonClient.getLock(POINT_LOCK_PREFIX + userId);
        try {
            // 락 획득 시도
            boolean locked = lock.tryLock(LOCK_WAIT_TIME, LOCK_LEASE_TIME, TimeUnit.SECONDS);
            if (!locked) {
                // 락 획득 실패
                throw new IllegalStateException("Failed to acquire lock for user: " + userId);
            }

            // 캐시된 잔액 조회 및 체크
            Long currentBalance = getBalanceFromCache(userId);
            if (currentBalance == null) {
                // 캐시된 잔액이 없으면 DB에서 조회
                currentBalance = getBalanceFromDB(userId);
                // 캐시 업데이트
                updateBalanceCache(userId, currentBalance);
            }

            if (currentBalance < amount) {
                // 잔액 부족
                throw new IllegalArgumentException("Insufficient balance");
            }

            // 포인트 잔액 감소
            PointBalance pointBalance = pointBalanceRepository.findByUserId(userId)
                    .orElseThrow(() -> new IllegalArgumentException("User not found"));

            pointBalance.subtractBalance(amount);
            pointBalance = pointBalanceRepository.save(pointBalance);
            // 캐시 업데이트
            updateBalanceCache(userId, pointBalance.getBalance());

            // 포인트 이력 저장
            Point point = Point.builder()
                    .userId(userId)
                    .amount(amount)
                    .type(PointType.USED)
                    .description(description)
                    .balanceSnapshot(pointBalance.getBalance())
                    .pointBalance(pointBalance)
                    .build();
            return pointRepository.save(point);
        } catch (InterruptedException e) {
            // 락 획득 중 인터럽트 발생
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Lock acquisition was interrupted", e);
        } finally {
            // 락 해제 - 현재 스레드가 락을 보유하고 있으면 락을 해제한다
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 포인트 취소 처리
     * 1. 원본 포인트 이력 조회
     * 2. 분산 락 획득
     * 3. 취소 가능 여부 확인
     * 4. 포인트 잔액 원복 (적립 취소는 차감, 사용 취소는 증가)
     * 5. DB 저장 및 캐시 업데이트
     * 6. 취소 이력 저장
     */
    @Transactional
    public Point cancelPoints(Long pointId, String description) {
        // 원본 포인트 이력 조회
        Point originalPoint = pointRepository.findById(pointId)
                .orElseThrow(() -> new IllegalArgumentException("Point not found"));

        Long userId = originalPoint.getUserId();
        // 분산 락 획득 - // 분산 락을 획득하기 위한 준비 작업
        RLock lock = redissonClient.getLock(POINT_LOCK_PREFIX + userId);

        try {
            // 락 획득 시도
            boolean locked = lock.tryLock(LOCK_WAIT_TIME, LOCK_LEASE_TIME, TimeUnit.SECONDS);
            if (!locked) {
                // 락 획득 실패
                throw new IllegalStateException("Failed to acquire lock for user: " + userId);
            }

            // 취소 가능 여부 확인
            if (originalPoint.getType() == PointType.CANCELED) {
                // 이미 취소된 포인트
                throw new IllegalArgumentException("Already cancelled point");
            }

            // 포인트 잔액 원복
            PointBalance pointBalance = originalPoint.getPointBalance();
            if (originalPoint.getType() == PointType.EARNED) { // 적립 취소인 경우
                pointBalance.subtractBalance(originalPoint.getAmount());
            } else { // 사용 취소인 경우
                pointBalance.addBalance(originalPoint.getAmount());
            }

            pointBalance = pointBalanceRepository.save(pointBalance);
            // 캐시 업데이트
            updateBalanceCache(userId, pointBalance.getBalance());

            // 취소 이력 저장
            Point point = Point.builder()
                    .userId(userId)
                    .amount(originalPoint.getAmount())
                    .type(PointType.CANCELED)
                    .description(description)
                    .balanceSnapshot(pointBalance.getBalance())
                    .pointBalance(pointBalance)
                    .build();
            return pointRepository.save(point);
        } catch (InterruptedException e) {
            // 락 획득 중 인터럽트 발생
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Lock acquisition was interrupted", e);
        } finally {
            // 락 해제 - 현재 스레드가 락을 보유하고 있으면 락을 해제한다
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 포인트 잔액 조회
     * 1. 캐시에서 조회
     * 2. 캐시 없으면 DB에서 조회 후 캐시 업데이트
     */
    @Transactional(readOnly = true)
    public Long getBalance(Long userId) {
        Long cachedBalance = getBalanceFromCache(userId);
        if (cachedBalance != null) {
            return cachedBalance;
        }

        Long dbBalance = getBalanceFromDB(userId);
        updateBalanceCache(userId, dbBalance);
        return dbBalance;
    }

    /**
     * Redis 캐시에서 잔액 조회
     */
    private Long getBalanceFromCache(Long userId) {
        RMap<String, Long> balanceMap = redissonClient.getMap(POINT_BALANCE_MAP);
        return balanceMap.get(String.valueOf(userId));
    }

    /**
     * DB에서 잔액 조회
     */
//    @Transactional(readOnly = true)
    private Long getBalanceFromDB(Long userId) {
        return pointBalanceRepository.findByUserId(userId)
                .map(PointBalance::getBalance)
                .orElse(0L);
    }

    /**
     * Redis 캐시 잔액 업데이트
     */
    private void updateBalanceCache(Long userId, Long balance) {
        RMap<String, Long> balanceMap = redissonClient.getMap(POINT_BALANCE_MAP);
        // fastPut은 RMap에 key-value 쌍을 저장한다.
        // put 과의 차이점은 fastPut은 동기화 작업을 최소화하여 더 빠른 성능을 제공한다.
        balanceMap.fastPut(String.valueOf(userId), balance);
    }

}
