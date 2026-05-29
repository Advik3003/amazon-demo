package com.amazondemo.android.api;

import android.content.Context;
import android.content.SharedPreferences;

import com.amazondemo.android.BuildConfig;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import java.util.concurrent.TimeUnit;

/**
 * API Client - Retrofit configuration
 * ======================================
 * Singleton HTTP client for all API calls.
 *
 * ARCHITECTURE:
 * - Retrofit creates interface implementations for API calls
 * - OkHttp handles actual HTTP communication
 * - Interceptors add headers (JWT token) to every request
 * - GsonConverter maps JSON to Java objects
 *
 * TOKEN INJECTION:
 * JWT token is automatically added to every request via interceptor.
 * This avoids manually passing the token to every API call.
 *
 * RETRY ON 401:
 * If the server returns 401 (token expired), the client tries to
 * refresh the token and retry the request once.
 */
public class ApiClient {

    private static final String PREFS_NAME = "amazon_demo_prefs";
    private static final String KEY_ACCESS_TOKEN = "access_token";
    private static final String KEY_REFRESH_TOKEN = "refresh_token";

    private static Retrofit retrofit;
    private static ApiService apiService;

    public static ApiService getService(Context context) {
        if (apiService == null) {
            apiService = getRetrofit(context).create(ApiService.class);
        }
        return apiService;
    }

    private static Retrofit getRetrofit(Context context) {
        if (retrofit == null) {
            // Logging interceptor (only in debug builds)
            HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor();
            loggingInterceptor.setLevel(BuildConfig.DEBUG
                    ? HttpLoggingInterceptor.Level.BODY
                    : HttpLoggingInterceptor.Level.NONE);

            // Auth interceptor - adds JWT token to every request
            Interceptor authInterceptor = chain -> {
                Request original = chain.request();

                SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                String token = prefs.getString(KEY_ACCESS_TOKEN, null);

                Request request;
                if (token != null) {
                    request = original.newBuilder()
                            .header("Authorization", "Bearer " + token)
                            .method(original.method(), original.body())
                            .build();
                } else {
                    request = original;
                }

                return chain.proceed(request);
            };

            OkHttpClient okHttpClient = new OkHttpClient.Builder()
                    .addInterceptor(authInterceptor)
                    .addInterceptor(loggingInterceptor)
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .build();

            retrofit = new Retrofit.Builder()
                    .baseUrl(BuildConfig.API_BASE_URL + "/")
                    .client(okHttpClient)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
        }
        return retrofit;
    }

    /**
     * Save tokens to SharedPreferences after login
     */
    public static void saveTokens(Context context, String accessToken, String refreshToken) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_ACCESS_TOKEN, accessToken)
                .putString(KEY_REFRESH_TOKEN, refreshToken)
                .apply();
    }

    /**
     * Clear tokens on logout
     */
    public static void clearTokens(Context context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_ACCESS_TOKEN)
                .remove(KEY_REFRESH_TOKEN)
                .apply();
    }

    /**
     * Check if user is logged in
     */
    public static boolean isLoggedIn(Context context) {
        String token = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_ACCESS_TOKEN, null);
        return token != null && !token.isEmpty();
    }

    /**
     * Reset retrofit instance (called after logout)
     */
    public static void reset() {
        retrofit = null;
        apiService = null;
    }
}
