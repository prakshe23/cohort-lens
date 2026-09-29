package com.cohortlens.core;

/** One line of the change history: who did what, and when. */
public record AuditEntry(long id, String at, String actor, String action, String entityType, String detail) {
}
