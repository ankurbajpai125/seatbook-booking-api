package com.ankur.seatbook.config;

import com.ankur.seatbook.domain.Role;
import com.ankur.seatbook.domain.User;
import com.ankur.seatbook.repo.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Creates the first admin account on startup if it does not exist yet. */
@Component
public class AdminSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminSeeder.class);

    private final UserRepository userRepo;
    private final PasswordEncoder encoder;
    private final String email;
    private final String password;

    public AdminSeeder(UserRepository userRepo, PasswordEncoder encoder,
                       @Value("${app.admin.email}") String email,
                       @Value("${app.admin.password}") String password) {
        this.userRepo = userRepo;
        this.encoder = encoder;
        this.email = email.trim().toLowerCase();
        this.password = password;
    }

    @Override
    public void run(String... args) {
        if (!userRepo.existsByEmail(email)) {
            userRepo.save(new User("Admin", email, encoder.encode(password), Role.ADMIN));
            log.info("Seeded admin account {}", email);
        }
    }
}
