# Piano di sviluppo SplitBill Backend — Suddivisione per sprint

> Basato sulle risposte alle domande aperte dell'analisi funzionale (`analisi-funzionale.md`).
> Ultimo aggiornamento: 2026-08-13

---

## Decisioni di progetto confermate

Le seguenti scelte sono state confermate dal committente e vincolano il piano:

1. **Eliminazione account**: cancellazione fisica dei dati personali accettabile, ma **non devono perdersi i debiti storici**. Si implementa quindi un **soft delete con anonimizzazione** (`deleted=true` + dati personali mascherati), mantenendo l'id utente per i riferimenti nelle spese/transazioni.
2. **Spese**: il buyer può essere anche debitore. La somma dei debiti **deve essere esattamente uguale** all'importo totale (`amount`), senza tolleranza per arrotondamenti.
3. **Gruppi**: esiste un ruolo **admin/creatore**. L'uscita di un membro avviene in modalità soft (`dataUscita` popolata). I gruppi vuoti devono essere gestiti (evitati o eliminati).
4. **Bilanci**: oltre al saldo netto personale, serve il calcolo **"chi deve a chi"**, sia a livello globale che per gruppo.
5. **Privacy**: il saldo netto personale è visibile solo al proprietario. Gli altri utenti possono vedere solo i debiti/crediti **verso di sé**.
6. **Recupero password**: fuori scope per il primo rilascio, previsto per release successiva.
7. **Notifiche**: per il momento basta un endpoint che esponga le richieste di amicizia in attesa (conteggio e lista). Il pannello notifiche sarà lato FE.
8. **Frontend**: non esiste ancora; le API devono rimanere generiche e riutilizzabili (Android, web, ecc.).
9. **MVP**: include obbligatoriamente il calcolo "chi deve a chi".
10. **Test**: ogni sprint include l'aggiunta/aggiornamento di test di unità e integrazione.

---

## Sprint 1 — Sicurezza, autorizzazioni e integrità delle spese

**Obiettivo**: chiudere i buchi di sicurezza e correggere l'algoritmo di creazione spese, in modo che il sistema non possa più generare dati inconsistenti o esposti.

### Task

- [x] Aggiungere controlli di ownership/autorizzazione:
  - `DELETE /bills/{id}`: consentito solo al buyer della spesa o all'admin del gruppo.
  - `DELETE /transactions/{id}`: consentito solo al buyer della spesa correlata o all'admin del gruppo.
  - `GET /balance/{userId}`: consentito solo se `userId` corrisponde all'utente autenticato.
- [x] Rivedere `BillService.createBill`:
  - consentire al buyer tra i debitori;
  - validare che `sum(usersDebit.values()) == amount` con `BigDecimal.compareTo`;
  - generare le `Transaction` coerentemente (buyer riceve credito solo per la parte effettivamente prestata agli altri).
- [x] Sostituire `System.out.println` in `SuspiciousRequestFilter` con logger SLF4J.
- [x] Aggiungere eccezioni dedicate per errori di autorizzazione/validazione e mapparle in `GlobalExceptionHandler`.
- [x] Aggiornare Swagger con le nuove risposte di errore.
- [x] **Prerequisito ruolo admin spostato nello Sprint 1**: aggiungere `GroupRole` su `UserGroup` e assegnare `ADMIN` al creatore del gruppo in `GroupService.createGroup`.
- [x] Correggere i metodi HTTP degli endpoint amicizie: `sendFriendshipRequest` deve usare `POST`, `acceptFriendship` e `refuseFriendship` devono usare `PUT` (`cancelFriendship` resta `DELETE`).
- [x] Tipizzare i ritorni `ResponseEntity<?>` in `UserController` e `GroupController` usando DTO concreti.

### Test

- [x] Test di unità per `BillService.createBill` (caso buyer debitore, somma errata, amount negativo).
- [x] Test di integrazione per `BillController` (delete non autorizzato, creazione con somma errata).
- [x] Test di integrazione per `BalanceController` (accesso al proprio bilancio vs bilancio altrui).
- [x] Test di integrazione per `TransactionController` (delete con autorizzazione buyer/admin).
- [x] Test di unità per `GroupService.createGroup` (ruolo ADMIN al creatore).

### Definition of Done

- [x] `mvn clean verify` passa.
- [x] I test coprono almeno il 70% delle righe modificate (classi interessate dalle modifiche: `BillService`, `TransactionController`, `BalanceController`, `GlobalExceptionHandler`, `SuspiciousRequestFilter`, `UserGroup` > 70%; `BillController` e `GroupService` risentono di codice preesistente non modificato).
- [x] Swagger riflette le nuove regole di autorizzazione.

---

## Sprint 2 — Gruppi, ruoli admin e gestione utenti

**Obiettivo**: introdurre il ruolo admin, gestire correttamente l'uscita dai gruppi e implementare la cancellazione account con preservazione dello storico.

### Task

