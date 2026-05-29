package com.amazondemo.android.ui.order;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.amazondemo.android.api.ApiClient;
import com.amazondemo.android.databinding.FragmentOrderListBinding;
import com.amazondemo.android.model.ApiResponse;
import com.amazondemo.android.model.OrderResponse;
import com.amazondemo.android.model.PageResponse;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class OrderListFragment extends Fragment {

    private FragmentOrderListBinding binding;
    private OrderAdapter adapter;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentOrderListBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        adapter = new OrderAdapter(getContext());
        binding.rvOrders.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.rvOrders.setAdapter(adapter);
        loadOrders();
    }

    private void loadOrders() {
        binding.progressBar.setVisibility(View.VISIBLE);
        ApiClient.getService(requireContext()).getOrders(0, 20)
                .enqueue(new Callback<ApiResponse<PageResponse<OrderResponse>>>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse<PageResponse<OrderResponse>>> call,
                                           @NonNull Response<ApiResponse<PageResponse<OrderResponse>>> response) {
                        binding.progressBar.setVisibility(View.GONE);
                        if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                            List<OrderResponse> orders = response.body().getData().getContent();
                            adapter.setOrders(orders);
                            if (orders.isEmpty()) {
                                binding.tvEmpty.setVisibility(View.VISIBLE);
                            }
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse<PageResponse<OrderResponse>>> call, @NonNull Throwable t) {
                        binding.progressBar.setVisibility(View.GONE);
                        Toast.makeText(getContext(), "Failed to load orders", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
