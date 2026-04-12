# Stellar Grooves

A self-hosted, multi-user music library for rock and metal collections. Scan local directories for audio files, auto-categorize tracks by sub-genre, manage playlists, and stream everything in the browser with a retro jukebox-themed UI.

Built with Spring Boot, MongoDB, and vanilla JavaScript.

---

## Features

- **Directory scanning** — recursively finds `.mp3`, `.flac`, and `.m4a` files and extracts metadata (artist, album, title, year) via JAudioTagger; configurable scan depth limit; symlink-safe
- **Auto genre classification** — customizable JSON catalog of 80+ rock/metal artists spanning the 1960s-2020s, mapping to Classic Rock, Hard Rock, Hair Metal, Heavy Metal, and Thrash Metal
- **Duplicate detection** — skips files already imported by both file path and metadata (title + artist)
- **In-browser playback** — persistent audio player bar with play/pause, seek, and volume controls; HTTP Range support for seeking in large files; auto-advances to the next track when a song ends
- **Playlist management** — create playlists, add/remove tracks, browse playlist contents
- **Browse & filter** — drill-down views by artist, album, and genre; full-text search across title/artist/album; sortable columns
- **Inline genre editing** — reclassify any track tagged as "Other" directly from the library table
- **Pagination** — optional paginated API responses for large libraries (`?page=0&size=50`)
- **Multi-user** — per-user libraries with session-based (form login) and JWT authentication
- **Security** — CSRF protection, rate limiting on auth endpoints, configurable CORS origins, path traversal prevention, server-side input validation
- **Admin bootstrap** — auto-create an admin user on first startup via environment variables
- **Health check** — `/actuator/health` endpoint for monitoring (includes MongoDB connectivity)
- **Admin panel** — admin endpoints for user management and cleanup (with pagination)
- **Jukebox theme** — retro dark UI with neon glow effects, chrome accents, wood grain textures, and "Righteous" display typography

---

## Prerequisites

