package com.cohortlens.core;

/** Academic term within a year. Spring sorts after Fall of the same academic year. */
public enum Term {
    FALL("Fall"),
    SPRING("Spring");

    private final String display;

    Term(String display) {
        this.display = display;
    }

    public String display() {
        return display;
    }

    /** Sortable integer for a year and term, e.g. 2022 Fall = 4044, 2022 Spring = 4045. */
    public static int index(int academicYear, Term term) {
        return academicYear * 2 + (term == SPRING ? 1 : 0);
    }

    public static String label(int termIndex) {
        int year = termIndex / 2;
        Term term = termIndex % 2 == 0 ? FALL : SPRING;
        return year + " " + term.display;
    }
}
