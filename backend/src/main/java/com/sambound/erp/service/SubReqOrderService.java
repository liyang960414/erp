package com.sambound.erp.service;

import com.sambound.erp.dto.SubReqOrderDTO;
import com.sambound.erp.dto.SubReqOrderItemDTO;
import com.sambound.erp.entity.SubReqOrder;
import com.sambound.erp.entity.SubReqOrderItem;
import com.sambound.erp.exception.BusinessException;
import com.sambound.erp.repository.SubReqOrderItemRepository;
import com.sambound.erp.repository.SubReqOrderRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class SubReqOrderService {
    
    private final SubReqOrderRepository subReqOrderRepository;
    private final SubReqOrderItemRepository subReqOrderItemRepository;
    
    public SubReqOrderService(
            SubReqOrderRepository subReqOrderRepository,
            SubReqOrderItemRepository subReqOrderItemRepository) {
        this.subReqOrderRepository = subReqOrderRepository;
        this.subReqOrderItemRepository = subReqOrderItemRepository;
    }
    
    /**
     * 分页查询委外订单
     */
    public Page<SubReqOrderDTO> getSubReqOrders(
            Integer billHeadSeq, 
            SubReqOrder.OrderStatus status,
            String description,
            Pageable pageable) {
        // 处理模糊查询参数
        String descriptionPattern = description != null && !description.trim().isEmpty() 
                ? "%" + description.trim() + "%" : null;
        
        Page<SubReqOrder> orders = subReqOrderRepository.findByConditions(
                billHeadSeq, status, descriptionPattern, pageable);
        return orders.map(this::toDTO);
    }
    
    /**
     * 根据ID获取订单详情（含明细）
     */
    public SubReqOrderDTO getSubReqOrderById(Long id) {
        SubReqOrder order = subReqOrderRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new BusinessException("订单不存在"));
        
        SubReqOrderDTO dto = toDTO(order);
        
        // 查询订单明细
        List<SubReqOrderItem> items = subReqOrderItemRepository.findBySubReqOrderIdWithDetails(id);
        List<SubReqOrderItemDTO> itemDTOs = items.stream()
                .map(this::toItemDTO)
                .collect(Collectors.toList());
        
        return new SubReqOrderDTO(
                dto.id(),
                dto.billHeadSeq(),
                dto.description(),
                dto.status(),
                dto.createdAt(),
                dto.updatedAt(),
                itemDTOs
        );
    }
    
    private SubReqOrderDTO toDTO(SubReqOrder order) {
        return new SubReqOrderDTO(
                order.getId(),
                order.getBillHeadSeq(),
                order.getDescription(),
                order.getStatus(),
                order.getCreatedAt(),
                order.getUpdatedAt(),
                null // items will be loaded separately
        );
    }
    
    private SubReqOrderItemDTO toItemDTO(SubReqOrderItem item) {
        return new SubReqOrderItemDTO(
                item.getId(),
                item.getSubReqOrder().getId(),
                item.getSequence(),
                item.getMaterial() != null ? item.getMaterial().getId() : null,
                item.getMaterial() != null ? item.getMaterial().getCode() : null,
                item.getMaterial() != null ? item.getMaterial().getName() : null,
                item.getUnit() != null ? item.getUnit().getId() : null,
                item.getUnit() != null ? item.getUnit().getCode() : null,
                item.getUnit() != null ? item.getUnit().getName() : null,
                item.getQty(),
                item.getBom() != null ? item.getBom().getId() : null,
                item.getBom() != null ? item.getBom().getVersion() : null,
                item.getBom() != null ? item.getBom().getName() : null,
                item.getSupplier() != null ? item.getSupplier().getId() : null,
                item.getSupplier() != null ? item.getSupplier().getCode() : null,
                item.getSupplier() != null ? item.getSupplier().getName() : null,
                item.getLotMaster(),
                item.getLotManual(),
                item.getBaseNoStockInQty(),
                item.getNoStockInQty(),
                item.getPickMtrlStatus(),
                item.getDescription(),
                item.getCreatedAt(),
                item.getUpdatedAt()
        );
    }
}

