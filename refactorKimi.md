# Refactor SplitBill Backend — Report

**Completato il:** 2026-07-31T06:44:13Z

## Cosa è cambiato

### Dipendenze (`pom.xml`)

- Spring Boot aggiornato a `3.5.16`
- JJWT aggiornato a `0.13.0`
- `spring-boot-starter-mail` al posto di `jakarta.mail` manuale
- Springdoc OpenAPI aggiornato a `2.8.3`
- H2 ripristinato per il profilo dev

### Regole di sviluppo

- Creato `agent.md` in root con le regole di sviluppo (stack, layer architecture, dependency injection, gestione errori, sicurezza, test, ecc.)

### Configurazione profilata YAML

- `application.yml` — configurazione comune
- `application-dev.yml` — H2 in-memory, console H2, mail stub
- `application-prod.yml` — PostgreSQL e SMTP reali
- Rimosso `DataSourceConfig.java`

### Refactor Spring

- Controller passati a `@AuthenticationPrincipal User` invece di `SecurityContextHolder`
- Rimossi `@PreAuthorize("isAuthenticated()")` ridondanti
- Aggiunto `@ControllerAdvice` per la gestione errori centralizzata
- Tutti i service annotati con `@Transactional` / `@Transactional(readOnly = true)`
- Injection per costruttore ovunque, rimossi `@Autowired` sui campi
- Entità JPA senza `@Data`, con `equals/hashCode` solo sull'id
- `EmailUtil` riscritto con `JavaMailSender`
- `JwtUtil` aggiornato alle API JJWT 0.13
- Rimosse classi inutilizzate (`CustomUserDetails`, `UserDetailsUtil`)
- Corretto bug in `BalanceService.getDetailedBalance` (`findByBuyer_Id` invece di `findById`)

### API minori

- `POST /bills/new` non richiede più `buyerId`: il pagante è forzato all'utente autenticato
- `README.md` e `Dockerfile` aggiornati per riflettere profili e nuove variabili d'ambiente

## Verifica

- `mvn clean verify` passa
- Avvio locale in dev funzionante
- `/status/isOn` risponde `ok`
- Swagger UI raggiungibile (`/swagger-ui/index.html`, HTTP 200)

## Nota per sviluppi futuri

La password di registrazione è ancora inclusa nel token email di conferma. È tecnicamente protetta dalla firma JWT, ma se si vuole eliminarla del tutto si può sostituire con un token opaco salvato in database/cache in un passaggio successivo.
