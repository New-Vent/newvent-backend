package com.newvent.filtering;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.newvent.editor.dto.EditRequest;
import com.newvent.generation.dto.GenerateRequest;

import tools.jackson.databind.json.JsonMapper;

/** MVC uses Jackson 3; Java constructors alone do not verify the JSON contract. */
class FilteringRequestJsonTest {
    private final JsonMapper mapper = JsonMapper.builder().build();

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"requestText\":\"hello\"}",
            "{\"requestText\":\"hello\",\"privacyConfirmed\":null}",
            "{\"requestText\":\"hello\",\"privacyConfirmed\":false}"
    })
    void missingNullOrFalseNeverConfirms(String json) {
        assertFalse(mapper.readValue(json, GenerateRequest.class).privacyConfirmed());
        assertFalse(mapper.readValue(json, EditRequest.class).privacyConfirmed());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"requestText\":\"hello\",\"privacyConfirmed\":true}"})
    void explicitTrueIsPreserved(String json) {
        assertTrue(mapper.readValue(json, GenerateRequest.class).privacyConfirmed());
        assertTrue(mapper.readValue(json, EditRequest.class).privacyConfirmed());
    }
}
