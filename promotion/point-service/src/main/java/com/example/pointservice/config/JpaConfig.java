package com.example.pointservice.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@Configuration
@EnableJpaAuditing // JPA Auditing 기능 사용함
public class JpaConfig {
    /*
    Spring Data JPA에서 감사(Auditing) 기능을 활성화하기 위한 설정
    이걸 통해 엔티티에 @CreatedDate, @LastModifiedDate, @CreatedBy, @LastModifiedBy 같은
    어노테이션이 자동으로 값이 채워질 수 있도록 설정
    */
}
