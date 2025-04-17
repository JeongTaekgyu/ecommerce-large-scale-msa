package com.example.pointservice.repository;

import com.example.pointservice.domain.PointBalance;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

public interface PointBalanceRepository extends JpaRepository<PointBalance, Long> {

    @Lock(LockModeType.OPTIMISTIC)
    Optional<PointBalance> findByUserId(Long userId); // 해당 객체가 있을 수도 있고 없을 수도 있어서 Optional로 선언함
}
