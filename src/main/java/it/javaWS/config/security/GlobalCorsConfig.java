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
        // Test da altri device sulla stessa rete (es. smartphone): l'origine è l'IP
        // privato della macchina che serve il FE, con porta variabile. I pattern
        // funzionano anche con allowCredentials=true (a differenza di "*" nelle origini).
        configuration.setAllowedOriginPatterns(List.of(
                "http://localhost:*", "http://127.0.0.1:*",
                "http://192.168.*:*", "http://10.*:*", "http://172.*:*"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true); // opzionale, utile se usi cookie o autenticazione

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration); // applica a tutte le rotte
        return source;
    }
}
