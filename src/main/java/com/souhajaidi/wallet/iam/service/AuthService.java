package com.souhajaidi.wallet.iam.service;

import com.souhajaidi.wallet.audit.service.AuditService;
import com.souhajaidi.wallet.common.error.EmailAlreadyRegisteredException;
import com.souhajaidi.wallet.iam.api.dto.LoginRequest;
import com.souhajaidi.wallet.iam.api.dto.RegisterRequest;
import com.souhajaidi.wallet.iam.api.dto.TokenResponse;
import com.souhajaidi.wallet.iam.domain.Role;
import com.souhajaidi.wallet.iam.domain.User;
import com.souhajaidi.wallet.iam.repository.UserRepository;
import com.souhajaidi.wallet.iam.security.JwtService;
import java.util.UUID;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuditService audit;

    public AuthService(
            UserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService, AuditService audit) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.audit = audit;
    }

    @Transactional
    public User register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase();
        if (users.existsByEmailIgnoreCase(email)) {
            throw new EmailAlreadyRegisteredException();
        }

        User user = new User(
                email, passwordEncoder.encode(request.password()), request.fullName().trim(), Role.USER);
        users.save(user);
        audit.recordSuccess("USER_REGISTERED", user.getId(), user.getId(), "role=USER");
        return user;
    }

    @Transactional
    public TokenResponse login(LoginRequest request) {
        User user = users.findByEmailIgnoreCase(request.email().trim())
                .filter(candidate -> passwordEncoder.matches(request.password(), candidate.getPasswordHash()))
                .filter(User::isEnabled)
                .orElseThrow(() -> {
                    // One failure path for unknown email, wrong password and disabled
                    // account, so the response cannot distinguish between them.
                    audit.recordFailure("LOGIN", null, null, "rejected credentials");
                    return new BadCredentialsException("invalid credentials");
                });

        audit.recordSuccess("LOGIN", user.getId(), user.getId(), null);
        return TokenResponse.bearer(jwtService.issue(user), jwtService.ttlSeconds());
    }

    @Transactional(readOnly = true)
    public User require(UUID userId) {
        return users.findById(userId)
                .orElseThrow(() -> new IllegalStateException("authenticated user " + userId + " no longer exists"));
    }
}
