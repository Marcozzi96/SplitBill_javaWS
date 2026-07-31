# Regole di sviluppo — SplitBill Backend

Questo documento raccoglie le convenzioni e le regole da rispettare quando si lavora sul backend Java/Spring di SplitBill.

## Stack e versioni

- **Java**: 21 (LTS).
- **Spring Boot**: ultima versione 3.x supportata disponibile su Maven Central.
- **Build tool**: Maven.
- **Database**: PostgreSQL in produzione, H2 in sviluppo locale.

## Dipendenze

- Controllare periodicamente la presenza di dipendenze obsolete o vulnerabili:
  - `mvn versions:display-dependency-updates`
  - `mvn dependency-check:check`
- Mantenere le versioni esplicite solo quando necessario; per il resto affidarsi al parent Spring Boot.
- Non includere librerie non utilizzate nel classpath.

## Architettura

- Mantenere la separazione a layer:
  - `controllers` → gestione delle richieste HTTP e risposte DTO.
  - `services` → logica di business e transazioni.
  - `repositories` → accesso ai dati con Spring Data JPA.
- Non scrivere logica di business nei controller.
- Esporre sempre DTO nelle API REST; non restituire direttamente le entità JPA.

## Dependency injection

- Preferire sempre l'injection per costruttore.
- Evitare `@Autowired` sui campi.
- Evitare cicli di dipendenze tra service.

## Configurazione

- Usare file YAML profilati (`application-dev.yml`, `application-prod.yml`) per ambienti diversi.
- Esternalizzare secrets e parametri sensibili tramite variabili d'ambiente.
- Non committare credenziali, chiavi JWT o URL di database con password.

## Sicurezza

- Usare Spring Security con JWT stateless.
- Codificare la chiave JWT in Base64 e caricarla da variabile d'ambiente.
- Non inserire password o dati sensibili nei claim del JWT.
- Disabilitare CSRF solo in contesti stateless JWT e con token non memorizzati in cookie.
- Abilitare CORS con origini esplicite; non usare `*` quando `allowCredentials` è `true`.

## Controller e gestione errori

- Usare `@AuthenticationPrincipal` per ottenere l'utente autenticato.
- Non ripetere `SecurityContextHolder.getContext().getAuthentication()` in ogni endpoint.
- Centralizzare la gestione degli errori con `@ControllerAdvice`.
- Restituire risposte coerenti e tipizzate; evitare `ResponseEntity<?>` con `Map.of("error", ...)`.

## Servizi

- Annotare i metodi di sola lettura con `@Transactional(readOnly = true)`.
- Non esporre entità JPA direttamente ai controller; mappare su DTO.
- Gestire esplicitamente i casi di entità non trovata con eccezioni dedicate.

## Entità JPA

- Non usare `@Data` di Lombok sulle entità.
- Usare `@Getter`, `@Setter`, `@NoArgsConstructor` (eventualmente `@AllArgsConstructor`).
- Definire `equals` e `hashCode` solo sull'identificativo (id), mai su relazioni lazy.
- Usare `FetchType.LAZY` per le associazioni, salvo necessità diverse documentate.

## Mail

- Usare `spring-boot-starter-mail` e `JavaMailSender`.
- Configurare host, port e credenziali tramite proprietà `spring.mail.*`.
- In ambiente di sviluppo usare uno stub o un server SMTP locale quando possibile.

## Logging

- Usare SLF4J/Logback tramite `LoggerFactory`.
- Non usare `System.out.println` in produzione.
- Non loggare dati sensibili (password, token, chiavi).

## Test

- Aggiungere test di unità per i service e test di integrazione per i controller/repository critici.
- Eseguire `mvn clean verify` prima di committare.
- Mantenere i test aggiornati in caso di refactor.

## API documentation

- Documentare gli endpoint con le annotazioni OpenAPI di springdoc.
- Mantenere Swagger UI raggiungibile e aggiornato.

## Database

- In sviluppo usare H2 con `ddl-auto: create-drop` o `update`.
- In produzione preferire `validate` o `update` a seconda della strategia di migration scelta.
- Non eseguire `ddl-auto: create-drop` in produzione.
