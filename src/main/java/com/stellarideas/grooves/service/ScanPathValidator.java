package com.stellarideas.grooves.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Validates user-supplied filesystem scan paths against traversal, symlink, and
 * system-directory attacks. Used at both the controller boundary and the service
 * layer (defense-in-depth, since scheduled scans skip the controller).
 *
 * <p>If {@code stellar.grooves.scan.allowedBaseDirs} is configured, scan paths
 * must resolve under one of those directories. Otherwise, a platform-specific
 * blocklist of sensitive system paths applies.
 */
@Component
public class ScanPathValidator {

    private static final Logger logger = LoggerFactory.getLogger(ScanPathValidator.class);

    private static final Set<String> UNIX_BLOCKED_PATHS = Set.of(
            "/etc", "/root", "/var", "/usr", "/bin", "/sbin",
            "/boot", "/dev", "/proc", "/sys", "/run", "/lib",
            "/opt", "/srv", "/tmp"
    );

    private static final List<String> WINDOWS_FALLBACK_PATHS = List.of(
            "C:\\Windows",
            "C:\\Program Files",
            "C:\\Program Files (x86)",
            "C:\\ProgramData"
    );

    private final MessageHelper msg;
    private final List<Path> allowedBaseDirs;
    private final boolean windows;
    private final Set<String> blockedPaths;

    public ScanPathValidator(MessageHelper msg,
                             @Value("${stellar.grooves.scan.allowedBaseDirs:}") String allowedBaseDirsCsv) {
        this.msg = msg;
        this.allowedBaseDirs = parseAllowedBaseDirs(allowedBaseDirsCsv);
        this.windows = File.separatorChar == '\\';
        this.blockedPaths = windows ? discoverWindowsBlockedPaths() : UNIX_BLOCKED_PATHS;
        if (!this.allowedBaseDirs.isEmpty()) {
            logger.info("Scan allowlist enabled. Scan paths must resolve under: {}", this.allowedBaseDirs);
        } else if (windows) {
            logger.warn("Windows host detected with no SCAN_ALLOWED_BASE_DIRS configured — "
                    + "the system-path blocklist is best-effort. Setting an allowlist is strongly recommended. "
                    + "Blocked paths: {}", this.blockedPaths);
        }
    }

    private static List<Path> parseAllowedBaseDirs(String csv) {
        List<Path> result = new ArrayList<>();
        if (csv == null || csv.isBlank()) return result;
        for (String entry : csv.split(",")) {
            String trimmed = entry.trim();
            if (trimmed.isEmpty()) continue;
            try {
                Path p = Paths.get(trimmed).toRealPath();
                result.add(p);
            } catch (IOException e) {
                logger.warn("Ignoring configured allowed base dir '{}': {}", trimmed, e.getMessage());
            }
        }
        return List.copyOf(result);
    }

    private static Set<String> discoverWindowsBlockedPaths() {
        LinkedHashSet<String> paths = new LinkedHashSet<>();
        addIfPresent(paths, System.getenv("SystemRoot"));
        addIfPresent(paths, System.getenv("ProgramFiles"));
        addIfPresent(paths, System.getenv("ProgramFiles(x86)"));
        addIfPresent(paths, System.getenv("ProgramData"));
        // Fallbacks if the env vars weren't available for some reason
        for (String fallback : WINDOWS_FALLBACK_PATHS) {
            paths.add(fallback.toLowerCase(Locale.ROOT));
        }
        return Set.copyOf(paths);
    }

    private static void addIfPresent(Set<String> sink, String value) {
        if (value != null && !value.isBlank()) {
            sink.add(value.trim().toLowerCase(Locale.ROOT));
        }
    }

    /**
     * Validate the path and return its canonical, real filesystem location.
     * Throws {@link IllegalArgumentException} for any input the caller must reject (4xx).
     * Throws {@link IOException} only for unexpected filesystem I/O errors (5xx).
     */
    public Path validate(String path) throws IOException {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException(msg.msg("scan.path.empty"));
        }
        Path requested = Paths.get(path).normalize().toAbsolutePath();
        if (requested.toString().contains("..")) {
            throw new IllegalArgumentException(msg.msg("scan.path.traversal"));
        }
        if (!Files.exists(requested) || !Files.isDirectory(requested)) {
            throw new IllegalArgumentException(msg.msg("scan.path.notfound"));
        }
        Path canonical = requested.toRealPath();
        if (!canonical.equals(requested)) {
            throw new IllegalArgumentException(msg.msg("scan.path.symlink"));
        }
        if (!allowedBaseDirs.isEmpty()) {
            boolean allowed = allowedBaseDirs.stream().anyMatch(canonical::startsWith);
            if (!allowed) {
                throw new IllegalArgumentException(msg.msg("scan.path.not_allowed"));
            }
        } else if (isBlocked(canonical)) {
            throw new IllegalArgumentException(msg.msg("scan.path.not_allowed"));
        }
        return canonical;
    }

    private boolean isBlocked(Path canonical) {
        String canonicalStr = windows
                ? canonical.toString().toLowerCase(Locale.ROOT)
                : canonical.toString();
        String sep = String.valueOf(File.separatorChar);
        for (String blocked : blockedPaths) {
            if (canonicalStr.equals(blocked) || canonicalStr.startsWith(blocked + sep)) {
                return true;
            }
        }
        return false;
    }
}
