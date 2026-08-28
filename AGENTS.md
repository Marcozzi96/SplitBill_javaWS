# SplitBill Backend — Guida per Agent AI

Questo documento descrive l'architettura, le convenzioni e i comandi utili per lavorare sul backend Java/Spring di **SplitBill**, un'applicazione REST per dividere le spese tra amici e all'interno di gruppi.

---

## Panoramica del progetto

- **Nome artefatto**: `rest-api-server` (`com.example:rest-api-server:0.0.1-SNAPSHOT`)
- **Applicazione**: backend REST per la gestione di utenti, amicizie, gruppi di spesa, spese, transazioni e bilanci.
- **Package radice**: `it.javaWS`
- **Classe principale**: `it.javaWS.JavawsApplication`

Funzionalità principali:

- Autenticazione con JWT (registrazione con conferma email, login).
- Login con Google (`POST /auth/google`): verifica dell'ID token con `GoogleIdTokenVerifier` (audience = `GOOGLE_CLIENT_ID`). Se l'email è già registrata (anche con password) viene fatto l'account linking sull'email verificata da Google; altrimenti viene creato un utente **senza password** (`users.password` null), che può accedere solo via Google finché non imposta una password dal profilo (in `updateUser` il controllo `oldPassword` è saltato per questi utenti). `UserDTO.hasPassword` indica se l'utente ha una password.
- Email univoche: l'email viene salvata come la digita l'utente (`users.email`); unicità e lookup usano `users.email_canonical` (vincolo `UNIQUE`, lowercase; per Gmail/Googlemail senza punti nel local part, perché Google li ignora — vedi `UserService.normalizeEmail`). Anche `users.username` ha vincolo `UNIQUE`.
- Gestione utenti (profilo, modifica, soft delete con anonimizzazione: `DELETE /user/delete` delega a `UserService.anonymizeUser`, che sostituisce email/username con placeholder univoci (`utente.<id>@eliminato.invalid`, `utente_eliminato_<id>`), azzera la password e imposta `deleted=true`; nei DTO (`UserDTO`, `GroupMemberDTO`) gli eliminati compaiono come `"UtenteEliminato"` con email null e flag `deleted=true`).
- Gestione amicizie (richieste, accettazione, rifiuto, annullamento, lista amici).
- Gestione gruppi di spesa (creazione, aggiunta membri, uscita soft: all'uscita i debiti/crediti dell'uscente si estinguono nel gruppo e vengono trasferiti a livello globale, cioè settlement con `group_id` null).
- I controlli di membership (`UserGroupRepository.existsByGroupIdAndUserId*`) considerano solo i membri attivi (`dataUscita` null): chi è uscito non può più operare sul gruppo.
- Gestione spese con suddivisione personalizzata dei debiti.
  - `POST /bills/new`: `groupId` opzionale — senza gruppo la spesa è **personale** (tra amici); i debitori devono esistere ed essere amici del buyer. Update/delete: qualsiasi membro attivo del gruppo; per le spese personali chiunque sia coinvolto (buyer o debitore).
  - `buyerId` opzionale in create/update ("Pagato da"): default l'utente autenticato in creazione, il buyer attuale in modifica. Nel gruppo il buyer deve essere un membro attivo.
  - Bilanci e settlement pairwise supportano `group_id` null (spese personali e uscite da gruppo).
  - Gli utenti eliminati (`deleted=true`) non possono partecipare a **nuove** spese, né come buyer né come debitori (`POST /bills/new`); in modifica (`PUT /bills/{id}`) restano ammessi solo se già coinvolti nella spesa esistente — un eliminato mai presente prima, o scelto come nuovo buyer, viene rifiutato con 400.
