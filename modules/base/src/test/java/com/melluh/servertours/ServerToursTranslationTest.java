package com.melluh.servertours;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ServerToursTranslationTest {
    @Test
    void placeholderValuesTreatDollarAndBackslashLiterally() {
        assertEquals("draft $1 C:\\camera", ServerTours.applyTranslationPlaceholders(
                "draft {} {}", "$1", "C:\\camera"));
    }
}
