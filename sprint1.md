# Piano Sprint 1 — Sessioni AI

> Basato su `piano-sviluppo-sprint.md`, sezione "Sprint 1 — Sicurezza, autorizzazioni e integrità delle spese"
> Stato: completato

---

## Step 1: Autorizzazioni endpoint + eccezioni dedicate

**Obiettivo**: Proteggere 3 endpoint con controlli ownership, creare eccezioni dedicate.

### Task specifici:

1. **Creare `UnauthorizedAccessException`**
   - Estensione di `RuntimeException`
   - Message template per endpoint

2. **BillController.deleteBill** (`DELETE /bills/{id}`)
   - Recuperare Bill da repository
   - Ottenere groupId della bill e userId autenticato
   - Verificare:
     - `userId == buyerId` OPPURE
     - `userId == adminGroupId` (da UserGroup con role ADMIN o creatore)
   - Se non autorizzato → throw `UnauthorizedAccessException`

3. **TransactionController.deleteTransaction** (`DELETE /transactions/{id}`)
   - Recuperare Transaction da repository
   - Ottenere billId e groupId della transaction
   - Recuperare Bill associata
   - Verificare:
     - `userId == buyerId` OPPURE
     - `userId == adminGroupId`
   - Se non autorizzato → throw `UnauthorizedAccessException`

4. **BalanceController.getUserBalance** (`GET /balance/{userId}`)
   - Verificare `userId == userIdAutenticato`
   - Se diverso → throw `UnauthorizedAccessException`

5. **GlobalExceptionHandler**
   - Aggiungere gestione `UnauthorizedAccessException` → REST 401
   - Aggiungere gestione `InvalidBillException` (preparazione step 2) → REST 400

6. **Swagger documentation**
   - Aggiornare annotazioni OpenAPI con risposte di errore 401/400

### File coinvolti:

| File | Azione |
|------|--------|
| `src/main/java/it/javaws/utils/UnauthorizedAccessException.java` | NUOVO |
| `src/main/java/it/javaws/controllers/BillController.java` | modifica deleteBill |
| `src/main/java/it/javaws/controllers/TransactionController.java` | modifica deleteTransaction |
| `src/main/java/it/javaws/controllers/BalanceController.java` | modifica getUserBalance |
| `src/main/java/it/javaws/config/GlobalExceptionHandler.java` | modifica |

### Verifica step 1:
- `mvn compile` senza errori
- Endpoint restituiscono 401 quando non autorizzati (test manuali con curl/postman)

---

## Step 2: BillService.createBill refactored

**Obiettivo**: Consentire buyer tra debitori, validare somma debiti = amount.

### Task specifici:

1. **Analizzare codice attuale**
   - Leggere `BillService.createBill` per capire struttura corrente
   - Identificare dove sono gestiti i debitori

2. **Modificare logica createBill**
   - Accettare buyerId tra la lista di debitori (validazione ridondante nel controller)
   - Calcolare `debitoBuyer`: quanto deve il buyer se presente in debitori
   - Importo da prestare = `sum(tutti_i_debiti_escluso_buyer)`
   - Creare Transaction per buyer con importo positivo corrispondente

3. **Validazione somma debiti**
   - Calcolare `debtSum = sum(debitori.values())`
   - Verificare: `amount.compareTo(debtSum) == 0` (nessuna tolleranza)
   - Se non valido → throw `InvalidBillException`

4. **Creare `InvalidBillException`**
   - Estensione di `RuntimeException`
   - Message con dettagli validazione fallita

### File coinvolti:

| File | Azione |
|------|--------|
| `src/main/java/it/javaws/utils/InvalidBillException.java` | NUOVO |
| `src/main/java/it/javaws/services/BillService.java` | modifica createBill |
| `src/main/java/it/javaws/dto/CreateBillRequest.java` | verifica struttura debitori |

### Verifica step 2:
- `mvn compile` senza errori
- Test manuale: creare spesa con buyer debitore → successo
- Test manuale: creare spesa con somma debiti ≠ amount → errore 400

---

## Step 3: Logging SLF4J + test suite

**Obiettivo**: Sostituire System.out, aggiungere test.

### Task specifici:

1. **SuspiciousRequestFilter**
   - Trovare `System.out.println`
   - Sostituire con `logger.info()` (import LoggerFactory)
   - Assicurarsi Logger configurato in class

2. **GlobalExceptionHandler**
   - Aggiungere gestione `InvalidBillException` → 400 Bad Request
   - Aggiornare messages di errore coerenti

3. **Test di unità BillService.createBill**
   - Test buyer debitore (buying amount + debito parziale)
   - Test somma debiti > amount → InvalidBillException
   - Test somma debiti < amount → InvalidBillException
   - Test amount negativo → InvalidBillException

4. **Test di integrazione BillController.deleteBill**
   - Delete autorizzato (buyer) → 200
   - Delete non autorizzato → 401
   - Delete utente estraneo → 401

5. **Test di integrazione BalanceController.getUserBalance**
   - Get proprio bilancio → 200
   - Get bilancio altrui → 401

6. **Eseguire verifica completa**
   - `mvn clean verify`
   - Copertura minima 70% file modificati

### File coinvolti:

| File | Azione |
|------|--------|
| `src/main/java/it/javaws/filters/SuspiciousRequestFilter.java` | modifica System.out → logger |
| `src/main/java/it/javaws/config/GlobalExceptionHandler.java` | modifica |
| `src/test/java/it/javaws/services/BillServiceTest.java` | NUOVO |
| `src/test/java/it/javaws/controllers/BillControllerTest.java` | NUOVO |
| `src/test/java/it/javaws/controllers/BalanceControllerTest.java` | NUOVO |

### Verifica step 3:
- `mvn clean verify` PASSA
- Log di SLF4J visibili in output Maven
- Coverage report ≥70% per file modificato

---

## Sequenza di esecuzione

```bash
# Sessione 1
→ Implementa Step 1 (autorizzazioni + eccezioni)
→ mvn compile
→ Test manuali endpoint

# Sessione 2  
→ Implementa Step 2 (BillService refactoring)
→ mvn compile
→ Test manuale creazione spesa buyer debitore

# Sessione 3
→ Implementa Step 3 (logging + test suite)
→ mvn clean verify
→ Verifica copertura e passaggi
```

---

## Definition of Done Sprint 1

- [ ] `mvn clean verify` passa senza errori
- [ ] Tutti i task di Step 1, 2, 3 completati
- [ ] Coverage ≥70% file modificati (Step 3)
- [ ] Swagger documentato con risposte errore 400/401
- [ ] No `System.out.println` nel codice
- [ ] Buyer può essere debitore nella creazione spesa
- [ ] Somma debiti deve uguagliare amount (nessuna tolleranza)

---

## Note per sessioni AI

- Ogni sessione ~32k token → 1 step massimo
- Non accumulare modifiche multiple in una sessione
- Dopo ogni sessione: `mvn compile` prima di procedere
- Mantenere coerenza con `agent.md` (SLF4J, controller advice, etc.)
