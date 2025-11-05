package com.sambound.erp.service;

import com.sambound.erp.dto.SaleOrderDTO;
import com.sambound.erp.dto.SaleOrderItemDTO;
import com.sambound.erp.entity.SaleOrder;
import com.sambound.erp.entity.SaleOrderItem;
import com.sambound.erp.exception.BusinessException;
import com.sambound.erp.repository.SaleOrderItemRepository;
import com.sambound.erp.repository.SaleOrderRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class SaleOrderService {
    
    private final SaleOrderRepository saleOrderRepository;
    private final SaleOrderItemRepository saleOrderItemRepository;
    
    public SaleOrderService(SaleOrderRepository saleOrderRepository, 
                           SaleOrderItemRepository saleOrderItemRepository) {
        this.saleOrderRepository = saleOrderRepository;
        this.saleOrderItemRepository = saleOrderItemRepository;
    }
    
    /**
     * 分页查询销售订单
     */
    public Page<SaleOrderDTO> getSaleOrders(String billNo, String customerCode, 
                                           LocalDate startDate, LocalDate endDate,
                                           Pageable pageable) {
        // 处理模糊查询参数
        String billNoPattern = billNo != null && !billNo.trim().isEmpty() 
                ? "%" + billNo.trim() + "%" : null;
        String customerCodePattern = customerCode != null && !customerCode.trim().isEmpty()
                ? "%" + customerCode.trim() + "%" : null;
        
        Page<SaleOrder> orders = saleOrderRepository.findByConditions(
                billNoPattern, customerCodePattern, startDate, endDate, pageable);
        return orders.map(this::toDTO);
    }
    
    /**
     * 根据ID获取订单详情（含明细）
     */
    public SaleOrderDTO getSaleOrderById(Long id) {
        SaleOrder order = saleOrderRepository.findByIdWithCustomer(id)
                .orElseThrow(() -> new BusinessException("订单不存在"));
        
        SaleOrderDTO dto = toDTO(order);
        
        // 查询订单明细
        List<SaleOrderItem> items = saleOrderItemRepository.findBySaleOrderId(id);
        List<SaleOrderItemDTO> itemDTOs = items.stream()
                .map(this::toItemDTO)
                .toList();
        
        return new SaleOrderDTO(
                dto.id(),
                dto.billNo(),
                dto.orderDate(),
                dto.note(),
                dto.woNumber(),
                dto.customerId(),
                dto.customerCode(),
                dto.customerName(),
                dto.createdAt(),
                dto.updatedAt(),
                itemDTOs
        );
    }
    
    private SaleOrderDTO toDTO(SaleOrder order) {
        return new SaleOrderDTO(
                order.getId(),
                order.getBillNo(),
                order.getOrderDate(),
                order.getNote(),
                order.getWoNumber(),
                order.getCustomer() != null ? order.getCustomer().getId() : null,
                order.getCustomer() != null ? order.getCustomer().getCode() : null,
                order.getCustomer() != null ? order.getCustomer().getName() : null,
                order.getCreatedAt(),
                order.getUpdatedAt(),
                null  // 明细列表在getSaleOrderById中单独填充
        );
    }
    
    private SaleOrderItemDTO toItemDTO(SaleOrderItem item) {
        return new SaleOrderItemDTO(
                item.getId(),
                item.getSaleOrder() != null ? item.getSaleOrder().getId() : null,
                item.getSequence(),
                item.getMaterial() != null ? item.getMaterial().getId() : null,
                item.getMaterial() != null ? item.getMaterial().getCode() : null,
                item.getMaterial() != null ? item.getMaterial().getName() : null,
                item.getUnit() != null ? item.getUnit().getId() : null,
                item.getUnit() != null ? item.getUnit().getCode() : null,
                item.getUnit() != null ? item.getUnit().getName() : null,
                item.getQty(),
                item.getOldQty(),
                item.getInspectionDate(),
                item.getDeliveryDate(),
                item.getBomVersion(),
                item.getEntryNote(),
                item.getCustomerOrderNo(),
                item.getCustomerLineNo(),
                item.getCreatedAt(),
                item.getUpdatedAt()
        );
    }
}
