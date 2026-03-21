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
- **Stage `build`** : `maven:3.9-eclipse-temurin-21` — copie le projet, lance `./mvnw package -DskipTests`
- **Stage `runtime`** : `eclipse-temurin:21-jre-jammy` — copie le JAR, expose 8080, lance `java -jar app.jar`

Variables d'environnement runtime :
- `DB_URL` — injecté par docker-compose
- `DB_USER` — injecté par docker-compose
- `DB_PASS` — injecté par docker-compose
- `ANTHROPIC_API_KEY` — injecté depuis `.env`
- `MARKET_DATA_URL` — `http://market-data:8081`

### `halaltrader-frontend/Dockerfile`

Multi-stage :
- **Stage `build`** : `node:20-alpine` — `npm ci && npm run build` (avec `VITE_MOCK=false`)
- **Stage `runtime`** : `nginx:alpine` — copie `dist/`, copie `nginx.conf`, expose 80

### `halaltrader-frontend/nginx.conf`

- Écoute sur le port 80
- `root /usr/share/nginx/html`
- `try_files $uri /index.html` — support React Router
- `location /api/ { proxy_pass http://backend:8080; }` — proxifie vers le backend container

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

Ajouter la prise en charge de `MARKET_DATA_URL` :
```yaml
market-data:
  base-url: ${MARKET_DATA_URL:http://localhost:8081}
```

La propriété `base-url` existe déjà avec la valeur hardcodée `http://localhost:8081` — remplacer par cette expression pour la rendre injectable.

### `market-data/main.py`

Ajouter un endpoint `/health` simple :
```python
@app.get("/health")
def health():
    return {"status": "ok"}
```

Nécessaire pour le `healthcheck` Docker du service market-data.

### Spring Boot Actuator

Ajouter `spring-boot-starter-actuator` au `pom.xml` pour que le healthcheck backend fonctionne (`/actuator/health`).

### `.env` (nouveau, gitignorée)

```
ANTHROPIC_API_KEY=sk-ant-...
```

Ajouter `.env` au `.gitignore` du backend.

---

## Lancer la stack

```bash
# Copier et remplir le fichier secrets
cp .env.example .env
# Éditer .env : mettre ANTHROPIC_API_KEY

# Lancer tout
docker-compose up --build

# Ouvrir le dashboard
# http://localhost
```

---

## Ce qui ne change pas

- La logique métier (agents, scheduler, orchestrator) — intacte
- Le schéma DB et les migrations Flyway — intacts
- L'API REST — intacte
- Le frontend React — même code, juste packagé différemment
