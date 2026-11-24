package com.limelight.pocket.files;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class VerifiedFileTransferTest {
    @Rule public final TemporaryFolder workspace = new TemporaryFolder();

    @Test public void verifiesNestedCopyAndPreservesAnExistingDestination() throws IOException {
        File source = workspace.newFolder("save-backup");
        File payload = write(new File(source, "slot.sav"), "source data");
        File into = workspace.newFolder("usb");
        File existing = new File(into, "save-backup");
        assertTrue(existing.mkdir());
        write(new File(existing, "slot.sav"), "old data");

        assertEquals(VerifiedFileTransfer.Result.COMPLETE, VerifiedFileTransfer.transfer(source, into, false));
        assertEquals("source data", read(payload));
        assertEquals("old data", read(new File(existing, "slot.sav")));
        assertEquals("source data", read(new File(into, "save-backup (2)/slot.sav")));
        assertEquals(2, into.list().length);
    }

    @Test public void movesToAnUnusedFileWithoutOverwritingAConflict() throws IOException {
        File source = write(workspace.newFile("slot.sav"), "new");
        File into = workspace.newFolder("sd");
        File existing = write(new File(into, "slot.sav"), "old");

        assertEquals(VerifiedFileTransfer.Result.COMPLETE, VerifiedFileTransfer.transfer(source, into, true));
        assertFalse(source.exists());
        assertEquals("old", read(existing));
        assertEquals("new", read(new File(into, "slot (2).sav")));
    }

    @Test public void exhaustedConflictNamesDoNotOverwriteOriginalOrDestination() throws IOException {
        File source = write(workspace.newFile("slot.sav"), "new");
        File into = workspace.newFolder("full-name-set");
        write(new File(into, "slot.sav"), "old");
        for (int i = 2; i <= 999; i++) {
            assertTrue(new File(into, "slot (" + i + ").sav").createNewFile());
        }

        assertThrows(IOException.class, () -> VerifiedFileTransfer.transfer(source, into, true));
        assertEquals("new", read(source));
        assertEquals("old", read(new File(into, "slot.sav")));
        assertEquals(999, into.list().length);
    }

    @Test public void refusesToCopyADirectoryIntoItself() throws IOException {
        File source = workspace.newFolder("source");
        File child = new File(source, "child");
        assertTrue(child.mkdir());

        assertThrows(IOException.class, () -> VerifiedFileTransfer.transfer(source, child, false));
        assertEquals(0, child.list().length);
    }

    @Test public void nestedSymbolicLinkAbortsCopyWithoutPublishingAFragment() throws IOException {
        File source = workspace.newFolder("source");
        File outside = write(workspace.newFile("outside.sav"), "outside");
        File into = workspace.newFolder("destination");
        write(new File(source, "ordinary.sav"), "inside");
        try {
            Files.createSymbolicLink(new File(source, "linked.sav").toPath(), outside.toPath());
        } catch (IOException | UnsupportedOperationException e) {
            Assume.assumeNoException(e);
        }

        assertThrows(IOException.class, () -> VerifiedFileTransfer.transfer(source, into, false));
        assertEquals("outside", read(outside));
        assertEquals("inside", read(new File(source, "ordinary.sav")));
        assertEquals(0, into.list().length);
    }

    @Test public void cancelledMoveRetainsSource() throws IOException {
        File source = write(workspace.newFile("slot.sav"), "source data");
        File into = workspace.newFolder("destination");

        Thread.currentThread().interrupt();
        try {
            assertThrows(InterruptedIOException.class, () -> VerifiedFileTransfer.transfer(source, into, true));
        } finally {
            Thread.interrupted();
        }
        assertEquals("source data", read(source));
        assertEquals(0, into.list().length);
    }

    private static File write(File file, String text) throws IOException {
        Files.write(file.toPath(), text.getBytes(StandardCharsets.UTF_8));
        return file;
    }

    private static String read(File file) throws IOException {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }
}
