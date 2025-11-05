package com.sambound.erp.service;

import com.sambound.erp.dto.SupplierDTO;
import com.sambound.erp.entity.Supplier;
import com.sambound.erp.repository.SupplierRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class SupplierService {

    private final SupplierRepository supplierRepository;

    public SupplierService(SupplierRepository supplierRepository) {
        this.supplierRepository = supplierRepository;
    }

    public List<SupplierDTO> getAllSuppliers() {
        return supplierRepository.findAll().stream()
                .map(this::toDTO)
                .toList();
    }

    public SupplierDTO getSupplierById(Long id) {
        Supplier supplier = supplierRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("供应商不存在"));
        return toDTO(supplier);
    }

    public SupplierDTO getSupplierByCode(String code) {
        Supplier supplier = supplierRepository.findByCode(code)
                .orElseThrow(() -> new RuntimeException("供应商不存在"));
        return toDTO(supplier);
    }

    private SupplierDTO toDTO(Supplier supplier) {
        return new SupplierDTO(
                supplier.getId(),
                supplier.getCode(),
                supplier.getName(),
                supplier.getShortName(),
                supplier.getEnglishName(),
                supplier.getDescription(),
                supplier.getCreatedAt(),
                supplier.getUpdatedAt()
        );
    }
}

