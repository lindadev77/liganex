package tech.liganex.studio.support;

import org.springframework.context.annotation.Configuration;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * 切片测试（{@code @WebMvcTest}）用的 MVC 配置。
 *
 * <p>Spring Security 7 把 {@code @AuthenticationPrincipal} 的参数解析器从
 * {@code ...web.configuration.WebMvcSecurityConfiguration} 拆到了 servlet 包下的新配置类；
 * 而 {@code @EnableWebSecurity} 仍会注册旧配置类的遗留 bean，直接再导入新配置类会撞
 * {@code requestDataValueProcessor} 的定义。因此这里只注册解析器本身，不导入整个配置类。
 *
 * <p>没有它，控制器里的 {@code @AuthenticationPrincipal Long ownerUserId} 在切片测试中会解析为
 * {@code null}，导致"owner 来自认证上下文"这类断言无法验证。
 */
@Configuration(proxyBeanMethods = false)
public class AuthenticationPrincipalTestConfig implements WebMvcConfigurer {

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new AuthenticationPrincipalArgumentResolver());
    }
}
