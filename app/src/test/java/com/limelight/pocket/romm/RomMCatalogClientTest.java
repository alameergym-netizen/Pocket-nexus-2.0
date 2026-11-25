package com.limelight.pocket.romm;

import android.app.Application;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.net.URI;
import java.net.URISyntaxException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, application = Application.class)
public class RomMCatalogClientTest {
    @Test public void fixedReadPathStaysInsideTheChosenServerAndEncodesSearch() throws Exception {
        URI base = RomMCatalogClient.serverUri("https://games.example:8443/romm");
        URI request = RomMCatalogClient.listUri(base, 24, "A & B/森");
        assertEquals("games.example", request.getHost());
        assertEquals(8443, request.getPort());
        assertEquals("/romm/api/roms", request.getPath());
        assertTrue(request.getRawQuery().contains("search_term=A+%26+B%2F%E6%A3%AE"));
        assertTrue(request.getRawQuery().contains("with_files=false"));
        for (String invalid : new String[]{"http://games.example", "https://user@games.example", "https://games.example/a/../b",
                "https://games.example/%2e%2e", "https://games.example/a?b=1", "https://games.example/a#token"}) {
            assertThrows(URISyntaxException.class, () -> RomMCatalogClient.serverUri(invalid));
        }
    }

    @Test public void usesStableIdsAndTheFallbackTitleReportedByRomM() throws Exception {
        JSONArray titles = new JSONArray();
        titles.put(new JSONObject().put("id", 17).put("name", JSONObject.NULL).put("fs_name_no_ext", "Sample ROM")
                .put("platform_display_name", "Handheld").put("summary", "Synopsis\u0000text"));
        RomMCatalogClient.Page page = RomMCatalogClient.parse(response(24, 25, titles), 24);
        assertEquals(17, page.titles.get(0).id);
        assertEquals("Sample ROM", page.titles.get(0).name);
        assertEquals("Handheld", page.titles.get(0).platform);
        assertEquals("Synopsis text", page.titles.get(0).summary);
        assertFalse(page.hasNext());
    }

    @Test public void rejectsAReplayedPageOrDuplicateTitleId() throws Exception {
        JSONObject title = new JSONObject().put("id", 4).put("name", "A title");
        JSONArray titles = new JSONArray().put(title).put(title);
        assertThrows(JSONException.class, () -> RomMCatalogClient.parse(response(0, 2, titles), 0));
        assertThrows(JSONException.class, () -> RomMCatalogClient.parse(response(0, 2, titles), 24));
    }

    @Test public void returningToAnEmptyCatalogDoesNotOfferEndlessPaging() throws Exception {
        JSONArray titles = new JSONArray();
        for (int i = 1; i <= RomMCatalogClient.PAGE_SIZE; i++) {
            titles.put(new JSONObject().put("id", i).put("name", "Title " + i));
        }
        assertTrue(RomMCatalogClient.parse(response(0, 25, titles), 0).hasNext());
        assertFalse(RomMCatalogClient.parse(response(24, 3, new JSONArray()), 24).hasNext());
    }

    private static JSONObject response(int offset, int total, JSONArray titles) throws JSONException {
        return new JSONObject().put("offset", offset).put("limit", RomMCatalogClient.PAGE_SIZE)
                .put("total", total).put("items", titles);
    }
}
