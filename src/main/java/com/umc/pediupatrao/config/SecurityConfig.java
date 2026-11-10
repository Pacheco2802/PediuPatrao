package com.umc.pediupatrao.config;

import com.umc.pediupatrao.service.UsuarioDetailsService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity // libera o uso do @PreAuthorize nos services
public class SecurityConfig {

    private final UsuarioDetailsService usuarioDetailsService;

    public SecurityConfig(UsuarioDetailsService usuarioDetailsService) {
        this.usuarioDetailsService = usuarioDetailsService;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(usuarioDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // CSRF fica LIGADO (padrao do Spring Security).
                // Os formularios com th:action ja mandam o token sozinhos
                // e quem usar fetch precisa mandar o header X-CSRF-TOKEN (ver layout.html)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/login", "/error", "/css/**", "/dist/**", "/plugins/**", "/js/**", "/images/**").permitAll()

                        // ---------- USUARIOS: somente ADMIN ----------
                        .requestMatchers("/usuarios", "/usuarios/**", "/api/usuarios", "/api/usuarios/**", "/admin").hasRole("ADMIN")

                        // ---------- AUDITORIA: so consulta, ADMIN e GERENTE ----------
                        .requestMatchers(HttpMethod.GET, "/auditoria", "/api/auditoria", "/api/auditoria/**").hasAnyRole("ADMIN", "GERENTE")
                        .requestMatchers("/auditoria/**", "/api/auditoria/**").denyAll()

                        // ---------- PEDIDOS ----------
                        // mudar status, desconto e cancelamento: somente GERENTE
                        .requestMatchers("/pedidos/*/status", "/pedidos/*/desconto", "/pedidos/*/cancelar").hasRole("GERENTE")
                        .requestMatchers("/api/pedidos/*/status", "/api/pedidos/*/desconto", "/api/pedidos/*/cancelar").hasRole("GERENTE")
                        // registrar pedido: ATENDENTE e GERENTE
                        .requestMatchers("/pedidos/novo", "/pedidos/salvar").hasAnyRole("ATENDENTE", "GERENTE")
                        .requestMatchers(HttpMethod.POST, "/api/pedidos").hasAnyRole("ATENDENTE", "GERENTE")
                        // consultar pedidos: os tres perfis
                        .requestMatchers(HttpMethod.GET, "/pedidos", "/pedidos/*", "/api/pedidos", "/api/pedidos/*").hasAnyRole("ADMIN", "GERENTE", "ATENDENTE")
                        .requestMatchers("/pedidos/**", "/api/pedidos/**").denyAll()

                        // ---------- CLIENTES ----------
                        // consultar: GERENTE e ATENDENTE
                        .requestMatchers(HttpMethod.GET, "/clientes", "/api/clientes", "/api/clientes/*").hasAnyRole("GERENTE", "ATENDENTE")
                        // excluir: somente GERENTE
                        .requestMatchers("/clientes/excluir/*").hasRole("GERENTE")
                        .requestMatchers(HttpMethod.DELETE, "/api/clientes/*").hasRole("GERENTE")
                        // cadastrar e atualizar: ATENDENTE
                        .requestMatchers("/clientes/**", "/api/clientes", "/api/clientes/**").hasRole("ATENDENTE")

                        // ---------- PRODUTOS ----------
                        // todos podem ver (precisa pra montar o pedido), so ADMIN e GERENTE alteram
                        .requestMatchers(HttpMethod.GET, "/produtos", "/api/produtos").authenticated()
                        .requestMatchers("/produtos/**", "/api/produtos", "/api/produtos/**").hasAnyRole("ADMIN", "GERENTE")

                        .anyRequest().authenticated()
                )
                // nas APIs devolve 401 em vez de redirecionar pra tela de login
                .exceptionHandling(ex -> ex
                        .defaultAuthenticationEntryPointFor(
                                new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                                new AntPathRequestMatcher("/api/**"))
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        .permitAll()
                        .successHandler(authenticationSuccessHandler())
                )
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout")
                        .permitAll()
                );

        return http.build();
    }

    @Bean
    public AuthenticationSuccessHandler authenticationSuccessHandler() {
        return (request, response, authentication) -> {
            response.sendRedirect("/"); // Redireciona para a página inicial após login bem-sucedido
        };
    }
}
