package com.example.userservice.repository;

import com.example.userservice.entity.User;
import com.example.userservice.entity.UserLoginHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserLoginHistoryRepository extends JpaRepository<UserLoginHistory, Integer> {
    List<UserLoginHistory> findByUserOrderByLoginTimeDesc(User user);
}
