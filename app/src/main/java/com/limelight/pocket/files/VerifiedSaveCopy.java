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
import java.util.Arrays;
import java.util.Locale;
import java.util.UUID;

final class VerifiedSaveCopy {
    private static final String EXTENSION = ".nexussave";
    private static final byte[] MAGIC = "NEXUSSAVE\1".getBytes(StandardCharsets.US_ASCII);
    private static final long MAX_PAYLOAD = 256L * 1024 * 1024;
    private static final int MAX_NAME_BYTES = 220;
    private static final int DIGEST_BYTES = 32;
    private static final int BUFFER_BYTES = 64 * 1024;
    private static final int FIXED_BYTES = MAGIC.length + Short.SIZE / Byte.SIZE
            + Long.SIZE / Byte.SIZE + DIGEST_BYTES;

    private VerifiedSaveCopy() { }

    static final class Preview {
        final String name;
        final long size;
        final String digest;

        private Preview(String name, long size, String digest) {
            this.name = name;
            this.size = size;
            this.digest = digest;
        }
    }

    static boolean isBackup(File file) {
        return file.getName().toLowerCase(Locale.ROOT).endsWith(EXTENSION);
    }

    static void create(File source, File destination, File cache) throws IOException {
        VerifiedFileTransfer.requireOrdinary(source);
        byte[] name = validatedName(source.getName());
        long size = source.length();
        long modified = source.lastModified();
        if (!source.isFile() || isBackup(source) || size > MAX_PAYLOAD) {
            throw new IOException("Save must be a single file of up to 256 MiB");
        }

        File staging = stage(cache);
        File archive = new File(staging, "Backup - " + source.getName() + EXTENSION);
        try {
            write(source, archive, name, size);
            VerifiedFileTransfer.requireOrdinary(source);
            if (!source.isFile() || source.length() != size || source.lastModified() != modified) {
                throw new IOException("Save changed during backup");
            }
            read(archive, null);
            VerifiedFileTransfer.transfer(archive, destination, false);
        } finally {
            cleanup(staging);
        }
    }

    static Preview preview(File archive) throws IOException {
        return read(archive, null);
    }

    static void restore(File archive, File destination, File cache, Preview confirmed) throws IOException {
        File staging = stage(cache);
        try {
            Preview actual = read(archive, staging);
            if (confirmed == null || !actual.digest.equals(confirmed.digest)) {
                throw new IOException("Save backup changed after preview");
            }
            VerifiedFileTransfer.transfer(new File(staging, actual.name), destination, false);
        } finally {
            cleanup(staging);
        }
    }

    private static void write(File source, File archive, byte[] name, long size) throws IOException {
        MessageDigest digest = VerifiedFileTransfer.sha256();
        try (InputStream input = new FileInputStream(source);
             FileOutputStream file = new FileOutputStream(archive);
             DigestOutputStream checked = new DigestOutputStream(new BufferedOutputStream(file), digest);
             DataOutputStream output = new DataOutputStream(checked)) {
            // Version 1: magic, unsigned 16-bit UTF-8 byte count, name, 64-bit payload size,
            // raw payload, and SHA-256 over the preceding bytes. Integers are big-endian.
            output.write(MAGIC);
            output.writeShort(name.length);
            output.write(name);
            output.writeLong(size);
            copyPayload(input, output, size, new byte[BUFFER_BYTES]);
            if (input.read() != -1) throw new IOException("Save grew during backup");
            checked.on(false);
            output.write(digest.digest());
            output.flush();
            file.getFD().sync();
        }
    }

