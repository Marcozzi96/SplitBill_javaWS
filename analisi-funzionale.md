# Analisi funzionale — SplitBill Backend (javaWS)

> Stato rilevato al: 2026-07-31
> Riferimento commit corrente del repository `SplitBill_javaWS`

---

## 1. Panoramica

Il progetto è il backend REST di **SplitBill**, un'applicazione per la divisione delle spese tra amici e all'interno di gruppi.  
L'obiettivo dichiarato è permettere a un utente di:

- registrarsi e autenticarsi con JWT;
- gestire un profilo personale;
- gestire relazioni di amicizia (richiesta / accettazione / rifiuto / rimozione);
- creare gruppi di spesa e aggiungervi amici;
- registrare spese con suddivisione personalizzata dei debiti;
- consultare spese e saldi.

Il codice è stato recentemente refactorato (vedi `refactorKimi.md`): architettura a layer, DTO esposti, gestione errori centralizzata, injection per costruttore, configurazione profilata YAML.

---

## 2. Stack tecnologico

| Componente | Versione / Tecnologia |
|------------|------------------------|
| Java | 21 LTS |
| Spring Boot | 3.5.16 |
| Maven | wrapper incluso |
| Database dev | H2 in-memory |
| Database prod | PostgreSQL |
| Sicurezza | Spring Security + JWT (JJWT 0.13.0) |
| Mail | Spring Boot Starter Mail |
| API Docs | SpringDoc OpenAPI 2.8.17 |
| Deploy | Docker + Railway |

Il profilo attivo è `dev` di default, selezionabile con `SPRING_PROFILES_ACTIVE`.

---

## 3. Modello dati

### 3.1 Entità principali

```text
User (users)
├── id, username, email, password, regDate
├── billsCredit     → Bill (come buyer)
├── transactions    → Transaction
├── richiesteInviate / richiesteRicevute → Friendship
└── userGroups      → UserGroup

Group (groups)
├── id, name, description, creationDate
├── userGroups      → UserGroup
├── bills           → Bill
└── transactions    → Transaction

UserGroup (user_group) — associazione molti-a-molti con storico
├── id (userId, groupId) embedded
├── user, group
├── dataIngresso, dataUscita

Bill
├── id, description, amount, date, notes
├── buyer → User (chi ha pagato)
├── group → Group
└── transactions → Transaction

Transaction
├── id, amount
├── user → User
├── bill → Bill
└── group → Group

Friendship
├── id
├── user1, user2 (con vincolo DB user1_id < user2_id)
├── userToBeConfirmed → User (chi deve accettare)
├── stato (IN_ATTESA, ACCETTATA, RIFIUTATA)
├── dataRichiesta, messaggio
```

### 3.2 Osservazioni sul modello

- **`User` non ha un flag `deleted`**: lo "soft delete" descritto nel README è attualmente implementato solo come mascheramento di `email`, `username` e `password` nel controller. Non c'è un flag booleano; le relazioni (amicizie, gruppi, spese) non vengono disassociate.
- **`UserGroup` prevede `dataUscita`**, ma `removeUsersFromGroup` esegue una `DELETE` fisica invece di popolarla.
- **`Transaction` ha una relazione diretta con `Group`**, ridondante ma comoda per query sui bilanci.

---

## 4. Flussi funzionali e algoritmi

### 4.1 Autenticazione

**Flow di registrazione**

1. `POST /auth/register` riceve `AuthRequest` (username, email, password in chiaro).
2. Viene generato un token email JWT contenente anche la password in chiaro nei claims (`JwtUtil.generateEmailToken`).
3. Viene inviata un'email con link `${OPEN_LINK}/auth/confirmEmail?token=...`.
4. `GET /auth/confirmEmail` estrae username/password/email dal token, controlla scadenza e duplicati, e crea l'utente con password codificata in BCrypt.
5. Viene inviata email di benvenuto.

**Flow di login**

