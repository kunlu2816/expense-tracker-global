# Expense Tracking - DevOps Plan

## Architecture Overview

```
Internet → [ Nginx :80/:443 ] → reverse proxy /api/ → [ Spring Boot :8080 ]
                ↓                                              ↓
        serves frontend                              [ PostgreSQL :5432 ]
        static files (dist/)
```

3 Docker containers orchestrated by `docker-compose.yaml`:
1. **nginx** — serves frontend static files, reverse-proxies `/api/` to backend, handles SSL
2. **backend** — Spring Boot JAR running on JRE 21
3. **database** — PostgreSQL 16 with persistent volume

All communication between containers is on an internal Docker network. Only Nginx exposes ports 80/443 to the outside.

---

## D1: Environment Configuration — PENDING

### What
- Create `.env.example` with all required environment variables (documented, no real secrets)
- Create `src/main/resources/application-prod.yaml` (production Spring profile)
- Add explicit `server.port: 8080` to existing `application.yaml`

### Why
- Secrets must never be hardcoded or committed to git
- `.env.example` documents what variables are needed without exposing values
- `application-prod.yaml` overrides dev defaults for production (e.g., `show-sql: false`, no fallback secrets)
- `server.port` should be explicit rather than relying on Spring Boot's default

### Variables in `.env.example`
```
POSTGRES_DB=expense_tracking
POSTGRES_USER=postgres
POSTGRES_PASSWORD=changeme
JWT_SECRET=changeme-generate-a-64-char-hex-string
SPRING_PROFILES_ACTIVE=prod
```

### `application-prod.yaml` key differences from dev
- `spring.jpa.show-sql: false`
- `spring.jpa.hibernate.ddl-auto: validate` (Flyway handles migrations, Hibernate only validates)
- No fallback values for secrets — app fails to start if env vars are missing
- `logging.level.root: WARN` (less verbose in production)

---

## D2: Frontend API URL Configuration — PENDING

### What
- Replace hardcoded `http://localhost:8080/api` in `frontend/src/api/axios.js` with `import.meta.env.VITE_API_URL`
- Create `frontend/.env.development` with `VITE_API_URL=http://localhost:8080/api`
- Create `frontend/.env.production` with `VITE_API_URL=/api` (relative, because Nginx serves both frontend and proxies API)

### Why
- In production, frontend and API are served from the same domain via Nginx, so `/api` is a relative path
- In development, frontend runs on `:5173` and backend on `:8080`, so full URL is needed
- Vite replaces `import.meta.env.VITE_*` at build time — the value is baked into the JS bundle

---

## D3: Dockerfiles — PENDING

### What
- Create `Dockerfile` (backend, multi-stage build)
- Create `frontend/Dockerfile` (frontend, multi-stage build)
- Create `.dockerignore`

### Backend Dockerfile (multi-stage)
```
Stage 1 (build): maven:3.9-eclipse-temurin-21 → copy source → mvn clean package -DskipTests → produces JAR
Stage 2 (runtime): eclipse-temurin-21-jre → copy JAR from stage 1 → run with java -jar
```
- Multi-stage keeps final image small (~300MB JRE vs ~800MB full JDK)
- Build dependencies (Maven, source code) are discarded after stage 1

### Frontend Dockerfile (multi-stage)
```
Stage 1 (build): node:22-alpine → npm ci → npm run build → produces dist/
Stage 2 (runtime): nginx:alpine → copy dist/ from stage 1 → copy nginx.conf → serve
```
- `npm ci` (clean install) is more reliable than `npm install` for CI builds
- Final image is just Nginx + static files (~40MB)

### `.dockerignore`
Excludes: `node_modules`, `.git`, `target`, `.env`, `*.md`, IDE files

---

## D4: Nginx Configuration — PENDING

### What
- Create `nginx/nginx.conf`

### Key features
- **Reverse proxy**: `location /api/ { proxy_pass http://backend:8080; }` — forwards API requests to Spring Boot container
- **SPA fallback**: `try_files $uri $uri/ /index.html;` — returns `index.html` for all non-file routes (React Router handles routing client-side)
- **Proxy headers**: `X-Real-IP`, `X-Forwarded-For`, `X-Forwarded-Proto` — so backend sees real client IP, not Docker internal IP
- **Security headers**: `X-Frame-Options: DENY`, `X-Content-Type-Options: nosniff`, `X-XSS-Protection`
- **Gzip compression**: compresses text/css/js/json responses to reduce bandwidth
- **Static file caching**: `Cache-Control` headers for assets with content hashes

---

