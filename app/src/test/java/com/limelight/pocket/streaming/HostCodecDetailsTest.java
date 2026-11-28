package com.limelight.pocket.streaming;

import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.NvHTTP;
import com.limelight.nvstream.http.LimelightCryptoProvider;
import com.limelight.nvstream.http.PairingManager;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class HostCodecDetailsTest {
    private ComputerDetails parse(String mask) throws Exception {
        NvHTTP http = new NvHTTP(new ComputerDetails.AddressTuple("127.0.0.1", 47989), 47984,
                "fixture", null, mock(LimelightCryptoProvider.class));
        String report = "<root status_code=\"200\"><uniqueid>host</uniqueid><hostname>My PC</hostname>"
                + "<HttpsPort>47984</HttpsPort><ExternalPort>47989</ExternalPort><PairStatus>1</PairStatus>"
                + "<state>SUNSHINE_SERVER_FREE</state>"
                + (mask == null ? "" : "<ServerCodecModeSupport>" + mask + "</ServerCodecModeSupport>")
                + "</root>";
        return http.getComputerDetails(report);
    }

    private String codecLine(ComputerDetails details) {
        String prefix = "Host-advertised video codecs: ";
        for (String line : details.toString().split("\n")) {
            if (line.startsWith(prefix)) return line.substring(prefix.length());
        }
        throw new AssertionError("Missing advertised codec line in PC Details");
    }

    @Test public void detailsIdentifyMixedAndForcedHostOffersWithoutClaimingALocalDecoder() throws Exception {
        ComputerDetails mixed = parse(Long.toString(0x07800000L | 0x00010000 | 0x00000100 | 1));
        assertTrue(mixed.toString().contains("Host-advertised video codecs: H.264, HEVC, AV1, PyroWave (no Nexus decoder)"));
        assertEquals("PyroWave (no Nexus decoder)", codecLine(parse(Long.toString(0x07800000))));
        assertEquals("H.264, HEVC, AV1", codecLine(parse(Long.toString(0x00040000 | 0x00100000 | 0x00400000))));
    }

    @Test public void incompleteOrUnknownReportsPreserveReachabilityAndPairing() throws Exception {
        for (String mask : new String[]{null, "0", "-1", "garbled", "4294967296"}) {
            ComputerDetails details = parse(mask);
            assertEquals("None reported", codecLine(details));
            assertEquals(ComputerDetails.State.ONLINE, details.state);
            assertEquals(PairingManager.PairState.PAIRED, details.pairState);
        }
        assertEquals("H.264, Other flags 0x80000000", codecLine(parse("2147483649")));
    }

    @Test public void offlineAndNewPollsCannotPresentAnOldOfferAsCurrent() throws Exception {
        ComputerDetails copy = new ComputerDetails(parse("8388608"));
        assertTrue(codecLine(copy).contains("PyroWave"));
        copy.state = ComputerDetails.State.OFFLINE;
        assertFalse(codecLine(copy).contains("PyroWave"));
        copy.update(parse("1"));
        assertEquals("H.264", codecLine(copy));
        copy.update(parse(null));
        assertEquals("None reported", codecLine(copy));
    }
}
