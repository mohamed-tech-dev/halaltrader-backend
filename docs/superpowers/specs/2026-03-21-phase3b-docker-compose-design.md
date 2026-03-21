# Phase 3B-Docker — Docker Compose complet

## Goal

Lancer toute la stack HalalTrader (postgres, market-data, backend, frontend) avec une seule commande `docker-compose up --build`. Aucune dépendance manuelle à démarrer.

## Architecture

```
docker-compose up --build
├── postgres        (port 5432) — PostgreSQL 15
├── market-data     (port 8081) — Python FastAPI + yfinance
├── backend         (port 8080) — Spring Boot 3, dépend de postgres + market-data
└── frontend        (port 80)  — nginx servant le build Vite, proxifie /api → backend
```

Note : yfinance fonctionne depuis Docker Desktop en local car le trafic sortant utilise l'IP de la machine hôte, pas une IP datacenter. La mise en garde datacenter ne s'applique qu'aux déploiements cloud.

**Secrets :** fichier `.env` à la racine du backend (gitignorée) contenant `ANTHROPIC_API_KEY`.

---

## Nouveaux fichiers

### `Dockerfile` (backend — halaltrader-backend/)

Multi-stage :
- **Stage `build`** : `eclipse-temurin:21-jdk` — copie `.mvn/`, `mvnw`, `pom.xml`, puis `src/`. Lance `./mvnw package -DskipTests`.
- **Stage `runtime`** : `eclipse-temurin:21-jre-jammy` — installe `curl` (`apt-get install -y curl --no-install-recommends`) pour le healthcheck, copie le JAR, expose 8080, lance `java -jar app.jar`.

Variables d'environnement runtime injectées par docker-compose :
- `DB_URL`, `DB_USER`, `DB_PASS`
- `ANTHROPIC_API_KEY`
- `MARKET_DATA_URL`
- `CORS_ALLOWED_ORIGINS`

### `.dockerignore` (backend — halaltrader-backend/)

Exclut du build context :
```
target/
.idea/
*.iml
.git/
application-local.yml
.env
```

`application-local.yml` contient une clé API en clair — elle ne doit jamais être copiée dans l'image.

### `halaltrader-frontend/Dockerfile`

Multi-stage :
- **Stage `build`** : `node:20-alpine` — `npm ci && npm run build` avec `VITE_MOCK=false` (ARG passé au build).
- **Stage `runtime`** : `nginx:alpine` — copie `dist/`, copie `nginx.conf`, expose 80.

### `halaltrader-frontend/nginx.conf`

```nginx
server {
    listen 80;

    root /usr/share/nginx/html;
    index index.html;

    # React Router — toutes les routes inconnues renvoient index.html
    location / {
        try_files $uri /index.html;
    }

    # Proxy API vers le backend Spring Boot
    # /api/portfolio → http://backend:8080/api/portfolio
    location /api/ {
        proxy_pass http://backend:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
}
```

Note : avec `proxy_pass http://backend:8080;` (sans trailing slash) et `location /api/`, nginx transmet l'URI complète incluant `/api/`. Les controllers Spring Boot étant mappés sous `/api/...`, le routage est correct.

### `.env.example` (backend — halaltrader-backend/)

```
ANTHROPIC_API_KEY=sk-ant-...
```

À copier en `.env` et remplir avant le premier `docker-compose up`.

---

## `docker-compose.yml` (mis à jour)

```yaml
services:

  postgres:
    image: postgres:15
    container_name: halaltrader-postgres
    environment:
      POSTGRES_DB: halaltrader
      POSTGRES_USER: halal
      POSTGRES_PASSWORD: halal123
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U halal -d halaltrader"]
      interval: 10s
      timeout: 5s
      retries: 5

  market-data:
    build: ./market-data
    container_name: halaltrader-market-data
    ports:
      - "8081:8081"
    healthcheck:
      test: ["CMD-SHELL", "python -c \"import urllib.request; urllib.request.urlopen('http://localhost:8081/health')\""]
      interval: 15s
      timeout: 10s
      retries: 3
      start_period: 30s

  backend:
    build: .
    container_name: halaltrader-backend
    ports:
      - "8080:8080"
    environment:
      DB_URL: jdbc:postgresql://postgres:5432/halaltrader
      DB_USER: halal
      DB_PASS: halal123
      ANTHROPIC_API_KEY: ${ANTHROPIC_API_KEY}
      MARKET_DATA_URL: http://market-data:8081
      CORS_ALLOWED_ORIGINS: http://localhost
    depends_on:
      postgres:
        condition: service_healthy
      market-data:
        condition: service_healthy
    healthcheck:
      test: ["CMD-SHELL", "curl -f http://localhost:8080/actuator/health || exit 1"]
      interval: 15s
      timeout: 10s
      retries: 5
      start_period: 60s

  frontend:
    build: ../halaltrader-frontend
    container_name: halaltrader-frontend
    ports:
      - "80:80"
    depends_on:
      backend:
        condition: service_healthy

volumes:
  postgres_data:
```

---

## Modifications de code existant

### `application.yml` (backend)

Deux changements :

```yaml
market-data:
  base-url: ${MARKET_DATA_URL:http://localhost:8081}

app:
  cors:
    allowed-origins: ${CORS_ALLOWED_ORIGINS:http://localhost:5173}
```

`CORS_ALLOWED_ORIGINS` vaut `http://localhost:5173` en dev local, `http://localhost` en Docker.

### `market-data/main.py`

Ajouter un endpoint `/health` pour le healthcheck Docker :

```python
@app.get("/health")
def health():
    return {"status": "ok"}
```

### `pom.xml` (backend)

Ajouter Spring Boot Actuator pour exposer `/actuator/health` (utilisé par le healthcheck Docker du backend). Aucune configuration `management:` supplémentaire nécessaire — Spring Boot 3 expose `/actuator/health` par défaut en HTTP.

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

### `.gitignore` (backend)

Ajouter `.env` s'il n'est pas déjà présent.

---

## Lancer la stack

```bash
# 1. Copier et remplir le fichier secrets
cp .env.example .env
# Éditer .env : renseigner ANTHROPIC_API_KEY

# 2. Lancer tout
docker-compose up --build

# 3. Ouvrir le dashboard
# http://localhost
```

---

## Ce qui ne change pas

- La logique métier (agents, scheduler, orchestrator) — intacte
- Le schéma DB et les migrations Flyway — intacts
- L'API REST — intacte
- Le frontend React — même code, juste packagé différemment
