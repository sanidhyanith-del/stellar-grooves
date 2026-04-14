package com.stellarideas.grooves.config;

/**
 * Centralized pagination constants used across library and admin endpoints.
 */
public final class PaginationDefaults {

    private PaginationDefaults() {}

    /** Default number of items per page when not specified by the client. */
    public static final int DEFAULT_PAGE_SIZE = 50;

    /** Maximum page size allowed for library endpoints. */
    public static final int MAX_PAGE_SIZE = 200;

    /** Maximum page size for admin user listings (heavier queries). */
    public static final int ADMIN_MAX_PAGE_SIZE = 100;

    /** Clamp page size to [1, maxSize]. */
    public static int clamp(int size, int maxSize) {
        return Math.min(Math.max(size, 1), maxSize);
    }
}
