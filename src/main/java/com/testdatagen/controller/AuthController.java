package com.testdatagen.controller;

import com.testdatagen.model.dto.LoginRequest;
import com.testdatagen.model.dto.LoginResponse;
import com.testdatagen.model.dto.RegisterRequest;
import com.testdatagen.security.CurrentUserContext;
import com.testdatagen.service.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@Valid @RequestBody RegisterRequest request) {
        Map<String, Object> user = authService.register(request);
        return ResponseEntity.ok(user);
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me() {
        Long userId = CurrentUserContext.getUserId();
        Map<String, Object> user = authService.getCurrentUser(userId);
        return ResponseEntity.ok(user);
    }
}
