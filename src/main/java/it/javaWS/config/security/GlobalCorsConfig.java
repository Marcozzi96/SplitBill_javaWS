package it.javaWS.config.security;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class GlobalCorsConfig {

    // Unica sorgente di configurazione CORS, applicata dal CorsFilter di Spring Security
    // (attivato con http.cors(...) in SecurityConfig). Gestisce anche i preflight OPTIONS,
    // che altrimenti verrebbero bloccati dalla filter chain prima di arrivare al layer MVC.
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of("http://localhost:3000", "https://fe-splitbill.vercel.app"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true); // opzionale, utile se usi cookie o autenticazione

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration); // applica a tutte le rotte
        return source;
    }
}
