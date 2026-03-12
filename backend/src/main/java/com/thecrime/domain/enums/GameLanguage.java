package com.thecrime.domain.enums;

public enum GameLanguage {
    ARABIC("Arabic"),
    ENGLISH("English");
    
    private final String value;
    
    GameLanguage(String value) {
        this.value = value;
    }
    
    public String getValue() {
        return value;
    }
}
