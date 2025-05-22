package it.javaWS.javaws.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

@Configuration
public class CorsConfig {

    @Bean
    public CorsFilter corsFilter() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        CorsConfiguration config = new CorsConfiguration();
        
        // Permetti le origini specificate
        config.addAllowedOrigin("http://localhost:3000");
        config.addAllowedOrigin("https://fe-splitbill.vercel.app");
        
        // Permetti le intestazioni standard
        config.addAllowedHeader("*");
        
        // Permetti i metodi HTTP specifici
        config.addAllowedMethod("GET");
        config.addAllowedMethod("POST");
        config.addAllowedMethod("PUT");
        config.addAllowedMethod("DELETE");
        config.addAllowedMethod("OPTIONS");
        
        // Permetti l'invio di credenziali (cookies, headers di autenticazione)
        config.setAllowCredentials(true);
        
        source.registerCorsConfiguration("/", config);
        return new CorsFilter(source);
	}
}
