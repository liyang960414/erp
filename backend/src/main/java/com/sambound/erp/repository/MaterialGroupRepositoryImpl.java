package com.sambound.erp.repository;

import com.sambound.erp.entity.MaterialGroup;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;

@Repository
public class MaterialGroupRepositoryImpl implements MaterialGroupRepositoryCustom {
    
    @PersistenceContext
    private EntityManager entityManager;
    
    @Override
    public Map<String, MaterialGroup> batchInsertOrGetByCode(List<String> codes, List<String> names) {
        if (codes == null || names == null || codes.size() != names.size() || codes.isEmpty()) {
            return new HashMap<>();
        }
        
        int size = codes.size();
        Map<String, MaterialGroup> result = new HashMap<>();
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        
        // 构建 VALUES 子句
        StringBuilder valuesClause = new StringBuilder();
        List<Object> parameters = new ArrayList<>();
        
        for (int i = 0; i < size; i++) {
            if (i > 0) {
                valuesClause.append(", ");
            }
            valuesClause.append("(?, ?, ?, ?)");
            parameters.add(codes.get(i));
            parameters.add(names.get(i));
            parameters.add(now);
            parameters.add(now);
        }
        
        String sql = String.format("""
            INSERT INTO material_groups (code, name, created_at, updated_at)
            VALUES %s
            ON CONFLICT (code) DO UPDATE SET code = EXCLUDED.code
            RETURNING id, code, name, description, parent_id, created_at, updated_at
            """, valuesClause);
        
        Query query = entityManager.createNativeQuery(sql, MaterialGroup.class);
        for (int i = 0; i < parameters.size(); i++) {
            query.setParameter(i + 1, parameters.get(i));
        }
        
        @SuppressWarnings("unchecked")
        List<MaterialGroup> results = query.getResultList();
        for (MaterialGroup group : results) {
            result.put(group.getCode(), group);
        }
        
        return result;
    }
    
    @Override
    public Map<String, MaterialGroup> batchInsertOrGetByCodeWithParent(
            List<com.sambound.erp.repository.MaterialGroupRepository.MaterialGroupBatchData> batchData) {
        if (batchData == null || batchData.isEmpty()) {
            return new HashMap<>();
        }
        
        Map<String, MaterialGroup> result = new HashMap<>();
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        
        // 构建 VALUES 子句
        StringBuilder valuesClause = new StringBuilder();
        List<Object> parameters = new ArrayList<>();
        
        for (int i = 0; i < batchData.size(); i++) {
            if (i > 0) {
                valuesClause.append(", ");
            }
            MaterialGroupRepository.MaterialGroupBatchData data = batchData.get(i);
            valuesClause.append("(?, ?, ?, ?, ?, ?)");
            parameters.add(data.code());
            parameters.add(data.name());
            parameters.add(data.description());
            parameters.add(data.parentId());
            parameters.add(now);
            parameters.add(now);
        }
        
        String sql = String.format("""
            INSERT INTO material_groups (code, name, description, parent_id, created_at, updated_at)
            VALUES %s
            ON CONFLICT (code) DO UPDATE 
            SET name = EXCLUDED.name, 
                description = EXCLUDED.description, 
                parent_id = EXCLUDED.parent_id,
                updated_at = EXCLUDED.updated_at
            RETURNING id, code, name, description, parent_id, created_at, updated_at
            """, valuesClause);
        
        Query query = entityManager.createNativeQuery(sql, MaterialGroup.class);
        for (int i = 0; i < parameters.size(); i++) {
            query.setParameter(i + 1, parameters.get(i));
        }
        
        @SuppressWarnings("unchecked")
        List<MaterialGroup> results = query.getResultList();
        for (MaterialGroup group : results) {
            result.put(group.getCode(), group);
        }
        
        return result;
    }
}

