package com.buyogo.ecommerce.service;

import com.buyogo.ecommerce.entity.User;
import com.buyogo.ecommerce.repository.OrderRepository;
import com.buyogo.ecommerce.repository.ProductRepository;
import com.buyogo.ecommerce.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Service
public class AdminService {
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private ProductRepository productRepository;
    
    @Autowired
    private OrderRepository orderRepository;
    
    public Map<String, Object> getAnalytics() {
        Map<String, Object> analytics = new HashMap<>();
        
        long totalUsers = userRepository.count();
        long totalProducts = productRepository.count();
        long totalOrders = orderRepository.countAllOrders();
        BigDecimal totalRevenue = orderRepository.getTotalRevenue();
        
        analytics.put("totalUsers", totalUsers);
        analytics.put("totalProducts", totalProducts);
        analytics.put("totalOrders", totalOrders);
        analytics.put("totalRevenue", totalRevenue != null ? totalRevenue : BigDecimal.ZERO);
        
        return analytics;
    }
    
    public Page<User> getAllUsers(Pageable pageable) {
        return userRepository.findAll(pageable);
    }
}
