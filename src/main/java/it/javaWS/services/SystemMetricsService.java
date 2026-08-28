package it.javaWS.services;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import it.javaWS.filters.HttpTrafficFilter;
import it.javaWS.models.dto.ServerStatusDTO;

/**
 * Raccoglie le metriche di sistema dell'host per l'endpoint GET /api/status.
 * Su Linux legge i dati da procfs (/proc/stat, /proc/meminfo, /proc/net/dev, /proc/uptime);
 * altrove (es. dev su Windows) ripiega sulle MXBean della JVM dove possibile.
 * Nessun metodo lancia eccezioni: in caso di errore i valori ricadono su default sicuri.
 */
@Service
public class SystemMetricsService {

    private static final Logger log = LoggerFactory.getLogger(SystemMetricsService.class);

    private static final Path PROC_STAT = Path.of("/proc/stat");
    private static final Path PROC_MEMINFO = Path.of("/proc/meminfo");
    private static final Path PROC_NET_DEV = Path.of("/proc/net/dev");
    private static final Path PROC_UPTIME = Path.of("/proc/uptime");

    private final HttpTrafficFilter httpTrafficFilter;

    // Ultimo campione CPU: l'uso % è calcolato sul delta tra letture consecutive
    private CampioneCpu ultimoCampioneCpu;

    public SystemMetricsService(HttpTrafficFilter httpTrafficFilter) {
        this.httpTrafficFilter = httpTrafficFilter;
    }

    public ServerStatusDTO getServerStatus() {
        return new ServerStatusDTO(
                Instant.now(),
                leggiHostname(),
                leggiUptimeSecondi(),
                leggiCpu(),
                leggiMemoria(),
                leggiDisco(),
                leggiRete(),
                leggiHttp());
    }

    // --- CPU ---

    private ServerStatusDTO.Cpu leggiCpu() {
        int cores = Runtime.getRuntime().availableProcessors();
        double uso;
        try {
            String riga = Files.readAllLines(PROC_STAT).get(0);
            CampioneCpu attuale = parseRigaCpu(riga);
            CampioneCpu precedente;
            synchronized (this) {
                precedente = ultimoCampioneCpu;
                ultimoCampioneCpu = attuale;
            }
            // Alla prima lettura si usa la media dal boot (delta rispetto allo zero)
            uso = calcolaUsoCpu(precedente == null ? new CampioneCpu(0, 0) : precedente, attuale);
        } catch (Exception e) {
            log.debug("Lettura CPU da /proc/stat non riuscita, uso MXBean: {}", e.getMessage());
            uso = usoCpuDaMxBean();
        }
        return new ServerStatusDTO.Cpu(cores, arrotonda(uso));
    }

    /**
     * Parsing della riga aggregata "cpu" di /proc/stat.
     * idle comprende anche iowait; il totale è la somma dei primi 8 campi
     * (guest e guest_nice sono esclusi perché già conteggiati in user e nice).
     */
    static CampioneCpu parseRigaCpu(String riga) {
        String[] campi = riga.trim().split("\\s+");
        if (campi.length < 5 || !"cpu".equals(campi[0])) {
            throw new IllegalArgumentException("Riga cpu non valida: " + riga);
        }
        long totale = 0;
        // campi[1..8] = user nice system idle iowait irq softirq steal
        for (int i = 1; i <= 8 && i < campi.length; i++) {
            totale += Long.parseLong(campi[i]);
        }
        long idle = Long.parseLong(campi[4]);
        if (campi.length > 5) {
            idle += Long.parseLong(campi[5]); // iowait
        }
        return new CampioneCpu(idle, totale);
    }

    /**
     * Uso CPU in percentuale (0-100) sul delta tra due campioni consecutivi.
     */
    static double calcolaUsoCpu(CampioneCpu precedente, CampioneCpu attuale) {
        long deltaTotale = attuale.totale() - precedente.totale();
        long deltaIdle = attuale.idle() - precedente.idle();
        if (deltaTotale <= 0) {
            return 0.0;
        }
        return 100.0 * (deltaTotale - deltaIdle) / deltaTotale;
    }

    private double usoCpuDaMxBean() {
        try {
            if (ManagementFactory.getOperatingSystemMXBean() instanceof com.sun.management.OperatingSystemMXBean osBean) {
                double carico = osBean.getCpuLoad(); // 0.0-1.0, -1 se non disponibile
                return carico >= 0 ? carico * 100 : 0.0;
            }
        } catch (Exception e) {
            log.debug("Lettura CPU da MXBean non riuscita: {}", e.getMessage());
        }
        return 0.0;
    }

    // --- Memoria ---

    private ServerStatusDTO.Memory leggiMemoria() {
        long totale = 0;
        long disponibile = 0;
        try {
            long[] mem = parseMemInfo(Files.readString(PROC_MEMINFO));
            totale = mem[0];
            disponibile = mem[1];
        } catch (Exception e) {
            log.debug("Lettura memoria da /proc/meminfo non riuscita, uso MXBean: {}", e.getMessage());
            try {
                if (ManagementFactory.getOperatingSystemMXBean() instanceof com.sun.management.OperatingSystemMXBean osBean) {
                    totale = osBean.getTotalMemorySize();
                    disponibile = osBean.getFreeMemorySize();
                }
            } catch (Exception e2) {
                log.debug("Lettura memoria da MXBean non riuscita: {}", e2.getMessage());
            }
        }
        long usata = Math.max(0, totale - disponibile);
        double percentuale = totale > 0 ? 100.0 * usata / totale : 0.0;
        return new ServerStatusDTO.Memory(totale, usata, disponibile, arrotonda(percentuale));
    }