| Requirement | Version | Notes |
|-------------|---------|-------|
| Java JDK | 17+ | [Adoptium Temurin](https://adoptium.net) recommended |
| Apache Maven | 3.6+ | Included via `mvnw` wrapper — no separate install needed |
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

Download the MSI installer from [mongodb.com/try/download/community](https://www.mongodb.com/try/download/community), then start the service:
```powershell
net start MongoDB
```

No additional database setup is needed — MongoDB creates the database and collections automatically on first run.

---

## Quick Start

```bash
git clone <repo-url>
cd stellar-grooves

# Run with Maven (development)
./mvnw spring-boot:run
```

Windows:
```powershell
mvnw.cmd spring-boot:run
```

The app starts at **http://localhost:8080**.

### Build a JAR (production)

```bash
./mvnw clean package -DskipTests
java -jar target/stellar-grooves-0.0.1-SNAPSHOT.jar
```

### Run tests

```bash
./mvnw test
```

---

## Configuration

All settings live in `src/main/resources/application.properties` and can be overridden via environment variables.

### Core Settings

| Property | Env var | Default | Description |
|----------|---------|---------|-------------|
| `spring.data.mongodb.uri` | `MONGO_URI` | `mongodb://localhost:27017/stellar_grooves` | MongoDB connection string |
| `stellar.grooves.jwtSecret` | `JWT_SECRET` | *(bundled dev key)* | Base64-encoded JWT signing secret — **must change in production** |
| `stellar.grooves.jwtExpirationMs` | `JWT_EXPIRATION_MS` | `86400000` (24h) | JWT token lifetime in milliseconds |
| `server.port` | `PORT` | `8080` | HTTP listen port |

### Security & Rate Limiting

| Property | Default | Description |
|----------|---------|-------------|
| `stellar.grooves.cors.allowedOrigins` | `http://localhost:*,http://127.0.0.1:*` | Comma-separated CORS origin patterns |
| `stellar.grooves.rateLimit.maxRequests` | `10` | Max auth requests per IP per window |
| `stellar.grooves.rateLimit.windowMs` | `60000` (1 min) | Rate limit window in milliseconds |

### Scanner

| Property | Default | Description |
|----------|---------|-------------|
| `stellar.grooves.scan.maxDepth` | `20` | Max directory depth for recursive scan |
| `stellar.grooves.catalogPath` | *(bundled catalog.json)* | Path to a custom artist-genre catalog JSON file |

**Example — production deployment:**
```bash
MONGO_URI=mongodb://mongo-host:27017/grooves \
JWT_SECRET=$(openssl rand -base64 64) \
PORT=9090 \
stellar.grooves.cors.allowedOrigins=https://myapp.example.com \
java -jar target/stellar-grooves-0.0.1-SNAPSHOT.jar
```

> **Security note:** The bundled JWT secret is for local development only. Always generate a strong secret for any network-accessible deployment.

### Admin User

On first startup, you can create an admin user via environment variables:

```bash
ADMIN_PASSWORD=changeme java -jar target/stellar-grooves-0.0.1-SNAPSHOT.jar
```

| Env var | Default | Description |
|---------|---------|-------------|
| `ADMIN_PASSWORD` | *(none — required)* | Password for the initial admin user |
| `ADMIN_USERNAME` | `admin` | Username for the admin user |
| `ADMIN_EMAIL` | `admin@stellargrooves.local` | Email for the admin user |

The admin is only created if no admin user already exists. If a user with the given username exists but lacks the admin role, they are promoted.

### Custom Artist Catalog

The artist-genre mapping is stored in `src/main/resources/catalog.json`. To customize without recompiling, create your own JSON file and point to it:

```bash
stellar.grooves.catalogPath=/path/to/my-catalog.json \
java -jar target/stellar-grooves-0.0.1-SNAPSHOT.jar
```

The JSON format maps artist names to arrays of genre values:
```json
{
  "Artist Name": ["CLASSIC_ROCK", "HARD_ROCK"],
  "Another Band": ["THRASH_METAL"]
}
```

---

## First Use

1. Open **http://localhost:8080/signup** and create an account.
2. Log in at **http://localhost:8080/login**.
3. Enter the absolute path to a music directory (e.g. `/home/user/Music`) and click **Start Scan**.
4. Tracks appear in the library. Click the play button on any row to start streaming.
5. When a song ends, the next track plays automatically — jukebox style.
6. Create playlists from the sidebar and add tracks via the "+" button on each row.

---

## Health Check

A health endpoint is available at `/actuator/health` (no authentication required). It reports the overall application status including MongoDB connectivity.

```bash
curl http://localhost:8080/actuator/health
# {"status":"UP"}
```

---

## REST API

All endpoints under `/api/library/*`, `/api/playlists/*`, and `/api/admin/*` require authentication. Use the session cookie from form login, or pass a JWT via the `Authorization: Bearer <token>` header (obtained from `/api/auth/signin`).

Session-authenticated requests (form login) must include a `X-XSRF-TOKEN` header with the value from the `XSRF-TOKEN` cookie for any mutating request (POST, PATCH, DELETE).

Auth endpoints are rate-limited to 10 requests per minute per IP by default.

### Auth

| Method | Endpoint | Body | Description |
|--------|----------|------|-------------|
| `POST` | `/api/auth/signup` | `{ "username", "email", "password" }` | Register a new user |
| `POST` | `/api/auth/signin` | `{ "username", "password" }` | Log in; returns `{ token, username }` |

### Library

| Method | Endpoint | Body | Description |
|--------|----------|------|-------------|
| `GET` | `/api/library/files` | — | List all tracks; optional `?genre=HARD_ROCK` filter; optional `?page=0&size=50` for pagination |
| `POST` | `/api/library/scan` | `{ "path": "/absolute/path" }` | Scan a directory for audio files |
| `GET` | `/api/library/files/{id}/stream` | — | Stream audio (supports HTTP Range) |
| `PATCH` | `/api/library/files/{id}/genre` | `{ "genre": "CLASSIC_ROCK" }` | Update a track's genre |
| `DELETE` | `/api/library/files/{id}` | — | Delete a single track |
| `DELETE` | `/api/library/files` | — | Clear the current user's entire library |

When `page` is provided, the response is a paginated object:
```json
{
  "content": [...],
  "page": 0,
  "size": 50,
  "totalElements": 342,
  "totalPages": 7
}
```

When `page` is omitted, the response is a plain JSON array (backwards compatible).

### Playlists

| Method | Endpoint | Body | Description |
|--------|----------|------|-------------|
| `GET` | `/api/playlists` | — | List all playlists with track counts |
| `POST` | `/api/playlists` | `{ "name": "My Playlist" }` | Create a new playlist (max 80 chars) |
| `DELETE` | `/api/playlists/{id}` | — | Delete a playlist |
| `GET` | `/api/playlists/{id}/tracks` | — | Get tracks in a playlist |
| `POST` | `/api/playlists/{id}/tracks` | `{ "fileId": "..." }` | Add a track to a playlist |
| `DELETE` | `/api/playlists/{id}/tracks/{fileId}` | — | Remove a track from a playlist |

### Admin (requires `ROLE_ADMIN`)

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/admin/users` | List all users; optional `?page=0&size=25` for pagination |
| `GET` | `/api/admin/users/{id}` | Get a single user |
| `DELETE` | `/api/admin/users/{id}` | Delete a user and all their data |

**Valid genre values:** `CLASSIC_ROCK`, `HARD_ROCK`, `HAIR_METAL`, `HEAVY_METAL`, `THRASH_METAL`, `OTHER`

---

## Project Structure

```
src/main/java/com/stellarideas/grooves/
├── StellarGroovesApplication.java       # Entry point
├── config/
│   ├── AdminBootstrap.java              # Auto-create admin on first startup
│   └── RateLimitFilter.java             # Per-IP rate limiting for auth endpoints
├── controller/
│   ├── BaseController.java              # Shared getCurrentUser() logic
│   ├── AuthController.java              # Signup/signin endpoints
│   ├── LibraryController.java           # Library CRUD + streaming + pagination
│   ├── PlaylistController.java          # Playlist management
│   ├── AdminController.java             # Admin user management (paginated)
│   ├── ViewController.java              # Thymeleaf page routes
│   └── GlobalExceptionHandler.java      # Centralized error handling
├── model/
│   ├── User.java                        # User document (@JsonIgnore on password)
│   ├── MusicFile.java                   # Track document (compound indexes)
│   ├── Playlist.java                    # Playlist document (indexed)
│   ├── Genre.java                       # Genre enum
│   └── Role.java                        # Role enum
├── dto/
│   ├── LoginRequest.java                # Login request validation
│   └── SignupRequest.java               # Signup request validation
├── repository/                          # Spring Data MongoDB repositories
├── security/
│   ├── WebSecurityConfig.java           # Security filter chain + CSRF + CORS
│   ├── AuthTokenFilter.java             # JWT extraction filter
│   ├── JwtUtils.java                    # Token generation/validation
│   ├── UserDetailsImpl.java             # Spring Security adapter
│   └── UserDetailsServiceImpl.java      # User loading service
└── service/
    ├── MusicCatalogService.java         # Artist -> genre mapping (JSON catalog)
    └── MusicScannerService.java         # Directory scanning + batch import

src/main/resources/
├── application.properties               # All configuration
├── catalog.json                         # Artist-genre catalog (customizable)
├── static/css/main.css                  # Jukebox theme stylesheet
├── static/js/app.js                     # Frontend application logic
└── templates/                           # Thymeleaf HTML templates
```

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Framework | Spring Boot 3.2.3 |
| Persistence | Spring Data MongoDB |
| Security | Spring Security + JJWT 0.11.5 |
| Monitoring | Spring Boot Actuator |
| Templating | Thymeleaf + Bootstrap 5.3 |
| Audio metadata | JAudioTagger 3.0.1 |
| Build | Maven 3 |
| Runtime | Java 17 |
| Testing | JUnit 5 + Mockito |

---

## Native Installer (Optional)

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

> MongoDB must still be running on the target machine.

---

## License

MIT
