package com.careerops.backend.collector;

import com.careerops.backend.collector.alio.AlioJobListResponse;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;

final class AlioFixtureSupport {

    private AlioFixtureSupport() {
    }

    static AlioJobListResponse read(ObjectMapper objectMapper, String name) throws IOException {
        try (InputStream input = AlioFixtureSupport.class.getResourceAsStream("/fixtures/alio/" + name)) {
            if (input == null) {
                throw new IllegalArgumentException("Fixture not found: " + name);
            }
            return objectMapper.readValue(input, AlioJobListResponse.class);
        }
    }
}
