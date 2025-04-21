package com.example.pointservicebatch.repository;

import com.example.pointservicebatch.domain.DailyPointReport;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DailyPointReportRepository extends JpaRepository<DailyPointReport, Long> {
}
