package com.sambound.erp.service;

import com.sambound.erp.dto.CustomerDTO;
import com.sambound.erp.entity.Customer;
import com.sambound.erp.repository.CustomerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CustomerService {
    
    private final CustomerRepository customerRepository;
    
    public CustomerService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }
    
    /**
     * 根据编码查找或创建客户
     * 
     * @param code 客户编码
     * @param name 客户名称
     * @return 客户实体
     */
    @Transactional
    public Customer findOrCreateByCode(String code, String name) {
        if (code == null || code.trim().isEmpty()) {
            throw new IllegalArgumentException("客户编码不能为空");
        }
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("客户名称不能为空");
        }
        
        return customerRepository.insertOrGetByCode(code.trim(), name.trim());
    }
    
    public CustomerDTO getCustomerById(Long id) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("客户不存在"));
        return toDTO(customer);
    }
    
    private CustomerDTO toDTO(Customer customer) {
        return new CustomerDTO(
                customer.getId(),
                customer.getCode(),
                customer.getName(),
                customer.getCreatedAt(),
                customer.getUpdatedAt()
        );
    }
}
