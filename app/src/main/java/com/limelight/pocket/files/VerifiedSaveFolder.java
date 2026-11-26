package com.limelight.pocket.files;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.DigestInputStream;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Portable folder copies. No live-save overwrite and no assumption of emulator-state compatibility. */
final class VerifiedSaveFolder {
    static final String EXTENSION = ".nexusfolder";
    private static final byte[] MAGIC = "NEXUSFOLDER\1".getBytes(StandardCharsets.US_ASCII);
    private static final long MAX_PAYLOAD = 256L * 1024 * 1024;
    private static final int MAX_ENTRIES = 2048, MAX_DEPTH = 32, MAX_PATH = 2048, MAX_NAME = 220;
    private static final int DIGEST_BYTES = 32, BUFFER_BYTES = 64 * 1024;
    private static final long MAX_ARCHIVE = MAX_PAYLOAD + (long) MAX_ENTRIES * (MAX_PATH + 43) + 512;

    static final class Preview {
        final String name, digest;
        final int files, directories;
        final long size, createdUtc;

        private Preview(String name, String digest, int files, int directories, long size, long createdUtc) {
            this.name = name; this.digest = digest; this.files = files; this.directories = directories;
            this.size = size; this.createdUtc = createdUtc;
        }
    }

    private VerifiedSaveFolder() {}

    static boolean isBackup(File file) {
        return file.getName().toLowerCase(Locale.ROOT).endsWith(EXTENSION);
    }

    static void create(File source, File destination, File cache) throws IOException {
        VerifiedFileTransfer.requireOrdinary(source);
        File root = source.getCanonicalFile();
        File privateCache = cache.getCanonicalFile();
        if (!root.isDirectory() || privateCache.equals(root)
                || privateCache.getPath().startsWith(root.getPath() + File.separator)) {
            throw new IOException("Choose a save folder outside the app's backup cache");
        }
        checkedName(root.getName());
        List<Entry> entries = new ArrayList<>();
        long[] size = {0};
        collect(root, "", 0, entries, size);
        long needed = MAGIC.length + checkedName(root.getName()).length + 46;
        for (int i = 1; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            needed += 11 + checkedPath(entry.path).length + entry.size + (entry.directory ? 0 : DIGEST_BYTES);
        }
        long available = privateCache.getUsableSpace();
        if (available > 0 && available < needed) throw new IOException("Not enough backup cache space");
        File stage = stage(privateCache);
        try {
            File archive = new File(stage, "Backup - " + root.getName() + EXTENSION);
            write(archive, root.getName(), entries);
            for (Entry entry : entries) entry.requireUnchanged();
            preview(archive);
            VerifiedFileTransfer.transfer(archive, destination, false);
        } finally { clean(stage); }
    }

    static Preview preview(File archive) throws IOException { return read(archive, null); }

    static void restore(File archive, File destination, File cache, Preview confirmed) throws IOException {
        if (confirmed == null) throw new IOException("Preview the folder backup first");
        File stage = stage(cache);
        try {
            Preview actual = read(archive, stage);
            if (!actual.digest.equals(confirmed.digest)) throw new IOException("Folder backup changed after preview");
            VerifiedFileTransfer.transfer(new File(stage, actual.name), destination, false);
        } finally { clean(stage); }
    }

    private static final class Entry {
        final File source;
        final String path;
        final boolean directory;
        final long size, modified;
        String[] children;

        Entry(File source, String path) {
            this.source = source; this.path = path; directory = source.isDirectory();
            size = directory ? 0 : source.length(); modified = source.lastModified();
        }

        void requireUnchanged() throws IOException {
            VerifiedFileTransfer.requireOrdinary(source);
            if (source.isDirectory() != directory || source.lastModified() != modified
                    || (!directory && source.length() != size)) throw new IOException("Save folder changed during backup");
            if (directory) {
                String[] current = source.list();
                if (current == null) throw new IOException("Save folder is no longer readable");
                Arrays.sort(current);
                if (!Arrays.equals(children, current)) throw new IOException("Save folder contents changed during backup");
            }
        }
    }

