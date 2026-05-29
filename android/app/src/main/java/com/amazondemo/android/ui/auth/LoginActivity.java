package com.amazondemo.android.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.amazondemo.android.api.ApiClient;
import com.amazondemo.android.api.ApiService;
import com.amazondemo.android.databinding.ActivityLoginBinding;
import com.amazondemo.android.model.ApiResponse;
import com.amazondemo.android.model.AuthResponse;
import com.amazondemo.android.model.LoginRequest;
import com.amazondemo.android.ui.home.MainActivity;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Login Activity
 * ===============
 * Handles user authentication via the auth-service API.
 *
 * PATTERN USED: MVP (Model-View-Presenter) simplified
 * - Activity is the View
 * - Retrofit Callback is the Presenter logic
 * - ApiResponse is the Model
 *
 * VIEW BINDING: Android View Binding generates type-safe access
 * to layout views, replacing findViewById().
 */
public class LoginActivity extends AppCompatActivity {

    private ActivityLoginBinding binding;  // View Binding
    private ApiService apiService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        apiService = ApiClient.getService(this);

        // Check if already logged in
        if (ApiClient.isLoggedIn(this)) {
            navigateToHome();
            return;
        }

        setupUI();
    }

    private void setupUI() {
        binding.btnLogin.setOnClickListener(v -> {
            String email = binding.etEmail.getText().toString().trim();
            String password = binding.etPassword.getText().toString().trim();

            if (validateInputs(email, password)) {
                performLogin(email, password);
            }
        });

        binding.tvRegister.setOnClickListener(v ->
            startActivity(new Intent(this, RegisterActivity.class)));
    }

    private boolean validateInputs(String email, String password) {
        if (email.isEmpty()) {
            binding.etEmail.setError("Email is required");
            return false;
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.etEmail.setError("Enter a valid email");
            return false;
        }
        if (password.isEmpty()) {
            binding.etPassword.setError("Password is required");
            return false;
        }
        if (password.length() < 6) {
            binding.etPassword.setError("Password must be at least 6 characters");
            return false;
        }
        return true;
    }

    private void performLogin(String email, String password) {
        // Show loading
        binding.btnLogin.setEnabled(false);
        binding.progressBar.setVisibility(View.VISIBLE);

        LoginRequest request = new LoginRequest(email, password);

        // Asynchronous API call using Retrofit + Callback
        apiService.login(request).enqueue(new Callback<ApiResponse<AuthResponse>>() {

            @Override
            public void onResponse(Call<ApiResponse<AuthResponse>> call,
                                   Response<ApiResponse<AuthResponse>> response) {
                runOnUiThread(() -> {
                    binding.btnLogin.setEnabled(true);
                    binding.progressBar.setVisibility(View.GONE);

                    if (response.isSuccessful() && response.body() != null
                            && response.body().isSuccess()) {
                        AuthResponse auth = response.body().getData();

                        // Save tokens for subsequent API calls
                        ApiClient.saveTokens(LoginActivity.this,
                                auth.getAccessToken(), auth.getRefreshToken());

                        Toast.makeText(LoginActivity.this,
                                "Welcome, " + auth.getFirstName() + "!", Toast.LENGTH_SHORT).show();

                        navigateToHome();
                    } else {
                        String error = response.body() != null
                                ? response.body().getMessage() : "Login failed";
                        Toast.makeText(LoginActivity.this, error, Toast.LENGTH_LONG).show();
                    }
                });
            }

            @Override
            public void onFailure(Call<ApiResponse<AuthResponse>> call, Throwable t) {
                runOnUiThread(() -> {
                    binding.btnLogin.setEnabled(true);
                    binding.progressBar.setVisibility(View.GONE);
                    Toast.makeText(LoginActivity.this,
                            "Network error: " + t.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void navigateToHome() {
        startActivity(new Intent(this, MainActivity.class));
        finish();  // Remove login from back stack
    }
}
