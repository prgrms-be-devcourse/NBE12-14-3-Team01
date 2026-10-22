package com.merge.backend

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class KotlinEnvironmentTest {

    @Test
    fun kotlinEnvironmentWorks() {
        assertEquals("Kotlin OK", KotlinEnvironmentCheck.message())
    }
}
