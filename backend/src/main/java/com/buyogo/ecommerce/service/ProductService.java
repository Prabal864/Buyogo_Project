package com.buyogo.ecommerce.service;

import com.buyogo.ecommerce.dto.ProductRequest;
import com.buyogo.ecommerce.entity.Product;
import com.buyogo.ecommerce.exception.ResourceNotFoundException;
import com.buyogo.ecommerce.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
public class ProductService {
    
    @Autowired
    private ProductRepository productRepository;
    
    public Page<Product> getAllProducts(Pageable pageable) {
        return productRepository.findAll(pageable);
    }
    
    public Page<Product> getProductsByCategory(String category, Pageable pageable) {
        return productRepository.findByCategory(category, pageable);
    }
    
    public Page<Product> searchProducts(String search, String category, Pageable pageable) {
        if (category != null && !category.isEmpty()) {
            return productRepository.searchProductsByCategory(category, search, pageable);
        }
        return productRepository.searchProducts(search, pageable);
    }
    
    public Product getProductById(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));
    }
    
    public Product createProduct(ProductRequest request) {
        Product product = new Product();
        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setPrice(request.getPrice());
        product.setCategory(request.getCategory());
        product.setStock(request.getStock());
        product.setImageUrl(request.getImageUrl());
        
        return productRepository.save(product);
    }
    
    public Product updateProduct(Long id, ProductRequest request) {
        Product product = getProductById(id);
        
        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setPrice(request.getPrice());
        product.setCategory(request.getCategory());
        product.setStock(request.getStock());
        product.setImageUrl(request.getImageUrl());
        
        return productRepository.save(product);
    }
    
    public void deleteProduct(Long id) {
        Product product = getProductById(id);
        productRepository.delete(product);
    }
}
