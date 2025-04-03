package com.example.couponservice.repository;

import com.example.couponservice.domain.CouponPolicy;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface CouponPolicyRepository extends JpaRepository<CouponPolicy, Long> {

    // 쿠폰을 발급할때 해당 row에 대해서 쓰기락을 걸거다.
    // 비관적락
    // 해당 데이터에 접근하는 트랜잭션에 대해서 쓰기 잠금을 한다. 다른 트랜잭션이 수정,삭제 하려고 하면 대기 상태가 된다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT cp FROM CouponPolicy cp WHERE cp.id = :id")
    Optional<CouponPolicy> findByIdWithLock(Long id);
}
