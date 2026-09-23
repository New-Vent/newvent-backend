package com.newvent.generation.domain;

public enum FailureType {
    VALIDATION_FAIL,
    TIMEOUT,
    LLM_ERROR,
    STOPPED,
    TRUNCATED
}
