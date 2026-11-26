package com.limelight.nvstream.http;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class HostCodecDetailsTest {
    private ComputerDetails parse(String mask) throws Exception {
        NvHTTP http = new NvHTTP(new ComputerDetails.AddressTuple("127.0.0.1", 47989), 47984,
                "fixture", null, null);
        String report = "<root status_code=\"200\"><uniqueid>host</uniqueid><hostname>My PC</hostname>"
                + "<HttpsPort>47984</HttpsPort><ExternalPort>47989</ExternalPort><PairStatus>1</PairStatus>"
                + "<state>SUNSHINE_SERVER_FREE</state>"
                + (mask == null ? "" : "<ServerCodecModeSupport>" + mask + "</ServerCodecModeSupport>")
                + "</root>";
        return http.getComputerDetails(report);
    }

    @Test public void detailsIdentifyMixedAndForcedHostOffersWithoutClaimingALocalDecoder() throws Exception {
        ComputerDetails mixed = parse(Long.toString(0x07800000L | 0x00010000 | 0x00000100 | 1));
        assertTrue(mixed.toString().contains("Host-advertised video codecs: H.264, HEVC, AV1, PyroWave (no Nexus decoder)"));
        assertEquals("PyroWave (no Nexus decoder)", parse(Long.toString(0x07800000)).advertisedCodecs());
        assertEquals("H.264, HEVC, AV1", parse(Long.toString(0x00040000 | 0x00100000 | 0x00400000)).advertisedCodecs());
    }

    @Test public void incompleteOrUnknownReportsPreserveReachabilityAndPairing() throws Exception {
        for (String mask : new String[]{null, "0", "-1", "garbled", "4294967296"}) {
            ComputerDetails details = parse(mask);
            assertEquals("None reported", details.advertisedCodecs());
            assertEquals(ComputerDetails.State.ONLINE, details.state);
            assertEquals(PairingManager.PairState.PAIRED, details.pairState);
        }
        assertEquals("H.264, Other flags 0x80000000", parse("2147483649").advertisedCodecs());
    }

    @Test public void offlineAndNewPollsCannotPresentAnOldOfferAsCurrent() throws Exception {
        ComputerDetails copy = new ComputerDetails(parse("8388608"));
        assertTrue(copy.advertisedCodecs().contains("PyroWave"));
        copy.state = ComputerDetails.State.OFFLINE;
        assertFalse(copy.advertisedCodecs().contains("PyroWave"));
        copy.update(parse("1"));
        assertEquals("H.264", copy.advertisedCodecs());
        copy.update(parse(null));
        assertEquals("None reported", copy.advertisedCodecs());
    }
}