    private static void collect(File source, String relative, int depth, List<Entry> entries, long[] size) throws IOException {
        VerifiedFileTransfer.checkInterrupted();
        VerifiedFileTransfer.requireOrdinary(source);
        if (depth > MAX_DEPTH || entries.size() > MAX_ENTRIES) throw new IOException("Save folder has too many items or levels");
        if (!relative.isEmpty()) checkedPath(relative);
        Entry entry = new Entry(source, relative);
        entries.add(entry);
        if (!entry.directory) {
            if (entry.size > MAX_PAYLOAD - size[0]) throw new IOException("Save folder exceeds 256 MiB");
            size[0] += entry.size;
            return;
        }
        File[] children = source.listFiles();
        if (children == null) throw new IOException("Save folder is not readable");
        if (children.length > MAX_ENTRIES - entries.size() + 1) throw new IOException("Save folder has too many items");
        Arrays.sort(children, (left, right) -> left.getName().compareTo(right.getName()));
        entry.children = new String[children.length];
        for (int i = 0; i < children.length; i++) {
            File child = children[i];
            checkedName(child.getName());
            entry.children[i] = child.getName();
            collect(child, relative.isEmpty() ? child.getName() : relative + "/" + child.getName(), depth + 1, entries, size);
        }
    }

    private static void write(File archive, String rootName, List<Entry> entries) throws IOException {
        MessageDigest digest = VerifiedFileTransfer.sha256();
        byte[] buffer = new byte[BUFFER_BYTES];
        try (FileOutputStream file = new FileOutputStream(archive);
             DigestOutputStream checked = new DigestOutputStream(new BufferedOutputStream(file), digest);
             DataOutputStream output = new DataOutputStream(checked)) {
            // v1, big-endian: magic; root UTF-8 name; UTC milliseconds; item count;
            // items (directory byte, UTF-8 relative path, payload size, payload and file SHA-256);
            // then SHA-256 of all preceding bytes. Directories precede their descendants.
            output.write(MAGIC);
            writeText(output, checkedName(rootName));
            output.writeLong(System.currentTimeMillis());
            output.writeInt(entries.size() - 1);
            for (int i = 1; i < entries.size(); i++) {
                Entry entry = entries.get(i);
                entry.requireUnchanged();
                output.writeByte(entry.directory ? 1 : 0);
                writeText(output, checkedPath(entry.path));
                output.writeLong(entry.size);
                if (!entry.directory) {
                    byte[] copied;
                    try (InputStream input = new FileInputStream(entry.source)) {
                        copied = payload(input, output, entry.size, buffer);
                        if (input.read() != -1) throw new IOException("Save file grew during backup");
                    }
                    if (!MessageDigest.isEqual(copied, VerifiedFileTransfer.readDigest(entry.source, buffer))) {
                        throw new IOException("Save file changed during backup");
                    }
                    output.write(copied);
                }
            }
            checked.on(false);
            output.write(digest.digest());
            output.flush();
            file.getFD().sync();
        }
    }

    private static Preview read(File archive, File stage) throws IOException {
        VerifiedFileTransfer.requireOrdinary(archive);
        if (!archive.isFile() || archive.length() < MAGIC.length + 47 || archive.length() > MAX_ARCHIVE) {
            throw new IOException("Invalid folder backup size");
        }
        MessageDigest digest = VerifiedFileTransfer.sha256();
        byte[] buffer = new byte[BUFFER_BYTES];
        try (DigestInputStream checked = new DigestInputStream(new BufferedInputStream(new FileInputStream(archive)), digest);
             DataInputStream input = new DataInputStream(checked)) {
            byte[] magic = new byte[MAGIC.length];
            input.readFully(magic);
            if (!Arrays.equals(magic, MAGIC)) throw new IOException("Unrecognized folder backup");
            String rootName = readText(input, true);
            long created = input.readLong();
            int count = input.readInt();
            if (created < 0 || count < 0 || count > MAX_ENTRIES) throw new IOException("Invalid folder backup manifest");
            File root = stage == null ? null : new File(stage, rootName);
            if (root != null && !root.mkdir()) throw new IOException("Could not stage a restored folder");
            Set<String> names = new HashSet<>(), directories = new HashSet<>();
            directories.add("");
            long total = 0;
            int files = 0;
            for (int i = 0; i < count; i++) {
                VerifiedFileTransfer.checkInterrupted();
                int type = input.readUnsignedByte();
                String path = readText(input, false);
                String parent = path.contains("/") ? path.substring(0, path.lastIndexOf('/')) : "";
                long size = input.readLong();
                if (type > 1 || !names.add(path.toLowerCase(Locale.ROOT)) || !directories.contains(parent)
                        || size < 0 || size > MAX_PAYLOAD - total || (type == 1 && size != 0)) {
                    throw new IOException("Invalid folder backup entry");
                }
                File target = root == null ? null : new File(root, path);
                if (type == 1) {
                    directories.add(path);
                    if (target != null && !target.mkdir()) throw new IOException("Could not stage a save directory");
                } else {
                    total += size;
                    files++;
                    byte[] copied;
                    if (target == null) copied = payload(input, null, size, buffer);
                    else try (FileOutputStream file = new FileOutputStream(target)) {
                        copied = payload(input, file, size, buffer);
                        file.getFD().sync();
                    }
                    byte[] expected = new byte[DIGEST_BYTES];
                    input.readFully(expected);
                    if (!MessageDigest.isEqual(copied, expected) || (target != null
                            && !MessageDigest.isEqual(expected, VerifiedFileTransfer.readDigest(target, buffer)))) {
                        throw new IOException("Save file failed SHA-256 readback");
                    }
                }
            }
            checked.on(false);
            byte[] expected = new byte[DIGEST_BYTES];
            input.readFully(expected);
            if (input.read() != -1 || !MessageDigest.isEqual(expected, digest.digest())) {
                throw new IOException("Folder backup integrity check failed");
            }
            return new Preview(rootName, hex(expected), files, directories.size() - 1, total, created);
        }
    }

