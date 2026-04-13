# Stellar Grooves

A self-hosted, multi-user music library for rock and metal collections. Scan local directories for audio files, auto-categorize tracks by sub-genre, manage playlists with drag-drop reordering, rate your favorites, resolve duplicates, and stream everything in the browser with a retro jukebox-themed UI.

Built with Spring Boot, MongoDB, and vanilla JavaScript.

---

## Features

### Library & Playback
- **Directory scanning** — recursively finds `.mp3`, `.flac`, and `.m4a` files and extracts metadata (artist, album, title, year, cover art) via JAudioTagger; configurable scan depth limit; symlink-safe; per-file timeout prevents corrupt files from stalling the scan
- **Real-time scan progress** — SSE endpoint streams live progress (files imported, skipped, errors) to the browser during scanning
- **Per-user scan rate limiting** — configurable cooldown between scans (default 60s) to prevent resource exhaustion
- **Auto genre classification** — customizable JSON catalog of 80+ rock/metal artists spanning the 1960s-2020s, mapping to Classic Rock, Hard Rock, Hair Metal, Heavy Metal, and Thrash Metal; user genre corrections are persisted and override the static catalog for future scans
- **Duplicate detection & resolution** — skips files already imported by both file path and metadata (title + artist); paginated duplicates view to compare and delete duplicate tracks
- **In-browser playback** — persistent audio player bar with play/pause, seek, volume, shuffle, and album art display; HTTP Range support for seeking in large files; auto-advances to next track
- **Crossfade & gapless playback** — toggle crossfade (3-second fade between tracks) for seamless listening; uses dual audio elements for smooth transitions
- **Queue management** — add tracks to an "Up Next" queue; queue drains before sequential/shuffle playback resumes; clear queue with one click; queue persists to MongoDB across sessions and devices (falls back to localStorage)
- **Keyboard shortcuts** — Space to play/pause, left/right arrows to seek, up/down for volume (active when player is visible and no input is focused)
- **Scheduled scans** — configure a cron expression and directory path per user; the system checks every 60 seconds and triggers scans when due

### Organization & Search
- **Full-text search** — MongoDB text index across title (3x weight), artist (2x), and album (1x) with relevance scoring; falls back to regex search when text index is unavailable
- **Playlist management** — create playlists, add/remove tracks, drag-drop reorder tracks, sort by title/artist/album/genre/rating/year, export as M3U or JSON; share playlists via read-only links
- **Library export** — download full library metadata as JSON or CSV for backup
- **Library statistics** — genre distribution, top 10 artists, decade distribution, and average rating via MongoDB aggregation
- **Soft delete / trash** — deleted tracks go to a 30-day trash instead of being permanently removed; restore or permanently delete from trash
- **Advanced filtering** — simultaneous artist, album, and genre dropdown filters alongside full-text search; all filters combine with AND logic
- **Browse views** — drill-down by artist, album, and genre; album grid view with cover art thumbnails (toggleable to list view); sortable columns including rating and decade
- **Bulk operations** — checkbox selection with select-all toggle; bulk delete and bulk add-to-playlist
- **User ratings** — 5-star rating widget on each track; sortable by rating
- **Inline genre editing** — reclassify any track tagged as "Other" directly from the library table
- **Virtual scrolling** — DOM virtualization for libraries with 10,000+ tracks; only visible rows are rendered

