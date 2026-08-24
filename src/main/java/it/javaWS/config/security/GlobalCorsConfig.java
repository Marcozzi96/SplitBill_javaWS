package it.javaWS.config.security;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class GlobalCorsConfig {

    // Origini aggiuntive da variabile d'ambiente (separate da virgola), es. il dominio del FE in prod
    private final List<String> extraOrigins;

    public GlobalCorsConfig(@Value("${app.cors.allowed-origins:}") String extraOrigins) {
        this.extraOrigins = extraOrigins.isBlank()
                ? List.of()
                : Arrays.stream(extraOrigins.split(",")).map(String::trim).toList();
    }

    // Unica sorgente di configurazione CORS, applicata dal CorsFilter di Spring Security
    // (attivato con http.cors(...) in SecurityConfig). Gestisce anche i preflight OPTIONS,
    // che altrimenti verrebbero bloccati dalla filter chain prima di arrivare al layer MVC.
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        List<String> allowedOrigins = new ArrayList<>(
                List.of("http://localhost:3000"));
        allowedOrigins.addAll(extraOrigins);
        configuration.setAllowedOrigins(allowedOrigins);
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
