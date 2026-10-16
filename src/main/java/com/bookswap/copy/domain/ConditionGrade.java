package com.bookswap.copy.domain;

public enum ConditionGrade {
    NEW("New", "Unread, no marks, no wear"),
    LIKE_NEW("Like New", "Read once or twice, no visible wear or marks"),
    GOOD("Good", "Light wear on cover or spine, no marks inside, all pages intact"),
    ACCEPTABLE("Acceptable", "Clear wear, possibly light notes or highlights, all pages intact and readable"),
    POOR("Poor", "Heavy wear or damage, but complete and readable");

    private final String label;
    private final String criteria;

    ConditionGrade(String label, String criteria) {
        this.label = label;
        this.criteria = criteria;
    }

    public String label() { return label; }
    public String criteria() { return criteria; }
}
