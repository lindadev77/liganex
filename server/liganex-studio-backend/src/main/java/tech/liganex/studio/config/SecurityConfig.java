package tech.liganex.studio.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import tech.liganex.studio.module.auth.security.JwtAuthenticationFilter;

/**
 * 安全配置：无状态、按路径前缀区分三套独立鉴权体系（ADR-0002 / ADR-0009）。
 *
 * <ul>
 *   <li>{@code /api/v1/auth/**} —— 开放（注册登录）</li>
 *   <li>{@code /api/**} —— 用户 JWT（人），由 {@code JwtAuthenticationFilter} 校验</li>
 *   <li>{@code /internal/**} —— 服务间凭证（服务），由 {@code InternalApiKeyFilter} 校验</li>
 *   <li>{@code /mcp/**} —— 开放平台应用签名（MCP/skill），由 {@code McpController} 内 {@code McpAuthService} 校验</li>
 * </ul>
 *
 * <p>后两者在本配置中 {@code permitAll()}，真正的鉴权由其各自的过滤器/控制器完成，
 * 避免把不同体系的凭证混用（如用户 JWT 访问 /internal 或 /mcp 一律无效）。
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/info", "/actuator/flyway").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .requestMatchers("/internal/**").permitAll()
                        .requestMatchers("/mcp/**").permitAll()
                        .anyRequest().denyAll())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
