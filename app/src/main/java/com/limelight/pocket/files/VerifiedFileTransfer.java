package com.limelight.pocket.files;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

final class VerifiedFileTransfer {
    enum Result { COMPLETE, SOURCE_RETAINED }

    private static final int BUFFER_SIZE = 256 * 1024;
    private static final int MAX_NAMES = 999;

    private VerifiedFileTransfer() { }

    /** One destination at a time across the Files screens in this process. */
    static synchronized Result transfer(File source, File destination, boolean move) throws IOException {
        checkInterrupted();
        File into = destination.getCanonicalFile();
        requireOrdinary(source);
        File from = source.getCanonicalFile();
        if (!into.isDirectory() || into.equals(from)
                || into.getPath().startsWith(from.getPath() + File.separator)) {
            throw new IOException("Invalid destination");
        }

        boolean directory = from.isDirectory();
        if (move) {
            File target = reserve(into, from.getName(), directory);
            if (from.renameTo(target)) return Result.COMPLETE;
            deleteReservation(target, directory);
        }

        File staging = new File(into, ".nexus-transfer-" + UUID.randomUUID());
        if (!staging.mkdir()) throw new IOException("Could not stage the transfer");
        File staged = new File(staging, "content");
        try {
            Snapshot snapshot = copyVerified(from, staged, new byte[BUFFER_SIZE]);
            checkInterrupted();
            File target = reserve(into, from.getName(), directory);
            if (!staged.renameTo(target)) {
                deleteReservation(target, directory);
                throw new IOException("Could not publish the verified copy");
            }
            if (move && (!snapshot.unchangedTree() || !snapshot.removeCopiedTree())) {
                return Result.SOURCE_RETAINED;
            }
            return Result.COMPLETE;
        } finally {
            cleanStaging(staging);
        }
    }

    /** Reserve a new name without opening an existing file, including when all suffixes are used. */
    private static File reserve(File into, String name, boolean directory) throws IOException {
        int dot = name.lastIndexOf('.');
        boolean hasExtension = !directory && dot > 0 && !name.startsWith(".");
        String base = hasExtension ? name.substring(0, dot) : name;
        String extension = hasExtension ? name.substring(dot) : "";
        for (int index = 1; index <= MAX_NAMES; index++) {
            String candidate = index == 1 ? name : base + " (" + index + ")" + extension;
            File target = new File(into, candidate);
            if (directory) {
                if (target.mkdir()) return target;
                if (!target.exists()) throw new IOException("Could not reserve the destination folder");
            } else if (target.createNewFile()) {
                return target;
            }
        }
        throw new IOException("No unused destination name");
    }

    private static Snapshot copyVerified(File from, File to, byte[] buffer) throws IOException {
        checkInterrupted();
        requireOrdinary(from);
        Snapshot source = new Snapshot(from);
        if (source.directory) {
            if (!to.mkdir()) throw new IOException("Could not stage a folder");
            File[] children = from.listFiles();
            if (children == null) throw new IOException("Could not read a folder");
            for (File child : children) {
                source.children.add(copyVerified(child, new File(to, child.getName()), buffer));
            }
        } else {
            MessageDigest copied = sha256();
            long count = 0;
            try (FileInputStream input = new FileInputStream(from); FileOutputStream output = new FileOutputStream(to)) {
                int length;
                while ((length = input.read(buffer)) != -1) {
                    checkInterrupted();
                    output.write(buffer, 0, length);
                    copied.update(buffer, 0, length);
                    count += length;
                }
                output.getFD().sync();
            }
            if (count != source.length || to.length() != count
                    || !MessageDigest.isEqual(copied.digest(), readDigest(to, buffer))) {
                throw new IOException("Transfer verification failed");
            }
        }
        if (!source.unchanged()) throw new IOException("Source changed during transfer");
        to.setLastModified(source.modified);
        return source;
    }

    private static byte[] readDigest(File path, byte[] buffer) throws IOException {
        MessageDigest digest = sha256();
        try (FileInputStream input = new FileInputStream(path)) {
            int length;
            while ((length = input.read(buffer)) != -1) {
                checkInterrupted();
                digest.update(buffer, 0, length);
            }
        }
        return digest.digest();
    }

    private static MessageDigest sha256() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    private static void checkInterrupted() throws InterruptedIOException {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Transfer cancelled");
    }

    private static boolean ordinaryPath(File file) throws IOException {
        File parent = file.getAbsoluteFile().getParentFile();
        return parent != null && new File(parent.getCanonicalFile(), file.getName()).equals(file.getCanonicalFile());
    }

    private static void requireOrdinary(File file) throws IOException {
        if (!ordinaryPath(file) || (!file.isDirectory() && !file.isFile())) {
            throw new IOException("Unsupported file or symbolic link");
        }
    }

    private static void deleteReservation(File file, boolean directory) {
        if (directory ? file.isDirectory() : file.isFile() && file.length() == 0) file.delete();
    }

    /** Only removes work created beneath the private staging name. Never follows a symbolic link. */
    private static void cleanStaging(File file) {
        try {
            if (ordinaryPath(file) && file.isDirectory()) {
                File[] children = file.listFiles();
                if (children != null) for (File child : children) cleanStaging(child);
            }
            file.delete();
        } catch (IOException | SecurityException ignored) {
            // An unavailable volume may leave a hidden staging folder.
        }
    }

    private static final class Snapshot {
        final File file;
        final long length, modified;
        final boolean directory;
        final List<Snapshot> children = new ArrayList<>();

        Snapshot(File file) {
            this.file = file;
            length = file.length();
            modified = file.lastModified();
            directory = file.isDirectory();
        }

        boolean unchanged() throws IOException {
            return ordinaryPath(file) && (directory ? file.isDirectory() : file.isFile() && file.length() == length)
                    && file.lastModified() == modified;
        }

        boolean unchangedTree() {
            try {
                if (Thread.currentThread().isInterrupted() || !unchanged()) return false;
                if (directory) {
                    String[] present = file.list();
                    if (present == null || present.length != children.size()) return false;
                    for (Snapshot child : children) if (!child.unchangedTree()) return false;
                }
                return true;
            } catch (IOException e) {
                return false;
            }
        }

        boolean removeCopiedTree() {
            try {
                if (Thread.currentThread().isInterrupted() || !unchanged()) return false;
                for (Snapshot child : children) if (!child.removeCopiedTree()) return false;
                return file.delete();
            } catch (IOException e) {
                return false;
            }
        }
    }
}
