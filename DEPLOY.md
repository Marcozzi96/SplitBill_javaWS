# Deploy su VPS (Docker Compose)

Guida al deploy dello stack completo SplitBill (PostgreSQL + backend + frontend + HTTPS) su una singola VPS. Testata su Ubuntu 22.04/24.04; funziona anche su ARM64 (es. Oracle Cloud Free Tier: tutte le immagini usate sono multi-arch).

## Architettura

```text
                 ┌────────────────────────── VPS ──────────────────────────┐
 Internet ─────► │  Caddy :80/:443 (HTTPS automatico Let's Encrypt)        │
                 │    ├── https://<APP_DOMAIN>  → frontend (nginx :80)     │
                 │    └── https://<API_DOMAIN>  → backend (Spring :8080)   │
                 │  backend → db (PostgreSQL 16, volume pgdata)            │
                 └─────────────────────────────────────────────────────────┘
```

Dopo la prima configurazione, **ogni push su `main` di uno dei due repo triggera il deploy automatico** del servizio corrispondente (GitHub Actions → SSH → `git pull` + `docker compose up -d --build`).

## 1. Prerequisiti

- Una VPS con Ubuntu (2 vCPU / 4 GB RAM consigliati; minimo 2 GB + swap).
- Un dominio con due record `A` (es. `app.example.com` e `api.example.com`) che puntano all'IP del VPS.
  - **Senza dominio**: usa [sslip.io](https://sslip.io) — niente da configurare, basta usare `app.<IP>.sslip.io` e `api.<IP>.sslip.io` come domini (HTTPS funziona comunque).
- Per le email di conferma/reset: credenziali SMTP (es. Gmail con App Password).

## 2. Preparazione del VPS (una tantum)

```bash
# Installazione Docker (include il plugin compose v2)
curl -fsSL https://get.docker.com | sh
sudo usermod -aG docker $USER   # poi logout/login

# Apertura porte
sudo ufw allow 80/tcp && sudo ufw allow 443/tcp
```

> **Oracle Cloud**: oltre a iptables/ufw sulla macchina, aprire 80/443 anche nella **Security List** della subnet dalla console OCI (Ingress Rules). Le immagini Ubuntu di Oracle hanno regole iptables restrictive: se Caddy non è raggiungibile, eseguire
> `sudo iptables -I INPUT 6 -m state --state NEW -p tcp -m multiport --dports 80,443 -j ACCEPT && sudo netfilter-persistent save`.

## 3. Clone dei repository e configurazione

I due repo vanno clonati **affiancati** (il compose del backend builda il frontend dal path `../SplitBill`):

```bash
mkdir -p ~/splitbill && cd ~/splitbill
git clone <url-repo-backend> javaWS
git clone <url-repo-frontend> SplitBill

cd javaWS
cp .env.example .env
nano .env
```

Nel `.env`:

- `APP_DOMAIN` / `API_DOMAIN`: i due domini (o quelli sslip.io), **senza** `https://`.
- `POSTGRES_PASSWORD`: password robusta.
- `JWT_SECRET`: generare con `openssl rand -base64 64`.
- `MAIL_USERNAME` / `MAIL_PASSWORD`: credenziali SMTP.

`OPEN_LINK` (link nei email di conferma/reset) e l'origine CORS del frontend vengono derivati automaticamente da `APP_DOMAIN`.

## 4. Primo avvio

```bash
cd ~/splitbill/javaWS
docker compose up -d --build
docker compose logs -f        # per seguire l'avvio (Ctrl+C per uscire)
```

Verifica:

```bash
curl https://<API_DOMAIN>/status/isOn   # backend
curl -I https://<APP_DOMAIN>            # frontend
```

Al primo avvio Caddy richiede i certificati a Let's Encrypt: i domini devono già puntare all'IP del VPS e le porte 80/443 devono essere aperte, altrimenti l'emissione fallisce.

## 5. Deploy automatici da GitHub

Generare una coppia di chiavi dedicata **sul VPS** (o in locale) e autorizzarla:

```bash
ssh-keygen -t ed25519 -f ~/.ssh/github-deploy -N ""
cat ~/.ssh/github-deploy.pub >> ~/.ssh/authorized_keys
cat ~/.ssh/github-deploy        # chiave privata, da copiare nei secret GitHub
```

Poi in **entrambi** i repository GitHub: `Settings → Secrets and variables → Actions`:

| Secret | Valore |
|---|---|
| `VPS_HOST` | IP o hostname del VPS |
| `VPS_USER` | utente SSH (es. `ubuntu`) |
| `VPS_SSH_KEY` | contenuto della chiave privata `github-deploy` |

Da questo momento ogni push su `main` fa il deploy del servizio relativo. I workflow si possono anche lanciare a mano dalla tab **Actions** (`workflow_dispatch`).

## 6. Operazioni utili

```bash
docker compose ps                          # stato dei servizi
docker compose logs -f backend             # log del backend
docker compose up -d --build backend       # redeploy manuale di un servizio
docker compose down                        # stop di tutto (i dati restano nei volumi)

# Backup del database
docker compose exec db pg_dump -U splitbill splitbill | gzip > backup-$(date +%F).sql.gz

# Rollback all'ultima versione funzionante
git revert HEAD && git push                # il workflow rideploya da solo
```

## 7. Note

- Il DB è raggiungibile **solo** all'interno della rete Docker (nessuna porta pubblicata).
- L'heap JVM è limitato a `-Xmx768m` via `JAVA_TOOL_OPTIONS` nel compose; alzarlo se il VPS ha RAM in eccesso.
- I test non girano sul VPS: eventuali verifiche pre-deploy vanno aggiunte come step nei workflow (o eseguite in locale con `mvn clean verify`).
