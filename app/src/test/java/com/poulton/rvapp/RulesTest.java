package com.poulton.rvapp;

import org.junit.Test;
import java.time.LocalDate;
import static org.junit.Assert.*;

public class RulesTest {
    @Test public void validatesCalendarDatesAndFiniteNonnegativeAmounts() {
        assertNull(Rules.validate("Date", "2028-02-29"));
        assertNotNull(Rules.validate("Date", "2026-02-29"));
        assertNotNull(Rules.validate("Cost GBP", "-1"));
        assertNotNull(Rules.validate("Litres", "NaN"));
        assertNotNull(Rules.validate("Odometer miles", "Infinity"));
        assertNull(Rules.validate("Cost GBP", "12.50"));
        assertNull(Rules.validate("Notes", "No number required"));
    }
    @Test public void eitherDateOrMileageCanMakeServiceDue() {
        LocalDate today = LocalDate.of(2026,10,20);
        assertTrue(Rules.due("2026-10-20", "", 100, today));
        assertTrue(Rules.due("2027-01-01", "100", 100, today));
        assertFalse(Rules.due("2027-01-01", "101", 100, today));
        assertFalse(Rules.due("", "", 100, today));
    }
}
