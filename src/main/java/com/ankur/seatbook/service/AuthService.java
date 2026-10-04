package com.ankur.seatbook.service;

import com.ankur.seatbook.config.JwtService;
import com.ankur.seatbook.domain.Role;
import com.ankur.seatbook.domain.User;
import com.ankur.seatbook.repo.UserRepository;
import com.ankur.seatbook.web.Dtos.AuthResponse;
import com.ankur.seatbook.web.Dtos.LoginRequest;
import com.ankur.seatbook.web.Dtos.RegisterRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepo;
    private final PasswordEncoder encoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepo, PasswordEncoder encoder, JwtService jwtService) {
        this.userRepo = userRepo;
        this.encoder = encoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest r) {
        String email = r.email().trim().toLowerCase();
        if (userRepo.existsByEmail(email)) {
            throw ApiException.conflict("Email is already registered");
        }
        // Everyone who registers is a USER; admins are seeded at startup (see AdminSeeder).
        User user = userRepo.save(new User(r.name().trim(), email, encoder.encode(r.password()), Role.USER));
        return new AuthResponse(jwtService.issue(user), user.getName(), user.getRole());
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest r) {
        User user = userRepo.findByEmail(r.email().trim().toLowerCase())
                .filter(u -> encoder.matches(r.password(), u.getPasswordHash()))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password"));
        return new AuthResponse(jwtService.issue(user), user.getName(), user.getRole());
    }
}
