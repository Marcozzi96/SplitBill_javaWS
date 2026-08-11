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
- Gestione utenti (profilo, modifica, soft delete).
- Gestione amicizie (richieste, accettazione, rifiuto, annullamento, lista amici).
- Gestione gruppi di spesa (creazione, aggiunta membri, uscita).
- Gestione spese con suddivisione personalizzata dei debiti.
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
│   ├── TransactionController.java
│   ├── StatusController.java
│   └── advice/
│       └── GlobalExceptionHandler.java
├── services/                        # Logica di business
│   ├── UserService.java
│   ├── FriendshipService.java
│   ├── GroupService.java
│   ├── BillService.java
│   ├── TransactionService.java
│   └── BalanceService.java
├── repositories/                    # Spring Data JPA
│   ├── UserRepository.java
│   ├── FriendshipRepository.java
│   ├── GroupRepository.java
│   ├── UserGroupRepository.java
│   ├── BillRepository.java
│   └── TransactionRepository.java
├── models/
│   ├── entities/                    # Entità JPA
│   │   ├── User.java
│   │   ├── Group.java
│   │   ├── UserGroup.java
│   │   ├── UserGroupId.java
│   │   ├── Friendship.java
│   │   ├── Bill.java
│   │   └── Transaction.java
│   ├── dto/                         # Data Transfer Objects
│   │   ├── UserDTO.java
│   │   ├── GroupDTO.java
│   │   ├── GroupMemberDTO.java
│   │   ├── BillDTO.java
│   │   ├── TransactionDTO.java
│   │   ├── UserBalanceDTO.java
│   │   ├── SettlementDTO.java
│   │   ├── FriendshipReqRecDTO.java
│   │   ├── FriendshipReqSenDTO.java
│   │   ├── AuthRequest.java
│   │   ├── AuthResponse.java
│   │   └── UpdateUserRequest.java
│   └── enums/
│       └── GroupRole.java
├── utils/                           # Utility e eccezioni custom
│   ├── JwtUtil.java
│   ├── EmailUtil.java
│   └── *Exception.java
└── filters/                         # Filtri servlet
    └── SuspiciousRequestFilter.java

src/main/resources/
├── application.yml                  # Configurazione comune
├── application-dev.yml              # Profilo sviluppo (H2)
└── application-prod.yml             # Profilo produzione (PostgreSQL)

src/test/java/it/javaWS/
├── JavawsApplicationTests.java
├── services/                        # Test di unità con Mockito
│   ├── BillServiceTest.java
│   ├── GroupServiceTest.java
│   └── UserServiceTest.java
├── controllers/                     # Test di integrazione REST
│   ├── AuthControllerTest.java
│   ├── UserControllerTest.java
│   ├── BillControllerTest.java
│   ├── GroupControllerTest.java
│   ├── BalanceControllerTest.java
│   └── TransactionControllerTest.java
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
| `JWT_SECRET` | Chiave segreta per la firma JWT | Valore di default in `application-dev.yml` |
| `JWT_VALIDITY` | Durata token in secondi | `86400` (24h) |
| `OPEN_LINK` | URL base frontend per link di conferma | `http://localhost:8080` |

### Profilo `prod`

| Variabile | Descrizione |
|-----------|-------------|
| `SPRING_PROFILES_ACTIVE` | Deve valere `prod` |
| `SPRING_DATASOURCE_URL` | URL JDBC PostgreSQL |
| `SPRING_DATASOURCE_USERNAME` | Username PostgreSQL |
| `SPRING_DATASOURCE_PASSWORD` | Password PostgreSQL |
| `JWT_SECRET` | Chiave segreta JWT (deve essere robusta) |
| `JWT_VALIDITY` | Durata token in secondi |
| `MAIL_HOST` | Host SMTP |
| `MAIL_PORT` | Porta SMTP |
| `MAIL_USERNAME` | Indirizzo email mittente |
| `MAIL_PASSWORD` | Password o app-specific password |
| `OPEN_LINK` | URL base frontend |
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
- Tutti gli altri endpoint richiedono l'header `Authorization: Bearer <token>`.
- CORS configurato con origini esplicite (`http://localhost:3000`, `https://fe-splitbill.vercel.app`).
- Presente un filtro `SuspiciousRequestFilter` che blocca pattern di richieste sospette (es. `${jndi:...`).

> **Nota di sicurezza**: durante la registrazione, il token di conferma email generato da `JwtUtil.generateEmailToken` include la password in chiaro nei claim JWT (`password`, `email`, `sub`). Questo è un rischio da rivedere in produzione: i token JWT possono essere decodificati da chiunque li intercetti e non devono contenere segreti. Si consiglia di usare un token opaco salvato lato server o, in alternativa, di non includere la password nei claim.

---

## Deploy

È incluso un `Dockerfile` multi-stage:

```dockerfile
# Stage build
FROM maven:3.9.6-eclipse-temurin-21 AS build
# Stage runtime
FROM eclipse-temurin:21-jdk
ENV SPRING_PROFILES_ACTIVE=prod
EXPOSE 8080
```

Comandi Docker:

```bash
docker build -t splitbill-backend .
docker run -p 8080:8080 --env-file .env splitbill-backend
```

L'applicazione è configurata per essere deployata su [Railway](https://railway.app/), leggendo le variabili d'ambiente dal provider.

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
