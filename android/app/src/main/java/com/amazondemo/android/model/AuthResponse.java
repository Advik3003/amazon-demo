package com.amazondemo.android.model;

import java.util.List;

public class AuthResponse {
    private String accessToken;
    private String refreshToken;
    private String userId;
    private String email;
    private String firstName;
    private String lastName;
    private List<String> roles;
    private long expiresIn;

    public String getAccessToken() { return accessToken; }
    public String getRefreshToken() { return refreshToken; }
    public String getUserId() { return userId; }
    public String getEmail() { return email; }
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public List<String> getRoles() { return roles; }
    public String getFullName() { return firstName + " " + lastName; }
}