## D5: Docker Compose (Full Stack) — PENDING

### What
- Update `docker-compose.yaml` from DB-only to full 3-service stack

### Services
1. **database** (postgres:16-alpine)
   - Healthcheck: `pg_isready`
   - Named volume for data persistence
   - NO exposed ports (only accessible from internal network)

2. **backend** (built from `./Dockerfile`)
   - `depends_on: database (condition: service_healthy)`
   - Environment variables from `.env`
   - NO exposed ports (only accessible from Nginx via internal network)

3. **nginx** (built from `./frontend/Dockerfile`)
   - `depends_on: backend`
   - Ports: `80:80`, `443:443`
   - Mounts `nginx/nginx.conf` as config
   - Mounts certbot volumes (for SSL in D7)

### Why no exposed ports for database and backend?
- Only Nginx needs to be reachable from the internet
- Database and backend communicate via Docker's internal network (`backend:8080`, `database:5432`)
- Reduces attack surface

---

## D6: GitHub Actions CI/CD — PENDING

### What
- Create `.github/workflows/deploy.yml`

### Workflow
```
Trigger: push to main branch
Jobs:
  1. test:
     - Checkout code
     - Set up Java 21
     - Run: mvn clean package (builds + runs tests)
     - Set up Node 22
     - Run: npm ci && npm run build (in frontend/)
  2. deploy (needs: test):
     - SSH into EC2
     - git pull
     - docker compose build
     - docker compose up -d
     - docker image prune (cleanup old images)
```

### GitHub Actions Secrets needed
- `EC2_HOST` — public IP or domain of EC2 instance
- `EC2_USER` — SSH user (usually `ubuntu`)
- `EC2_SSH_KEY` — private SSH key for EC2 access
- `ENV_FILE` — contents of production `.env` file (written to server)

---

## D7: SSL with Let's Encrypt — PENDING

### What
- Add certbot service to `docker-compose.yaml`
- Update `nginx/nginx.conf` for HTTPS + HTTP→HTTPS redirect

### How Let's Encrypt works
1. Certbot requests a cert from Let's Encrypt
2. Let's Encrypt challenges: "prove you own this domain" by checking `http://yourdomain/.well-known/acme-challenge/`
3. Nginx serves the challenge file from a shared volume
4. Let's Encrypt issues the cert, certbot saves it to another shared volume
5. Nginx reads the cert from that volume and serves HTTPS
6. Certs expire every 90 days — a cron/certbot timer auto-renews

### Nginx changes for SSL
- Port 80: redirect all traffic to HTTPS (except `/.well-known/acme-challenge/`)
- Port 443: SSL with cert/key from certbot volumes
- HSTS header for security

### Docker Compose additions
- `certbot` service (certonly, webroot mode)
- Shared volumes: `certbot-etc` (certs), `certbot-var` (lib), `certbot-www` (challenge files)

---

## D8: EC2 Setup Guide — PENDING

### What
- Document step-by-step instructions for launching and configuring the EC2 instance

### Steps
1. **Launch EC2**: Ubuntu 24.04, t2.micro (free tier), 20GB EBS
2. **Security Group**: Allow SSH (22), HTTP (80), HTTPS (443)
3. **SSH in**: `ssh -i key.pem ubuntu@<ip>`
4. **Install Docker + Docker Compose**:
   ```bash
   curl -fsSL https://get.docker.com | sh
   sudo usermod -aG docker ubuntu
   ```
5. **Clone repo**: `git clone <repo-url> && cd expense-tracking-global`
6. **Create `.env`**: copy from `.env.example`, fill in real secrets
7. **Domain DNS**: In Namecheap, add A record pointing to EC2 public IP
8. **First deploy without SSL**:
   ```bash
   docker compose up -d
   ```
9. **Provision SSL cert** (after DNS propagates):
   ```bash
   docker compose run --rm certbot certonly --webroot -w /var/www/certbot -d yourdomain.me
   ```
10. **Switch Nginx to HTTPS config** and restart
11. **Set up cert auto-renewal** (cron job)
12. **Configure GitHub Actions secrets** in repo settings

---

## Execution Order

```
D1 (env config) → D2 (frontend env) → D3 (Dockerfiles) → D4 (Nginx) → D5 (Docker Compose) → D6 (CI/CD) → D7 (SSL) → D8 (EC2 guide)
```

D1 and D2 can be done in parallel. D3-D5 depend on each other. D6-D7 depend on D5. D8 is documentation.

## Status: PLAN SAVED — AWAITING USER GO-AHEAD TO START EXECUTION
