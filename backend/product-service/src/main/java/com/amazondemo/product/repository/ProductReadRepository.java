package com.amazondemo.product.repository;

import com.amazondemo.product.model.ProductReadModel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

/**
 * MongoDB Repository for the QUERY side of CQRS.
 * Spring Data MongoDB generates implementation automatically.
 */
@Repository
public interface ProductReadRepository extends MongoRepository<ProductReadModel, String> {

    Page<ProductReadModel> findByCategoryId(String categoryId, Pageable pageable);

    Page<ProductReadModel> findByStatus(String status, Pageable pageable);

    // Full-text search using MongoDB text index
    @Query("{ $text: { $search: ?0 } }")
    Page<ProductReadModel> searchByText(String searchTerm, Pageable pageable);

    Page<ProductReadModel> findByBrand(String brand, Pageable pageable);
    Page<ProductReadModel> findByFeaturedTrue(Pageable pageable);
}
