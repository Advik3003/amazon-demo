package com.amazondemo.user.model;

import jakarta.persistence.*;
import lombok.*;

/**
 * Address Entity
 * Users can have multiple addresses (Home, Work, etc.)
 */
@Entity
@Table(name = "addresses")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Address {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @ToString.Exclude
    private UserProfile userProfile;

    @Column(nullable = false)
    private String fullName;

    @Column(nullable = false)
    private String street;

    private String apartment;

    @Column(nullable = false)
    private String city;

    @Column(nullable = false)
    private String state;

    @Column(nullable = false)
    private String zipCode;

    @Column(nullable = false)
    private String country;

    private String phoneNumber;

    @Builder.Default
    private boolean isDefault = false;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private AddressType type = AddressType.HOME;

    public enum AddressType {
        HOME, WORK, OTHER
    }
}
