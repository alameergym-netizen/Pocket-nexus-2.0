package com.limelight.pocket.pclib;

import org.junit.Test;
import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;
import static org.junit.Assert.*;

public class HorizonLibraryReplyTest {
    private HorizonLibraryReply read(int limit,String... lines) throws IOException {
        Iterator<String> it=Arrays.asList(lines).iterator();
        return HorizonLibraryReply.read(() -> it.hasNext()?it.next():null,"G\t",limit,
                line -> line.length()>2);
    }
    @Test public void completeAndEmptyListsAccepted() throws Exception {
        assertEquals(Arrays.asList("G\ta","G\tb"),read(2,"G\ta","G\tb","END 2").lines);
        assertTrue(read(2,"END 0").lines.isEmpty());
    }
    @Test(expected=IOException.class) public void rejectsPartialEof() throws Exception {
        read(2,"G\ta");
    }
    @Test(expected=IOException.class) public void rejectsWrongCount() throws Exception {
        read(2,"G\ta","END 2");
    }
    @Test(expected=IOException.class) public void rejectsFalseTerminator() throws Exception {
        read(2,"END nonsense");
    }
    @Test(expected=IOException.class) public void rejectsExcessRows() throws Exception {
        read(1,"G\ta","G\tb","END 2");
    }
    @Test(expected=IOException.class) public void rejectsUnknownRecord() throws Exception {
        read(2,"P\ta","END 1");
    }
    @Test public void errorDiscardsPendingRows() throws Exception {
        HorizonLibraryReply reply=read(2,"G\ta","ERR auth");
        assertTrue(reply.lines.isEmpty());
        assertEquals("ERR auth",reply.error);
    }
}
