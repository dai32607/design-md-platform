package com.designmd.platform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 应用入口。
 * <p>说明：MyBatis Mapper 扫描 {@code @MapperScan} 已移至 {@code MybatisPlusConfig}，
 * 使 {@code @WebMvcTest} 接口切片测试不会注册 Mapper Bean（无需真实数据源即可运行）。
 */
@SpringBootApplication
public class DesignMdPlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(DesignMdPlatformApplication.class, args);
    }
}
