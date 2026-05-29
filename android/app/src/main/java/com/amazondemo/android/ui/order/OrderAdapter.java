package com.amazondemo.android.ui.order;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.amazondemo.android.R;
import com.amazondemo.android.model.OrderResponse;

import java.util.ArrayList;
import java.util.List;

public class OrderAdapter extends RecyclerView.Adapter<OrderAdapter.OrderViewHolder> {

    private final Context context;
    private List<OrderResponse> orders = new ArrayList<>();

    public OrderAdapter(Context context) { this.context = context; }

    public void setOrders(List<OrderResponse> orders) {
        this.orders = orders != null ? orders : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public OrderViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_order, parent, false);
        return new OrderViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull OrderViewHolder holder, int position) {
        holder.bind(orders.get(position));
    }

    @Override
    public int getItemCount() { return orders.size(); }

    static class OrderViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvOrderNumber, tvStatus, tvTotal, tvDate;

        OrderViewHolder(@NonNull View itemView) {
            super(itemView);
            tvOrderNumber = itemView.findViewById(R.id.tv_order_number);
            tvStatus = itemView.findViewById(R.id.tv_status);
            tvTotal = itemView.findViewById(R.id.tv_total);
            tvDate = itemView.findViewById(R.id.tv_date);
        }

        void bind(OrderResponse order) {
            tvOrderNumber.setText("Order: " + (order.getOrderNumber() != null ? order.getOrderNumber() : order.getId()));
            tvStatus.setText(order.getStatus() != null ? order.getStatus() : "PENDING");
            tvTotal.setText(String.format("$%.2f", order.getTotalAmount()));
            tvDate.setText(order.getCreatedAt() != null ? order.getCreatedAt().substring(0, 10) : "");
        }
    }
}