- [x] ~~Estendere `UserGroup` con un campo `role` (es. `MEMBER`, `ADMIN`)~~ — realizzato nello Sprint 1.
- [x] ~~Assegnare `ADMIN` al creatore del gruppo in `GroupService.createGroup`~~ — realizzato nello Sprint 1.
- [x] Modificare `GroupService.removeUsersFromGroup`:
  - non eseguire più `DELETE` fisico;
  - popolare `dataUscita = LocalDate.now()`;
  - se l'utente che esce è l'ultimo membro attivo, eliminare il gruppo;
  - se l'utente che esce è admin, promuovere un altro membro attivo a admin (o impedire l'uscita se non viene designato un successore).
- [x] Aggiungere endpoint `GET /groups/{groupId}/settlement-status` per ottenere lo stato dei debiti/crediti pendenti tra i membri attivi (utile al FE per mostrare il popup di conferma eliminazione).
- [x] Aggiungere endpoint `DELETE /groups/{groupId}` per eliminazione gruppo (solo admin) con parametro `force`:
  - `force=false` (default): se esistono debiti/crediti pendenti nel gruppo, restituire `409 Conflict` con l'elenco dei debiti pendenti; se non ce ne sono, procedere con l'eliminazione.
  - `force=true`: l'utente conferma di aver pareggiato le spese; eliminare fisicamente il gruppo, tutte le `Bill`, tutte le `Transaction` e tutte le `UserGroup` associate.
- [x] Aggiungere endpoint `PUT /groups/{groupId}` per modifica nome/descrizione (solo admin).
- [x] Aggiungere endpoint `GET /groups/{groupId}/members` per lista membri attivi.
- [x] Implementare soft delete utente:
  - aggiungere flag `deleted` su `User`;
  - anonimizzare `username`, `email`, `password`;
  - impedire login di utenti cancellati;
  - escludere utenti cancellati da ricerche, liste amici e gruppi attivi;
  - preservare bill e transaction (non cascade delete).
- [x] Aggiornare `UserRepository` e `FriendshipRepository` per filtrare utenti `deleted=false`.
- [x] Aggiungere validazione a `PUT /user/update`: richiedere la vecchia password e controllare che il nuovo username/email non siano già in uso da altri utenti.
- [x] Rimuovere `@Data` da `UserGroupId` e sostituirlo con `@Getter`/`@Setter`/`@NoArgsConstructor`.
- [x] Aggiungere `FetchType.LAZY` esplicito sulle associazioni `@ManyToOne` di `UserGroup`, `Bill`, `Transaction` e `Friendship`.

### Test

- [x] Test di unità per `GroupService` (uscita ultimo membro, promozione admin, modifica gruppo, eliminazione con e senza debiti pendenti).
- [x] Test di integrazione per `GroupController` (solo admin elimina/modifica, uscita soft, blocco eliminazione con debiti pendenti).
- [x] Test di integrazione per `UserController.deleteUser` (login bloccato, dati storici preservati).

### Definition of Done

- [x] Gruppi vuoti non persistono.
- [x] Solo admin può eliminare/modificare un gruppo.
- [x] L'eliminazione di un gruppo con debiti pendenti viene bloccata a meno che non venga forzata esplicitamente, e in tal caso vengono eliminate anche spese e transazioni.
- [x] Utente cancellato non può loggarsi, ma le spese storiche rimangono consultabili.

---

## Sprint 3 — Bilanci e calcolo "chi deve a chi"

**Obiettivo**: fornire una visione chiara dei debiti/crediti, personale e tra utenti, con il rispetto della privacy.

### Task

- [x] Rivedere `BalanceService`:
  - separare semanticamente `totalPaid` (quanto l'utente ha pagato) e `totalOwed` (quanto l'utente deve agli altri);
  - `netBalance = totalPaid - totalOwed` deve avere significato coerente.
  - *Approccio implementativo*: tabella riepilogo (`UserBalance`, `UserGroupBalance`, `PairwiseSettlement`) aggiornata ad ogni spesa/eliminazione per garantire letture O(1).
- [x] Endpoint saldo globale personale:
  - `GET /balance/me` (solo utente autenticato).
- [x] Endpoint saldo per gruppo:
  - `GET /groups/{groupId}/balance` (solo membri del gruppo, restituisce saldo dell'utente autenticato all'interno del gruppo).
- [x] Endpoint "chi deve a chi" globale:
  - `GET /balance/settlements`;
  - restituisce, per l'utente autenticato, l'elenco degli altri utenti con cui ha un debito/credito e l'importo.
- [x] Endpoint "chi deve a chi" per gruppo:
  - `GET /groups/{groupId}/settlements`;
  - restituisce solo i debiti/crediti dell'utente autenticato verso gli altri membri del gruppo.
- [x] Creare DTO dedicati per i settlement (`UserSettlementDTO` con `counterparty`, `amount`, `direction`).
- [x] Assicurarsi che un utente non possa vedere i settlement tra altri utenti.

### Algoritmo di calcolo "chi deve a chi"

Per ogni spesa nel perimetro (globale o gruppo):

- il buyer ha prestato agli altri partecipanti la somma dei loro debiti;
- ogni debitore deve al buyer l'importo della propria transazione negativa;
- se il buyer è anche debitore, il suo debito personale va sottratto dal credito verso il gruppo.

