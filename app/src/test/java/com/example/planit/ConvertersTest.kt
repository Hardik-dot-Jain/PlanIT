package com.example.planit

import com.example.planit.data.local.converter.Converters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class ConvertersTest {

    private val converters = Converters()

    @Test
    fun localDateConversion_roundTrip_isAccurate() {
        val originalDate = LocalDate.of(2026, 10, 8)
        val serialized = converters.fromLocalDate(originalDate)
        assertEquals("2026-10-08", serialized)

        val deserialized = converters.toLocalDate(serialized)
        assertEquals(originalDate, deserialized)
    }

    @Test
    fun localDateConversion_nullHandling() {
        assertNull(converters.fromLocalDate(null))
        assertNull(converters.toLocalDate(null))
    }

    @Test
    fun localTimeConversion_roundTrip_isAccurate() {
        val originalTime = LocalTime.of(14, 30, 45)
        val serialized = converters.fromLocalTime(originalTime)
        assertEquals("14:30:45", serialized)

        val deserialized = converters.toLocalTime(serialized)
        assertEquals(originalTime, deserialized)
    }

    @Test
    fun localTimeConversion_nullHandling() {
        assertNull(converters.fromLocalTime(null))
        assertNull(converters.toLocalTime(null))
    }
}
