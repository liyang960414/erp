package com.sambound.erp.service.importing.task;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 导入任务执行结果。
 */
public final class ImportTaskExecutionResult {

    private final int totalCount;
    private final int successCount;
    private final int failureCount;
    private final List<ImportTaskFailureDetail> failures;
    private final Object summary;

    private ImportTaskExecutionResult(Builder builder) {
        this.totalCount = builder.totalCount;
        this.successCount = builder.successCount;
        this.failureCount = builder.failureCount;
        this.failures = Collections.unmodifiableList(builder.failures);
        this.summary = builder.summary;
    }

    public int getTotalCount() {
        return totalCount;
    }

    public int getSuccessCount() {
        return successCount;
    }

    public int getFailureCount() {
        return failureCount;
    }

    public List<ImportTaskFailureDetail> getFailures() {
        return failures;
    }

    public Object getSummary() {
        return summary;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private int totalCount;
        private int successCount;
        private int failureCount;
        private List<ImportTaskFailureDetail> failures = List.of();
        private Object summary;

        private Builder() {
        }

        public Builder totalCount(int totalCount) {
            this.totalCount = totalCount;
            return this;
        }

        public Builder successCount(int successCount) {
            this.successCount = successCount;
            return this;
        }

        public Builder failureCount(int failureCount) {
            this.failureCount = failureCount;
            return this;
        }

        public Builder failures(List<ImportTaskFailureDetail> failures) {
            this.failures = failures != null ? failures : List.of();
            return this;
        }

        public Builder summary(Object summary) {
            this.summary = summary;
            return this;
        }

        public ImportTaskExecutionResult build() {
            if (failureCount < 0 || successCount < 0 || totalCount < 0) {
                throw new IllegalArgumentException("count values must be >= 0");
            }
            if (totalCount == 0) {
                totalCount = successCount + failureCount;
            }
            if (failures == null) {
                failures = List.of();
            }
            return new ImportTaskExecutionResult(this);
        }
    }

    @Override
    public String toString() {
        return "ImportTaskExecutionResult{"
                + "totalCount=" + totalCount
                + ", successCount=" + successCount
                + ", failureCount=" + failureCount
                + ", failures=" + failures.size()
                + ", summary=" + (summary != null ? summary.getClass().getSimpleName() : "null")
                + '}';
    }

    @Override
    public int hashCode() {
        return Objects.hash(totalCount, successCount, failureCount, failures, summary);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        ImportTaskExecutionResult that = (ImportTaskExecutionResult) obj;
        return totalCount == that.totalCount
                && successCount == that.successCount
                && failureCount == that.failureCount
                && Objects.equals(failures, that.failures)
                && Objects.equals(summary, that.summary);
    }
}


