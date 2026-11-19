package com.luany.inventory.controller;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.session.HttpSessionEventPublisher;
import org.springframework.security.web.session.SessionInformationExpiredStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import com.luany.inventory.service.SeguridadService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import java.util.Arrays;
import java.util.List;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

@Configuration
@EnableWebSecurity
public class ConfiguracionSeguridad {

    static final int MAX_SESIONES_POR_USUARIO = 3;

    @Value("${security.cors.allowed-origins:}")
    private String corsAllowedOrigins;

    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    public SessionRegistry sessionRegistry() {
        return new SessionRegistryImpl();
    }

    @Bean
    public HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            SecurityContextRepository securityContextRepository,
            SessionRegistry sessionRegistry,
            ObjectProvider<SeguridadService> seguridadProvider) throws Exception {
        CsrfTokenRequestAttributeHandler csrfHandler = new CsrfTokenRequestAttributeHandler();
        HttpSessionCsrfTokenRepository csrfTokenRepository = new HttpSessionCsrfTokenRepository();
        SessionInformationExpiredStrategy expiredSessionStrategy = event -> {
            HttpServletResponse response = event.getResponse();
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write(
                    "{\"mensaje\":\"Tu sesión ha expirado. Inicia sesión nuevamente.\"}");
        };

        http
                .securityContext(context -> context.securityContextRepository(securityContextRepository))
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository)
                        .csrfTokenRequestHandler(csrfHandler))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/api/auth/csrf",
                                "/api/auth/login",
                                "/api/auth/registro-inicial/disponible",
                                "/api/auth/solicitar-recuperacion",
                                "/api/auth/restablecer-password")
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/registro-inicial").permitAll()
                        .requestMatchers("/api/auth/me").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/auth/cambiar-password")
                        .hasAnyAuthority("PASSWORD_CHANGE_REQUIRED", "ROLE_ADMIN", "ROLE_EMPLEADO")
                        .requestMatchers(HttpMethod.POST, "/api/alertas/notificar-stock")
                            .hasAnyRole("ADMIN", "EMPLEADO")
                        .requestMatchers("/api/usuarios", "/api/usuarios/**").hasRole("ADMIN")
                        .requestMatchers("/api/seguridad", "/api/seguridad/**").hasRole("ADMIN")
                        .requestMatchers("/api/**").hasAnyRole("ADMIN", "EMPLEADO")
                        .anyRequest().permitAll())
                .sessionManagement(session -> session
                        .maximumSessions(MAX_SESIONES_POR_USUARIO)
                        .maxSessionsPreventsLogin(false)
                        .expiredSessionStrategy(expiredSessionStrategy)
                        .sessionRegistry(sessionRegistry))
                .headers(headers -> headers
                        .frameOptions(frame -> frame.deny())
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'self'; script-src 'self' 'unsafe-inline'; "
                                        + "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; "
                                        + "img-src 'self' data: https://fonts.gstatic.com; "
                                        + "font-src 'self' https://fonts.gstatic.com; connect-src 'self'; "
                                        + "frame-ancestors 'none'; base-uri 'self'; form-action 'self'"))
                        .referrerPolicy(referrer -> referrer.policy(
                                ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31_536_000)))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) -> {
                            registrarAuditoria(seguridadProvider, "anonimo",
                                    "ACCESO_SIN_AUTENTICACION", "DENEGADO",
                                    request.getRemoteAddr(), "Acceso a ruta protegida sin sesión");
                            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
                        })
                        .accessDeniedHandler((request, response, exception) -> {
                            String usuario = request.getUserPrincipal() == null
                                    ? "anonimo" : request.getUserPrincipal().getName();
                            registrarAuditoria(seguridadProvider, usuario,
                                    "ACCESO_SIN_AUTORIZACION", "DENEGADO",
                                    request.getRemoteAddr(),
                                    "Acceso denegado a ruta administrativa o restringida");
                            response.sendError(HttpServletResponse.SC_FORBIDDEN);
                        }))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(logout -> logout
                        .logoutUrl("/api/auth/logout")
                        .logoutSuccessHandler((request, response, authentication) -> {
                            String usuario = authentication == null ? "anonimo" : authentication.getName();
                            registrarAuditoria(seguridadProvider, usuario,
                                    "CIERRE_SESION", "EXITOSO",
                                    request.getRemoteAddr(), "Sesión cerrada");
                            response.setStatus(HttpServletResponse.SC_NO_CONTENT);
                        }));

        return http.build();
    }

    private static void registrarAuditoria(
            ObjectProvider<SeguridadService> seguridadProvider,
            String usuario,
            String tipo,
            String resultado,
            String ip,
            String descripcion) {
        SeguridadService seguridad = seguridadProvider.getIfAvailable();
        if (seguridad != null) {
            seguridad.registrarAuditoria(usuario, tipo, resultado, ip, descripcion);
        }
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuracion = new CorsConfiguration();
        List<String> origenes = Arrays.stream(corsAllowedOrigins.split(","))
                .map(String::trim)
                .filter(origen -> !origen.isBlank())
                .toList();
        if (origenes.isEmpty()) {
            configuracion.addAllowedOriginPattern("http://localhost:*");
        } else {
            configuracion.setAllowedOrigins(origenes);
        }
        configuracion.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuracion.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-CSRF-TOKEN", "Accept"));
        configuracion.setAllowCredentials(true);
        configuracion.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuracion);
        return source;
    }
}