1. `POST /auth/login` riceve `AuthRequest`.
2. `UserService.loadUserByEmailOrUsername` cerca per email o username (case-insensitive).
3. `AuthenticationManager` autentica con username + password.
4. Viene rilasciato JWT con claim `userId`.

**Criticità**

- La password in chiaro transita nel token email. È firmata, ma costituisce una superficie d'attacco non necessaria.
- Non è presente alcun meccanismo di recupero password.
- Il token email ha la stessa durata del token di sessione (`JWT_VALIDITY`, default 24h). Se l'utente non conferma in tempo, deve registrarsi di nuovo.

### 4.2 Gestione utente

- `GET /user/me`: restituisce `UserDTO` dell'utente autenticato.
- `PUT /user/update`: aggiorna email, username, password e rilascia un nuovo JWT.
- `DELETE /user/delete`: sovrascrive i dati personali con stringhe di mascheramento.

**Criticità**

- Il soft delete non disattiva le amicizie, non rimuove l'utente dai gruppi, non elimina le spese. Gli altri utenti continuano a vedere un "UtenteEliminato" nelle spese.
- Non c'è controllo su username/email già esistenti in fase di update (a parte la logica di `createUser`, non invocata).
- L'aggiornamento della password non richiede la vecchia password.

### 4.3 Amicizie

**Algoritmo di gestione richieste**

- Ogni coppia di utenti è rappresentata da una sola riga `Friendship` con vincolo `user1_id < user2_id`.
- `userToBeConfirmed` indica chi deve accettare.
- Gli stati sono `IN_ATTESA`, `ACCETTATA`, `RIFIUTATA`.
- Quando una richiesta rifiutata viene reinviata dall'altro utente, il sistema inverte `userToBeConfirmed` e riporta lo stato a `IN_ATTESA`.

**Endpoint**

- `GET /user/sendFriendshipRequest?name=...&message=...`
- `GET /user/acceptFriendship?friendId=...`
- `GET /user/refuseFriendship?friendId=...`
- `DELETE /user/cancelFriendship?friendId=...`
- `GET /user/getFriends`
- `GET /user/getFriendshipReqReceived`
- `GET /user/getFriendshipReqSent`

**Criticità**

- Gli endpoint mutativi usano `GET` invece di `POST/PUT/DELETE`.
- `sendFriendshipRequest` cerca l'utente target per `name` come username **o** email (`loadUserByEmailOrUsername`), quindi un parametro ambiguo potrebbe generare `IllegalStateException` se esistono sia username che email uguali (case-insensitive).
- Il DTO `FriendshipReqRecDTO` e `FriendshipReqSenDTO` escludono il `userToBeConfirmed` dalla visualizzazione. La logica è corretta ma dipende dall'uguaglianza tra `User` basata solo sull'id.

### 4.4 Gruppi

**Algoritmo di creazione**

1. `GroupController.createGroup` riceve nome, descrizione e set di `userIds`.
2. Verifica che tutti gli `userIds` siano amici dell'utente autenticato (`FriendshipService.areAllFriends`).
3. Aggiunge sé stesso al set.
4. `GroupService.createGroup` crea il gruppo e le relazioni `UserGroup` con `dataIngresso = oggi`.

**Algoritmo di aggiunta utenti**

1. `GroupController.addUsersToGroup` verifica amicizia, aggiunge sé stesso al set (anche se già membro), verifica appartenenza al gruppo.
2. `GroupService.addUsersToGroup` per ogni utente:
   - se esiste già una riga `UserGroup`, azzera `dataUscita`;
   - altrimenti crea una nuova riga.

**Algoritmo di uscita**

- `DELETE /groups/leave/{groupId}` chiama `removeUsersFromGroup`, che esegue `DELETE` fisica delle righe `UserGroup`. Non viene valorizzata `dataUscita`.

**Criticità**

