package io.drive.api.controller;

import io.drive.api.dto.AuthDtos.AuthResponse;
import io.drive.api.dto.AuthDtos.LoginRequest;
import io.drive.api.dto.AuthDtos.RegisterRequest;
import io.drive.api.model.User;
import io.drive.api.security.JwtService;
import io.drive.api.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService users;
    private final JwtService jwt;

    public AuthController(UserService users, JwtService jwt) {
        this.users = users;
        this.jwt = jwt;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest req) {
        User user = users.register(req.username().trim(), req.password());
        String token = jwt.generate(user.getId(), user.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new AuthResponse(token, user.getUsername()));
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest req) {
        User user = users.authenticate(req.username().trim(), req.password());
        String token = jwt.generate(user.getId(), user.getUsername());
        return new AuthResponse(token, user.getUsername());
    }
}
