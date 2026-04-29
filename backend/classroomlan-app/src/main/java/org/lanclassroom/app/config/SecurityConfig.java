package org.lanclassroom.app.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

import static org.springframework.security.config.Customizer.withDefaults;

/**
 * Spring Security 配置 - 启用方法级安全校验
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable()) // WebSocket 需要禁用 CSRF
            .authorizeHttpRequests(auth -> auth
                .anyRequest().permitAll() // 开发阶段全部放行，后续细化
            )
            .httpBasic(withDefaults()); // 简单鉴权（可选）

        return http.build();
    }
}
