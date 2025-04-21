package com.example.pointservicebatch.repository;

import com.example.pointservicebatch.domain.Point;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PointRepository extends JpaRepository<Point, Long> {
}
