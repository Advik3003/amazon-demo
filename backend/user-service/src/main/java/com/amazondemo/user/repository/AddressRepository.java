package com.amazondemo.user.repository;

import com.amazondemo.user.model.Address;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AddressRepository extends JpaRepository<Address, String> {

    List<Address> findByUserProfileId(String userId);

    @Modifying
    @Query("UPDATE Address a SET a.isDefault = false WHERE a.userProfile.id = :userId")
    void unsetDefaultAddresses(String userId);
}
