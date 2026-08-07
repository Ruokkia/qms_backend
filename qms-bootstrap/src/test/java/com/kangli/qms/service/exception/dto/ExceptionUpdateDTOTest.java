package com.kangli.qms.service.exception.dto;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;

class ExceptionUpdateDTOTest {

    @Test
    void doesNotExposeWorkflowStateFieldsForGenericUpdate() {
        Set<String> writableProperties = Arrays.stream(ExceptionUpdateDTO.class.getDeclaredFields())
                .map(field -> field.getName())
                .collect(Collectors.toSet());

        assertFalse(writableProperties.contains("capaStatus"));
        assertFalse(writableProperties.contains("processType"));
    }
}
