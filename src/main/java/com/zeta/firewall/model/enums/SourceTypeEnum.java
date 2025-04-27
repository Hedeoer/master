package com.zeta.firewall.model.enums;

public enum SourceTypeEnum {
    ANY("any"),
    SPECIFIC("specific");

    private final String value;

    SourceTypeEnum(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static boolean isValid(String value) {
        if (value == null) {
            return false;
        }
        for (SourceTypeEnum type : values()) {
            if (type.getValue().equals(value)) {
                return true;
            }
        }
        return false;
    }
}