- Non c'è gestione dell'ultimo membro: un gruppo può rimanere vuoto.
- Non c'è endpoint per eliminare un gruppo (esiste `GroupService.deleteGroup` ma non esposto).
- `addUsersToGroup` aggiunge sempre l'utente autenticato, anche se non richiesto.
- Non c'è ruolo "admin" o "creatore" del gruppo: chiunque membro può aggiungere altri amici.
- Non c'è endpoint per vedere i membri di un gruppo in modo isolato, anche se `GET /groups/{id}` li include.

### 4.5 Spese e transazioni

**Algoritmo di creazione spesa (`BillService.createBill`)**

1. Crea un oggetto `Bill` con descrizione, importo totale, note, data, buyer e gruppo.
2. Per ogni debitore (tutti tranne il buyer) crea una `Transaction` con importo negativo (`-debito`).
3. Accumula la somma dei debiti in `a`.
4. Crea una `Transaction` per il buyer con importo positivo `a` (quanto ha "prestato").

**Esempio**

- Spesa di 100€, buyer A, debiti: B=40, C=60.
- Transactions: B=-40, C=-60, A=+100.
- Bilancio A: +100 (credito verso il gruppo). Bilanci B e C: -40 e -60.

**Criticità**

- **Nessuna validazione che l'importo totale `amount` sia uguale alla somma dei debiti**. Se i debiti sommano a 80 ma `amount` è 100, il sistema registra comunque la spesa.
- **Nessun supporto a spese pagate da più persone**: solo un buyer.
- **Nessun supporto a spese non divise equamente con arrotondamenti**: è responsabilità del chiamante fornire importi `BigDecimal` corretti.
- Il buyer non può essere anche debitore (il codice lo esclude esplicitamente). Se il buyer deve partecipare alla spesa, il debito personale non è rappresentato.
- `DELETE /bills/{id}` non verifica che l'utente autenticato sia il buyer o membro del gruppo: chiunque autenticato può eliminare qualsiasi spesa.
- `DELETE /transactions/{id}` non verifica autorizzazione.
- Non c'è endpoint per modificare una spesa esistente.

### 4.6 Bilanci

**Algoritmo di calcolo saldo (`BalanceService`)**

- `getUserBalance(userId)`: somma tutte le `Transaction.amount` dell'utente.
- `getDetailedBalance(userId)`:
  - `totalPaid` = somma `Bill.amount` dove l'utente è buyer.
  - `totalOwed` = somma `Transaction.amount` dell'utente.
  - `netBalance = totalPaid - totalOwed`.

**Interpretazione**

- Per il buyer di una spesa da 100€: `totalPaid=100`, `totalOwed=+100` (la transaction positiva), quindi `netBalance=0`.  
  Questo significa che il buyer ha pagato 100€ ma ha anche un credito di 100€, quindi risulta "in pari".
- Per un debitore B (debito 40€): `totalPaid=0`, `totalOwed=-40`, quindi `netBalance=+40` (deve 40€).

**Criticità**

- La semantica dei campi è confusa: `totalOwed` per il buyer contiene un importo positivo (credito), non un debito.
- Non c'è un calcolo di "quanto X deve a Y" o del saldo all'interno di un singolo gruppo.
- `BalanceController.getUserBalance(userId)` non verifica che l'`userId` richiesto corrisponda all'utente autenticato: chiunque può vedere il bilancio di chiunque.

---

## 5. Stato delle API

### 5.1 API funzionali (con limiti noti)

| Area | Endpoint | Stato |
|------|----------|-------|
| Auth | `POST /auth/login` | Funzionale |
| Auth | `POST /auth/register` | Funzionale, password in chiaro nel token email |
| Auth | `GET /auth/confirmEmail` | Funzionale |
| Utente | `GET /user/me` | Funzionale |
| Utente | `PUT /user/update` | Funzionale, senza conferma vecchia password |
| Utente | `DELETE /user/delete` | **Mascheramento, non soft delete reale** |
| Amicizie | CRUD amicizie | Funzionale, ma metodi HTTP non RESTful |
| Gruppi | CRUD base gruppi | Funzionale, manca eliminazione gruppo |
| Spese | `POST /bills/new`, liste, delete | Funzionale, manca validazione importi e autorizzazione delete |
| Transazioni | `DELETE /transactions/{id}` | Funzionale, senza autorizzazione |
| Bilanci | `GET /balance/{userId}` | Funzionale, senza autorizzazione |
| Stato | `GET /status/isOn` | Funzionale |

