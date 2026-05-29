package com.amazondemo.product.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "categories")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false, unique = true)
    private String name;

    private String description;
    private String imageUrl;
    private String slug;  // URL-friendly name (e.g., "electronics")

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    @ToString.Exclude
    private Category parent;  // Hierarchical categories (Electronics > Phones > Smartphones)

    @OneToMany(mappedBy = "parent")
    @Builder.Default
    @ToString.Exclude
    private List<Category> children = new ArrayList<>();

    @Builder.Default
    private boolean active = true;

    @Builder.Default
    private int displayOrder = 0;
}
