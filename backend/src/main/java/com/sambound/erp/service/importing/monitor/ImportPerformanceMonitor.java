package com.sambound.erp.service.importing.monitor;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 导入性能监控切面。
 */
@Aspect
@Component
public class ImportPerformanceMonitor {

    private static final Logger logger = LoggerFactory.getLogger(ImportPerformanceMonitor.class);

    @Around("@annotation(monitored)")
    public Object monitor(ProceedingJoinPoint pjp, Monitored monitored) throws Throwable {
        long start = System.currentTimeMillis();
        String methodName = monitored.value().isEmpty() 
                ? pjp.getSignature().getName() 
                : monitored.value();
        
        try {
            return pjp.proceed();
        } finally {
            long duration = System.currentTimeMillis() - start;
            logger.info("性能监控 [{}]: 耗时 {} ms", methodName, duration);
        }
    }
}
