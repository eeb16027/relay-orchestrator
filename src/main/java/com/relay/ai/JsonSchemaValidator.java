package com.relay.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
public class JsonSchemaValidator {

    private final ObjectMapper objectMapper;
    private final JsonSchemaFactory schemaFactory;

    public JsonSchemaValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.schemaFactory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
    }

    /** Empty list = valid. Otherwise one message per violation. */
    public List<String> validate(String schemaJson, String dataJson) {
        try {
            JsonSchema schema = schemaFactory.getSchema(schemaJson);
            JsonNode data = objectMapper.readTree(dataJson);
            Set<ValidationMessage> violations = schema.validate(data);
            return violations.stream().map(ValidationMessage::getMessage).toList();
        } catch (Exception e) {
            return List.of("Could not validate AI output against schema: " + e.getMessage());
        }
    }
}