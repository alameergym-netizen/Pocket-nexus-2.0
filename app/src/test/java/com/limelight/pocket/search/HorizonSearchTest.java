package com.limelight.pocket.search;

import org.junit.Test;
import static org.junit.Assert.*;

public class HorizonSearchTest {
    private static int rank(String name, String source, String query) {
        String q = SearchActivity.plain(query);
        return SearchActivity.rank(SearchActivity.plain(name), SearchActivity.plain(source), q, q.split(" "));
    }

    @Test public void titleWinsOverPlatform() {
        assertTrue(rank("Steam", "Android", "steam") > rank("Halo", "Steam", "steam"));
    }

    @Test public void mixedTitleAndPlatform() {
        assertTrue(rank("The Wind Waker HD", "Wii U", "wind waker wii u") > 0);
    }

    @Test public void unrelatedQueryDoesNotMatch() {
        assertEquals(0, rank("Halo", "Gaming PC", "zelda"));
    }

    @Test public void accentsAndCaseNormalize() {
        assertEquals(100, rank("Pokémon", "Nintendo DS", "POKEMON"));
    }

    @Test public void emptyQueryDoesNotRank() {
        assertEquals(0, rank("Halo", "PC", ""));
    }
}