### Administration & Security
- **Multi-user** — per-user libraries with session-based (form login) and JWT authentication; 15-minute access tokens with 7-day refresh tokens; 30-minute idle session timeout (configurable via `SESSION_TIMEOUT`)
- **Password reset** — token-based password reset flow with 15-minute one-time-use tokens; anti-enumeration response (always returns 200)
- **Account lockout** — accounts lock after 5 consecutive failed login attempts; auto-unlocks after 15 minutes; configurable thresholds; all refresh tokens are revoked on lockout
- **Admin dashboard** — stats overview (users, files, playlists), paginated user management table with per-user file counts, delete user with cascade
- **Distributed rate limiting** — pluggable `RateLimitStore` interface with in-memory (default) and Redis-backed implementations; auto-detects Redis on the classpath for multi-instance deployments
- **Cover art storage quotas** — configurable per-user cover art quota (default 500 MB); quota checked during scan, extraction skipped when exceeded
- **Request body size limits** — Tomcat max post size, max header size, and multipart limits configured to prevent oversized payloads
- **Security** — CSRF protection (HttpOnly cookies with meta-tag delivery), rate limiting on auth and scan endpoints (with proxy-aware IP detection and `Retry-After` header), configurable CORS origins (explicit origins, not patterns), path traversal prevention on scan and stream endpoints, symlink detection, server-side input validation with typed DTOs, Content Security Policy headers (no `unsafe-inline` for scripts), Permissions-Policy header (disables geolocation, microphone, camera, payment, USB), password complexity requirements (enforced on both signup and password reset), JWT with `jti`/`iss` claims, token blacklisting on logout
- **RFC 7807 error responses** — all API error responses follow the Problem Details standard (`type`, `title`, `status`, `detail`) with backwards-compatible `error` property
- **Audit logging** — dedicated `AUDIT` logger routed to a separate `logs/audit.log` file with 90-day retention; structured MDC context tracks all security-sensitive operations: logins, signups, password resets, file deletions, genre changes, playlist modifications, and admin actions
- **API documentation** — interactive Swagger UI at `/swagger-ui.html` with OpenAPI 3.0 spec at `/api-docs`; JWT bearer auth support; disabled in production profile
- **API versioning** — all REST endpoints under `/api/v1/` for forward compatibility
- **Structured logging** — correlation IDs on every request (`X-Correlation-Id` header), MDC-based log pattern for request tracing
- **Health check** — `/actuator/health` endpoint for monitoring (includes MongoDB connectivity)

### UI & Accessibility
- **Jukebox theme** — retro dark UI with neon glow effects, chrome accents, wood grain textures, and "Righteous" display typography
- **Light mode** — full light theme with manual toggle (sun/moon button in navbar); respects `prefers-color-scheme` media query; preference saved to localStorage
- **Loading states** — spinner feedback on genre changes, rating updates, bulk delete, and add-to-playlist operations
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

### Docker

The fastest way to get started — requires only Docker:

```bash
docker compose up --build
```

This starts the app and MongoDB together. Set `MUSIC_DIR` to mount your music library:

```bash
MUSIC_DIR=/path/to/your/music docker compose up --build
```

To enable Redis-backed distributed rate limiting, uncomment the `redis` service in `docker-compose.yml`.

### Build a JAR (production)

```bash
mvn clean package -DskipTests
JWT_SECRET=$JWT_SECRET java -jar target/stellar-grooves-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod
```

### Run tests

```bash
mvn test                    # Unit tests only
mvn verify                  # Unit tests + JaCoCo coverage check + OWASP dependency check
mvn test -Dtest='*IT'       # Integration tests only (requires Docker for Testcontainers)
```

Unit tests generate a JaCoCo coverage report at `target/site/jacoco/index.html`.

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
| `stellar.grooves.jwtExpirationMs` | `JWT_EXPIRATION_MS` | `900000` (15 min) | Access token lifetime in milliseconds |
| `stellar.grooves.refreshTokenExpirationMs` | `REFRESH_TOKEN_EXPIRATION_MS` | `604800000` (7 days) | Refresh token lifetime in milliseconds |
| `stellar.grooves.swagger.enabled` | — | `true` (`false` in prod) | Enable/disable Swagger UI and OpenAPI endpoints |
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
| `server.servlet.session.timeout` | `SESSION_TIMEOUT` | `30m` | Idle session timeout |

### Scanner & Storage

