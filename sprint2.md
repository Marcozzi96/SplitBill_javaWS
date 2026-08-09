# Piano Sprint 2 — Sessioni AI

> Basato su `piano-sviluppo-sprint.md`, sezione "Sprint 2 — Gruppi, ruoli admin e gestione utenti"
> Stato: completato

---

## Step 1: Refactor entità JPA e associazioni lazy

**Obiettivo**: allineare le entità alle convenzioni di progetto (`agent.md`) prima di aggiungere nuova logica.

### Task specifici:

1. **Rimuovere `@Data` da `UserGroupId`**
   - Sostituire con `@Getter`, `@Setter`, `@NoArgsConstructor`.
   - Mantenere `equals`/`hashCode` solo sull'id embedded.

2. **Aggiungere `FetchType.LAZY` esplicito sulle associazioni `@ManyToOne`**
   - `UserGroup.user`, `UserGroup.group`
   - `Bill.buyer`, `Bill.group`
   - `Transaction.user`, `Transaction.bill`, `Transaction.group`
   - `Friendship.user1`, `Friendship.user2`, `Friendship.userToBeConfirmed`

### File coinvolti:

| File | Azione |
|------|--------|
| `src/main/java/it/javaWS/models/entities/UserGroupId.java` | modifica |
| `src/main/java/it/javaWS/models/entities/UserGroup.java` | modifica |
| `src/main/java/it/javaWS/models/entities/Bill.java` | modifica |
| `src/main/java/it/javaWS/models/entities/Transaction.java` | modifica |
| `src/main/java/it/javaWS/models/entities/Friendship.java` | modifica |

### Verifica step 1:
- `mvn compile` senza errori.
- Nessun cambio di comportamento funzionale (solo fetch strategy).

---

## Step 2: Soft delete utente e filtri su repository

**Obiettivo**: implementare una vera cancellazione logica con anonimizzazione, preservando lo storico spese/transazioni.

### Task specifici:

1. **Aggiungere flag `deleted` su `User`**
   - Campo `boolean deleted = false` (o `Boolean`).
   - Aggiornare schema JPA/H2.

2. **Anonimizzare `UserController.deleteUser`**
   - Sovrascrivere `username`, `email`, `password` con valori mascherati.
   - Impostare `deleted = true`.

3. **Bloccare login per utenti cancellati**
   - In `UserService.loadUserByEmailOrUsername` o nel flusso di autenticazione, rifiutare utenti con `deleted = true`.

4. **Filtrare utenti cancellati**
   - `UserRepository`: metodi di ricerca devono restituire solo `deleted = false`.
   - `FriendshipRepository`: ricerche amici e richieste devono escludere utenti `deleted = true`.
   - Gruppi attivi: escludere utenti cancellati dai membri attivi.

5. **Preservare bill e transaction**
   - Non eseguire cascade delete.
   - I riferimenti storici restano validi tramite `id` utente anonimizzato.

### File coinvolti:

| File | Azione |
|------|--------|
| `src/main/java/it/javaWS/models/entities/User.java` | modifica |
| `src/main/java/it/javaWS/controllers/UserController.java` | modifica deleteUser |
| `src/main/java/it/javaWS/services/UserService.java` | modifica login/search |
| `src/main/java/it/javaWS/repositories/UserRepository.java` | aggiungere filtri |
| `src/main/java/it/javaWS/repositories/FriendshipRepository.java` | aggiungere filtri |

### Verifica step 2:
- `mvn compile` senza errori.
- Test manuale: registrazione → login → delete → login fallisce.
- Spese storiche ancora consultabili.

---

## Step 3: Validazione aggiornamento profilo

**Obiettivo**: rendere sicuro `PUT /user/update` richiedendo la vecchia password e controllando univocità.

### Task specifici:

1. **Richiedere vecchia password**
   - Aggiungere campo `oldPassword` al DTO di update.
   - Verificare corrispondenza con password corrente prima di applicare modifiche.

2. **Controllare username/email in uso**
   - Se l'utente cambia username o email, verificare che non siano già usati da altri utenti attivi (`deleted = false`).
   - Escludere l'utente corrente dal controllo.

