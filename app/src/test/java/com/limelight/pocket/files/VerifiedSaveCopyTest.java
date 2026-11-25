package com.limelight.pocket.files;

import static org.junit.Assert.*;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class VerifiedSaveCopyTest {
    @Rule public final TemporaryFolder directory = new TemporaryFolder();

    @Test public void verifiesUnicodeRoundTripAndKeepsOriginalAndExistingBackup() throws IOException {
        File cache = directory.newFolder("cache");
        String saveName = "حفظゲーム.sav";
        File save = directory.newFile(saveName);
        byte[] original = {0, 127, -1, 42};
        Files.write(save.toPath(), original);
        File folder = directory.getRoot();

        VerifiedSaveCopy.create(save, folder, cache);
        File first = new File(folder, "Backup - " + saveName + ".nexussave");
        byte[] firstBackup = Files.readAllBytes(first.toPath());
        Files.write(save.toPath(), new byte[] {2, 3});
        VerifiedSaveCopy.create(save, folder, cache);
        File second = new File(folder, "Backup - " + saveName + " (2).nexussave");
        assertTrue(VerifiedSaveCopy.isBackup(second));
        assertTrue(second.isFile());

        VerifiedSaveCopy.Preview preview = VerifiedSaveCopy.preview(first);
        assertEquals(saveName, preview.name);
        assertEquals(original.length, preview.size);
        assertTrue(preview.digest.matches("[a-f0-9]{64}"));
        VerifiedSaveCopy.restore(first, folder, cache, preview);
        assertArrayEquals(original, Files.readAllBytes(new File(folder, "حفظゲーム (2).sav").toPath()));
        assertArrayEquals(new byte[] {2, 3}, Files.readAllBytes(save.toPath()));
        assertArrayEquals(firstBackup, Files.readAllBytes(first.toPath()));
        assertArrayEquals(new String[0], cache.list());
    }

    @Test public void canRecoverEmptyFileIntoAnotherFolder() throws IOException {
        File save = directory.newFile("slot.srm");
        File cache = directory.newFolder("cache");
        File recovery = directory.newFolder("recovery");
        VerifiedSaveCopy.create(save, recovery, cache);
        File backup = new File(recovery, "Backup - slot.srm.nexussave");

        VerifiedSaveCopy.restore(backup, recovery, cache, VerifiedSaveCopy.preview(backup));
        File recovered = new File(recovery, "slot.srm");
        assertTrue(recovered.isFile());
        assertEquals(0L, recovered.length());
    }

    @Test public void damagedPayloadNameTruncationAndExtraBytesNeverPublish() throws IOException {
        File cache = directory.newFolder("cache");
        File recovery = directory.newFolder("recovery");
        File backup = directory.newFile("slot.nexussave");
        byte[] valid = envelope("slot.sav", new byte[] {3, 9, -22});
        Files.write(backup.toPath(), valid);
        VerifiedSaveCopy.Preview confirmed = VerifiedSaveCopy.preview(backup);
        byte[] editedPayload = valid.clone();
        editedPayload[valid.length - 33] ^= 1;
        byte[] editedName = valid.clone();
        editedName[12] = 'x';
        for (byte[] invalid : new byte[][] {
                editedPayload, editedName, Arrays.copyOf(valid, valid.length - 1), Arrays.copyOf(valid, valid.length + 1)
        }) {
            Files.write(backup.toPath(), invalid);
            assertThrows(IOException.class, () -> VerifiedSaveCopy.preview(backup));
            assertThrows(IOException.class, () -> VerifiedSaveCopy.restore(backup, recovery, cache, confirmed));
            assertArrayEquals(new String[0], recovery.list());
            assertArrayEquals(new String[0], cache.list());
        }
    }

    @Test public void validChecksumsDoNotAuthorizePathsOutsideTheRestoreFolder() throws IOException {
        File cache = directory.newFolder("cache");
        File recovery = directory.newFolder("recovery");
        File backup = directory.newFile("slot.nexussave");
        for (String name : new String[] {"../outside.sav", "/outside.sav", "..\\outside.sav", "..", "slot.sav:other", "slot\nsav"}) {
            Files.write(backup.toPath(), envelope(name, new byte[] {3}));
            assertThrows(IOException.class, () -> VerifiedSaveCopy.preview(backup));
        }
        assertFalse(new File(directory.getRoot(), "outside.sav").exists());
        assertArrayEquals(new String[0], recovery.list());
        assertArrayEquals(new String[0], cache.list());
    }

    @Test public void aValidReplacementRequiresASeparatePreview() throws IOException {
        File cache = directory.newFolder("cache");
        File recovery = directory.newFolder("recovery");
        File backup = directory.newFile("slot.nexussave");
        Files.write(backup.toPath(), envelope("slot.sav", new byte[] {4}));
        VerifiedSaveCopy.Preview first = VerifiedSaveCopy.preview(backup);
        assertArrayEquals(new String[0], recovery.list());
        Files.write(backup.toPath(), envelope("slot.sav", new byte[] {5}));
        assertThrows(IOException.class, () -> VerifiedSaveCopy.restore(backup, recovery, cache, first));
        assertArrayEquals(new String[0], recovery.list());
        assertArrayEquals(new String[0], cache.list());
        VerifiedSaveCopy.restore(backup, recovery, cache, VerifiedSaveCopy.preview(backup));
        assertArrayEquals(new byte[] {5}, Files.readAllBytes(new File(recovery, "slot.sav").toPath()));
    }

    @Test public void oversizedAndCancelledBackupsLeaveNoStagingOrDestination() throws IOException {
        File source = directory.newFile("slot.sav");
        File cache = directory.newFolder("cache");
        File destination = directory.newFolder("destination");
        try (RandomAccessFile file = new RandomAccessFile(source, "rw")) {
            file.setLength(256L * 1024 * 1024 + 1);
            assertThrows(IOException.class, () -> VerifiedSaveCopy.create(source, destination, cache));
            file.setLength(0);
        }
        Thread.currentThread().interrupt();
        try {
            assertThrows(InterruptedIOException.class, () -> VerifiedSaveCopy.create(source, destination, cache));
        } finally {
            Thread.interrupted();
        }
        assertTrue(source.isFile());
        assertArrayEquals(new String[0], destination.list());
        assertArrayEquals(new String[0], cache.list());
    }

    private static byte[] envelope(String name, byte[] payload) throws IOException {
        byte[] utf8 = name.getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            output.write("NEXUSSAVE\1".getBytes(StandardCharsets.US_ASCII));
            output.writeShort(utf8.length);
            output.write(utf8);
            output.writeLong(payload.length);
            output.write(payload);
            output.write(VerifiedFileTransfer.sha256().digest(bytes.toByteArray()));
        }
        return bytes.toByteArray();
    }
}
