package com.sambound.erp.repository;

import com.sambound.erp.entity.Material;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Repository
public class MaterialRepositoryImpl implements MaterialRepositoryCustom {
    
    @PersistenceContext
    private EntityManager entityManager;
    
    @Override
    public List<Material> batchInsertOrGetByCode(List<MaterialRepository.MaterialBatchData> batchData) {
        if (batchData == null || batchData.isEmpty()) {
            return new ArrayList<>();
        }
        
        List<Material> result = new ArrayList<>();
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        
        // 构建 VALUES 子句
        StringBuilder valuesClause = new StringBuilder();
        List<Object> parameters = new ArrayList<>();
        
        for (int i = 0; i < batchData.size(); i++) {
            if (i > 0) {
                valuesClause.append(", ");
            }
            MaterialRepository.MaterialBatchData data = batchData.get(i);
            valuesClause.append("(?, ?, ?, ?, ?, ?, ?)");
            parameters.add(data.code());
            parameters.add(data.name());
            parameters.add(data.materialGroupId());
            parameters.add(data.baseUnitId());
            parameters.add(data.erpClsId());
            parameters.add(now);
            parameters.add(now);
        }
        
        String sql = String.format("""
            INSERT INTO materials (code, name, material_group_id, base_unit_id, erp_cls_id, created_at, updated_at)
            VALUES %s
            ON CONFLICT (code) DO UPDATE 
            SET name = EXCLUDED.name,
                material_group_id = EXCLUDED.material_group_id,
                base_unit_id = EXCLUDED.base_unit_id,
                erp_cls_id = EXCLUDED.erp_cls_id,
                updated_at = CURRENT_TIMESTAMP
            RETURNING id, code, name, specification, mnemonic_code, old_number, description, erp_cls_id,
                      material_group_id, base_unit_id, created_at, updated_at
            """, valuesClause);
        
        Query query = entityManager.createNativeQuery(sql, Material.class);
        for (int i = 0; i < parameters.size(); i++) {
            query.setParameter(i + 1, parameters.get(i));
        }
        
        @SuppressWarnings("unchecked")
        List<Material> results = query.getResultList();
        result.addAll(results);
        
        return result;
    }
}

