package com.amazondemo.android.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.amazondemo.android.api.ApiClient;
import com.amazondemo.android.api.ApiService;
import com.amazondemo.android.databinding.ActivityRegisterBinding;
import com.amazondemo.android.model.ApiResponse;
import com.amazondemo.android.model.AuthResponse;
import com.amazondemo.android.model.RegisterRequest;
import com.amazondemo.android.ui.home.MainActivity;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class RegisterActivity extends AppCompatActivity {

    private ActivityRegisterBinding binding;
    private ApiService apiService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityRegisterBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        apiService = ApiClient.getService(this);

        binding.btnRegister.setOnClickListener(v -> {
            String firstName = binding.etFirstName.getText().toString().trim();
            String lastName = binding.etLastName.getText().toString().trim();
            String email = binding.etEmail.getText().toString().trim();
            String password = binding.etPassword.getText().toString().trim();

            if (firstName.isEmpty() || lastName.isEmpty() || email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "All fields are required", Toast.LENGTH_SHORT).show();
                return;
            }
            if (password.length() < 8) {
                binding.etPassword.setError("Password must be at least 8 characters");
                return;
            }

            performRegister(firstName, lastName, email, password);
        });

        binding.tvLogin.setOnClickListener(v -> finish());
    }

    private void performRegister(String firstName, String lastName, String email, String password) {
        binding.btnRegister.setEnabled(false);
        binding.progressBar.setVisibility(View.VISIBLE);

        RegisterRequest request = new RegisterRequest(firstName, lastName, email, password);

        apiService.register(request).enqueue(new Callback<ApiResponse<AuthResponse>>() {
            @Override
            public void onResponse(Call<ApiResponse<AuthResponse>> call,
                                   Response<ApiResponse<AuthResponse>> response) {
                runOnUiThread(() -> {
                    binding.btnRegister.setEnabled(true);
                    binding.progressBar.setVisibility(View.GONE);

                    if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                        AuthResponse auth = response.body().getData();
                        ApiClient.saveTokens(RegisterActivity.this, auth.getAccessToken(), auth.getRefreshToken());
                        Toast.makeText(RegisterActivity.this, "Account created! Welcome " + auth.getFirstName(), Toast.LENGTH_SHORT).show();
                        startActivity(new Intent(RegisterActivity.this, MainActivity.class));
                        finishAffinity();
                    } else {
                        String error = response.body() != null ? response.body().getMessage() : "Registration failed";
                        Toast.makeText(RegisterActivity.this, error, Toast.LENGTH_LONG).show();
                    }
                });
            }

            @Override
            public void onFailure(Call<ApiResponse<AuthResponse>> call, Throwable t) {
                runOnUiThread(() -> {
                    binding.btnRegister.setEnabled(true);
                    binding.progressBar.setVisibility(View.GONE);
                    Toast.makeText(RegisterActivity.this, "Network error: " + t.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }
}
