package com.example.pointservicebatch.config;

import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Configuration
@EnableBatchProcessing
@EnableTransactionManagement
public class BatchConfig {
    // Spring Batch의 기본 설정을 활성화함
    // ex) JobRepository 같은 빈들이 자동으로 주입 가능해짐.
}
