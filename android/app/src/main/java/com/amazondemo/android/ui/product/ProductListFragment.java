package com.amazondemo.android.ui.product;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;

import com.amazondemo.android.api.ApiClient;
import com.amazondemo.android.databinding.FragmentProductListBinding;
import com.amazondemo.android.model.ApiResponse;
import com.amazondemo.android.model.PageResponse;
import com.amazondemo.android.model.ProductResponse;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Product List Fragment
 * ======================
 * Displays a paginated grid of products.
 * Uses RecyclerView with GridLayoutManager for Amazon-like 2-column layout.
 */
public class ProductListFragment extends Fragment {

    private FragmentProductListBinding binding;
    private ProductAdapter adapter;
    private int currentPage = 0;
    private boolean isLoading = false;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentProductListBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        setupRecyclerView();
        loadProducts(0);

        // Pull to refresh
        binding.swipeRefresh.setOnRefreshListener(() -> {
            currentPage = 0;
            loadProducts(0);
        });
    }

    private void setupRecyclerView() {
        adapter = new ProductAdapter(getContext(), product -> {
            // Navigate to product detail
            ProductDetailFragment detail = ProductDetailFragment.newInstance(product.getId());
            requireActivity().getSupportFragmentManager()
                    .beginTransaction()
                    .replace(android.R.id.content, detail)
                    .addToBackStack(null)
                    .commit();
        });

        // 2-column grid (like Amazon mobile app)
        binding.rvProducts.setLayoutManager(new GridLayoutManager(getContext(), 2));
        binding.rvProducts.setAdapter(adapter);
    }

    private void loadProducts(int page) {
        if (isLoading) return;
        isLoading = true;

        ApiClient.getService(requireContext())
                .getProducts(page, 20, "createdAt")
                .enqueue(new Callback<ApiResponse<PageResponse<ProductResponse>>>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse<PageResponse<ProductResponse>>> call,
                                           @NonNull Response<ApiResponse<PageResponse<ProductResponse>>> response) {
                        isLoading = false;
                        binding.swipeRefresh.setRefreshing(false);
                        binding.progressBar.setVisibility(View.GONE);

                        if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                            List<ProductResponse> products = response.body().getData().getContent();
                            if (page == 0) {
                                adapter.setProducts(products);
                            } else {
                                adapter.addProducts(products);
                            }
                        } else {
                            Toast.makeText(getContext(), "Failed to load products", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse<PageResponse<ProductResponse>>> call,
                                          @NonNull Throwable t) {
                        isLoading = false;
                        binding.swipeRefresh.setRefreshing(false);
                        binding.progressBar.setVisibility(View.GONE);
                        Toast.makeText(getContext(), "Network error: Check if backend is running", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
