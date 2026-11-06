package com.thira.tps.config;

import java.io.IOException;
import java.net.URI;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.context.annotation.*;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
public class SecurityConfiguration {
    @Bean UserDetailsService users(RuntimeSettings settings) {
        return new InMemoryUserDetailsManager(User.withUsername("tps-developer")
            .password("{bcrypt}"+new BCryptPasswordEncoder().encode(settings.getDeveloperPassword())).roles("DEVELOPER").build());
    }
    @Bean SecurityFilterChain security(HttpSecurity http) throws Exception {
        return http.csrf(csrf -> csrf.disable()).cors(cors -> cors.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .requestCache(c -> c.disable())
            .authorizeHttpRequests(auth -> auth.requestMatchers("/api/**").authenticated().anyRequest().permitAll())
            .httpBasic(b -> b.authenticationEntryPoint((request,response,ex) -> error(response,401,"AUTH_REQUIRED","연결 정보를 확인하세요.")))
            .exceptionHandling(e -> e.accessDeniedHandler((request,response,ex) -> error(response,403,"ACCESS_DENIED","접근할 수 없습니다.")))
            .headers(h -> h.contentSecurityPolicy(p -> p.policyDirectives("default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; font-src 'self'; connect-src 'self'; object-src 'none'; base-uri 'self'; frame-ancestors 'none'")))
            .addFilterBefore(new SameOriginMutationFilter(), BasicAuthenticationFilter.class).build();
    }
    private static void error(HttpServletResponse r,int status,String code,String message) throws IOException {
        r.setStatus(status); r.setContentType("application/json;charset=UTF-8");
        r.getWriter().write("{\"code\":\""+code+"\",\"message\":\""+message+"\"}");
    }
    private static class SameOriginMutationFilter extends OncePerRequestFilter {
        @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain) throws ServletException,IOException {
            if(req.getRequestURI().startsWith("/api/")) {
                res.setHeader("Cache-Control","no-store");
                if(!req.getMethod().equals("GET") && !req.getMethod().equals("HEAD")) {
                    if(!"designer".equals(req.getHeader("X-TPS-Client")) ||
                       req.getContentType()==null || !req.getContentType().toLowerCase(java.util.Locale.ROOT).startsWith("application/json") ||
                       "cross-site".equals(req.getHeader("Sec-Fetch-Site")) || !sameOrigin(req)) {
                        error(res,403,"REQUEST_ORIGIN_REJECTED","요청 출처를 확인할 수 없습니다."); return;
                    }
                }
            }
            chain.doFilter(req,res);
        }
        private boolean sameOrigin(HttpServletRequest req) {
            String raw=req.getHeader("Origin"); if(raw==null) return true;
            try { URI uri=URI.create(raw); int port=uri.getPort()<0?("https".equals(uri.getScheme())?443:80):uri.getPort();
                return req.getScheme().equals(uri.getScheme()) && req.getServerName().equals(uri.getHost()) && req.getServerPort()==port;
            } catch(IllegalArgumentException ex) { return false; }
        }
    }
}
