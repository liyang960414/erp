package com.sambound.erp.service;

import com.sambound.erp.dto.OrderAlertDTO;
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
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
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
    
    /**
     * 获取订单提醒列表
     * 包含三种提醒类型：
     * 1. 采购料提醒：验货期前40天需要采购料
     * 2. 生产提醒：临近验货期7天内如果生产未完成需要提示（目前仅基于时间条件，后续需添加生产状态判断）
     * 3. 超期告警：要货日期超期需要告警
     * 
     * 注意：目前所有提醒仅基于时间条件判断，不包含生产状态、采购状态等业务逻辑判断
     * 后续需要添加生产状态字段和采购状态判断逻辑
     * 
     * 优化：在数据库层面过滤符合条件的订单明细，避免查询所有数据导致性能问题
     */
    public List<OrderAlertDTO> getOrderAlerts() {
        LocalDate today = LocalDate.now();
        List<OrderAlertDTO> alerts = new ArrayList<>();
        
        // 计算日期范围
        // 采购提醒：验货期前40天（包含第40天），即验货日期在 [today, today+40] 范围内
        // 生产提醒：临近验货期7天内（包含第7天），即验货日期在 [today, today+7] 范围内
        // 超期告警：要货日期 < today
        LocalDate inspectionDateStart = today;  // 验货日期范围开始（今天）
        LocalDate inspectionDateEnd = today.plusDays(40);  // 验货日期范围结束（今天+40天）
        LocalDateTime deliveryDateEndTime = today.atStartOfDay();  // 要货日期范围结束（今天0点，用于查询超期的）
        
        // 在数据库层面过滤出符合条件的订单明细
        List<SaleOrderItem> items = saleOrderItemRepository.findAlertsForDateRange(
            inspectionDateStart, inspectionDateEnd, deliveryDateEndTime
        );
        
        // 限制返回数量，避免前端渲染过多数据导致卡顿
        int maxAlerts = 200;
        int processedCount = 0;
        
        for (SaleOrderItem item : items) {
            if (processedCount >= maxAlerts) {
                break;
            }
            
            SaleOrder order = item.getSaleOrder();
            if (order == null) {
                continue;
            }
            
            // 检查采购料提醒：验货期前40天需要采购料
            if (item.getInspectionDate() != null) {
                long daysUntilInspection = ChronoUnit.DAYS.between(today, item.getInspectionDate());
                
                // 采购提醒：验货期前40天（包含第40天），但不在7天内
                if (daysUntilInspection <= 40 && daysUntilInspection > 7) {
                    alerts.add(createPurchaseReminder(item, order, daysUntilInspection));
                    processedCount++;
                }
                
                // 生产提醒：临近验货期7天内（包含第7天）
                // 注意：目前仅基于时间条件判断，后续需要添加生产状态字段判断生产是否完成
                if (daysUntilInspection <= 7 && daysUntilInspection >= 0) {
                    alerts.add(createProductionReminder(item, order, daysUntilInspection));
                    processedCount++;
                }
            }
            
            // 检查要货日期超期告警
            if (item.getDeliveryDate() != null) {
                long daysOverdue = ChronoUnit.DAYS.between(item.getDeliveryDate().toLocalDate(), today);
                if (daysOverdue > 0) {
                    alerts.add(createDeliveryOverdueAlert(item, order, daysOverdue));
                    processedCount++;
                }
            }
        }
        
        // 对提醒进行排序：超期告警 > 生产提醒 > 采购提醒
        alerts.sort((a1, a2) -> {
            int priority1 = getAlertPriority(a1.alertType());
            int priority2 = getAlertPriority(a2.alertType());
            if (priority1 != priority2) {
                return Integer.compare(priority1, priority2);
            }
            // 同类型按日期排序
            if (a1.alertType() == OrderAlertDTO.AlertType.DELIVERY_OVERDUE) {
                return a1.deliveryDate() != null && a2.deliveryDate() != null
                    ? a1.deliveryDate().compareTo(a2.deliveryDate())
                    : 0;
            } else {
                return a1.inspectionDate() != null && a2.inspectionDate() != null
                    ? a1.inspectionDate().compareTo(a2.inspectionDate())
                    : 0;
            }
        });
        
        return alerts;
    }
    
    /**
     * 创建采购料提醒
     */
    private OrderAlertDTO createPurchaseReminder(SaleOrderItem item, SaleOrder order, long daysRemaining) {
        String message = String.format("验货期前%d天，需要采购料", daysRemaining);
        return new OrderAlertDTO(
            OrderAlertDTO.AlertType.PURCHASE_REMINDER,
            order.getId(),
            order.getBillNo(),
            order.getCustomer() != null ? order.getCustomer().getName() : null,
            order.getWoNumber(),
            item.getId(),
            item.getMaterial() != null ? item.getMaterial().getCode() : null,
            item.getMaterial() != null ? item.getMaterial().getName() : null,
            item.getQty(),
            item.getUnit() != null ? item.getUnit().getCode() : null,
            item.getUnit() != null ? item.getUnit().getName() : null,
            item.getInspectionDate(),
            item.getDeliveryDate(),
            daysRemaining,
            message
        );
    }
    
    /**
     * 创建生产提醒
     * 注意：目前仅基于时间条件判断，后续需要添加生产状态字段判断生产是否完成
     */
    private OrderAlertDTO createProductionReminder(SaleOrderItem item, SaleOrder order, long daysRemaining) {
        String message = String.format("临近验货期%d天，请检查生产状态", daysRemaining);
        return new OrderAlertDTO(
            OrderAlertDTO.AlertType.PRODUCTION_REMINDER,
            order.getId(),
            order.getBillNo(),
            order.getCustomer() != null ? order.getCustomer().getName() : null,
            order.getWoNumber(),
            item.getId(),
            item.getMaterial() != null ? item.getMaterial().getCode() : null,
            item.getMaterial() != null ? item.getMaterial().getName() : null,
            item.getQty(),
            item.getUnit() != null ? item.getUnit().getCode() : null,
            item.getUnit() != null ? item.getUnit().getName() : null,
            item.getInspectionDate(),
            item.getDeliveryDate(),
            daysRemaining,
            message
        );
    }
    
    /**
     * 获取提醒优先级（数字越小优先级越高）
     */
    private int getAlertPriority(OrderAlertDTO.AlertType alertType) {
        return switch (alertType) {
            case DELIVERY_OVERDUE -> 1;      // 超期告警优先级最高
            case PRODUCTION_REMINDER -> 2;   // 生产提醒次之
            case PURCHASE_REMINDER -> 3;      // 采购提醒最低
        };
    }
    
    /**
     * 创建要货日期超期告警
     */
    private OrderAlertDTO createDeliveryOverdueAlert(SaleOrderItem item, SaleOrder order, long daysOverdue) {
        String message = String.format("要货日期已超期%d天", daysOverdue);
        return new OrderAlertDTO(
            OrderAlertDTO.AlertType.DELIVERY_OVERDUE,
            order.getId(),
            order.getBillNo(),
            order.getCustomer() != null ? order.getCustomer().getName() : null,
            order.getWoNumber(),
            item.getId(),
            item.getMaterial() != null ? item.getMaterial().getCode() : null,
            item.getMaterial() != null ? item.getMaterial().getName() : null,
            item.getQty(),
            item.getUnit() != null ? item.getUnit().getCode() : null,
            item.getUnit() != null ? item.getUnit().getName() : null,
            item.getInspectionDate(),
            item.getDeliveryDate(),
            -daysOverdue,  // 负数表示超期天数
            message
        );
    }
}
