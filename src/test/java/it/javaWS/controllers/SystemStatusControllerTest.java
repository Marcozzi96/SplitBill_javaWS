package it.javaWS.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import it.javaWS.filters.HttpTrafficFilter;

@SpringBootTest
@AutoConfigureMockMvc
class SystemStatusControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private HttpTrafficFilter httpTrafficFilter;

    @Test
    void getApiStatus_senzaToken_returns4xx() throws Exception {
        mockMvc.perform(get("/api/status"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void getApiStatus_conUtenteAutenticato_returns200eStrutturaDto() throws Exception {
        mockMvc.perform(get("/api/status").with(user("testuser")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timestamp").isString())
                .andExpect(jsonPath("$.hostname").isString())
                .andExpect(jsonPath("$.uptimeSeconds").isNumber())
                .andExpect(jsonPath("$.cpu.cores").isNumber())
                .andExpect(jsonPath("$.cpu.usagePercent").isNumber())
                .andExpect(jsonPath("$.memory.totalBytes").isNumber())
                .andExpect(jsonPath("$.memory.usedBytes").isNumber())
                .andExpect(jsonPath("$.memory.freeBytes").isNumber())
                .andExpect(jsonPath("$.memory.usagePercent").isNumber())
                .andExpect(jsonPath("$.disk.totalBytes").isNumber())
                .andExpect(jsonPath("$.disk.usedBytes").isNumber())
                .andExpect(jsonPath("$.disk.freeBytes").isNumber())
                .andExpect(jsonPath("$.disk.usagePercent").isNumber())
                .andExpect(jsonPath("$.network.rxBytesTotal").isNumber())
                .andExpect(jsonPath("$.network.txBytesTotal").isNumber())
                .andExpect(jsonPath("$.http.requestsTotal").isNumber())
                .andExpect(jsonPath("$.http.bytesInTotal").isNumber())
                .andExpect(jsonPath("$.http.bytesOutTotal").isNumber())
                .andExpect(jsonPath("$.http.requests2xx").isNumber())
                .andExpect(jsonPath("$.http.requests4xx").isNumber())
                .andExpect(jsonPath("$.http.requests5xx").isNumber())
                .andExpect(jsonPath("$.http.totalTimeMs").isNumber());
    }

    @Test
    void httpTrafficFilter_contaLeRichiesteMaEscludeApiStatus() throws Exception {
        HttpTrafficFilter.Snapshot prima = httpTrafficFilter.snapshot();

        // Endpoint pubblico: deve essere conteggiato come 2xx
        mockMvc.perform(get("/status/isOn"))
                .andExpect(status().isOk());

        // Endpoint di metriche: NON deve essere conteggiato
        mockMvc.perform(get("/api/status").with(user("testuser")))
                .andExpect(status().isOk());

        HttpTrafficFilter.Snapshot dopo = httpTrafficFilter.snapshot();

        assertThat(dopo.requestsTotal()).isEqualTo(prima.requestsTotal() + 1);
        assertThat(dopo.requests2xx()).isEqualTo(prima.requests2xx() + 1);
        assertThat(dopo.bytesOutTotal()).isGreaterThan(prima.bytesOutTotal());
        assertThat(dopo.totalTimeMs()).isGreaterThanOrEqualTo(prima.totalTimeMs());
    }

    @Test
    void httpTrafficFilter_contaGliErrori4xx() throws Exception {
        HttpTrafficFilter.Snapshot prima = httpTrafficFilter.snapshot();

        // Login con credenziali errate → 401 conteggiato come 4xx
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"utente-inesistente\",\"password\":\"sbagliata\"}"))
                .andExpect(status().isUnauthorized());

        HttpTrafficFilter.Snapshot dopo = httpTrafficFilter.snapshot();

        assertThat(dopo.requests4xx()).isEqualTo(prima.requests4xx() + 1);
    }
}
