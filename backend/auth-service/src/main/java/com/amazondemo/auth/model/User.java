package com.amazondemo.auth.model;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * User Entity
 * ============
 * Represents a user in the auth database.
 * Stores credentials and roles.
 *
 * NOTE: This is the auth representation of a user.
 * The user-service has a richer profile (address, phone, etc.)
 * Auth service keeps only authentication-related data.
 *
 * @EntityListeners(AuditingEntityListener.class) - automatically fills
 * createdAt and updatedAt fields without manual code.
 */
@Entity
@Table(name = "users",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = "email")
        })
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)  // UUID primary key
    @Column(updatable = false, nullable = false)
    private String id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;  // BCrypt hashed

    @Column(nullable = false)
    private String firstName;

    @Column(nullable = false)
    private String lastName;

    /** RBAC - Role Based Access Control */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "role")
    @Builder.Default
    private Set<String> roles = Set.of("ROLE_USER");

    /** Account status */
    @Builder.Default
    private boolean enabled = true;

    @Builder.Default
    private boolean accountNonExpired = true;

    @Builder.Default
    private boolean credentialsNonExpired = true;

    @Builder.Default
    private boolean accountNonLocked = true;

    /** Failed login attempts - locks account after 5 failed attempts */
    @Builder.Default
    private int failedLoginAttempts = 0;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    /** Last successful login timestamp */
    private LocalDateTime lastLoginAt;
}
