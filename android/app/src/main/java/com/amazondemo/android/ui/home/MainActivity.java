package com.amazondemo.android.ui.home;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.amazondemo.android.BuildConfig;
import com.amazondemo.android.R;
import com.amazondemo.android.api.ApiClient;
import com.amazondemo.android.databinding.ActivityMainBinding;
import com.amazondemo.android.ui.auth.LoginActivity;
import com.google.android.material.bottomnavigation.BottomNavigationView;

/**
 * Main Activity - Entry point after login
 * =========================================
 * Uses Navigation Component with Bottom Navigation:
 * - Home (Featured products)
 * - Products (Browse all)
 * - Cart
 * - Orders
 * - Profile
 */
public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Set app name from BuildConfig (supports white-labeling)
        getSupportActionBar().setTitle(BuildConfig.APP_NAME);

        // Setup Navigation
        NavHostFragment navHostFragment = (NavHostFragment)
                getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment);
        NavController navController = navHostFragment.getNavController();

        BottomNavigationView bottomNav = binding.bottomNavigation;
        AppBarConfiguration config = new AppBarConfiguration.Builder(
                R.id.nav_home, R.id.nav_products, R.id.nav_cart,
                R.id.nav_orders, R.id.nav_profile).build();

        NavigationUI.setupActionBarWithNavController(this, navController, config);
        NavigationUI.setupWithNavController(bottomNav, navController);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_logout) {
            ApiClient.clearTokens(this);
            ApiClient.reset();
            startActivity(new Intent(this, LoginActivity.class));
            finishAffinity();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
