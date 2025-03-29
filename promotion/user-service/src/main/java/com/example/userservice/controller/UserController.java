package com.example.userservice.controller;

import com.example.userservice.dto.UserDto;
import com.example.userservice.entity.User;
import com.example.userservice.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {
    private final UserService userService;

    @Autowired
    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/signup")
    public ResponseEntity<?> createUser(
            @RequestBody UserDto.SignupRequest request) {
        User user = userService.createUser(request.getEmail(), request.getPassword(), request.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(UserDto.Response.from(user));
    }

    @GetMapping("/me") // api gateway로 부터 전달 받은 X-USER-ID 헤더를 이용하여 사용자 정보를 조회
    public ResponseEntity<?> getProfile(@RequestHeader("X-USER-ID") Integer userId) {
        User user = userService.getUserById(userId);
        return ResponseEntity.ok(UserDto.Response.from(user));
    }

    @PutMapping("/me")
    public ResponseEntity<?> updateProfile(
            @RequestHeader("X-USER-ID") Integer userId,
            @RequestBody UserDto.UpdateRequest request) {
        User user = userService.updateUser(userId, request.getName());
        return ResponseEntity.ok(UserDto.Response.from(user));
    }
}