### 5.2 API incomplete o con criticità bloccanti

| Problema | Impatto | Dove si risolve |
|----------|---------|-----------------|
| Soft delete fittizio | Dati orfani, GDPR rischioso | `UserController.deleteUser`, entità `User` |
| Eliminazione spesa senza autorizzazione | Un utente può cancellare spese altrui | `BillController.deleteBill` |
| Eliminazione transazione senza autorizzazione | Un utente può cancellare transazioni altrui | `TransactionController.deleteTransaction` |
| Bilancio di qualsiasi utente visibile | Privacy | `BalanceController.getUserBalance` |
| Creazione spesa senza validazione importo | Inconsistenze contabili | `BillController.createBill`, `BillService.createBill` |
| Uscita gruppo con DELETE fisica | Perdita storico | `GroupService.removeUsersFromGroup` |
| Nessun controllo ultimo membro | Gruppi vuoti | `GroupService.removeUsersFromGroup` |

### 5.3 API completamente mancanti (per un MVP solido)

- Recupero password / reset password.
- Eliminazione gruppo (logica o fisica).
- Modifica gruppo (nome/descrizione).
- Modifica spesa.
- Calcolo del saldo all'interno di un singolo gruppo.
- Calcolo "chi deve quanto a chi" tra due utenti o all'interno di un gruppo.
- Registrazione di un rimborso/pagamento tra utenti.
- Lista paginata di spese e gruppi.
- Endpoint per ottenere solo i membri di un gruppo.
- Notifiche (almeno per richieste di amicizia, aggiunta a gruppo, nuova spesa).

---

## 6. Criticità di sicurezza e integrità

### 6.1 Sicurezza

- **JWT secret**: in `application-dev.yml` è presente un valore di default. In produzione è obbligatorio sovrascriverlo con `JWT_SECRET`. Il dev-default è sufficientemente lungo per gli algoritmi HMAC usati da JJWT.
- **Password nel token email**: la password in chiaro viene inclusa nei claims del JWT di conferma. Sebbene il token sia firmato, se venisse leaked (es. log, email intercettata) l'account è compromesso.
- **CORS**: consentite `http://localhost:3000` e le origini da `CORS_ALLOWED_ORIGINS` (in produzione `https://splitbill.it`). OK per ambienti noti.
- **CSRF**: disabilitato correttamente in contesto stateless JWT.
- **Filtro sospetti**: `SuspiciousRequestFilter` blocca pattern JNDI e `MDEDiscovery`, ma usa `System.out.println` (vietato dalle regole di progetto).
- **Autorizzazione fine-grained**: manca. Endpoint come `DELETE /bills/{id}`, `DELETE /transactions/{id}`, `GET /balance/{userId}` non controllano ownership.

### 6.2 Integrità dei dati

- **Divisone spesa non controllata**: somma debiti vs importo totale non verificata.
- **Gruppi vuoti**: possibili.
- **Utenti eliminati nei dati storici**: le spese mostreranno "UtenteEliminato".
- **Amicizie di utenti eliminati**: rimangono attive.

---

## 7. Test e qualità

- Unico test presente: `JavawsApplicationTests.contextLoads()`.
- Nessun test di unità sui service.
- Nessun test di integrazione sui controller.
- Nessun test sui repository.
- Il README indica di eseguire `mvn clean verify` prima di committare, ma non ci sono test che coprano la logica di business.

---

## 8. Cosa manca prima che l'app possa essere usata

