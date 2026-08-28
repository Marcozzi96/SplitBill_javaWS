package it.javaWS.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

import it.javaWS.services.SystemMetricsService.CampioneCpu;

class SystemMetricsServiceTest {

    @Test
    void parseRigaCpu_conRigaReale_calcolaIdleETotale() {
        // user=100 nice=10 system=50 idle=800 iowait=20 irq=5 softirq=5 steal=10 guest=7 guest_nice=3
        CampioneCpu campione = SystemMetricsService.parseRigaCpu(
                "cpu  100 10 50 800 20 5 5 10 7 3");

        // idle = idle + iowait = 800 + 20
        assertThat(campione.idle()).isEqualTo(820);
        // totale = somma dei primi 8 campi (guest esclusi: già conteggiati in user/nice)
        assertThat(campione.totale()).isEqualTo(100 + 10 + 50 + 800 + 20 + 5 + 5 + 10);
    }

    @Test
    void parseRigaCpu_conRigaNonValida_lanciaEccezione() {
        assertThatThrownBy(() -> SystemMetricsService.parseRigaCpu("cpu0 1 2 3"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void calcolaUsoCpu_suDeltaTraCampioni_restituiscePercentuale() {
        // Primo campione: idle 820 su 1000; secondo: +100 totale, +50 idle → 50% uso
        CampioneCpu precedente = new CampioneCpu(820, 1000);
        CampioneCpu attuale = new CampioneCpu(870, 1100);

        assertThat(SystemMetricsService.calcolaUsoCpu(precedente, attuale))
                .isCloseTo(50.0, within(0.001));
    }

    @Test
    void calcolaUsoCpu_daZeroRestituisceMediaDalBoot() {
        // idle 800 su 1000 → 20% di uso medio dal boot
        assertThat(SystemMetricsService.calcolaUsoCpu(new CampioneCpu(0, 0), new CampioneCpu(800, 1000)))
                .isCloseTo(20.0, within(0.001));
    }

    @Test
    void calcolaUsoCpu_conDeltaTotaleNullo_restituisceZero() {
        CampioneCpu campione = new CampioneCpu(800, 1000);
        assertThat(SystemMetricsService.calcolaUsoCpu(campione, campione)).isZero();
    }

    @Test
    void parseMemInfo_conContenutoReale_restituisceByte() {
        String meminfo = """
                MemTotal:       16384000 kB
                MemFree:         1234567 kB
                MemAvailable:    8192000 kB
                Buffers:          500000 kB
                """;

        long[] mem = SystemMetricsService.parseMemInfo(meminfo);

        assertThat(mem[0]).isEqualTo(16384000L * 1024);
        assertThat(mem[1]).isEqualTo(8192000L * 1024);
    }

    @Test
    void parseMemInfo_senzaMemAvailable_lanciaEccezione() {
        assertThatThrownBy(() -> SystemMetricsService.parseMemInfo("MemTotal:  16384000 kB\n"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parseNetDev_conContenutoReale_sommaInterfacceEsclusoLoopback() {
        String netdev = """
                Inter-|   Receive                                                |  Transmit
                 face |bytes    packets errs drop fifo frame compressed multicast|bytes    packets errs drop fifo colls carrier compressed
                    lo: 9999999   10000    0    0    0     0          0         0  9999999   10000    0    0    0     0       0          0
                  eth0: 1234567    5000    0    0    0     0          0         0  7654321    6000    0    0    0     0       0          0
                 wlan0:  100000    1000    0    0    0     0          0         0   200000    2000    0    0    0     0       0          0
                """;

        long[] rete = SystemMetricsService.parseNetDev(netdev);

        assertThat(rete[0]).isEqualTo(1234567L + 100000L);
        assertThat(rete[1]).isEqualTo(7654321L + 200000L);
    }

    @Test
    void parseUptime_conContenutoReale_restituisceSecondi() {
        assertThat(SystemMetricsService.parseUptime("123456.78 234567.89\n"))
                .isCloseTo(123456.78, within(0.001));
    }
}
