package com.indiana.zwl.presentation.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WaterSourceLabelTest {

    @Test
    fun `pump labels cover real OSM values`() {
        assertEquals("Pompa ręczna", pumpLabel("manual"))
        assertEquals("Pompa ręczna", pumpLabel("hand_pump"))
        assertEquals("Pompa ręczna", pumpLabel("manual;yes"))
        assertEquals("Otwarty szyb (własna lina)", pumpLabel("no"))
        assertEquals("Pompa mechaniczna", pumpLabel("powered"))
        assertEquals("Pompa automatyczna", pumpLabel("automatic"))
        assertEquals("Pompa (rodzaj nieznany)", pumpLabel("yes"))
    }

    @Test
    fun `drinking water raw labels cover real OSM values`() {
        assertEquals("Woda pitna po przegotowaniu", drinkingWaterRawLabel("boil"))
        assertEquals("Woda uzdatniona", drinkingWaterRawLabel("treated"))
        assertEquals("Woda nieuzdatniona", drinkingWaterRawLabel("untreated"))
        assertEquals("Woda mineralna", drinkingWaterRawLabel("mineral"))
        assertEquals("Dostępna sezonowo", drinkingWaterRawLabel("seasonal"))
        assertEquals("Dostępna warunkowo", drinkingWaterRawLabel("conditional"))
        assertEquals("Źródło", drinkingWaterRawLabel("manantial"))
        assertEquals("Poidełko", drinkingWaterRawLabel("bubbler"))
        assertEquals("Fontanna", drinkingWaterRawLabel("fountain"))
        assertNull(drinkingWaterRawLabel("yes"))
        assertNull(drinkingWaterRawLabel("no"))
        assertNull(drinkingWaterRawLabel("unknown"))
        assertNull(drinkingWaterRawLabel("fixme"))
    }

    @Test
    fun `seasonal labels cover real OSM values`() {
        assertEquals("Sezonowo", seasonalLabel("yes"))
        assertEquals("Całorocznie", seasonalLabel("no"))
        assertEquals("Pora deszczowa", seasonalLabel("wet_season"))
        assertEquals("Lato", seasonalLabel("summer"))
        assertEquals("Wiosna, lato, jesień", seasonalLabel("spring;summer;autumn"))
    }

    @Test
    fun `fountain labels cover real OSM values`() {
        assertEquals("Poidełko", fountainLabel("bubbler"))
        assertEquals("Fontanna pitna", fountainLabel("drinking"))
        assertEquals("Napełnianie butelek", fountainLabel("bottle_refill"))
        assertEquals("Napełnianie butelek, kran", fountainLabel("bottle_refill;water_tap"))
    }

    @Test
    fun `bottle labels cover real OSM values`() {
        assertEquals("Można napełnić butelkę", bottleLabel("yes"))
        assertEquals("Wyznaczone do napełniania", bottleLabel("designated"))
        assertEquals("Ograniczona możliwość", bottleLabel("limited"))
        assertNull(bottleLabel("no"))
    }

    @Test
    fun `intermittent and fee labels cover real OSM values`() {
        assertEquals("Okresowo (może nie działać)", intermittentLabel("yes"))
        assertEquals("Stale", intermittentLabel("no"))
        assertEquals("Płatne", feeLabel("yes"))
        assertEquals("Bezpłatne", feeLabel("no"))
    }
}
