package com.amazondemo.user.service;

import com.amazondemo.common.exception.ConflictException;
import com.amazondemo.common.exception.ResourceNotFoundException;
import com.amazondemo.user.dto.AddressDto;
import com.amazondemo.user.dto.UserProfileDto;
import com.amazondemo.user.model.Address;
import com.amazondemo.user.model.UserProfile;
import com.amazondemo.user.repository.AddressRepository;
import com.amazondemo.user.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserProfileService {

    private final UserProfileRepository userProfileRepository;
    private final AddressRepository addressRepository;

    /**
     * Create a user profile (called after auth-service registers a user)
     */
    @Transactional
    public UserProfileDto createProfile(String userId, String email, String firstName, String lastName) {
        if (userProfileRepository.existsByEmail(email)) {
            throw new ConflictException("Profile already exists for: " + email);
        }

        UserProfile profile = UserProfile.builder()
                .id(userId)
                .email(email)
                .firstName(firstName)
                .lastName(lastName)
                .build();

        UserProfile saved = userProfileRepository.save(profile);
        log.info("User profile created for: {}", userId);
        return toDto(saved);
    }

    /**
     * Get user profile by ID
     */
    public UserProfileDto getProfile(String userId) {
        UserProfile profile = userProfileRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
        return toDto(profile);
    }

    /**
     * Update user profile
     */
    @Transactional
    public UserProfileDto updateProfile(String userId, UserProfileDto updateRequest) {
        UserProfile profile = userProfileRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        if (updateRequest.getFirstName() != null) profile.setFirstName(updateRequest.getFirstName());
        if (updateRequest.getLastName() != null) profile.setLastName(updateRequest.getLastName());
        if (updateRequest.getPhoneNumber() != null) profile.setPhoneNumber(updateRequest.getPhoneNumber());

        UserProfile saved = userProfileRepository.save(profile);
        log.info("Profile updated for user: {}", userId);
        return toDto(saved);
    }

    /**
     * Add address to user profile
     */
    @Transactional
    public AddressDto addAddress(String userId, AddressDto addressDto) {
        UserProfile profile = userProfileRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        // If this is default, unset other defaults
        if (addressDto.isDefault()) {
            addressRepository.unsetDefaultAddresses(userId);
        }

        Address address = toAddressEntity(addressDto, profile);
        Address saved = addressRepository.save(address);
        return toAddressDto(saved);
    }

    /**
     * Get all addresses for a user
     */
    public List<AddressDto> getAddresses(String userId) {
        return addressRepository.findByUserProfileId(userId)
                .stream()
                .map(this::toAddressDto)
                .collect(Collectors.toList());
    }

    // ==================== PRIVATE MAPPERS ====================

    private UserProfileDto toDto(UserProfile profile) {
        return UserProfileDto.builder()
                .id(profile.getId())
                .email(profile.getEmail())
                .firstName(profile.getFirstName())
                .lastName(profile.getLastName())
                .phoneNumber(profile.getPhoneNumber())
                .profileImageUrl(profile.getProfileImageUrl())
                .status(profile.getStatus().name())
                .createdAt(profile.getCreatedAt())
                .updatedAt(profile.getUpdatedAt())
                .build();
    }

    private AddressDto toAddressDto(Address address) {
        return AddressDto.builder()
                .id(address.getId())
                .fullName(address.getFullName())
                .street(address.getStreet())
                .apartment(address.getApartment())
                .city(address.getCity())
                .state(address.getState())
                .zipCode(address.getZipCode())
                .country(address.getCountry())
                .phoneNumber(address.getPhoneNumber())
                .isDefault(address.isDefault())
                .type(address.getType().name())
                .build();
    }

    private Address toAddressEntity(AddressDto dto, UserProfile profile) {
        return Address.builder()
                .fullName(dto.getFullName())
                .street(dto.getStreet())
                .apartment(dto.getApartment())
                .city(dto.getCity())
                .state(dto.getState())
                .zipCode(dto.getZipCode())
                .country(dto.getCountry())
                .phoneNumber(dto.getPhoneNumber())
                .isDefault(dto.isDefault())
                .type(dto.getType() != null ? Address.AddressType.valueOf(dto.getType()) : Address.AddressType.HOME)
                .userProfile(profile)
                .build();
    }
}
