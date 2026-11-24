package com.limelight.pocket;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

public class HorizonPcProtocolTest {
    private static ByteArrayInputStream input(String s) {
        return new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8));
    }
    @Test public void longResponseStaysOneFrame() throws Exception {
        char[] chars = new char[2048];
        Arrays.fill(chars, 'x');
        String longLine = new String(chars);
        ByteArrayInputStream stream = input(longLine + "\r\nEND\n");
        assertEquals(longLine, PcCompanion.readLine(stream));
        assertEquals("END", PcCompanion.readLine(stream));
        assertNull(PcCompanion.readLine(stream));
    }
    @Test(expected = IOException.class) public void rejectsTruncatedResponse() throws Exception {
        PcCompanion.readLine(input("OK 1 unfinished"));
    }
    @Test(expected = IOException.class) public void rejectsOversizeResponse() throws Exception {
        byte[] data = new byte[PcCompanion.MAX_RESPONSE_BYTES + 1];
        Arrays.fill(data, (byte) 'a');
        PcCompanion.readLine(new ByteArrayInputStream(data));
    }
    @Test public void encodesSingleCommand() throws Exception {
        assertArrayEquals("PING\n".getBytes(StandardCharsets.UTF_8), PcCompanion.encodeCommand("PING"));
    }
    @Test(expected = IOException.class) public void rejectsEmbeddedNewline() throws Exception {
        PcCompanion.encodeCommand("PING\nSTATUS");
    }
    @Test(expected = IOException.class) public void rejectsOversizeCommand() throws Exception {
        char[] data = new char[PcCompanion.MAX_COMMAND_BYTES + 1];
        Arrays.fill(data, 'a');
        PcCompanion.encodeCommand(new String(data));
    }
}