| Property | Env var | Default | Description |
|----------|---------|---------|-------------|
| `stellar.grooves.scan.maxDepth` | — | `20` | Max directory depth for recursive scan |
| `stellar.grooves.scan.timeoutMinutes` | `SCAN_TIMEOUT_MINUTES` | `5` | Overall scan timeout |
| `stellar.grooves.scan.cooldownSeconds` | `SCAN_COOLDOWN_SECONDS` | `60` | Per-user cooldown between scans |
| `stellar.grooves.scan.perFileTimeoutSeconds` | `SCAN_PER_FILE_TIMEOUT_SECONDS` | `30` | Timeout for reading a single audio file |
| `stellar.grooves.coverArt.maxBytesPerUser` | `COVER_ART_QUOTA_BYTES` | `524288000` (500 MB) | Per-user cover art storage quota |
| `stellar.grooves.catalogPath` | — | *(bundled catalog.json)* | Path to a custom artist-genre catalog JSON file |

### Request Size Limits

| Property | Default | Description |
|----------|---------|-------------|
| `server.tomcat.max-http-form-post-size` | `2MB` | Maximum form POST size |
| `server.max-http-request-header-size` | `16KB` | Maximum request header size |
| `spring.servlet.multipart.max-file-size` | `2MB` | Maximum multipart file size |
| `spring.servlet.multipart.max-request-size` | `2MB` | Maximum multipart request size |

### Spring Profiles

| Profile | Activate with | Description |
|---------|--------------|-------------|
| `dev` | `--spring.profiles.active=dev` | Debug logging, Thymeleaf cache disabled, CORS allows `localhost:8080`, Swagger enabled |
| `prod` | `--spring.profiles.active=prod` | INFO logging, requires `CORS_ALLOWED_ORIGINS` env var, trusts proxy headers from configured IPs, Swagger disabled, audit + app logs written to files |

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

### Redis (Optional — Distributed Rate Limiting)

When `spring-boot-starter-data-redis` is on the classpath and a Redis instance is available, rate limiting automatically switches from in-memory to Redis-backed. This enables consistent rate limiting across multiple app instances.

```bash
# application.properties or env vars
spring.data.redis.host=localhost
spring.data.redis.port=6379
```

With Docker Compose, uncomment the `redis` service block.

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
4. Watch real-time progress as files are scanned, imported, and categorized.
5. Tracks appear in the library with extracted metadata, genre classification, and album art.
6. Click the play button on any row to start streaming. Use keyboard shortcuts (Space, arrows) to control playback.
7. Toggle crossfade with the sparkle button for smooth transitions between tracks.
8. Use the artist, album, and genre filter dropdowns or the search box to find tracks.
9. Rate tracks with the 5-star widget. Sort by any column including rating.
10. Create playlists from the sidebar, add tracks via the "+" button, and reorder with drag-drop.
11. Share a playlist via the share button — generates a read-only public link.
12. Select multiple tracks with checkboxes for bulk delete or bulk add-to-playlist.
13. Deleted tracks go to trash — restore them or empty trash to permanently remove.
14. Click the "Duplicated Songs" stat card to review and resolve duplicate tracks (paginated).
15. Export playlists as M3U or JSON, or export your entire library as JSON/CSV from the library view.
16. Set up a scheduled scan to auto-import new files on a cron schedule.
17. Toggle light/dark mode with the sun/moon button in the navbar.
18. Admin users can access the admin dashboard at **/admin** to manage users.
19. Browse the interactive API documentation at **/swagger-ui.html** (dev mode only).

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

Session-authenticated requests (form login) must include the CSRF token header for any mutating request (POST, PUT, PATCH, DELETE). The token is available from `<meta name="_csrf">` tags in Thymeleaf pages, or from the HttpOnly `XSRF-TOKEN` cookie for non-browser clients.

Auth endpoints are rate-limited to 10 requests per minute per IP by default. Scan endpoints have a per-user cooldown (default 60s). Rate-limited responses include a `Retry-After` header.

