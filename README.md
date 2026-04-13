# Stellar Grooves

A self-hosted, multi-user music library for rock and metal collections. Scan local directories for audio files, auto-categorize tracks by sub-genre, manage playlists with drag-drop reordering, rate your favorites, resolve duplicates, and stream everything in the browser with a retro jukebox-themed UI.

Built with Spring Boot, MongoDB, and vanilla JavaScript.

---

## Features

### Library & Playback
- **Directory scanning** — recursively finds `.mp3`, `.flac`, and `.m4a` files and extracts metadata (artist, album, title, year, cover art) via JAudioTagger; configurable scan depth limit; symlink-safe
- **Auto genre classification** — customizable JSON catalog of 80+ rock/metal artists spanning the 1960s-2020s, mapping to Classic Rock, Hard Rock, Hair Metal, Heavy Metal, and Thrash Metal; user genre corrections are persisted and override the static catalog for future scans
- **Duplicate detection & resolution** — skips files already imported by both file path and metadata (title + artist); dedicated duplicates view to compare and delete duplicate tracks
- **In-browser playback** — persistent audio player bar with play/pause, seek, volume, shuffle, and album art display; HTTP Range support for seeking in large files; auto-advances to next track
- **Queue management** — add tracks to an "Up Next" queue; queue drains before sequential/shuffle playback resumes; clear queue with one click
- **Keyboard shortcuts** — Space to play/pause, left/right arrows to seek, up/down for volume (active when player is visible and no input is focused)

### Organization & Search
- **Playlist management** — create playlists, add/remove tracks, drag-drop reorder tracks, sort by title/artist/album/genre/rating/year, export as M3U or JSON
- **Server-side search** — debounced search across title, artist, and album fields via MongoDB regex queries; complements the client-side filters for large libraries
- **Advanced filtering** — simultaneous artist, album, and genre dropdown filters alongside full-text search; all filters combine with AND logic
- **Browse views** — drill-down by artist, album, and genre; album grid view with cover art thumbnails (toggleable to list view); sortable columns including rating and decade
- **Bulk operations** — checkbox selection with select-all toggle; bulk delete and bulk add-to-playlist
- **User ratings** — 5-star rating widget on each track; sortable by rating
- **Inline genre editing** — reclassify any track tagged as "Other" directly from the library table
- **Virtual scrolling** — DOM virtualization for libraries with 10,000+ tracks; only visible rows are rendered

### Administration & Security
- **Multi-user** — per-user libraries with session-based (form login) and JWT authentication; 15-minute idle session timeout
- **Account lockout** — accounts lock after 5 consecutive failed login attempts; auto-unlocks after 15 minutes; configurable thresholds
- **Admin dashboard** — stats overview (users, files, playlists), paginated user management table with per-user file counts, delete user with cascade
- **Security** — CSRF protection, rate limiting on auth endpoints (with proxy-aware IP detection and `Retry-After` header), configurable CORS origins, path traversal prevention, symlink detection, server-side input validation with typed DTOs, Content Security Policy headers, password complexity requirements, JWT with `jti`/`iss` claims
- **Audit logging** — dedicated `AUDIT` logger with structured MDC context tracks all security-sensitive operations: logins, signups, file deletions, genre changes, playlist modifications, and admin actions
- **API documentation** — interactive Swagger UI at `/swagger-ui.html` with OpenAPI 3.0 spec at `/api-docs`; JWT bearer auth support
- **API versioning** — all REST endpoints under `/api/v1/` for forward compatibility
- **Structured logging** — correlation IDs on every request (`X-Correlation-Id` header), MDC-based log pattern for request tracing
- **Health check** — `/actuator/health` endpoint for monitoring (includes MongoDB connectivity)

### UI & Accessibility
- **Jukebox theme** — retro dark UI with neon glow effects, chrome accents, wood grain textures, and "Righteous" display typography
- **Album art** — embedded cover art extracted during scan, displayed in the player bar, album grid view, and available via API
- **Accessibility** — ARIA labels on all interactive elements, `aria-sort` on sortable columns, `aria-live` regions for status updates, keyboard-navigable sort headers, `prefers-reduced-motion` support
- **Responsive design** — mobile-first layout with Bootstrap 5.3; columns hide on small screens

---

## Prerequisites

