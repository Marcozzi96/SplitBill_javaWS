//package filters;

package it.javaWS.filters;

import java.io.IOException;

import org.springframework.stereotype.Component;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class SuspiciousRequestFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        String uri = httpRequest.getRequestURI();

        // Verifica presenza di pattern sospetti
        if (uri.matches(".*\\$\\{jndi:.*") || uri.contains("MDEDiscovery")) {
            System.out.println("[- Sicurezza -] : Bloccata richiesta sospetta: " + uri);
            ((HttpServletResponse) response).sendError(HttpServletResponse.SC_BAD_REQUEST, "Richiesta sospetta bloccata.");
            return;
        }
        // Prosegui con la richiesta
        chain.doFilter(request, response);
    }
}