    private static byte[] payload(InputStream input, OutputStream output, long size, byte[] buffer) throws IOException {
        MessageDigest digest = VerifiedFileTransfer.sha256();
        while (size > 0) {
            VerifiedFileTransfer.checkInterrupted();
            int n = input.read(buffer, 0, (int) Math.min(size, buffer.length));
            if (n < 0) throw new IOException("Incomplete save file");
            if (output != null) output.write(buffer, 0, n);
            digest.update(buffer, 0, n);
            size -= n;
        }
        return digest.digest();
    }

    private static void writeText(DataOutputStream output, byte[] bytes) throws IOException {
        output.writeShort(bytes.length);
        output.write(bytes);
    }

    private static String readText(DataInputStream input, boolean name) throws IOException {
        int length = input.readUnsignedShort();
        if (length < 1 || length > (name ? MAX_NAME : MAX_PATH)) throw new IOException("Invalid save path length");
        byte[] bytes = new byte[length];
        input.readFully(bytes);
        String value = new String(bytes, StandardCharsets.UTF_8);
        if (!Arrays.equals(bytes, name ? checkedName(value) : checkedPath(value))) throw new IOException("Invalid save path encoding");
        return value;
    }

    private static byte[] checkedName(String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 1 || bytes.length > MAX_NAME || value.endsWith(".") || value.endsWith(" ")
                || value.indexOf('/') >= 0 || value.indexOf('\\') >= 0 || value.indexOf(':') >= 0
                || value.matches(".*[<>\\\"|?*].*")) {
            throw new IOException("Unsupported save name");
        }
        for (int i = 0; i < value.length(); i++) if (Character.isISOControl(value.charAt(i))) throw new IOException("Unsupported save name");
        return bytes;
    }

    private static byte[] checkedPath(String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        String[] parts = value.split("/", -1);
        if (bytes.length > MAX_PATH || parts.length > MAX_DEPTH) throw new IOException("Save path exceeds the folder format limit");
        for (String part : parts) checkedName(part);
        return bytes;
    }

    private static File stage(File cache) throws IOException {
        VerifiedFileTransfer.checkInterrupted();
        File stage = new File(cache, "nexus-save-folder-" + UUID.randomUUID());
        if (!stage.mkdir()) throw new IOException("Could not stage the folder backup");
        return stage;
    }

    private static void clean(File path) {
        try {
            VerifiedFileTransfer.requireOrdinary(path);
            if (path.isDirectory()) {
                File[] children = path.listFiles();
                if (children != null) for (File child : children) clean(child);
            }
            path.delete();
        } catch (IOException | SecurityException ignored) { }
    }

    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) result.append(Character.forDigit((value & 255) >>> 4, 16)).append(Character.forDigit(value & 15, 16));
        return result.toString();
    }
}