    /**
     * Parsing di /proc/meminfo: restituisce [MemTotal, MemAvailable] in byte.
     */
    static long[] parseMemInfo(String contenuto) {
        long totale = -1;
        long disponibile = -1;
        for (String riga : contenuto.split("\\R")) {
            if (riga.startsWith("MemTotal:")) {
                totale = parseValoreKb(riga);
            } else if (riga.startsWith("MemAvailable:")) {
                disponibile = parseValoreKb(riga);
            }
        }
        if (totale < 0 || disponibile < 0) {
            throw new IllegalArgumentException("MemTotal/MemAvailable non trovati in /proc/meminfo");
        }
        return new long[]{totale, disponibile};
    }

    private static long parseValoreKb(String riga) {
        // Formato: "MemTotal:       16384000 kB"
        return Long.parseLong(riga.split(":")[1].trim().split("\\s+")[0]) * 1024;
    }

    // --- Disco ---

    private ServerStatusDTO.Disk leggiDisco() {
        long totale = 0;
        long libero = 0;
        try {
            File radice = new File("/");
            totale = radice.getTotalSpace();
            libero = radice.getUsableSpace();
        } catch (Exception e) {
            log.debug("Lettura spazio disco non riuscita: {}", e.getMessage());
        }
        long usato = Math.max(0, totale - libero);
        double percentuale = totale > 0 ? 100.0 * usato / totale : 0.0;
        return new ServerStatusDTO.Disk(totale, usato, libero, arrotonda(percentuale));
    }

    // --- Rete ---

    private ServerStatusDTO.Network leggiRete() {
        long rx = 0;
        long tx = 0;
        try {
            long[] rete = parseNetDev(Files.readString(PROC_NET_DEV));
            rx = rete[0];
            tx = rete[1];
        } catch (Exception e) {
            log.debug("Lettura rete da /proc/net/dev non riuscita: {}", e.getMessage());
        }
        return new ServerStatusDTO.Network(rx, tx);
    }

    /**
     * Parsing di /proc/net/dev: restituisce [rxBytes, txBytes] sommati su tutte
     * le interfacce tranne il loopback.
     */
    static long[] parseNetDev(String contenuto) {
        long rx = 0;
        long tx = 0;
        String[] righe = contenuto.split("\\R");
        for (int i = 2; i < righe.length; i++) { // le prime due righe sono intestazioni
            String riga = righe[i];
            int separatore = riga.indexOf(':');
            if (separatore < 0) {
                continue;
            }
            String interfaccia = riga.substring(0, separatore).trim();
            if ("lo".equals(interfaccia)) {
                continue;
            }
            String[] campi = riga.substring(separatore + 1).trim().split("\\s+");
            if (campi.length < 9) {
                continue;
            }
            rx += Long.parseLong(campi[0]);
            tx += Long.parseLong(campi[8]);
        }
        return new long[]{rx, tx};
    }

    // --- Uptime ---

    private long leggiUptimeSecondi() {
        try {
            return (long) parseUptime(Files.readString(PROC_UPTIME));
        } catch (Exception e) {
            log.debug("Lettura uptime da /proc/uptime non riuscita, uso MXBean: {}", e.getMessage());
            try {
                RuntimeMXBean runtimeBean = ManagementFactory.getRuntimeMXBean();
                return runtimeBean.getUptime() / 1000;
            } catch (Exception e2) {
                log.debug("Lettura uptime da MXBean non riuscita: {}", e2.getMessage());
                return 0;
            }
        }
    }

    /**
     * Parsing di /proc/uptime: restituisce i secondi di uptime (primo campo).
     */
    static double parseUptime(String contenuto) {
        return Double.parseDouble(contenuto.trim().split("\\s+")[0]);
    }

    // --- Hostname ---

    private String leggiHostname() {
        // Se impostata, la variabile d'ambiente SERVER_NAME ha la precedenza
        // (es. in produzione: nome leggibile al posto dell'ID del container Docker)
        String serverName = System.getenv("SERVER_NAME");
        if (serverName != null && !serverName.isBlank()) {
            return serverName.trim();
        }
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            log.debug("Lettura hostname non riuscita: {}", e.getMessage());
            return "sconosciuto";
        }
    }

    // --- Traffico HTTP ---

    private ServerStatusDTO.Http leggiHttp() {
        HttpTrafficFilter.Snapshot s = httpTrafficFilter.snapshot();
        return new ServerStatusDTO.Http(
                s.requestsTotal(), s.bytesInTotal(), s.bytesOutTotal(),
                s.requests2xx(), s.requests4xx(), s.requests5xx(), s.totalTimeMs());
    }

    private static double arrotonda(double valore) {
        return Math.round(valore * 100.0) / 100.0;
    }

    /**
     * Campione dei contatori CPU di /proc/stat: idle comprende iowait.
     */
    record CampioneCpu(long idle, long totale) {
    }
}
