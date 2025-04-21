package com.example.pointservicebatch.repository;

import com.example.pointservicebatch.domain.PointBalance;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PointBalanceRepository extends JpaRepository<PointBalance, Long> {
}
