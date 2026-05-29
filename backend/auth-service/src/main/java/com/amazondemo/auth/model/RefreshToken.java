package com.amazondemo.auth.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Refresh Token Entity
 * =====================
 * Stores refresh tokens in the database for validation and rotation.
 *
 * REFRESH TOKEN ROTATION:
 * - Every time a refresh token is used to get a new access token,
 *   the old refresh token is invalidated and a new one is issued.
 * - This limits the window of token theft.
 *
 * WHY STORE IN DB?
 * - Unlike JWT access tokens (stateless), refresh tokens need to be revocable
 * - If user logs out or token is stolen, we can invalidate it in DB
 * - Database check happens only on token refresh (rare), so performance is fine
 */
@Entity
@Table(name = "refresh_tokens")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false, unique = true)
    private String token;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private LocalDateTime expiryDate;

    @Builder.Default
    private boolean revoked = false;

    private LocalDateTime createdAt;

    /** Check if token is expired */
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiryDate);
    }

    /** Check if token is valid (not revoked and not expired) */
    public boolean isValid() {
        return !revoked && !isExpired();
    }
}
