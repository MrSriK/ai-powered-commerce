package com.mrsrik.securityfixture;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Test-only endpoints for the user security chain. Lives outside the scanned
 * application package and is only brought in with {@code @Import}.
 */
@RestController
public class SecurityTestController {

    @GetMapping("/api/products/_ping")
    public String publicPing() {
        return "pong";
    }

    @GetMapping("/api/_securitytest/protected")
    public String protectedRoute() {
        return "ok";
    }

    @GetMapping("/api/_securitytest/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public String adminRoute() {
        return "admin";
    }
}
