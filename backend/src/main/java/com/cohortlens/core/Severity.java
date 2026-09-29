package com.cohortlens.core;

public enum Severity {
    /** The row is rejected and not stored. */
    ERROR,
    /** The row is kept, but something was missing or was corrected. */
    WARNING
}
