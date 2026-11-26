package com.limelight.pocket.files;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.Arrays;

import static org.junit.Assert.*;

public class VerifiedSaveFolderTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();

    @Test public void nestedFilesAndEmptyDirectoriesRecoverSeparatelyWithOriginalBytes() throws Exception {
        File source = temp.newFolder("Game"), vault = temp.newFolder("vault"), cache = temp.newFolder("cache");
        File nested = new File(source, "states"); assertTrue(nested.mkdir());
        assertTrue(new File(source, "empty").mkdir());
        Files.write(new File(source, "save.dat").toPath(), new byte[] {1, 2, 3});
        Files.write(new File(nested, "slot.sav").toPath(), new byte[] {4, 5});
        VerifiedSaveFolder.create(source, vault, cache);
        File archive = only(vault);
        VerifiedSaveFolder.Preview preview = VerifiedSaveFolder.preview(archive);
        assertEquals("Game", preview.name);
        assertEquals(2, preview.files); assertEquals(2, preview.directories); assertEquals(5, preview.size);
        assertEquals(64, preview.digest.length()); assertTrue(preview.createdUtc > 0);
        Files.write(new File(source, "save.dat").toPath(), new byte[] {9});
        VerifiedSaveFolder.restore(archive, temp.getRoot(), cache, preview);
        File recovered = new File(temp.getRoot(), "Game (2)");
        assertArrayEquals(new byte[] {9}, Files.readAllBytes(new File(source, "save.dat").toPath()));
        assertArrayEquals(new byte[] {1, 2, 3}, Files.readAllBytes(new File(recovered, "save.dat").toPath()));
        assertArrayEquals(new byte[] {4, 5}, Files.readAllBytes(new File(recovered, "states/slot.sav").toPath()));
        assertTrue(new File(recovered, "empty").isDirectory());
        assertEquals(0, cache.list().length);
    }

    @Test public void newSnapshotsRetainEarlierArchivesAndEmptyFoldersAreSupported() throws Exception {
        File source = temp.newFolder("Empty"), vault = temp.newFolder("vault"), cache = temp.newFolder("cache");
        VerifiedSaveFolder.create(source, vault, cache);
        byte[] first = Files.readAllBytes(only(vault).toPath());
        VerifiedSaveFolder.create(source, vault, cache);
        assertEquals(2, vault.list().length);
        assertTrue(Arrays.equals(first, Files.readAllBytes(new File(vault, "Backup - Empty.nexusfolder").toPath())));
        File recovered = temp.newFolder("recovered");
        File archive = new File(vault, "Backup - Empty.nexusfolder");
        VerifiedSaveFolder.restore(archive, recovered, cache, VerifiedSaveFolder.preview(archive));
        assertTrue(new File(recovered, "Empty").isDirectory());
    }

    @Test public void previewConfirmationCannotRestoreAnotherOtherwiseValidArchive() throws Exception {
        File source = temp.newFolder("Game"), vault = temp.newFolder("vault"), cache = temp.newFolder("cache");
        File live = new File(source, "save"); Files.write(live.toPath(), new byte[] {1});
        VerifiedSaveFolder.create(source, vault, cache);
        File archive = only(vault);
        VerifiedSaveFolder.Preview confirmed = VerifiedSaveFolder.preview(archive);
        Files.write(live.toPath(), new byte[] {2});
        VerifiedSaveFolder.create(source, vault, cache);
        File other = new File(vault, "Backup - Game (2).nexusfolder");
        Files.write(archive.toPath(), Files.readAllBytes(other.toPath()));
        File restored = temp.newFolder("restored");
        rejects(() -> VerifiedSaveFolder.restore(archive, restored, cache, confirmed));
        assertEquals(0, restored.list().length); assertEquals(0, cache.list().length);
        assertArrayEquals(new byte[] {2}, Files.readAllBytes(live.toPath()));
    }

    @Test public void traversalAbsolutePathsMissingParentsAndCaseCollisionsAreRejected() throws Exception {
        for (String path : new String[] {"../escape", "/absolute", "a/../../escape", "C:/escape", "a\\escape", "absent/file"}) {
            File archive = crafted("Game", new Item(path, false, new byte[] {1}));
            rejects(() -> VerifiedSaveFolder.preview(archive));
        }
        rejects(() -> VerifiedSaveFolder.preview(crafted("..", new Item("save", false, new byte[0]))));
        rejects(() -> VerifiedSaveFolder.preview(crafted("Game", new Item("save", false, new byte[0]),
                new Item("SAVE", false, new byte[0]))));
        rejects(() -> VerifiedSaveFolder.preview(crafted("Game", new Item("folder", false, new byte[0]),
                new Item("folder/child", false, new byte[0]))));
    }

    @Test public void corruptPayloadDigestTrailerAndTruncationNeverPublishARecovery() throws Exception {
        File cache = temp.newFolder("cache"), into = temp.newFolder("into");
        File archive = crafted("Game", new Item("save", false, new byte[] {1, 2, 3}));
        VerifiedSaveFolder.Preview preview = VerifiedSaveFolder.preview(archive);
        byte[] valid = Files.readAllBytes(archive.toPath());
        for (int index : new int[] {valid.length - 66, valid.length - 33, valid.length - 1}) {
            byte[] bad = valid.clone(); bad[index] ^= 4;
            Files.write(archive.toPath(), bad);
            rejects(() -> VerifiedSaveFolder.restore(archive, into, cache, preview));
            assertEquals(0, into.list().length); assertEquals(0, cache.list().length);
        }
        Files.write(archive.toPath(), Arrays.copyOf(valid, valid.length - 1));
        rejects(() -> VerifiedSaveFolder.preview(archive));
        byte[] trailing = Arrays.copyOf(valid, valid.length + 1);
        Files.write(archive.toPath(), trailing);
        rejects(() -> VerifiedSaveFolder.preview(archive));
    }

    @Test public void perFileDigestIsCheckedEvenIfTheOuterDigestWasRecomputed() throws Exception {
        File archive = crafted("Game", new Item("save", false, new byte[] {1, 2, 3}));
        byte[] data = Files.readAllBytes(archive.toPath());
        data[data.length - 33] ^= 1;
        byte[] recomputed = MessageDigest.getInstance("SHA-256").digest(Arrays.copyOf(data, data.length - 32));
        System.arraycopy(recomputed, 0, data, data.length - 32, 32);
        Files.write(archive.toPath(), data);
        rejects(() -> VerifiedSaveFolder.preview(archive));
    }

    @Test public void symbolicLinksAndAnInterruptedOperationLeaveSourceAndVaultAlone() throws Exception {
        File source = temp.newFolder("Game"), vault = temp.newFolder("vault"), cache = temp.newFolder("cache");
        File outside = temp.newFile("outside"); Files.write(outside.toPath(), new byte[] {8});
        Files.createSymbolicLink(new File(source, "link").toPath(), outside.toPath());
        rejects(() -> VerifiedSaveFolder.create(source, vault, cache));
        assertEquals(0, vault.list().length); assertEquals(0, cache.list().length);
        Files.delete(new File(source, "link").toPath());
        Thread.currentThread().interrupt();
        try { rejects(() -> VerifiedSaveFolder.create(source, vault, cache)); }
        finally { assertTrue(Thread.interrupted()); }
        assertArrayEquals(new byte[] {8}, Files.readAllBytes(outside.toPath()));
        assertTrue(source.isDirectory()); assertEquals(0, vault.list().length);
    }

    @Test public void oversizedDeclarationsAreRejectedBeforeReadingPayload() throws Exception {
        File archive = crafted("Game", new Item("save", false, new byte[0]));
        byte[] data = Files.readAllBytes(archive.toPath());
        // magic(12), name length/name(6), time(8), count(4), type(1), path length/path(6).
        java.nio.ByteBuffer.wrap(data, 37, 8).putLong(256L * 1024 * 1024 + 1);
        Files.write(archive.toPath(), data);
        rejects(() -> VerifiedSaveFolder.preview(archive));
    }

    private static final class Item {
        final String path; final boolean directory; final byte[] data;
        Item(String path, boolean directory, byte[] data) { this.path = path; this.directory = directory; this.data = data; }
    }

    private File crafted(String root, Item... items) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.write("NEXUSFOLDER\1".getBytes(StandardCharsets.US_ASCII));
        text(out, root); out.writeLong(10); out.writeInt(items.length);
        for (Item item : items) {
            out.writeByte(item.directory ? 1 : 0); text(out, item.path); out.writeLong(item.data.length);
            if (!item.directory) { out.write(item.data); out.write(MessageDigest.getInstance("SHA-256").digest(item.data)); }
        }
        byte[] body = bytes.toByteArray();
        out.write(MessageDigest.getInstance("SHA-256").digest(body)); out.flush();
        File archive = temp.newFile(); Files.write(archive.toPath(), bytes.toByteArray()); return archive;
    }

    private static void text(DataOutputStream out, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8); out.writeShort(bytes.length); out.write(bytes);
    }

    private static File only(File directory) { File[] files = directory.listFiles(); assertEquals(1, files.length); return files[0]; }
    private interface Operation { void run() throws Exception; }
    private static void rejects(Operation operation) throws Exception {
        try { operation.run(); fail("Expected an invalid or changed folder backup to be rejected"); }
        catch (IOException expected) { }
    }
}
