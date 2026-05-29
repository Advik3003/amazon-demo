package com.amazondemo.android.ui.product;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.amazondemo.android.R;
import com.amazondemo.android.model.ProductResponse;
import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.List;

/**
 * Product RecyclerView Adapter
 * ==============================
 * Displays product cards in a grid.
 * Uses ViewHolder pattern for memory efficiency (reuses views during scroll).
 */
public class ProductAdapter extends RecyclerView.Adapter<ProductAdapter.ProductViewHolder> {

    public interface OnProductClickListener {
        void onProductClick(ProductResponse product);
    }

    private final Context context;
    private List<ProductResponse> products = new ArrayList<>();
    private final OnProductClickListener listener;

    public ProductAdapter(Context context, OnProductClickListener listener) {
        this.context = context;
        this.listener = listener;
    }

    public void setProducts(List<ProductResponse> products) {
        this.products = products != null ? products : new ArrayList<>();
        notifyDataSetChanged();
    }

    public void addProducts(List<ProductResponse> newProducts) {
        int start = products.size();
        products.addAll(newProducts);
        notifyItemRangeInserted(start, newProducts.size());
    }

    @NonNull
    @Override
    public ProductViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_product, parent, false);
        return new ProductViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ProductViewHolder holder, int position) {
        ProductResponse product = products.get(position);
        holder.bind(product);
    }

    @Override
    public int getItemCount() { return products.size(); }

    class ProductViewHolder extends RecyclerView.ViewHolder {
        private final ImageView ivProduct;
        private final TextView tvName;
        private final TextView tvPrice;
        private final TextView tvRating;

        ProductViewHolder(@NonNull View itemView) {
            super(itemView);
            ivProduct = itemView.findViewById(R.id.iv_product);
            tvName = itemView.findViewById(R.id.tv_product_name);
            tvPrice = itemView.findViewById(R.id.tv_price);
            tvRating = itemView.findViewById(R.id.tv_rating);
        }

        void bind(ProductResponse product) {
            tvName.setText(product.getName());
            tvPrice.setText(String.format("$%.2f", product.getPrice()));
            tvRating.setText(String.format("⭐ %.1f (%d)", product.getAverageRating(), product.getReviewCount()));

            // Load image with Glide (handles caching, placeholder, error states)
            Glide.with(context)
                    .load(product.getFirstImageUrl())
                    .placeholder(R.drawable.ic_product_placeholder)
                    .error(R.drawable.ic_product_placeholder)
                    .centerCrop()
                    .into(ivProduct);

            itemView.setOnClickListener(v -> listener.onProductClick(product));
        }
    }
}
