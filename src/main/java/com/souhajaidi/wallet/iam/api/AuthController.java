package com.souhajaidi.wallet.iam.api;

import com.souhajaidi.wallet.iam.api.dto.LoginRequest;
import com.souhajaidi.wallet.iam.api.dto.RegisterRequest;
import com.souhajaidi.wallet.iam.api.dto.TokenResponse;
import com.souhajaidi.wallet.iam.api.dto.UserResponse;
import com.souhajaidi.wallet.iam.security.CurrentUser;
import com.souhajaidi.wallet.iam.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Account creation and token issuance")
public class AuthController {

    private final AuthService authService;
    private final CurrentUser currentUser;

    public AuthController(AuthService authService, CurrentUser currentUser) {
        this.authService = authService;
        this.currentUser = currentUser;
    }

    @PostMapping("/register")
    @SecurityRequirements
    @Operation(summary = "Create an account")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Account created"),
        @ApiResponse(responseCode = "400", description = "Validation failed", content = @io.swagger.v3.oas.annotations.media.Content),
        @ApiResponse(responseCode = "409", description = "Email cannot be registered", content = @io.swagger.v3.oas.annotations.media.Content)
    })
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        UserResponse body = UserResponse.from(authService.register(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @PostMapping("/login")
    @SecurityRequirements
    @Operation(summary = "Exchange credentials for an access token")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Token issued"),
        @ApiResponse(responseCode = "401", description = "Invalid credentials", content = @io.swagger.v3.oas.annotations.media.Content)
    })
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @GetMapping("/me")
    @Operation(summary = "Read the authenticated user's profile")
    public UserResponse me() {
        return UserResponse.from(authService.require(currentUser.requireId()));
    }
}