    private static Preview read(File archive, File staging) throws IOException {
        VerifiedFileTransfer.requireOrdinary(archive);
        long fileSize = archive.length();
        if (!archive.isFile() || fileSize < FIXED_BYTES + 1
                || fileSize > FIXED_BYTES + MAX_NAME_BYTES + MAX_PAYLOAD) {
            throw new IOException("Invalid save backup size");
        }
        MessageDigest digest = VerifiedFileTransfer.sha256();
        try (DigestInputStream checked = new DigestInputStream(
                new BufferedInputStream(new FileInputStream(archive)), digest);
             DataInputStream input = new DataInputStream(checked)) {
            byte[] magic = new byte[MAGIC.length];
            input.readFully(magic);
            if (!Arrays.equals(magic, MAGIC)) throw new IOException("Unrecognized save backup");
            int nameSize = input.readUnsignedShort();
            if (nameSize < 1 || nameSize > MAX_NAME_BYTES) throw new IOException("Invalid save name length");
            byte[] encodedName = new byte[nameSize];
            input.readFully(encodedName);
            String name = new String(encodedName, StandardCharsets.UTF_8);
            if (!Arrays.equals(encodedName, validatedName(name))) throw new IOException("Invalid save name encoding");
            long size = input.readLong();
            if (size < 0 || size > MAX_PAYLOAD || fileSize != FIXED_BYTES + nameSize + size) {
                throw new IOException("Invalid save payload size");
            }

            byte[] buffer = new byte[BUFFER_BYTES];
            File result = staging == null ? null : new File(staging, name);
            if (result == null) {
                copyPayload(input, null, size, buffer);
            } else {
                extract(input, result, size, buffer);
            }
            checked.on(false);
            byte[] expected = new byte[DIGEST_BYTES];
            input.readFully(expected);
            if (input.read() != -1 || !MessageDigest.isEqual(expected, digest.digest())) {
                throw new IOException("Save backup integrity check failed");
            }
            char[] hex = new char[expected.length * 2];
            for (int i = 0; i < expected.length; i++) {
                hex[i * 2] = Character.forDigit((expected[i] & 0xff) >>> 4, 16);
                hex[i * 2 + 1] = Character.forDigit(expected[i] & 0xf, 16);
            }
            return new Preview(name, size, new String(hex));
        }
    }

    private static void extract(InputStream input, File result, long size, byte[] buffer) throws IOException {
        MessageDigest payload = VerifiedFileTransfer.sha256();
        try (FileOutputStream file = new FileOutputStream(result);
             DigestOutputStream output = new DigestOutputStream(file, payload)) {
            copyPayload(input, output, size, buffer);
            file.getFD().sync();
        }
        if (result.length() != size || !MessageDigest.isEqual(payload.digest(), VerifiedFileTransfer.readDigest(result, buffer))) {
            throw new IOException("Restored save did not match readback");
        }
    }

    private static void copyPayload(InputStream input, OutputStream output, long size, byte[] buffer) throws IOException {
        while (size > 0) {
            VerifiedFileTransfer.checkInterrupted();
            int count = input.read(buffer, 0, (int) Math.min(size, buffer.length));
            if (count == -1) throw new IOException("Incomplete save payload");
            if (output != null) output.write(buffer, 0, count);
            size -= count;
        }
    }

    private static byte[] validatedName(String name) throws IOException {
        byte[] encoded = name.getBytes(StandardCharsets.UTF_8);
        if (encoded.length == 0 || encoded.length > MAX_NAME_BYTES || name.endsWith(".") || name.endsWith(" ")
                || name.indexOf('/') >= 0 || name.indexOf('\\') >= 0 || name.indexOf(':') >= 0) {
            throw new IOException("Unsupported save file name");
        }
        for (int i = 0; i < name.length(); i++) {
            if (Character.isISOControl(name.charAt(i))) throw new IOException("Unsupported save file name");
        }
        return encoded;
    }

    private static File stage(File cache) throws IOException {
        VerifiedFileTransfer.checkInterrupted();
        File staging = new File(cache, "nexus-save-" + UUID.randomUUID());
        if (!staging.mkdir()) throw new IOException("Could not stage the save copy");
        return staging;
    }

    private static void cleanup(File staging) {
        // Files are created only in this unique, app-private staging directory.
        File[] files = staging.listFiles();
        if (files != null) for (File file : files) file.delete();
        staging.delete();
    }
}
