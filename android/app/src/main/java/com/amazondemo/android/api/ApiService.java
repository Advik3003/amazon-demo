package com.amazondemo.android.api;

import com.amazondemo.android.model.ApiResponse;
import com.amazondemo.android.model.AuthResponse;
import com.amazondemo.android.model.LoginRequest;
import com.amazondemo.android.model.OrderResponse;
import com.amazondemo.android.model.PageResponse;
import com.amazondemo.android.model.ProductResponse;
import com.amazondemo.android.model.RegisterRequest;

import retrofit2.Call;
import retrofit2.http.*;

import java.util.List;
import java.util.Map;

/**
 * API Service Interface - Retrofit
 * ==================================
 * Retrofit generates the implementation of this interface at runtime.
 * All API endpoints are defined here as Java methods.
 *
 * ANNOTATIONS:
 * @GET, @POST, @PUT, @DELETE - HTTP methods
 * @Path - URL path variables (/products/{id})
 * @Query - URL query parameters (?page=0&size=20)
 * @Body - Request body (POST/PUT)
 * @Header - Custom headers
 */
public interface ApiService {

    // ==================== AUTH ====================

    @POST("api/v1/auth/login")
    Call<ApiResponse<AuthResponse>> login(@Body LoginRequest request);

    @POST("api/v1/auth/register")
    Call<ApiResponse<AuthResponse>> register(@Body RegisterRequest request);

    @POST("api/v1/auth/logout")
    Call<ApiResponse<Void>> logout(@Body Map<String, String> request);

    @POST("api/v1/auth/refresh")
    Call<ApiResponse<AuthResponse>> refreshToken(@Body Map<String, String> request);

    // ==================== PRODUCTS ====================

    @GET("api/v1/products")
    Call<ApiResponse<PageResponse<ProductResponse>>> getProducts(
            @Query("page") int page,
            @Query("size") int size,
            @Query("sortBy") String sortBy
    );

    @GET("api/v1/products/{id}")
    Call<ApiResponse<ProductResponse>> getProduct(@Path("id") String productId);

    @GET("api/v1/products/featured")
    Call<ApiResponse<List<ProductResponse>>> getFeaturedProducts();

    @GET("api/v1/products/search")
    Call<ApiResponse<PageResponse<ProductResponse>>> searchProducts(
            @Query("q") String query,
            @Query("page") int page
    );

    // ==================== ORDERS ====================

    @GET("api/v1/orders")
    Call<ApiResponse<PageResponse<OrderResponse>>> getOrders(
            @Query("page") int page,
            @Query("size") int size
    );

    @GET("api/v1/orders/{id}")
    Call<ApiResponse<OrderResponse>> getOrder(@Path("id") String orderId);

    @POST("api/v1/orders")
    Call<ApiResponse<OrderResponse>> createOrder(@Body Map<String, Object> orderRequest);

    @PATCH("api/v1/orders/{id}/cancel")
    Call<ApiResponse<OrderResponse>> cancelOrder(
            @Path("id") String orderId,
            @Body Map<String, String> body
    );
}
