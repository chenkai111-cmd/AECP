package com.xiaou.web.organization;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaou.web.config.JacksonConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.context.annotation.Import;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@JsonTest
@Import(JacksonConfig.class)
class OrganizationMemberSerializationTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void totalRemainsNumericWithApplicationJacksonConfiguration() throws Exception {
        OrganizationMemberListResponse response = new OrganizationMemberListResponse(List.of(), 2);

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(response));

        assertThat(json.path("total").isIntegralNumber()).isTrue();
        assertThat(json.path("total").intValue()).isEqualTo(2);
    }
}
