package com.sambound.erp.service;

import com.sambound.erp.dto.PurchaseOrderDTO;
import com.sambound.erp.dto.PurchaseOrderItemDTO;
import com.sambound.erp.entity.PurchaseOrder;
import com.sambound.erp.entity.PurchaseOrderItem;
import com.sambound.erp.exception.BusinessException;
import com.sambound.erp.repository.PurchaseOrderItemRepository;
import com.sambound.erp.repository.PurchaseOrderRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class PurchaseOrderService {
    
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderItemRepository purchaseOrderItemRepository;
    
    public PurchaseOrderService(
            PurchaseOrderRepository purchaseOrderRepository,
            PurchaseOrderItemRepository purchaseOrderItemRepository) {
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.purchaseOrderItemRepository = purchaseOrderItemRepository;
    }
    
    /**
     * 分页查询采购订单
     */
    public Page<PurchaseOrderDTO> getPurchaseOrders(
            String billNo, 
            String supplierCode,
            PurchaseOrder.OrderStatus status,
            java.time.LocalDate startDate, 
            java.time.LocalDate endDate,
            Pageable pageable) {
        // 处理模糊查询参数
        String billNoPattern = billNo != null && !billNo.trim().isEmpty() 
                ? "%" + billNo.trim() + "%" : null;
        String supplierCodePattern = supplierCode != null && !supplierCode.trim().isEmpty()
                ? "%" + supplierCode.trim() + "%" : null;
        
        Page<PurchaseOrder> orders = purchaseOrderRepository.findByConditions(
                billNoPattern, supplierCodePattern, status, startDate, endDate, pageable);
        return orders.map(this::toDTO);
    }
    
    /**
     * 根据ID获取订单详情（含明细和交货计划）
     */
    public PurchaseOrderDTO getPurchaseOrderById(Long id) {
        PurchaseOrder order = purchaseOrderRepository.findByIdWithSupplier(id)
                .orElseThrow(() -> new BusinessException("订单不存在"));
        
        PurchaseOrderDTO dto = toDTO(order);
        
        // 查询订单明细
        List<PurchaseOrderItem> items = purchaseOrderItemRepository.findByPurchaseOrderIdWithDetails(id);
        List<PurchaseOrderItemDTO> itemDTOs = items.stream()
                .map(item -> toItemDTO(item))
                .collect(Collectors.toList());
        
        return new PurchaseOrderDTO(
                dto.id(),
                dto.billNo(),
                dto.orderDate(),
                dto.supplierId(),
                dto.supplierCode(),
                dto.supplierName(),
                dto.status(),
                dto.note(),
                dto.createdAt(),
                dto.updatedAt(),
                itemDTOs
        );
    }
    
    // 交货明细表已移除，订单状态更新逻辑不再依赖交货明细
    
    private PurchaseOrderDTO toDTO(PurchaseOrder order) {
        return new PurchaseOrderDTO(
                order.getId(),
                order.getBillNo(),
                order.getOrderDate(),
                order.getSupplier() != null ? order.getSupplier().getId() : null,
                order.getSupplier() != null ? order.getSupplier().getCode() : null,
                order.getSupplier() != null ? order.getSupplier().getName() : null,
                order.getStatus(),
                order.getNote(),
                order.getCreatedAt(),
                order.getUpdatedAt(),
                null  // 明细列表在getPurchaseOrderById中单独填充
        );
    }
    
    private PurchaseOrderItemDTO toItemDTO(PurchaseOrderItem item) {
        // 交货明细已移除，默认返回0
        BigDecimal deliveredQty = BigDecimal.ZERO;
        
        return new PurchaseOrderItemDTO(
                item.getId(),
                item.getPurchaseOrder() != null ? item.getPurchaseOrder().getId() : null,
                item.getSequence(),
                item.getMaterial() != null ? item.getMaterial().getId() : null,
                item.getMaterial() != null ? item.getMaterial().getCode() : null,
                item.getMaterial() != null ? item.getMaterial().getName() : null,
                item.getBom() != null ? item.getBom().getId() : null,
                item.getBom() != null ? item.getBom().getVersion() : null,
                item.getMaterialDesc(),
                item.getUnit() != null ? item.getUnit().getId() : null,
                item.getUnit() != null ? item.getUnit().getCode() : null,
                item.getUnit() != null ? item.getUnit().getName() : null,
                item.getQty(),
                item.getPlanConfirm(),
                item.getSalUnit() != null ? item.getSalUnit().getId() : null,
                item.getSalUnit() != null ? item.getSalUnit().getCode() : null,
                item.getSalUnit() != null ? item.getSalUnit().getName() : null,
                item.getSalQty(),
                item.getSalJoinQty(),
                item.getBaseSalJoinQty(),
                item.getRemarks(),
                item.getSalBaseQty(),
                deliveredQty,
                item.getCreatedAt(),
                item.getUpdatedAt()
        );
    }
}

