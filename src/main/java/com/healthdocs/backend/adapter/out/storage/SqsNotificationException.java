package com.healthdocs.backend.adapter.out.storage;

public class SqsNotificationException extends RuntimeException {
    public enum Kind { CONFIGURATION, SERIALIZATION, TIMEOUT, TEMPORARY, PERMANENT }
    private final Kind kind;

    public SqsNotificationException(Kind kind, String message) { super(message); this.kind = kind; }
    public SqsNotificationException(Kind kind, String message, Throwable cause) { super(message, cause); this.kind = kind; }
    public Kind kind() { return kind; }
}