3. **Eccezioni dedicate**
   - Creare/riutilizzare eccezione per password errata → REST 401/400.
   - Creare/riutilizzare eccezione per username/email già in uso → REST 409.

### File coinvolti:

| File | Azione |
|------|--------|
| `src/main/java/it/javaWS/models/dto/UpdateUserRequest.java` | NUOVO |
| `src/main/java/it/javaWS/services/UserService.java` | modifica update |
| `src/main/java/it/javaWS/controllers/UserController.java` | modifica |
| `src/main/java/it/javaWS/controllers/advice/GlobalExceptionHandler.java` | gestione nuove eccezioni |
| `src/main/java/it/javaWS/utils/InvalidCredentialsException.java` | NUOVO |
| `src/main/java/it/javaWS/utils/DuplicateUserException.java` | NUOVO |

### Verifica step 3:
- `mvn compile` senza errori.
- Test manuale: update con vecchia password errata → errore; update verso username esistente → errore.

---

## Step 4: Uscita soft dai gruppi e gestione admin

**Obiettivo**: sostituire l'eliminazione fisica con soft exit, gestendo gruppi vuoti e successione admin.

### Task specifici:

1. **Modificare `GroupService.removeUsersFromGroup`**
   - Non eseguire più `DELETE` fisico.
   - Popolare `dataUscita = LocalDate.now()`.

2. **Gestione ultimo membro attivo**
   - Se l'utente che esce è l'ultimo membro attivo, eliminare fisicamente il gruppo e le sue `Bill`/`Transaction`/`UserGroup` associate.

3. **Gestione admin uscente**
   - Se l'utente uscente è admin e rimangono altri membri attivi, promuovere un altro membro attivo a `ADMIN`.
   - Se non è possibile (nessun altro membro attivo), eliminare il gruppo come al punto 2.

4. **Endpoint lascia gruppo**
   - `DELETE /groups/leave/{groupId}` chiama la nuova logica.

### File coinvolti:

| File | Azione |
|------|--------|
| `src/main/java/it/javaWS/services/GroupService.java` | modifica removeUsersFromGroup |
| `src/main/java/it/javaWS/controllers/GroupController.java` | verifica leave endpoint |
| `src/main/java/it/javaWS/models/entities/UserGroup.java` | verifica campi role/dataUscita |
| `src/main/java/it/javaWS/repositories/UserGroupRepository.java` | query per membri attivi/admin |
| `src/main/java/it/javaWS/repositories/BillRepository.java` | utilizzato per eliminazione gruppo |
| `src/main/java/it/javaWS/repositories/TransactionRepository.java` | utilizzato per eliminazione gruppo |

### Verifica step 4:
- `mvn compile` senza errori.
- Test manuale: ultimo membro esce → gruppo eliminato; admin esce con altri membri → nuovo admin.

---

## Step 5: Nuovi endpoint gruppo

**Obiettivo**: esporre eliminazione, modifica, lista membri e stato debiti per gruppo.

### Task specifici:

1. **`GET /groups/{groupId}/members`**
   - Restituire lista membri attivi (`dataUscita` null).
   - Solo membri del gruppo possono accedere.

2. **`PUT /groups/{groupId}`**
   - Modificare nome e descrizione.
   - Solo admin del gruppo.

3. **`GET /groups/{groupId}/settlement-status`**
   - Calcolare debiti/crediti pendenti tra i membri attivi del gruppo.
   - Restituire elenco coppie (debitore, creditore, importo).
   - Utile al FE per il popup di conferma eliminazione.

4. **`DELETE /groups/{groupId}`** (solo admin)
   - Parametro `force` boolean, default `false`.
   - `force=false`: se esistono debiti/crediti pendenti, restituire `409 Conflict` con l'elenco dei debiti pendenti.
   - `force=true`: eliminare fisicamente il gruppo, tutte le `Bill`, `Transaction` e `UserGroup` associate.

5. **Eccezioni dedicate**
   - `GroupNotFoundException`, `NotGroupAdminException` (o simili) e mapparle in `GlobalExceptionHandler`.

### File coinvolti:

| File | Azione |
|------|--------|
| `src/main/java/it/javaWS/controllers/GroupController.java` | nuovi endpoint |
| `src/main/java/it/javaWS/services/GroupService.java` | nuova logica |
| `src/main/java/it/javaWS/models/dto/GroupMemberDTO.java` | NUOVO |
| `src/main/java/it/javaWS/models/dto/SettlementDTO.java` | NUOVO |
| `src/main/java/it/javaWS/utils/GroupNotFoundException.java` | NUOVO |
| `src/main/java/it/javaWS/utils/NotGroupAdminException.java` | NUOVO |
| `src/main/java/it/javaWS/utils/PendingSettlementsException.java` | NUOVO |
| `src/main/java/it/javaWS/controllers/advice/GlobalExceptionHandler.java` | gestione eccezioni |

### Verifica step 5:
- `mvn compile` senza errori.
- Test manuale: admin modifica gruppo → 200; non-admin → 401/403; eliminazione con debiti pendenti → 409; force=true → 200.

---

## Step 6: Test suite

**Obiettivo**: coprire con test la nuova logica di gruppi e utenti.

### Task specifici:

1. **Test di unità `GroupService`**
   - Uscita ultimo membro → gruppo eliminato.
   - Uscita admin → promozione nuovo admin.
   - Modifica gruppo solo da admin.
   - Eliminazione gruppo con/senza debiti pendenti.

2. **Test di integrazione `GroupController`**
   - Solo admin elimina/modifica.
   - Uscita soft e blocco eliminazione con debiti pendenti.
   - Lista membri attivi.

3. **Test di integrazione `UserController.deleteUser`**
   - Login bloccato dopo cancellazione.
   - Dati storici preservati (bill/transaction consultabili).

4. **Eseguire verifica completa**
   - `mvn clean verify`.
   - Copertura minima 70% file modificati.

### File coinvolti:

| File | Azione |
|------|--------|
| `src/test/java/it/javaws/services/GroupServiceTest.java` | NUOVO |
| `src/test/java/it/javaws/services/UserServiceTest.java` | NUOVO |
| `src/test/java/it/javaws/controllers/GroupControllerTest.java` | NUOVO |
| `src/test/java/it/javaws/controllers/UserControllerTest.java` | NUOVO |
| `src/test/java/it/javaws/models/entities/UserGroupIdTest.java` | NUOVO |

### Verifica step 6:
- `mvn clean verify` PASSA.
- Coverage report ≥70% per file modificato.

---

## Sequenza di esecuzione

```bash
# Sessione 1
→ Step 1 (refactor entità JPA)
→ Step 2 (soft delete utente)
→ mvn compile
→ Test manuali login/delete

# Sessione 2
→ Step 3 (validazione update profilo)
→ Step 4 (uscita soft dai gruppi)
→ mvn compile
→ Test manuali gruppi

# Sessione 3
→ Step 5 (nuovi endpoint gruppo)
→ mvn compile
→ Test manuali endpoint gruppo

# Sessione 4
→ Step 6 (test suite)
→ mvn clean verify
→ Verifica copertura e passaggi
```

---

## Definition of Done Sprint 2

- [x] `mvn clean verify` passa senza errori.
- [x] Tutti i task di Step 1-6 completati.
- [x] Coverage ≥70% file modificati (report JaCoCo totale 84%; UserService 81%, GroupService 78%, GroupController 80%, UserController 100%).
- [x] Gruppi vuoti non persistono (ultimo membro esce → gruppo eliminato).
- [x] Solo admin può eliminare/modificare un gruppo.
- [x] Eliminazione gruppo con debiti pendenti bloccata (`409`) a meno che `force=true`.
- [x] Utente cancellato non può loggarsi, ma le spese/transazioni storiche rimangono consultabili.
- [x] Swagger riflette i nuovi endpoint e le risposte di errore (annotazioni `@Operation`/`@ApiResponses` presenti sui nuovi endpoint).
- [x] Nessuna entità JPA usa `@Data`; `@ManyToOne` esplicitamente lazy.

---

## Note per sessioni AI

- Ogni sessione ~32k token → 1 step massimo.
- Non accumulare modifiche multiple in una sessione.
- Dopo ogni sessione: `mvn compile` prima di procedere.
- Mantenere coerenza con `agent.md` (DTO ai controller, eccezioni dedicate, lazy fetch).