Per considerare il backend pronto per un uso reale (anche solo in beta), consiglio di completare almeno i seguenti punti:

1. **Correggere le autorizzazioni** su `DELETE /bills/{id}`, `DELETE /transactions/{id}`, `GET /balance/{userId}`.
2. **Implementare un vero soft delete** con flag `deleted` e conseguente gestione di gruppi, amicizie e visibilità.
3. **Validare la creazione delle spese**: la somma dei debiti deve coincidere con `amount` (entro tolleranza `BigDecimal`).
4. **Gestire l'ultimo membro di un gruppo**: impedire gruppi vuoti o eliminare il gruppo.
5. **Esportare o implementare l'eliminazione gruppo**.
6. **Aggiungere test di unità e integrazione** per i service e i controller critici.
7. **Rimuovere la password dal token email** (sostituire con token opaco salvato in DB o comunque non contenere password).
8. **Implementare il recupero password**.
9. **Aggiungere paginazione** su liste spese/gruppi/amici.
10. **Implementare calcolo "chi deve a chi"** (saldie tra utenti, anche per gruppo).

---

## 9. Domande aperte

Per affinare il piano di sviluppo ho bisogno di chiarimenti sui seguenti punti:

1. **Soft delete**: volete un vero flag `deleted` con anonimizzazione dati, oppure la cancellazione fisica è accettabile?  
   In caso di soft delete, come devono comportarsi gruppi e spese storiche?
        La cancellazione fisica è accettabile ma non devono perdersi i debiti storici.

2. **Spese**: il buyer deve poter essere anche debitore (pagare una parte della spesa)?  
   La somma dei debiti deve essere obbligatoriamente uguale all'importo totale, o volete tollerare una "mancia"/arrotondamento?
    Si, il buyer deve poter essere anche debitore. La somma dei debiti deve essere uguale all'importo totale, senza tollerare arrotondamenti.

3. **Gruppi**: volete un ruolo di "admin"/"creatore" che possa eliminare il gruppo o rimuovere membri?   SI
   L'uscita di un membro deve essere registrata con `dataUscita` (soft) o rimossa fisicamente? soft fiche il gruppo esiste o rimane vuoto.

4. **Bilanci**: volete esporre un endpoint che dica "X deve Y€ a Z" (saldi tra coppie di utenti) oltre al saldo netto individuale?  Si
   Il bilancio deve essere calcolato per gruppo o globalmente? Sia di gruppo che globale.

5. **Privacy**: il bilancio di un utente deve essere visibile solo a sé stesso o anche agli amici / membri dello stesso gruppo? 
   Solo a sé stesso. Ma gli altri utenti possono vedere i debiti o crediti verso di loro (es. "X deve Y€ a me").

6. **Recupero password**: volete implementarlo ora con email, o è fuori scope per il primo rilascio?
    Lasciamolo per il prossimo rilascio.

7. **Notifiche**: volete aggiungere notifiche in-app per richieste di amicizia, aggiunta a gruppo e nuove spese?
    Nell'app android/web ci sarà un pannello che dovrà visualizzare le richieste di amicizia.

8. **Frontend**: il frontend si aspetta già API specifiche non presenti in questo backend? Se sì, quali?
    Non esiste ancora un FE quindi non ci sono API specifiche richieste. Ma devono essere abbastanza generiche da poter essere usate da qualsiasi FE (android, web, ecc.).

9. **Priorità**: per voi il primo rilascio utilizzabile (MVP) richiede anche il calcolo "chi deve a chi", oppure basta creare spese e vedere il saldo netto?
    Chi deve a chi ci dovrà essere.

10. **Test**: volete che venga aggiunta una suite di test automatizzata come parte del prossimo sprint, o preferite concentrarsi prima sulle funzionalità?
    Aggiungere test automatizzati come parte del prossimo sprint.
---

*Documento prodotto in modalità read-only a partire dal codice sorgente attuale. Non sono state apportate modifiche al progetto.*
