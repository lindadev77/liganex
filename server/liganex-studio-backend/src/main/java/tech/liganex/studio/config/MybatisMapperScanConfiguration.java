package tech.liganex.studio.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis Mapper 扫描配置。
 *
 * <p>单独成类而非挂在 {@code StudioApplication} 上，是为了让 {@code @WebMvcTest} 之类的切片测试
 * 不加载任何 Mapper（切片测试只加载启动类本身及 Web 层组件）。否则切片测试会因缺少
 * SqlSessionFactory 而启动失败，只能靠 mock 数据源绕开。
 *
 * <p>生产启动路径不受影响：{@code @SpringBootApplication} 的组件扫描会覆盖本配置类。
 */
@Configuration
@MapperScan("tech.liganex.studio.**.mapper")
public class MybatisMapperScanConfiguration {
}
