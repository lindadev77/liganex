package tech.liganex.studio;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Liganex Studio 后端入口。
 *
 * <p>部署形态为模块化单体（ADR-0006）：auth / order / openapp 为同进程内的独立模块，
 * 模块边界由包结构与 DTO 隔离保证，便于未来渐进拆分。
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class StudioApplication {

    public static void main(String[] args) {
        SpringApplication.run(StudioApplication.class, args);
    }
}
