package com.loopone.loopinbe.global.config;

import com.loopone.loopinbe.global.oauth.authorization.CustomOAuth2AuthorizationRequestResolver;
import com.loopone.loopinbe.global.oauth.authorization.HttpCookieOAuth2AuthorizationRequestRepository;
import com.loopone.loopinbe.global.oauth.handler.WebOAuth2FailureHandler;
import com.loopone.loopinbe.global.oauth.handler.WebOAuth2SuccessHandler;
import com.loopone.loopinbe.global.oauth.user.CustomOAuth2UserService;
import com.loopone.loopinbe.global.security.JwtAuthenticationFilter;
import com.loopone.loopinbe.domain.account.auth.serviceImpl.CustomUserDetailsServiceImpl;
import jakarta.servlet.DispatcherType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final CustomUserDetailsServiceImpl customUserDetailsServiceImpl;
    private final HttpCookieOAuth2AuthorizationRequestRepository cookieAuthRequestRepo;
    private final CustomOAuth2AuthorizationRequestResolver customAuthRequestResolver;
    private final CustomOAuth2UserService customOAuth2UserService;
    private final WebOAuth2SuccessHandler webOAuth2SuccessHandler;
    private final WebOAuth2FailureHandler webOAuth2FailureHandler;
    private final PasswordEncoder passwordEncoder;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .headers(headers -> headers.frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers("/error", "/favicon.ico").permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(
                                "/rest-api/v1/auth/signup-login",
                                "/rest-api/v1/auth/login",
                                "/rest-api/v1/member/available",
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/api/v1/health-check",
                                "/ws/**",
                                "/oauth2/**",              // OAuth2 시작 엔드포인트.
                                "/login/oauth2/**"         // OAuth2 콜백 엔드포인트
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                .oauth2Login(oauth -> oauth
                        .authorizationEndpoint(ae -> ae
                                .authorizationRequestRepository(cookieAuthRequestRepo)
                                .authorizationRequestResolver(customAuthRequestResolver)
                        )
                        .userInfoEndpoint(ui -> ui.userService(customOAuth2UserService))
                        .successHandler(webOAuth2SuccessHandler)
                        .failureHandler(webOAuth2FailureHandler)
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager(HttpSecurity http) throws Exception {
        AuthenticationManagerBuilder authenticationManagerBuilder = http.getSharedObject(AuthenticationManagerBuilder.class);
        authenticationManagerBuilder
                .userDetailsService(customUserDetailsServiceImpl)
                .passwordEncoder(passwordEncoder);
        return authenticationManagerBuilder.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(Arrays.asList(
                "http://localhost:8080",
                "http://localhost:3000",
                "http://local.loopin.co.kr",
                "https://local.loopin.co.kr",
                "https://loopin.co.kr",
                "https://develop.loopin.co.kr"
        ));
        configuration.setAllowedMethods(Arrays.asList("HEAD", "POST", "GET", "DELETE", "PUT", "PATCH", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "Cache-Control", "Content-Length", "*"));
        configuration.setAllowCredentials(true); // 쿠키 허용
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
