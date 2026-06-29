package com.unfurl.foundry.substrate.serialization;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.io.InputStream;

/** JSON/YAML codec for public foundry-substrate model records. */
/**
 * class for the Foundry AI substrate surface; documents the FoundrySubstrateCodec contract used by DCP ports, adapters, or domain code.
 * Inputs and outputs remain defined by the declared fields and methods, with validation kept inside this type where present.
 */
public final class FoundrySubstrateCodec {
    private final ObjectMapper jsonMapper;
    private final ObjectMapper yamlMapper;

/**
 * Constructs FoundrySubstrateCodec with the dependencies or value fields required by this component and preserves constructor validation invariants.
 */
    public FoundrySubstrateCodec() {
        this.jsonMapper = mapper(new ObjectMapper());
        this.yamlMapper = mapper(new ObjectMapper(new YAMLFactory()));
    }

    public <T> T fromJson(String json, Class<T> type) {
        try {
            return jsonMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid foundry-substrate JSON", e);
        }
    }

    public <T> T fromYaml(String yaml, Class<T> type) {
        try {
            return yamlMapper.readValue(yaml, type);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid foundry-substrate YAML", e);
        }
    }

    public <T> T fromYaml(InputStream inputStream, Class<T> type) {
        try {
            return yamlMapper.readValue(inputStream, type);
        } catch (IOException e) {
            throw new IllegalArgumentException("Invalid foundry-substrate YAML", e);
        }
    }

/**
 * Implements the toJson helper for this component, preserving the surrounding input, output, and edge-case contract.
 */
    public String toJson(Object value) {
        try {
            return jsonMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Unable to serialize foundry-substrate JSON", e);
        }
    }

/**
 * Implements the toYaml helper for this component, preserving the surrounding input, output, and edge-case contract.
 */
    public String toYaml(Object value) {
        try {
            return yamlMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Unable to serialize foundry-substrate YAML", e);
        }
    }

/**
 * Implements the mapper helper for this component, preserving the surrounding input, output, and edge-case contract.
 */
    private static ObjectMapper mapper(ObjectMapper mapper) {
        return mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }
}
