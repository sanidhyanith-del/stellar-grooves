# Stellar Grooves

A self-hosted, multi-user music library application for rock and metal collections. Stellar Grooves scans local directories for audio files, extracts metadata, automatically categorises tracks by sub-genre, and streams them directly in the browser.

---

## Features

- Scan local directories for `.mp3`, `.flac`, and `.m4a` files
- Automatic genre classification (Classic Rock, Hard Rock, Hair Metal, Heavy Metal, Thrash Metal) based on a built-in artist catalogue spanning the 1960s–2020s
- Duplicate detection — skips files already in the library by both file path and song metadata (title + artist)
- In-browser audio playback with a persistent player bar (play/pause, seek, volume)
- Inline genre editing for tracks classified as "Other"
- Sortable library table with artist, album, and genre drill-down views
- Multi-user with per-user libraries; session-based and JWT authentication
- Admin endpoints for user management

---

## Prerequisites

| Requirement | Version | Notes |
|-------------|---------|-------|
| Java JDK | 17+ | [Adoptium Temurin](https://adoptium.net) recommended |
| Apache Maven | 3.6+ | Included via `mvnw` wrapper — no separate install required |
| MongoDB | 6.0+ | Must be running before the app starts |

### Installing MongoDB

**macOS (Homebrew)**
```bash
brew tap mongodb/brew
brew install mongodb-community
brew services start mongodb-community
```

**Ubuntu / Debian**
```bash
sudo apt-get install -y mongodb
sudo systemctl start mongodb
```

**Windows**

Download and run the MSI installer from [mongodb.com/try/download/community](https://www.mongodb.com/try/download/community), then start the service via Services or:
```powershell
net start MongoDB
```

By default the app connects to `mongodb://localhost:27017/stellar_grooves`. No additional database setup or schema creation is needed — MongoDB creates the database and collections automatically on first run.

---

## Build and Run

### Quick start (development)

```bash
# Clone the repository
git clone <repo-url>
cd stellar-grooves

# Run directly with Maven (no separate build step needed)
./mvnw spring-boot:run
```

On Windows:
```powershell
mvnw.cmd spring-boot:run
```

The application starts at **http://localhost:8080**.

---

### Build a runnable JAR (production)

```bash
./mvnw clean package -DskipTests
java -jar target/stellar-grooves-0.0.1-SNAPSHOT.jar
```

On Windows:
```powershell
mvnw.cmd clean package -DskipTests
java -jar target\stellar-grooves-0.0.1-SNAPSHOT.jar
```

---

### Run tests

```bash
./mvnw test
```

---

## Configuration

All configuration is in `src/main/resources/application.properties`. Every value can be overridden with an environment variable without touching the file.

| Property | Env var | Default | Description |
|----------|---------|---------|-------------|
| `spring.data.mongodb.uri` | `MONGO_URI` | `mongodb://localhost:27017/stellar_grooves` | MongoDB connection string |
| `stellar.grooves.jwtSecret` | `JWT_SECRET` | *(bundled dev key)* | Base64-encoded JWT signing secret — **change in production** |
| `stellar.grooves.jwtExpirationMs` | `JWT_EXPIRATION_MS` | `86400000` (24 h) | JWT token lifetime in milliseconds |
| `server.port` | `PORT` | `8080` | HTTP port |

**Example — override via environment variables:**
```bash
MONGO_URI=mongodb://mongo-host:27017/grooves \
JWT_SECRET=<your-base64-secret> \
PORT=9090 \
java -jar target/stellar-grooves-0.0.1-SNAPSHOT.jar
```

> **Security note:** The bundled `jwtSecret` is for local development only. Generate a strong secret for any internet-facing deployment:
> ```bash
> openssl rand -base64 64
> ```

---

## First use

1. Open **http://localhost:8080/signup** and create an account.
2. Log in at **http://localhost:8080/login**.
3. Enter the absolute path to a local music directory (e.g. `/home/user/Music` or `C:\Users\You\Music`) and click **Start Scan**.
4. Tracks appear in the library table once scanning completes. Click **▶** on any row to play.

---

## REST API

All `/api/library/*` and `/api/admin/*` endpoints require authentication. Use the session cookie from form login, or pass a JWT in the `Authorization: Bearer <token>` header obtained from `/api/auth/signin`.

### Auth

| Method | Endpoint | Body | Description |
|--------|----------|------|-------------|
| `POST` | `/api/auth/signup` | `{ "username", "email", "password" }` | Register a new user |
| `POST` | `/api/auth/signin` | `{ "username", "password" }` | Log in, returns JWT token |

### Library

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/library/files` | List all tracks (optional `?genre=HARD_ROCK` filter) |
| `POST` | `/api/library/scan` | Scan a directory — body: `{ "path": "/absolute/path" }` |
| `GET` | `/api/library/files/{id}/stream` | Stream audio file (supports HTTP Range requests) |
| `PATCH` | `/api/library/files/{id}/genre` | Update a track's genre — body: `{ "genre": "CLASSIC_ROCK" }` |
| `DELETE` | `/api/library/files` | Clear the current user's entire library |

### Admin

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/admin/users` | List all users |
| `DELETE` | `/api/admin/users/{username}` | Delete a user and their library |

**Valid genre values:** `CLASSIC_ROCK`, `HARD_ROCK`, `HAIR_METAL`, `HEAVY_METAL`, `THRASH_METAL`, `OTHER`

---

## Packaging as a native installer

Requires `jpackage` (bundled with JDK 14+). Build the JAR first, then:

**macOS**
```bash
jpackage --type app-image \
  --input target/ \
  --main-jar stellar-grooves-0.0.1-SNAPSHOT.jar \
  --main-class com.stellarideas.grooves.StellarGroovesApplication \
  --name "StellarGrooves"
```

**Windows**
```powershell
jpackage --type exe `
  --input target/ `
  --main-jar stellar-grooves-0.0.1-SNAPSHOT.jar `
  --main-class com.stellarideas.grooves.StellarGroovesApplication `
  --name "StellarGrooves" `
  --win-shortcut --win-menu
```

> MongoDB must be installed and running separately on the target machine.

---

## Tech stack

| Layer | Technology |
|-------|-----------|
| Framework | Spring Boot 3.2.3 |
| Persistence | Spring Data MongoDB |
| Security | Spring Security + JJWT 0.11.5 |
| Templating | Thymeleaf + Bootstrap 5.3 |
| Metadata | JAudioTagger 3.0.1 |
| Build | Maven 3 |
| Runtime | Java 17 |
