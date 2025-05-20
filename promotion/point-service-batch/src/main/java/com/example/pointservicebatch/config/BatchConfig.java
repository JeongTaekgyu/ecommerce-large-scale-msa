package com.example.pointservicebatch.config;

import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Configuration
@EnableBatchProcessing
@EnableTransactionManagement
public class BatchConfig {
    // @EnableBatchProcessing 어노테이션은 Spring Batch 기능을 활성화하는 어노테이션이다.
    // JobRepository, JobLauncher, JobRegistry 등과 같은 Spring Batch의 핵심 컴포넌트들을 자동으로 설정해준다.
}