| Requirement | Version | Notes |
|-------------|---------|-------|
| Java JDK | 17+ | [Adoptium Temurin](https://adoptium.net) recommended |
| Apache Maven | 3.6+ | Or use `mvn` if installed globally |
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

A `JWT_SECRET` environment variable is **required** to start the app. Generate one with:

```bash
export JWT_SECRET=$(openssl rand -base64 64)
```

Then run:

```bash
git clone <repo-url>
cd stellar-grooves

# Run with Maven (development)
JWT_SECRET=$JWT_SECRET mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

Windows:
```powershell
$env:JWT_SECRET = [Convert]::ToBase64String((1..64 | ForEach-Object { Get-Random -Max 256 }) -as [byte[]])
mvn spring-boot:run "-Dspring-boot.run.profiles=dev"
```

The app starts at **http://localhost:8080**.

### Build a JAR (production)

```bash
mvn clean package -DskipTests
JWT_SECRET=$JWT_SECRET java -jar target/stellar-grooves-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod
```

### Run tests

```bash
mvn test
```

Tests generate a JaCoCo coverage report at `target/site/jacoco/index.html`.

### Check code formatting

```bash
mvn spotless:check      # verify formatting
mvn spotless:apply      # auto-fix formatting
```

---

## Configuration

All settings live in `src/main/resources/application.properties` and can be overridden via environment variables.

### Core Settings

| Property | Env var | Default | Description |
|----------|---------|---------|-------------|
| `spring.data.mongodb.uri` | `MONGO_URI` | `mongodb://localhost:27017/stellar_grooves` | MongoDB connection string |
| `stellar.grooves.jwtSecret` | `JWT_SECRET` | *(none — required)* | Base64-encoded JWT signing secret (minimum 256 bits). App **fails to start** without this. |
| `stellar.grooves.jwtExpirationMs` | `JWT_EXPIRATION_MS` | `86400000` (24h) | JWT token lifetime in milliseconds |
| `server.port` | `PORT` | `8080` | HTTP listen port |

### Security & Rate Limiting

| Property | Env var | Default | Description |
|----------|---------|---------|-------------|
| `stellar.grooves.cors.allowedOrigins` | `CORS_ALLOWED_ORIGINS` | `http://localhost:8080,http://127.0.0.1:8080` | Comma-separated CORS origin patterns |
| `stellar.grooves.login.maxFailedAttempts` | `LOGIN_MAX_FAILED_ATTEMPTS` | `5` | Failed login attempts before account lockout |
| `stellar.grooves.login.lockoutDurationMinutes` | `LOGIN_LOCKOUT_MINUTES` | `15` | Minutes before a locked account auto-unlocks |
| `stellar.grooves.rateLimit.maxRequests` | — | `10` | Max auth requests per IP per window |
| `stellar.grooves.rateLimit.windowMs` | — | `60000` (1 min) | Rate limit window in milliseconds |
| `stellar.grooves.rateLimit.trustProxy` | `RATE_LIMIT_TRUST_PROXY` | `false` | Trust `X-Forwarded-For` header for client IP detection |
| `stellar.grooves.rateLimit.trustedProxies` | `RATE_LIMIT_TRUSTED_PROXIES` | *(empty)* | Comma-separated proxy IPs allowed to set `X-Forwarded-For` (only used when `trustProxy=true`) |
| `server.servlet.session.timeout` | — | `15m` | Idle session timeout |

### Scanner

| Property | Default | Description |
|----------|---------|-------------|
| `stellar.grooves.scan.maxDepth` | `20` | Max directory depth for recursive scan |
| `stellar.grooves.catalogPath` | *(bundled catalog.json)* | Path to a custom artist-genre catalog JSON file |

### Spring Profiles

| Profile | Activate with | Description |
|---------|--------------|-------------|
| `dev` | `--spring.profiles.active=dev` | Debug logging, Thymeleaf cache disabled, CORS allows `localhost:8080` |
| `prod` | `--spring.profiles.active=prod` | INFO logging, requires `CORS_ALLOWED_ORIGINS` env var, trusts proxy headers from configured IPs |

When no profile is active, the base `application.properties` defaults apply.

**Example — production deployment:**
```bash
MONGO_URI=mongodb://mongo-host:27017/grooves \
JWT_SECRET=$(openssl rand -base64 64) \
PORT=9090 \
CORS_ALLOWED_ORIGINS=https://myapp.example.com \
RATE_LIMIT_TRUST_PROXY=true \
RATE_LIMIT_TRUSTED_PROXIES=127.0.0.1,::1 \
ADMIN_PASSWORD=changeme \
java -jar target/stellar-grooves-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod
```

> **Security note:** `JWT_SECRET` is required. Generate a strong Base64-encoded key (minimum 32 bytes decoded) with `openssl rand -base64 64`.

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

**Genre corrections:** When a user changes a track's genre in the UI, that correction is stored in MongoDB and takes precedence over the static catalog for future scans. This means the catalog effectively "learns" from user corrections without editing the JSON file.

---

## First Use

1. Open **http://localhost:8080/signup** and create an account.
2. Log in at **http://localhost:8080/login**.
3. Enter the absolute path to a music directory (e.g. `/home/user/Music`) and click **Start Scan**.
4. Tracks appear in the library with extracted metadata, genre classification, and album art.
5. Click the play button on any row to start streaming. Use keyboard shortcuts (Space, arrows) to control playback.
6. Use the artist, album, and genre filter dropdowns or the search box to find tracks.
7. Rate tracks with the 5-star widget. Sort by any column including rating.
8. Create playlists from the sidebar, add tracks via the "+" button, and reorder with drag-drop.
9. Select multiple tracks with checkboxes for bulk delete or bulk add-to-playlist.
10. Click the "Duplicated Songs" stat card to review and resolve duplicate tracks.
11. Export playlists as M3U or JSON from the playlist view.
12. Admin users can access the admin dashboard at **/admin** to manage users.
13. Browse the interactive API documentation at **/swagger-ui.html**.

---

## Health Check

A health endpoint is available at `/actuator/health` (no authentication required). It reports the overall application status including MongoDB connectivity.

```bash
curl http://localhost:8080/actuator/health
# {"status":"UP"}
```

---

## REST API

> **Interactive docs:** Browse the full API at [/swagger-ui.html](http://localhost:8080/swagger-ui.html) when the app is running. The OpenAPI spec is available at `/api-docs`.

All endpoints under `/api/v1/library/*`, `/api/v1/playlists/*`, and `/api/v1/admin/*` require authentication. Use the session cookie from form login, or pass a JWT via the `Authorization: Bearer <token>` header (obtained from `/api/v1/auth/signin`).

Session-authenticated requests (form login) must include a `X-XSRF-TOKEN` header with the value from the `XSRF-TOKEN` cookie for any mutating request (POST, PUT, PATCH, DELETE).

Auth endpoints are rate-limited to 10 requests per minute per IP by default. Rate-limited responses include a `Retry-After` header.

### Auth

| Method | Endpoint | Body | Description |
|--------|----------|------|-------------|
| `POST` | `/api/v1/auth/signup` | `{ "username", "email", "password" }` | Register a new user (password: min 8 chars, requires upper + lower + digit) |
| `POST` | `/api/v1/auth/signin` | `{ "username", "password" }` | Log in; returns `{ token, username }`; returns 403 if account is locked |

### Library

| Method | Endpoint | Body | Description |
|--------|----------|------|-------------|
| `GET` | `/api/v1/library/files` | — | List tracks (paginated); `?page=0&size=50`; optional `?genre=HARD_ROCK`; max 200/page |
| `GET` | `/api/v1/library/search` | — | Search tracks; `?q=metallica&page=0&size=50`; searches title, artist, and album |
| `POST` | `/api/v1/library/scan` | `{ "path": "/absolute/path" }` | Scan a directory; returns `{ filesFound, skipped, errors, errorDetails }` |
| `GET` | `/api/v1/library/files/{id}/stream` | — | Stream audio (supports HTTP Range) |
| `GET` | `/api/v1/library/files/{id}/cover` | — | Get album cover art (30-day cache) |
| `PATCH` | `/api/v1/library/files/{id}/genre` | `{ "genre": "CLASSIC_ROCK" }` | Update a track's genre; records a correction for the artist so future scans use this genre |
| `PATCH` | `/api/v1/library/files/{id}/rating` | `{ "rating": 4 }` | Set track rating (0-5, 0 = unrated) |
| `POST` | `/api/v1/library/files/bulk-delete` | `{ "fileIds": ["id1", "id2"] }` | Bulk delete tracks (max 500); cascades to playlists |
| `GET` | `/api/v1/library/duplicates` | — | Get duplicate track groups (by title + artist) |
| `DELETE` | `/api/v1/library/files/{id}` | — | Delete a single track |
| `DELETE` | `/api/v1/library/files` | — | Clear the current user's entire library |

### Playlists

| Method | Endpoint | Body | Description |
|--------|----------|------|-------------|
| `GET` | `/api/v1/playlists` | — | List all playlists with track counts |
| `POST` | `/api/v1/playlists` | `{ "name": "My Playlist" }` | Create a new playlist (max 80 chars) |
| `DELETE` | `/api/v1/playlists/{id}` | — | Delete a playlist |
| `GET` | `/api/v1/playlists/{id}/tracks` | — | Get tracks in playlist order |
| `POST` | `/api/v1/playlists/{id}/tracks` | `{ "fileId": "..." }` | Add a track to a playlist |
| `DELETE` | `/api/v1/playlists/{id}/tracks/{fileId}` | — | Remove a track from a playlist |
| `PUT` | `/api/v1/playlists/{id}/tracks/reorder` | `{ "trackIds": ["id1","id2",...] }` | Reorder playlist tracks (IDs must match) |
| `GET` | `/api/v1/playlists/{id}/export?format=json` | — | Export playlist as JSON (attachment download) |
| `GET` | `/api/v1/playlists/{id}/export?format=m3u` | — | Export playlist as M3U (attachment download) |

### Admin (requires `ROLE_ADMIN`)

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/v1/admin/stats` | System stats: `{ totalUsers, totalFiles, totalPlaylists }` |
| `GET` | `/api/v1/admin/users` | List users with file counts; `?page=0&size=25` |
| `GET` | `/api/v1/admin/users/{id}` | Get a single user |
| `DELETE` | `/api/v1/admin/users/{id}` | Delete a user and all their data (files, playlists, cover art) |

**Valid genre values:** `CLASSIC_ROCK`, `HARD_ROCK`, `HAIR_METAL`, `HEAVY_METAL`, `THRASH_METAL`, `OTHER`

---

## Project Structure

```
src/main/java/com/stellarideas/grooves/
├── StellarGroovesApplication.java       # Entry point
├── config/
│   ├── AdminBootstrap.java              # Auto-create admin on first startup
│   ├── OpenApiConfig.java               # Swagger/OpenAPI configuration
│   ├── RateLimitFilter.java             # Per-IP rate limiting (proxy-aware, Retry-After)
│   ├── RequestCorrelationFilter.java    # MDC correlation ID for request tracing
│   └── WebConfig.java                   # Registers @CurrentUser argument resolver
├── controller/
│   ├── AuthController.java              # Signup/signin endpoints + account lockout
│   ├── LibraryController.java           # Library CRUD + streaming + search + rating + bulk ops + duplicates + cover art
│   ├── PlaylistController.java          # Playlist management + reorder + export
│   ├── AdminController.java             # Admin stats + user management
│   ├── ViewController.java              # Thymeleaf page routes (/, /login, /signup, /admin)
│   └── GlobalExceptionHandler.java      # Centralized error handling
├── model/
│   ├── User.java                        # User document
│   ├── MusicFile.java                   # Track document (with rating, hasCoverArt)
│   ├── Playlist.java                    # Playlist document
│   ├── CoverArt.java                    # Album cover art storage (binary)
│   ├── Genre.java                       # Genre enum
│   ├── GenreCorrection.java             # User genre corrections (artist → genre override)
│   └── Role.java                        # Role enum
├── dto/
│   ├── AddTrackRequest.java             # Add track to playlist
│   ├── BulkDeleteRequest.java           # Bulk delete tracks (validated, max 500)
│   ├── CreatePlaylistRequest.java       # Create playlist (validated)
│   ├── LoginRequest.java                # Login validation
│   ├── MusicFileDTO.java                # Track response (with rating + cover art flag)
│   ├── PlaylistDTO.java                 # Playlist response
│   ├── ReorderTracksRequest.java        # Playlist track reorder
│   ├── ScanRequest.java                 # Directory scan request
│   ├── ScanResult.java                  # Scan result (saved, skipped, errors + details)
│   ├── SignupRequest.java               # Signup (with password policy)
│   ├── UpdateGenreRequest.java          # Genre update
│   └── UpdateRatingRequest.java         # Rating update (0-5)
├── repository/                          # Spring Data MongoDB repositories
│   ├── CoverArtRepository.java
│   ├── GenreCorrectionRepository.java
│   ├── MusicFileRepository.java         # Includes regex search query
│   ├── PlaylistRepository.java
│   └── UserRepository.java
├── security/
│   ├── WebSecurityConfig.java           # Security filter chain + CSRF + CORS + CSP
│   ├── AuthTokenFilter.java             # JWT extraction filter
│   ├── JwtUtils.java                    # Token generation/validation (jti + iss claims)
│   ├── CurrentUser.java                 # @CurrentUser parameter annotation
│   ├── CurrentUserResolver.java         # Resolves authenticated user into controller params
│   ├── UserDetailsImpl.java             # Spring Security adapter
│   └── UserDetailsServiceImpl.java      # User loading service
└── service/
    ├── AuditService.java                # Structured audit logging (AUDIT logger + MDC)
    ├── LoginAttemptService.java         # Failed login tracking + account lockout
    ├── MusicCatalogService.java         # Artist -> genre mapping (JSON catalog + user corrections)
    └── MusicScannerService.java         # Directory scanning + batch import + cover art extraction

src/main/resources/
├── application.properties               # Shared configuration
├── application-dev.properties           # Dev profile (debug logging, no cache)
├── application-prod.properties          # Prod profile (strict CORS, proxy trust)
├── messages.properties                  # Externalized UI/error messages (i18n-ready)
├── logback-spring.xml                   # Logging config with correlation IDs
├── catalog.json                         # Artist-genre catalog (customizable)
├── static/css/main.css                  # Jukebox theme stylesheet
├── static/js/app.js                     # Frontend application (IIFE module)
├── static/js/signup.js                  # Signup form handler
└── templates/
    ├── index.html                       # Main library dashboard
    ├── admin.html                       # Admin dashboard
    ├── login.html                       # Login page
    └── signup.html                      # Registration page

src/test/java/com/stellarideas/grooves/
├── config/
│   └── RateLimitFilterTest.java         # Rate limiting (6 tests)
├── controller/
│   ├── AuthControllerTest.java          # Auth flow + lockout (7 tests)
│   ├── AuthConcurrencyTest.java         # Concurrent signup race condition (1 test)
│   ├── BaseControllerTest.java          # CurrentUserResolver (4 tests)
│   ├── LibraryControllerTest.java       # Library CRUD + pagination (7 tests)
│   ├── PlaylistControllerTest.java      # Playlist ops + ordering (6 tests)
│   ├── PlaylistSecurityTest.java        # Cross-user isolation (5 tests)
│   ├── ScanPathValidationTest.java      # Symlink attacks, path traversal, edge cases (7 tests)
│   └── StreamingTest.java              # Audio streaming + range requests + media types (8 tests)
├── security/
│   └── JwtUtilsTest.java               # JWT generation + validation (6 tests)
└── service/
    ├── LoginAttemptServiceTest.java     # Account lockout logic (9 tests)
    ├── MusicCatalogServiceTest.java     # Genre identification (9 tests)
    ├── MusicCatalogServiceLoadTest.java # Catalog loading (2 tests)
    ├── MusicScannerServiceTest.java     # Scan scenarios + error handling (9 tests)
    └── ScanConcurrencyTest.java         # Concurrent scan safety (2 tests)
```

**88 tests total** with JaCoCo coverage reports.

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Framework | Spring Boot 3.4.4 |
| Persistence | Spring Data MongoDB |
| Security | Spring Security 6.4 + JJWT 0.12.6 |
| API docs | springdoc-openapi 2.8.6 (Swagger UI + OpenAPI 3.0) |
| Monitoring | Spring Boot Actuator |
| Templating | Thymeleaf + Bootstrap 5.3 |
| Audio metadata | JAudioTagger 3.0.1 |
| Build | Maven 3 |
| Runtime | Java 17 |
| Testing | JUnit 5 + Mockito + JaCoCo (88 tests) |
| Code quality | Spotless (Google Java Format) + OWASP Dependency Check |

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