All error responses follow [RFC 7807 Problem Details](https://www.rfc-editor.org/rfc/rfc7807) format with `type`, `title`, `status`, `detail`, and an `error` property for backwards compatibility.

### Auth

| Method | Endpoint | Body | Description |
|--------|----------|------|-------------|
| `POST` | `/api/v1/auth/signup` | `{ "username", "email", "password" }` | Register a new user (password: min 8 chars, requires upper + lower + digit) |
| `POST` | `/api/v1/auth/signin` | `{ "username", "password" }` | Log in; returns `{ token, refreshToken, username }`; returns 401 for invalid credentials or locked accounts (uniform response to prevent enumeration) |
| `POST` | `/api/v1/auth/refresh` | `{ "refreshToken": "..." }` | Exchange a valid refresh token for a new access token + refresh token |
| `POST` | `/api/v1/auth/logout` | — | Blacklist the current JWT and delete refresh tokens (requires `Authorization: Bearer` header) |
| `POST` | `/api/v1/auth/password-reset/request` | `{ "email": "..." }` | Request a password reset token (always returns 200 to prevent enumeration; token logged at INFO) |
| `POST` | `/api/v1/auth/password-reset/execute` | `{ "token": "...", "newPassword": "..." }` | Reset password using a valid one-time token (15-min expiry); same complexity requirements as signup |

### Library

| Method | Endpoint | Body | Description |
|--------|----------|------|-------------|
| `GET` | `/api/v1/library/files` | — | List tracks (paginated); `?page=0&size=50`; optional `?genre=HARD_ROCK`; max 200/page |
| `GET` | `/api/v1/library/search` | — | Search tracks; `?q=metallica&page=0&size=50`; uses text index with relevance scoring, falls back to regex |
| `POST` | `/api/v1/library/scan` | `{ "path": "/absolute/path" }` | Scan a directory; returns `{ filesFound, skipped, errors, errorDetails }`; rate-limited per user |
| `GET` | `/api/v1/library/scan/progress` | — | SSE stream of scan progress events (`progress`, `complete`, `error`) |
| `GET` | `/api/v1/library/files/{id}/stream` | — | Stream audio (supports HTTP Range) |
| `GET` | `/api/v1/library/files/{id}/cover` | — | Get album cover art (30-day cache) |
| `PATCH` | `/api/v1/library/files/{id}/genre` | `{ "genre": "CLASSIC_ROCK" }` | Update a track's genre; records a correction for the artist so future scans use this genre |
| `PATCH` | `/api/v1/library/files/{id}/rating` | `{ "rating": 4 }` | Set track rating (0-5, 0 = unrated) |
| `POST` | `/api/v1/library/files/bulk-delete` | `{ "fileIds": ["id1", "id2"] }` | Soft-delete tracks (max 100); moves to trash |
| `GET` | `/api/v1/library/duplicates` | — | Get duplicate track groups (paginated); `?page=0&size=50` |
| `DELETE` | `/api/v1/library/files/{id}` | — | Soft-delete a single track (moves to trash) |
| `DELETE` | `/api/v1/library/files` | — | Clear the current user's entire library |
| `GET` | `/api/v1/library/trash` | — | List tracks in trash |
| `POST` | `/api/v1/library/trash/{id}/restore` | — | Restore a trashed track |
| `DELETE` | `/api/v1/library/trash/{id}` | — | Permanently delete a trashed track |
| `DELETE` | `/api/v1/library/trash` | — | Empty trash (permanently delete all trashed tracks) |
| `GET` | `/api/v1/library/stats` | — | Library statistics: genre/artist/decade distribution, average rating |
| `GET` | `/api/v1/library/export?format=json` | — | Export full library metadata as JSON (attachment download) |
| `GET` | `/api/v1/library/export?format=csv` | — | Export full library metadata as CSV (attachment download) |
| `GET` | `/api/v1/library/queue` | — | Get the user's persisted playback queue |
| `PUT` | `/api/v1/library/queue` | `{ "trackIds", "currentTrackId", "shuffle" }` | Save the playback queue |
| `DELETE` | `/api/v1/library/queue` | — | Clear the playback queue |
| `PUT` | `/api/v1/library/scan/schedule` | `{ "cronExpression", "path" }` | Set a scheduled scan (cron + directory path) |
| `GET` | `/api/v1/library/scan/schedule` | — | Get current scan schedule |
| `DELETE` | `/api/v1/library/scan/schedule` | — | Remove scheduled scan |

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
| `POST` | `/api/v1/playlists/{id}/share` | — | Generate a read-only share link; returns `{ shareToken, shareUrl }` |
| `DELETE` | `/api/v1/playlists/{id}/share` | — | Revoke the share link |

### Shared Playlists (public, no auth required)

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/v1/shared/playlists/{token}` | View a shared playlist's name and tracks |

### Admin (requires `ROLE_ADMIN`)

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/v1/admin/stats` | System stats: `{ totalUsers, totalFiles, totalPlaylists }` |
| `GET` | `/api/v1/admin/users` | List users with file counts; `?page=0&size=25` |
| `GET` | `/api/v1/admin/users/{id}` | Get a single user |
| `DELETE` | `/api/v1/admin/users/{id}` | Delete a user and all their data (files, playlists, cover art, queue) |

**Valid genre values:** `CLASSIC_ROCK`, `HARD_ROCK`, `HAIR_METAL`, `HEAVY_METAL`, `THRASH_METAL`, `OTHER`

---

## Project Structure

```
src/main/java/com/stellarideas/grooves/
├── StellarGroovesApplication.java       # Entry point
├── config/
│   ├── AdminBootstrap.java              # Auto-create admin on first startup
│   ├── InMemoryRateLimitStore.java      # In-memory rate limit counter (single instance)
│   ├── MongoIndexConfig.java            # Creates text search index on startup
│   ├── OpenApiConfig.java               # Swagger/OpenAPI configuration
│   ├── RateLimitConfig.java             # Auto-selects Redis or in-memory rate limiting
│   ├── RateLimitFilter.java             # Per-IP rate limiting (proxy-aware, Retry-After)
│   ├── RateLimitStore.java              # Rate limiting interface (pluggable backend)
│   ├── RedisRateLimitStore.java         # Redis-backed rate limiting (distributed)
│   ├── RequestCorrelationFilter.java    # MDC correlation ID for request tracing
│   └── WebConfig.java                   # Registers @CurrentUser argument resolver
├── controller/
│   ├── AuthController.java              # Signup/signin/logout/refresh/password-reset endpoints
│   ├── LibraryController.java           # Library CRUD + streaming + search + scan + queue + duplicates + cover art + trash + export + stats
│   ├── PlaylistController.java          # Playlist management + reorder + export + sharing
│   ├── SharedPlaylistController.java    # Public read-only shared playlist access
│   ├── AdminController.java             # Admin stats + user management
│   ├── ViewController.java              # Thymeleaf page routes (/, /login, /signup, /admin)
│   └── GlobalExceptionHandler.java      # RFC 7807 Problem Details error handling
├── model/
│   ├── User.java                        # User document (with scan schedule fields)
│   ├── MusicFile.java                   # Track document (with rating, hasCoverArt, soft delete)
│   ├── Playlist.java                    # Playlist document (with shareToken)
│   ├── PlaybackQueue.java               # Persisted playback queue (per user)
│   ├── CoverArt.java                    # Album cover art storage (binary, quota-managed)
│   ├── BlacklistedToken.java            # Revoked JWT tokens (TTL-indexed)
│   ├── RefreshToken.java                # Long-lived refresh tokens (TTL-indexed)
│   ├── PasswordResetToken.java          # One-time password reset tokens (TTL-indexed)
│   ├── Genre.java                       # Genre enum
│   ├── GenreCorrection.java             # User genre corrections (artist -> genre override)
│   └── Role.java                        # Role enum
├── dto/
│   ├── AddTrackRequest.java             # Add track to playlist
│   ├── BulkDeleteRequest.java           # Bulk delete tracks (validated, max 100)
│   ├── CreatePlaylistRequest.java       # Create playlist (validated)
│   ├── LoginRequest.java                # Login validation
│   ├── MusicFileDTO.java                # Track response (with rating + cover art flag)
│   ├── PasswordResetRequestDTO.java     # Password reset request (email)
│   ├── PasswordResetExecuteDTO.java     # Password reset execution (token + new password)
│   ├── PlaybackQueueDTO.java            # Playback queue state
│   ├── PlaylistDTO.java                 # Playlist response (with shareToken)
│   ├── RefreshTokenRequest.java         # Refresh token exchange
│   ├── ReorderTracksRequest.java        # Playlist track reorder
│   ├── ScanRequest.java                 # Directory scan request
│   ├── ScanResult.java                  # Scan result (saved, skipped, errors + details)
│   ├── ScanScheduleRequest.java         # Scheduled scan configuration
│   ├── SignupRequest.java               # Signup (with password policy)
│   ├── UpdateGenreRequest.java          # Genre update
│   └── UpdateRatingRequest.java         # Rating update (0-5)
├── repository/                          # Spring Data MongoDB repositories
│   ├── BlacklistedTokenRepository.java
│   ├── CoverArtRepository.java          # Includes cover art size aggregation for quotas
│   ├── GenreCorrectionRepository.java
│   ├── MusicFileRepository.java         # Includes regex search, soft-delete filtering
│   ├── MusicFileRepositoryCustom.java   # Custom aggregation interface (duplicates, text search, statistics)
│   ├── MusicFileRepositoryCustomImpl.java # MongoDB aggregation implementations
│   ├── PasswordResetTokenRepository.java
│   ├── PlaybackQueueRepository.java     # Playback queue persistence
│   ├── PlaylistRepository.java          # Includes findByShareToken
│   ├── RefreshTokenRepository.java
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
    ├── LibraryService.java              # Library business logic (CRUD, search, trash, export, stats)
    ├── LoginAttemptService.java         # Failed login tracking + account lockout
    ├── MessageHelper.java               # Shared i18n message resolution
    ├── MusicCatalogService.java         # Artist -> genre mapping (JSON catalog + user corrections)
    ├── MusicScannerService.java         # Directory scanning + batch import + per-file timeout + cover art extraction
    ├── PasswordResetMailService.java     # Password reset email delivery
    ├── PlaylistService.java             # Playlist business logic (CRUD, sharing, track management)
    ├── ScanProgressEmitter.java         # SSE emitter for real-time scan progress
    ├── ScanRateLimiter.java             # Per-user scan cooldown
    └── ScheduledScanService.java        # Cron-based automatic directory scanning

src/main/resources/
├── application.properties               # Shared configuration
├── application-dev.properties           # Dev profile (debug logging, no cache)
├── application-prod.properties          # Prod profile (strict CORS, proxy trust)
├── messages.properties                  # Externalized UI/error messages (i18n-ready)
├── logback-spring.xml                   # Logging config with correlation IDs
├── catalog.json                         # Artist-genre catalog (customizable)
├── static/css/main.css                  # Jukebox theme stylesheet (dark + light mode)
├── static/js/app.js                     # Frontend application (queue sync, crossfade, SSE progress)
├── static/js/admin.js                   # Admin dashboard logic
├── static/js/signup.js                  # Signup form handler
└── templates/
    ├── index.html                       # Main library dashboard (with crossfade button)
    ├── admin.html                       # Admin dashboard
    ├── login.html                       # Login page
    └── signup.html                      # Registration page

Dockerfile                               # Multi-stage build (JDK build + JRE runtime)
docker-compose.yml                       # App + MongoDB (optional Redis)
.dockerignore                            # Build context exclusions
```

**235 unit tests** across all layers. JaCoCo coverage reports generated at `target/site/jacoco/index.html` with a **60% minimum line coverage** threshold enforced at the `verify` phase.

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Framework | Spring Boot 3.4.4 |
| Persistence | Spring Data MongoDB |
| Caching (optional) | Spring Data Redis (for distributed rate limiting) |
| Security | Spring Security 6.4 + JJWT 0.12.6 |
| API docs | springdoc-openapi 2.8.6 (Swagger UI + OpenAPI 3.0) |
| Monitoring | Spring Boot Actuator |
| Templating | Thymeleaf + Bootstrap 5.3 |
| Audio metadata | JAudioTagger 3.0.1 |
| Containerization | Docker (multi-stage) + Docker Compose |
| Build | Maven 3 |
| Runtime | Java 17 |
| Testing | JUnit 5 + Mockito + JaCoCo (60% min) + Testcontainers (235 tests) |
| Code quality | Spotless (Google Java Format) + OWASP Dependency Check (build lifecycle) |

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
