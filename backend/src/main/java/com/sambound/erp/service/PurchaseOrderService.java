package com.sambound.erp.service;

import com.sambound.erp.dto.PurchaseOrderDTO;
import com.sambound.erp.dto.PurchaseOrderDeliveryDTO;
import com.sambound.erp.dto.PurchaseOrderItemDTO;
import com.sambound.erp.entity.PurchaseOrder;
import com.sambound.erp.entity.PurchaseOrderDelivery;
import com.sambound.erp.entity.PurchaseOrderItem;
import com.sambound.erp.exception.BusinessException;
import com.sambound.erp.repository.PurchaseOrderDeliveryRepository;
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
    private final PurchaseOrderDeliveryRepository purchaseOrderDeliveryRepository;
    
    public PurchaseOrderService(
            PurchaseOrderRepository purchaseOrderRepository,
            PurchaseOrderItemRepository purchaseOrderItemRepository,
            PurchaseOrderDeliveryRepository purchaseOrderDeliveryRepository) {
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.purchaseOrderItemRepository = purchaseOrderItemRepository;
        this.purchaseOrderDeliveryRepository = purchaseOrderDeliveryRepository;
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
    
    /**
     * 检查并更新订单状态
     * 当订单明细的所有交货明细总数量达到订单明细的采购数量时，将订单状态更新为CLOSED
     */
    @Transactional
    public void checkAndUpdateOrderStatus(Long orderId) {
        PurchaseOrder order = purchaseOrderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException("订单不存在"));
        
        if (order.getStatus() == PurchaseOrder.OrderStatus.CLOSED) {
            return; // 已关闭的订单不需要检查
        }
        
        // 查询订单的所有明细
        List<PurchaseOrderItem> items = purchaseOrderItemRepository.findByPurchaseOrderId(orderId);
        
        boolean allCompleted = true;
        for (PurchaseOrderItem item : items) {
            // 计算该明细的所有交货明细总数量
            BigDecimal totalDeliveredQty = purchaseOrderDeliveryRepository
                    .sumPlanQtyByItemId(item.getId())
                    .orElse(BigDecimal.ZERO);
            
            // 检查是否完成
            if (totalDeliveredQty.compareTo(item.getQty()) < 0) {
                allCompleted = false;
                break;
            }
        }
        
        // 如果所有明细都已完成，更新订单状态为CLOSED
        if (allCompleted) {
            order.setStatus(PurchaseOrder.OrderStatus.CLOSED);
            purchaseOrderRepository.save(order);
        }
    }
    
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
        // 计算已交货数量汇总
        BigDecimal deliveredQty = purchaseOrderDeliveryRepository
                .sumPlanQtyByItemId(item.getId())
                .orElse(BigDecimal.ZERO);
        
        // 查询交货明细
        List<PurchaseOrderDelivery> deliveries = purchaseOrderDeliveryRepository
                .findByPurchaseOrderItemIdOrderBySequence(item.getId());
        List<PurchaseOrderDeliveryDTO> deliveryDTOs = deliveries.stream()
                .map(this::toDeliveryDTO)
                .collect(Collectors.toList());
        
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
                item.getUpdatedAt(),
                deliveryDTOs
        );
    }
    
    private PurchaseOrderDeliveryDTO toDeliveryDTO(PurchaseOrderDelivery delivery) {
        return new PurchaseOrderDeliveryDTO(
                delivery.getId(),
                delivery.getPurchaseOrderItem() != null ? delivery.getPurchaseOrderItem().getId() : null,
                delivery.getSequence(),
                delivery.getDeliveryDate(),
                delivery.getPlanQty(),
                delivery.getSupplierDeliveryDate(),
                delivery.getPreArrivalDate(),
                delivery.getTransportLeadTime(),
                delivery.getCreatedAt(),
                delivery.getUpdatedAt()
        );
    }
}

