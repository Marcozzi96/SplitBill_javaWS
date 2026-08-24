# SplitBill Backend (javaWS)

Backend REST di **SplitBill**, un'applicazione per dividere le spese tra amici e all'interno di gruppi.

## Funzionalità principali

- **Autenticazione**: registrazione con conferma email, login e gestione token JWT.
- **Utenti**: profilo personale, modifica dati ed eliminazione account (soft delete).
- **Amicizie**: invio, accettazione, rifiuto, annullamento richieste e gestione lista amici.
- **Gruppi**: creazione gruppi di spesa, aggiunta di membri, uscita dal gruppo (soft: i debiti/crediti dell'uscente passano a livello globale).
- **Spese**: creazione di una spesa con suddivisione personalizzata dei debiti, consultazione per gruppo o per utente.
- **Saldi**: calcolo del bilancio netto di un utente (totale pagato - totale dovuto).
- **Documentazione API**: esplorabile tramite Swagger UI.

## Stack tecnologico

| Tecnologia | Versione / Descrizione |
|------------|------------------------|
| Java       | 21                     |
| Spring Boot| 3.5.16                 |
| Maven      | 3.9+                   |
| Database   | H2 (dev) / PostgreSQL (prod) |
| Sicurezza  | Spring Security + JWT  |
| Mail       | Spring Boot Starter Mail |
| API Docs   | SpringDoc OpenAPI 2.8.3|
| Deploy     | Docker + Railway       |

## Requisiti

- [JDK 21](https://adoptium.net/)
- [Maven](https://maven.apache.org/)

## Profili

Il progetto usa due profili Spring:

- **`dev`** (default in locale): database H2 in-memory, console H2 attiva su `/h2-console`, mail disabilitata (host localhost).
- **`prod`**: database PostgreSQL, mail configurata con server SMTP reale.

Il profilo attivo si imposta con la variabile `SPRING_PROFILES_ACTIVE`.

## Variabili d'ambiente

### Profilo `dev`

| Variabile             | Descrizione                                                            | Default / Esempio                                    |
|-----------------------|------------------------------------------------------------------------|------------------------------------------------------|
| `JWT_SECRET`          | Chiave segreta Base64 (minimo 512 bit) per la firma dei token JWT. Se assente, in dev viene generata una chiave effimera casuale ad ogni avvio | generata automaticamente (solo dev) |
| `JWT_VALIDITY`        | Durata di validità del token in secondi                                | `86400` (24 ore)                                     |
| `OPEN_LINK`           | URL base del frontend, usato nei link inviati via email                | `http://localhost:3000`                               |
| `RATE_LIMIT_LIMIT`    | Numero massimo di richieste per finestra sugli endpoint `/auth/**`     | `10`                                                 |
| `RATE_LIMIT_WINDOW_SECONDS` | Durata della finestra di rate limiting in secondi                | `60`                                                 |

### Profilo `prod`

| Variabile                     | Descrizione                                                            | Esempio                                              |
|-------------------------------|------------------------------------------------------------------------|------------------------------------------------------|
| `SPRING_PROFILES_ACTIVE`      | Deve essere impostato a `prod`                                         | `prod`                                               |
| `SPRING_DATASOURCE_URL`       | URL JDBC del database PostgreSQL                                       | `jdbc:postgresql://localhost:5432/splitbill`         |
| `SPRING_DATASOURCE_USERNAME`  | Username PostgreSQL                                                    | `splitbill`                                          |
| `SPRING_DATASOURCE_PASSWORD`  | Password PostgreSQL                                                    |                                                      |
| `JWT_SECRET`                  | Chiave segreta Base64 (minimo 512 bit) per la firma dei token JWT      | generata con `openssl rand -base64 64`               |
| `JWT_VALIDITY`                | Durata di validità del token in secondi                                | `86400` (24 ore)                                     |
| `MAIL_HOST`                   | Host SMTP                                                              | `smtp.gmail.com`                                     |
| `MAIL_PORT`                   | Porta SMTP                                                             | `587`                                                |
| `MAIL_USERNAME`               | Indirizzo email usato per inviare le mail di conferma                  | `tua-app@gmail.com`                                  |
| `MAIL_PASSWORD`               | Password o app-specific password dell'account email                    |                                                      |
| `OPEN_LINK`                   | URL base del frontend, usato nel link di conferma registrazione        | `https://splitbill.it`                    |

La porta del server è configurabile tramite `PORT` (default `8080`).

## Avvio in locale

1. **Clona il repository**
   ```bash
   git clone <url-repository>
   cd SplitBill_javaWS
   ```

2. **Configura le variabili d'ambiente (opzionale in dev)**

   In dev il backend parte con H2 e valori di default: se `JWT_SECRET` non è impostata viene generata una chiave effimera ad ogni avvio (i token si invalidano a ogni riavvio). Puoi comunque sovrascrivere `JWT_SECRET` (in Base64) e `OPEN_LINK`:

   ```bash
   export JWT_SECRET=$(openssl rand -base64 64)
   export OPEN_LINK=http://localhost:3000
   ```

3. **Compila ed avvia**
   ```bash
   ./mvnw spring-boot:run
   ```

   Oppure con Maven installato a sistema:
   ```bash
   mvn spring-boot:run
   ```

   Per avviare in produzione (es. con PostgreSQL):
   ```bash
   export SPRING_PROFILES_ACTIVE=prod
   export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/splitbill
   export SPRING_DATASOURCE_USERNAME=splitbill
   export SPRING_DATASOURCE_PASSWORD=password
   export JWT_SECRET=$(openssl rand -base64 64)
   export MAIL_USERNAME=tua-email@gmail.com
   export MAIL_PASSWORD=tua-password
   export OPEN_LINK=https://splitbill.it
   ./mvnw spring-boot:run
   ```

4. **Verifica che sia attivo**
   ```bash
   curl http://localhost:8080/status/isOn
   ```

5. **Esplora le API**
   Apri il browser su: `http://localhost:8080/swagger-ui/index.html`

## Struttura del progetto

```
src/main/java/it/javaWS/
├── config/               # Configurazioni (DB, OpenAPI, sicurezza, CORS)
├── controllers/          # Endpoint REST
├── models/
│   ├── dto/              # Data Transfer Objects
│   └── entities/         # Entità JPA
├── repositories/         # Spring Data JPA
├── services/             # Logica di business
├── utils/                # JwtUtil, EmailUtil
├── filters/              # Filtro richieste sospette
└── JavawsApplication.java
```

## Panoramica API

Tutti gli endpoint richiedono autenticazione JWT tramite header `Authorization: Bearer <token>`, tranne quelli indicati come pubblici.

### Autenticazione (pubblici)

| Metodo | Endpoint                | Descrizione                                                        |
|--------|-------------------------|--------------------------------------------------------------------|
| POST   | `/auth/login`           | Login con username/email e password                                |
| POST   | `/auth/register`        | Registrazione nuovo utente + invio email con token opaco           |
| GET    | `/auth/confirmEmail`    | Conferma registrazione tramite token email                         |
| POST   | `/auth/forgotPassword`  | Invia email con token opaco per il reset della password            |
| POST   | `/auth/resetPassword`   | Reimposta la password tramite il token ricevuto via email          |

Gli endpoint di autenticazione sono protetti da rate limiting (default: 10 richieste/minuto per IP e endpoint, oltre soglia `429 Too Many Requests`).

### Utenti

| Metodo | Endpoint                         | Descrizione                                  |
|--------|----------------------------------|----------------------------------------------|
| GET    | `/user/me`                       | Dati dell'utente autenticato                 |
| PUT    | `/user/update`                   | Aggiorna profilo e rilascia nuovo token      |
| DELETE | `/user/delete`                   | Elimina account (soft delete)                |
| GET    | `/user/getFriends`               | Lista amici                                  |
| GET    | `/user/sendFriendshipRequest`    | Invia richiesta di amicizia                  |
| GET    | `/user/acceptFriendship`         | Accetta una richiesta ricevuta               |
| GET    | `/user/refuseFriendship`         | Rifiuta/annulla una richiesta                |
| DELETE | `/user/cancelFriendship`         | Rimuove un'amicizia esistente                |
| GET    | `/user/getFriendshipReqReceived` | Richieste di amicizia ricevute               |
| GET    | `/user/getFriendshipReqSent`     | Richieste di amicizia inviate                |

### Gruppi

| Metodo | Endpoint                      | Descrizione                                  |
|--------|-------------------------------|----------------------------------------------|
| POST   | `/groups/create`              | Crea un nuovo gruppo                         |
| GET    | `/groups`                     | Lista gruppi dell'utente autenticato         |
| GET    | `/groups/{groupId}`           | Dettaglio di un gruppo                       |
| POST   | `/groups/addUsers/{groupId}`  | Aggiunge amici a un gruppo                   |
| DELETE | `/groups/leave/{groupId}`     | Esce da un gruppo; debiti/crediti dell'uscente si estinguono nel gruppo e diventano personali (livello globale) |

### Spese

| Metodo | Endpoint                         | Descrizione                                  |
|--------|----------------------------------|----------------------------------------------|
| POST   | `/bills/new`                     | Crea una nuova spesa                         |
| GET    | `/bills/group/{groupId}`         | Spese di un gruppo                           |
| GET    | `/bills/getWhereImBuyer`         | Spese in cui l'utente è il pagante           |
| GET    | `/bills/getMyBills`              | Spese in cui l'utente è coinvolto            |
| DELETE | `/bills/{id}`                    | Elimina una spesa                            |

### Transazioni

| Metodo | Endpoint              | Descrizione                                  |
|--------|-----------------------|----------------------------------------------|
| DELETE | `/transactions/{id}`  | Elimina una singola transazione              |

### Bilanci

| Metodo | Endpoint           | Descrizione                                  |
|--------|--------------------|----------------------------------------------|
| GET    | `/balance/{userId}`| Dettaglio saldo dell'utente                  |

### Stato (pubblico)

| Metodo | Endpoint        | Descrizione                                  |
|--------|-----------------|----------------------------------------------|
| GET    | `/status/isOn`  | Health check base                            |

## Sicurezza

- Gli endpoint pubblici sono `/auth/**`, `/status/**`, `/swagger-ui/**`, `/v3/api-docs/**` e `/swagger-ui.html`.
- CSRF è disabilitato perché l'autenticazione è stateless basata su JWT.
- Ogni richiesta autenticata deve includere l'header `Authorization: Bearer <token>`.
- La chiave JWT è decodificata da Base64: `JWT_SECRET` deve essere una stringa Base64 di almeno 512 bit (es. `openssl rand -base64 64`).
- I token di conferma registrazione e di reset password sono opachi, salvati su DB con scadenza (24h e 15 minuti) e utilizzabili una sola volta; non contengono dati sensibili.
- È presente un filtro (`SuspiciousRequestFilter`) che blocca pattern di richieste sospette.
- Gli endpoint `/auth/**` sono protetti da rate limiting in-memory (`AuthRateLimitFilter`).

## Deploy

È incluso un `Dockerfile` multi-stage per il build e l'esecuzione:

```bash
docker build -t splitbill-backend .
docker run -p 8080:8080 --env-file .env splitbill-backend
```

L'applicazione è configurata per essere deployata su [Railway](https://railway.app/), leggendo le variabili d'ambiente dal provider.

## Link utili

- **Swagger UI online**: [https://javaws.up.railway.app/swagger-ui/index.html](https://javaws.up.railway.app/swagger-ui/index.html)
- **DevTools condivisi**: [Google Drive](https://drive.google.com/drive/folders/1amJA8S-9JxPdoc04cDMawuHqNAAW4t20?usp=sharing)
