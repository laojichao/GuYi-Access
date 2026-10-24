package com.guyi.access.config;

import com.guyi.access.filter.JwtAuthenticationFilter;
import com.guyi.access.filter.RateLimitFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RateLimitFilter rateLimitFilter;

    @Value("${app.cors.allowed-origins:http://localhost:5173}")
    private String allowedOrigins;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter, RateLimitFilter rateLimitFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.rateLimitFilter = rateLimitFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf.disable())
            .headers(headers -> headers
                .frameOptions(frame -> frame.sameOrigin())
                .xssProtection(xss -> {})
                .contentTypeOptions(cto -> {})
            )
            // Without an explicit entry point Spring Security answers 403 for a missing/expired JWT,
            // which the admin frontend never maps to a logout (it only reacts to 401).
            .exceptionHandling(ex -> ex.authenticationEntryPoint((request, response, authException) -> {
                response.setStatus(401);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"code\":401,\"msg\":\"未登录或登录已过期\"}");
            }))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/login", "/api/admin/install", "/api/health").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/verify").permitAll()
                // Unknown /api/** paths must reach the DispatcherServlet so it can answer 404 (JSON,
                // via ApiErrorController). Authenticating them first would turn every 404 into a 401,
                // which both contradicts the documented status codes and hides the real cause from
                // clients. Everything that DOES map to a handler still requires authentication.
                .requestMatchers("/api/**").access(apiAccessManager())
                .anyRequest().permitAll()
            )
            // Rate limiting runs before JWT parsing: cheap rejection happens first, and anchoring to
            // the JWT filter makes the order explicit instead of depending on registration order.
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(rateLimitFilter, JwtAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Both filters are {@code @Component}s, so Boot would ALSO register them in the servlet
     * container's own chain, where they ran a second time per request - doubling every counter and
     * halving the effective rate limits, and parsing every JWT twice. These disabled registrations
     * keep them exclusive to the security filter chain.
     */
    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtAuthenticationFilterRegistration(
            JwtAuthenticationFilter filter) {
        FilterRegistrationBean<JwtAuthenticationFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilterRegistration(RateLimitFilter filter) {
        FilterRegistrationBean<RateLimitFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Authentication rule for {@code /api/**}: authenticated when the request maps to a real
     * controller method, granted otherwise so the DispatcherServlet produces its own 404/405 JSON
     * envelope.
     *
     * <p>{@code AuthorizationFilter} normally runs before MVC resolves the path, so an unknown API
     * path would be rejected as 401 (unauthenticated) instead of 404. Letting those requests through
     * keeps the documented status codes (404/405) truthful without weakening authentication for any
     * endpoint that actually exists: an unmapped path has nothing to protect.
     *
     * <p>Only {@link RequestMappingHandlerMapping} is consulted. The static-resource mapping matches
     * every path (its resolver decides later whether a file exists), so including it would grant
     * access to paths that no endpoint serves.
     */
    private AuthorizationManager<RequestAuthorizationContext> apiAccessManager() {
        return (authenticationSupplier, context) -> {
            HttpServletRequest request = context.getRequest();
            try {
                HandlerExecutionChain handler = requestMappingHandlerMapping().getHandler(request);
                log.debug("api access check: uri={} mapped={}", request.getRequestURI(), handler != null);
                if (handler != null) {
                    Authentication authentication = authenticationSupplier.get();
                    // AnonymousAuthenticationFilter runs before AuthorizationFilter and installs an
                    // anonymous token whose isAuthenticated() is TRUE, so that flag alone cannot be
                    // used: an anonymous token means "not logged in" for our purposes.
                    boolean anonymous = authentication instanceof AnonymousAuthenticationToken;
                    boolean authenticated = authentication != null && authentication.isAuthenticated() && !anonymous;
                    return new AuthorizationDecision(authenticated);
                }
            } catch (Exception e) {
                // A mapping failure must not silently grant access: fall through to the denial
                // below, which is the safe direction for an endpoint we could not classify.
                log.warn("apiAccessManager: lookup failed for {}", request.getRequestURI(), e);
                return new AuthorizationDecision(false);
            }
            return new AuthorizationDecision(true);
        };
    }

    private RequestMappingHandlerMapping requestMappingHandlerMapping() {
        return applicationContext.getBean(RequestMappingHandlerMapping.class);
    }

    @Autowired
    public void setApplicationContext(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    private ApplicationContext applicationContext;


    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // Trim each entry: "a.com, b.com" (a very common way to write it) otherwise leaves a leading
        // space on the second origin, which then never matches a browser Origin header.
        List<String> origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList();
        // Spring rejects setAllowedOrigins("*") with setAllowCredentials(true)
        if (origins.contains("*")) {
            configuration.setAllowedOriginPatterns(List.of("*"));
            configuration.setAllowCredentials(false);
        } else {
            configuration.setAllowedOrigins(origins);
            configuration.setAllowCredentials(true);
        }
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(List.of("Authorization"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