Aggregando per coppia (A, B), si ottiene l'importo netto che A deve a B (positivo se A deve a B, negativo se B deve a A).

### Test

- [x] Test di unità per `BalanceService` con scenari multi-spesa e multi-gruppo.
- [x] Test di integrazione per i nuovi endpoint, con verifica privacy.
- [ ] Test per verificare che i totali tornino dopo rimborsi futuri (preparazione al prossimo sprint).

### Definition of Done

- [x] I saldi sono calcolati correttamente in tutti gli scenari di test.
- [x] Un utente vede solo i propri dati e i debiti verso sé stesso.
- [x] Documentazione Swagger aggiornata.

---

## Sprint 4 — API generiche, paginazione e rimborsi

**Obiettivo**: rendere le API pronte per un frontend generico, aggiungere paginazione e la possibilità di registrare rimborsi tra utenti.

### Task

- [x] Aggiungere paginazione su:
  - `GET /groups` (gruppi dell'utente);
  - `GET /bills/group/{groupId}` (spese di un gruppo);
  - `GET /bills/getMyBills`;
  - `GET /user/getFriends`;
  - richieste di amicizia ricevute/inviate.
- [x] Aggiungere endpoint per conteggio richieste di amicizia in attesa:
  - `GET /user/friendshipRequests/count`.
- [x] Aggiungere endpoint per modifica spesa (`PUT /bills/{id}`), con le stesse validazioni della creazione e solo per buyer/admin.
- [x] Aggiungere entità `Payment` (rimborso tra due utenti, eventualmente in un gruppo):
  - campi: `id`, `payer`, `payee`, `amount`, `group` (opzionale), `date`, `notes`;
  - endpoint `POST /payments` per registrare un rimborso;
  - endpoint `GET /payments` per la cronologia dei propri rimborsi;
  - i rimborsi devono influenzare il calcolo "chi deve a chi".
- [x] Aggiornare `BalanceService` per tenere conto dei rimborsi nei settlement.
- [x] Aggiungere validazione: un rimborso non può superare il debito effettivo tra due utenti.
- [x] Refactor dei service per non esporre più entità JPA ai controller: restituire DTO dedicati invece di `Bill`, `Group`, `Transaction`, `User`.
- [x] Introduzione eccezioni di dominio dedicate per entità non trovata (es. `BillNotFoundException`, `GroupNotFoundException`) e mapparle in `GlobalExceptionHandler`.

### Test

- [x] Test di integrazione per paginazione.
- [x] Test di unità/integrazione per i rimborsi e il loro impatto sui bilanci.
- [x] Test end-to-end di uno scenario completo: registrazione → amicizia → gruppo → spesa → saldi → rimborso.

### Definition of Done

- [x] Tutti gli endpoint esposti sono documentati in Swagger.
- [x] `mvn clean verify` passa con almeno il 60% di coverage complessiva.
- [x] Il flusso end-to-end è testato automaticamente.

> Completato il 2026-08-13: 160 test passati, coverage 84% istruzioni / 68% branch.

---

## Sprint 5 — Refinimento, deploy e recupero password (post-MVP)

**Obiettivo**: funzionalità aggiuntive e preparazione al rilascio in produzione.

### Task

- [ ] Implementare recupero password via email:
  - endpoint `POST /auth/forgotPassword`;
  - endpoint `POST /auth/resetPassword` con token;
  - token di reset opaco e a breve scadenza, salvato su DB.
- [ ] Rimuovere la password dal token di conferma registrazione (sostituire con token opaco).
- [ ] Decodificare la chiave JWT da Base64 in `JwtUtil` e caricarla da variabile d'ambiente.
- [ ] Rimuovere il default hardcoded di `JWT_SECRET` da `application-dev.yml` (rendere la variabile d'ambiente obbligatoria o generarla in modo sicuro in locale).
- [ ] Aggiungere rate limiting sugli endpoint di autenticazione.
- [ ] Revisone delle configurazioni di produzione (`application-prod.yml`, variabili d'ambiente).
- [ ] Aggiornare `README.md` e documentazione Swagger con le nuove API.
- [ ] Eseguire audit dipendenze (`mvn versions:display-dependency-updates`, `mvn dependency-check:check`).

### Test

- [ ] Test di integrazione per recupero password.
- [ ] Test di sicurezza sui token di conferma e reset.
- [ ] Verifica del build Docker.

### Definition of Done

- Il recupero password funziona in ambiente dev con mail stub.
- Il container Docker si avvia correttamente.
- Nessuna dipendenza critica obsoleta.

---

## Note per le prossime sessioni AI

- Prima di iniziare uno sprint, verificare che lo sprint precedente sia completato e mergiato.
- Ogni task deve essere implementato con il minimo cambiamento necessario, rispettando `agent.md`.
- Aggiornare questo file durante lo sprint barrando i task completati.
- Non iniziare lo Sprint 5 prima che il MVP (Sprint 1-4) sia stabile e testato.
- MVP (Sprint 1-4) completato e testato in data 2026-08-13.
