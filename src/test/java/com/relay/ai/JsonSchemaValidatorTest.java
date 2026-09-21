package com.relay.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JsonSchemaValidatorTest {

    private final JsonSchemaValidator validator = new JsonSchemaValidator(new ObjectMapper());

    private static final String SCHEMA = """
            {"type":"object","properties":{
               "category":{"type":"string"},"confidence":{"type":"number"}
             },"required":["category","confidence"]}
            """;

    @Test
    void validate_returnsEmptyForConformingData() {
        assertTrue(validator.validate(SCHEMA, "{\"category\":\"urgent\",\"confidence\":0.9}").isEmpty());
    }

    @Test
    void validate_returnsErrorsForMissingRequiredField() {
        assertFalse(validator.validate(SCHEMA, "{\"category\":\"urgent\"}").isEmpty());
    }

    @Test
    void validate_returnsErrorsForWrongType() {
        assertFalse(validator.validate(SCHEMA, "{\"category\":123,\"confidence\":0.9}").isEmpty());
    }
}