- Rimborsi tra utenti: `POST /payments` (l'importo non può superare il debito effettivo) e `GET /payments` (cronologia paginata).
- "Dimentica il debito": `POST /payments/forgive?payerId=<id>[&groupId=<id>]` registra un rimborso fittizio pari al debito residuo di un **utente eliminato** verso il creditore autenticato, azzerandolo (note: "Debito dimenticato (utente eliminato)"). Con `groupId` il creditore deve essere un membro attivo del gruppo; non è richiesta la membership del payer eliminato.
- Calcolo del saldo netto di un utente.
- Documentazione API tramite Swagger UI.

---

## Stack tecnologico

| Tecnologia | Versione / Uso |
|------------|----------------|
| Java | 21 (LTS) |
| Spring Boot | 3.5.16 |
| Maven | 3.9+ (wrapper incluso: `./mvnw` / `mvnw.cmd`) |
| Database dev | H2 in-memory |
| Database prod | PostgreSQL |
| Sicurezza | Spring Security + JWT (jjwt 0.13.0) |
| Persistenza | Spring Data JPA / Hibernate |
| Mail | Spring Boot Starter Mail |
| API Docs | SpringDoc OpenAPI 2.8.17 (Swagger UI) |
| Build/Deploy | Docker multi-stage, Railway |

Altre librerie rilevanti:

- **Lombok** (opzionale) per ridurre il boilerplate.
- **Spring Boot DevTools** (opzionale) per hot reload in dev.
- **JaCoCo** 0.8.12 per la code coverage (`mvn verify` genera il report).

---

## Struttura del codice

```text
src/main/java/it/javaWS/
├── JavawsApplication.java           # Entry point Spring Boot
├── config/                          # Configurazioni
│   ├── OpenApiConfig.java           # Swagger/OpenAPI
│   └── security/
│       ├── SecurityConfig.java      # Spring Security + filtro JWT
│       ├── JwtFilter.java           # Estrazione/validazione token
│       ├── PasswordEncoderConfig.java
│       ├── GlobalCorsConfig.java    # CORS
│       └── RequestWrapper.java
├── controllers/                     # Endpoint REST
│   ├── AuthController.java
│   ├── UserController.java
│   ├── GroupController.java
│   ├── BillController.java
│   ├── BalanceController.java
│   ├── PaymentController.java      # Rimborsi e "dimentica il debito"
│   ├── StatusController.java
│   ├── SystemStatusController.java # GET /api/status: metriche di sistema (richiede JWT)
│   └── advice/
│       └── GlobalExceptionHandler.java
├── services/                        # Logica di business
│   ├── UserService.java
│   ├── FriendshipService.java
│   ├── GroupService.java
│   ├── BillService.java
│   ├── BalanceService.java
│   ├── PaymentService.java         # Rimborsi, forgiveDebt (dimentica il debito)
│   ├── GoogleAuthService.java       # Verifica ID token Google, creazione utente senza password
│   ├── AuthTokenService.java        # Token opachi registrazione/reset password
│   └── SystemMetricsService.java    # Metriche host da procfs Linux (fallback MXBean)
├── repositories/                    # Spring Data JPA
│   ├── UserRepository.java
│   ├── FriendshipRepository.java
│   ├── GroupRepository.java
│   ├── UserGroupRepository.java
│   ├── BillRepository.java
│   ├── PaymentRepository.java
│   ├── AuthTokenRepository.java
│   └── TransactionRepository.java
├── models/
│   ├── entities/                    # Entità JPA
│   │   ├── User.java
│   │   ├── Group.java
│   │   ├── UserGroup.java
│   │   ├── UserGroupId.java
│   │   ├── Friendship.java
│   │   ├── Bill.java
│   │   ├── Payment.java
│   │   ├── AuthToken.java
│   │   └── Transaction.java
│   ├── dto/                         # Data Transfer Objects
│   │   ├── UserDTO.java
│   │   ├── GroupDTO.java
│   │   ├── GroupMemberDTO.java
│   │   ├── BillDTO.java
│   │   ├── PaymentDTO.java
│   │   ├── TransactionDTO.java
│   │   ├── UserBalanceDTO.java
│   │   ├── SettlementDTO.java
│   │   ├── FriendshipReqRecDTO.java
│   │   ├── FriendshipReqSenDTO.java
│   │   ├── AuthRequest.java
│   │   ├── AuthResponse.java
│   │   ├── GoogleLoginRequest.java
│   │   ├── ForgotPasswordRequest.java
│   │   ├── ResetPasswordRequest.java
│   │   ├── ServerStatusDTO.java
│   │   └── UpdateUserRequest.java
│   └── enums/
│       └── GroupRole.java
├── enums/                           # Enum di dominio (es. StatoAmicizia, AuthTokenType)
├── utils/                           # Utility e eccezioni custom
│   ├── JwtUtil.java
│   ├── EmailUtil.java
│   └── *Exception.java
└── filters/                         # Filtri servlet
    ├── SuspiciousRequestFilter.java
    ├── AuthRateLimitFilter.java     # Rate limiting in-memory su /auth/**
    └── HttpTrafficFilter.java       # Contatori traffico HTTP per /api/status (escluso il path stesso)

src/main/resources/
├── application.yml                  # Configurazione comune
├── application-dev.yml              # Profilo sviluppo (H2)
└── application-prod.yml             # Profilo produzione (PostgreSQL)

src/test/java/it/javaWS/
├── JavawsApplicationTests.java
├── services/                        # Test di unità con Mockito
│   ├── BillServiceTest.java
│   ├── PaymentServiceTest.java
│   ├── GroupServiceTest.java
│   ├── GroupServiceLazyInitTest.java # Regressione: proxy lazy usati fuori dalla sessione (non @Transactional)
│   ├── AuthTokenServiceTest.java
│   ├── SystemMetricsServiceTest.java # Parsing procfs (/proc/stat, meminfo, net/dev, uptime)
│   └── UserServiceTest.java
├── controllers/                     # Test di integrazione REST
│   ├── AuthControllerTest.java
│   ├── UserControllerTest.java
│   ├── BillControllerTest.java
│   ├── PaymentControllerTest.java
│   ├── GroupControllerTest.java
│   ├── SystemStatusControllerTest.java
│   └── BalanceControllerTest.java
├── controllers/advice/
│   └── GlobalExceptionHandlerTest.java
└── models/entities/
    └── UserGroupIdTest.java
```

---

## Comandi di build e avvio

Tutti i comandi si eseguono dalla root del progetto.

### Build

```bash
# Compilazione e pacchettizzazione
./mvnw clean package

# Compilazione e pacchettizzazione saltando i test
./mvnw clean package -DskipTests

# Verifica completa con test e report JaCoCo
./mvnw clean verify
```

Su Windows usare `mvnw.cmd` oppure Maven installato a sistema:

```bash
mvn clean verify
```

### Avvio in locale (profilo `dev`)

```bash
# Avvio con Maven wrapper
./mvnw spring-boot:run

# Oppure con Maven a sistema
mvn spring-boot:run
```

Il profilo `dev` è attivo di default e usa:

- H2 in-memory all'URL `jdbc:h2:mem:splitbill`.
- Console H2 raggiungibile su `/h2-console`.
- Mail disabilitata (host `localhost:25` senza auth).

### Verifica avvio

```bash
curl http://localhost:8080/status/isOn
```

### Documentazione API

Con l'applicazione avviata:

- Swagger UI: `http://localhost:8080/swagger-ui/index.html`
- OpenAPI docs: `http://localhost:8080/v3/api-docs`

---

## Profili e variabili d'ambiente

Il profilo attivo è determinato da `SPRING_PROFILES_ACTIVE` (default `dev`).

### Profilo `dev`

| Variabile | Descrizione | Default |
|-----------|-------------|---------|
| `JWT_SECRET` | Chiave segreta Base64 (≥512 bit) per la firma JWT. Se assente, `JwtUtil` genera una chiave effimera ad ogni avvio (solo dev) | generata automaticamente |
| `JWT_VALIDITY` | Durata token in secondi | `86400` (24h) |
| `OPEN_LINK` | URL base frontend per link di conferma/reset | `http://localhost:8080` |
| `GOOGLE_CLIENT_ID` | OAuth Client ID Google (login con Google); se vuoto il login con Google è disabilitato | vuoto (disabilitato) |
| `RATE_LIMIT_LIMIT` | Max richieste per finestra su `/auth/**` | `10` |
| `RATE_LIMIT_WINDOW_SECONDS` | Durata finestra rate limiting in secondi | `60` |

### Profilo `prod`

| Variabile | Descrizione |
|-----------|-------------|
| `SPRING_PROFILES_ACTIVE` | Deve valere `prod` |
| `SPRING_DATASOURCE_URL` | URL JDBC PostgreSQL |
| `SPRING_DATASOURCE_USERNAME` | Username PostgreSQL |
| `SPRING_DATASOURCE_PASSWORD` | Password PostgreSQL |
| `JWT_SECRET` | Chiave segreta JWT in Base64, ≥512 bit (es. `openssl rand -base64 64`) — obbligatoria |
| `JWT_VALIDITY` | Durata token in secondi |
| `MAIL_HOST` | Host SMTP |
| `MAIL_PORT` | Porta SMTP |
| `MAIL_USERNAME` | Indirizzo email mittente |
| `MAIL_PASSWORD` | Password o app-specific password |
| `CORS_ALLOWED_ORIGINS` | Origini CORS aggiuntive separate da virgola (es. `https://app.example.com`); lette da `GlobalCorsConfig` via `app.cors.allowed-origins` |
| `OPEN_LINK` | URL base frontend |
| `GOOGLE_CLIENT_ID` | OAuth Client ID "Web application" da Google Cloud Console (stesso valore passato al frontend come build arg `VITE_GOOGLE_CLIENT_ID`) |
| `PORT` | Porta del server (default `8080`) |

---

## Convenzioni di codice

- **Lingua**: il codice e i commenti sono in italiano; i nomi di classi/metodi/variabili seguono la convenzione Java.
- **Layer architecture**:
  - `controllers` → gestione HTTP, ricezione request, restituzione DTO.
  - `services` → logica di business e transazioni.
  - `repositories` → accesso ai dati con Spring Data JPA.
- **Dependency injection**: preferire l'injection per costruttore; evitare `@Autowired` sui campi.
- **DTO**: esporre sempre DTO nelle API REST; non restituire direttamente entità JPA.
- **Entità JPA**:
  - Non usare `@Data` di Lombok.
  - Usare `@Getter`, `@Setter`, `@NoArgsConstructor`.
  - `equals`/`hashCode` solo sull'id (`@EqualsAndHashCode(onlyExplicitlyIncluded = true)`).
  - Relazioni lazy di default (`FetchType.LAZY`).
- **Transactional**: annotare i metodi di sola lettura con `@Transactional(readOnly = true)`.
  - Attenzione ai **proxy lazy che escono dalla transazione**: se il chiamante usa l'entità fuori dalla sessione (es. in collezioni hash-based o per costruire DTO nel controller), inizializzare le associazioni lazy dentro il metodo (`Hibernate.initialize(...)`), altrimenti `LazyInitializationException`. Regression test: `GroupServiceLazyInitTest`.
- **Gestione errori**: centralizzata in `GlobalExceptionHandler` con `@RestControllerAdvice`.
- **Logging**: usare SLF4J (`LoggerFactory`); evitare `System.out.println`.
- **Sicurezza**: non committare mai chiavi JWT, password o URL di database nelle configurazioni.

---

## Istruzioni per i test

- Eseguire `mvn clean verify` prima di committare.
- I test si dividono in:
  - **Unit test** per i service (`src/test/java/it/javaWS/services/`) con Mockito.
  - **Test di integrazione** per i controller (`src/test/java/it/javaWS/controllers/`) con `@SpringBootTest(webEnvironment = RANDOM_PORT)` e `TestRestTemplate`.
- Il report JaCoCo viene generato in `target/site/jacoco/index.html` dopo `mvn verify`.

---

## Sicurezza

- Autenticazione stateless basata su JWT.
- CSRF disabilitato perché l'autenticazione non usa cookie/sessioni.
- Endpoint pubblici: `/auth/**`, `/status/**`, `/swagger-ui/**`, `/v3/api-docs/**`, `/swagger-ui.html`, `/h2-console/**`.
- Tutti gli altri endpoint richiedono l'header `Authorization: Bearer <token>`. Tra questi `GET /api/status` (metriche di sistema): il path non ricade nel pattern pubblico `/status/**`, quindi è protetto dalla regola di default.
- CORS configurato con origini esplicite (`http://localhost:3000` più quelle da `CORS_ALLOWED_ORIGINS`, es. `https://splitbill.it`) più `allowedOriginPatterns` per il dev in LAN (`localhost:*` e range privati `192.168.*`, `10.*`, `172.*` con qualsiasi porta).
- Presente un filtro `AuthRateLimitFilter` che applica rate limiting in-memory (finestra fissa per IP+endpoint) su `POST /auth/login|register|forgotPassword|resetPassword|google`; oltre soglia risponde `429`.
- La chiave JWT viene decodificata da Base64 in `JwtUtil` (`Decoders.BASE64`); se `jwt.secret` è vuota (solo dev), viene generata una chiave HS512 effimera con warning.
- Presente un filtro `SuspiciousRequestFilter` che blocca pattern di richieste sospette (es. `${jndi:...`).

> **Nota di sicurezza (risolta nello Sprint 5)**: i token di conferma registrazione e di reset password sono ora **opachi** (UUID casuali salvati su DB nella tabella `auth_tokens`), con scadenza (24h registrazione, 15 minuti reset) e uso singolo. La password non transita più nei claim JWT: per le registrazioni in attesa viene salvata solo in forma encodata (BCrypt) nel record del token.

---

## Deploy

Il deploy di produzione avviene su VPS con **Docker Compose** (guida completa in `DEPLOY.md`):

- `docker-compose.yml` — orchestra `db` (PostgreSQL 16), `backend`, `frontend` (buildato dal repo `../SplitBill` clonato affiancato) e `caddy` (HTTPS automatico con Let's Encrypt).
- `Caddyfile` — reverse proxy: `APP_DOMAIN` → frontend, `API_DOMAIN` → backend (domini da `.env`).
- `.env.example` — template delle variabili d'ambiente del server.
- `.github/workflows/deploy.yml` — deploy automatico del backend a ogni push su `main` (SSH verso il VPS; secret `VPS_HOST`, `VPS_USER`, `VPS_SSH_KEY`).

Il `Dockerfile` multi-stage (build con Maven, runtime `eclipse-temurin:21-jre`) resta usabile da solo:

```bash
docker build -t splitbill-backend .
docker run -p 8080:8080 --env-file .env splitbill-backend
```

L'applicazione resta compatibile con il deploy su [Railway](https://railway.app/), leggendo le variabili d'ambiente dal provider.

---

## Link utili

- Swagger UI online: `https://javaws.up.railway.app/swagger-ui/index.html`
- DevTools condivisi: vedi `README.md`

---

## File di riferimento

- `README.md` — documentazione utente completa.
- `agent.md` — regole di sviluppo e convenzioni.
- `pom.xml` — dipendenze e configurazione Maven.
- `Dockerfile` — immagine Docker multi-stage.
- `DEPLOY.md` — guida deploy VPS con Docker Compose e CI/CD GitHub Actions.
