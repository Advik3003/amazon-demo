package com.amazondemo.android.ui.product;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.amazondemo.android.api.ApiClient;
import com.amazondemo.android.databinding.FragmentProductDetailBinding;
import com.amazondemo.android.model.ApiResponse;
import com.amazondemo.android.model.ProductResponse;
import com.bumptech.glide.Glide;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ProductDetailFragment extends Fragment {

    private static final String ARG_PRODUCT_ID = "product_id";
    private FragmentProductDetailBinding binding;

    public static ProductDetailFragment newInstance(String productId) {
        ProductDetailFragment fragment = new ProductDetailFragment();
        Bundle args = new Bundle();
        args.putString(ARG_PRODUCT_ID, productId);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentProductDetailBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        String productId = getArguments() != null ? getArguments().getString(ARG_PRODUCT_ID) : null;
        if (productId != null) loadProduct(productId);
    }

    private void loadProduct(String productId) {
        binding.progressBar.setVisibility(View.VISIBLE);

        ApiClient.getService(requireContext()).getProduct(productId)
                .enqueue(new Callback<ApiResponse<ProductResponse>>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse<ProductResponse>> call,
                                           @NonNull Response<ApiResponse<ProductResponse>> response) {
                        binding.progressBar.setVisibility(View.GONE);
                        if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                            bindProduct(response.body().getData());
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse<ProductResponse>> call, @NonNull Throwable t) {
                        binding.progressBar.setVisibility(View.GONE);
                        Toast.makeText(getContext(), "Failed to load product", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void bindProduct(ProductResponse product) {
        binding.tvProductName.setText(product.getName());
        binding.tvPrice.setText(String.format("$%.2f", product.getPrice()));
        binding.tvDescription.setText(product.getDescription());
        binding.tvRating.setText(String.format("⭐ %.1f (%d reviews)", product.getAverageRating(), product.getReviewCount()));
        binding.tvStock.setText(product.isInStock() ? "✓ In Stock" : "Out of Stock");
        binding.btnAddToCart.setEnabled(product.isInStock());

        Glide.with(this).load(product.getFirstImageUrl()).centerCrop().into(binding.ivProduct);

        binding.btnAddToCart.setOnClickListener(v -> {
            Toast.makeText(getContext(), product.getName() + " added to cart!", Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
