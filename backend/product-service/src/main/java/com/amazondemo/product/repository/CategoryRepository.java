package com.amazondemo.product.repository;

import com.amazondemo.product.model.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CategoryRepository extends JpaRepository<Category, String> {
    List<Category> findByParentIsNullAndActiveTrue();
    Optional<Category> findBySlug(String slug);
    boolean existsByName(String name);
}
