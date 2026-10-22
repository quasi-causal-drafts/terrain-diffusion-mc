package com.github.xandergos.terraindiffusionmc.pipeline;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

/**
 * Loads the DirectML redistributable bundled with the Windows build.
 *
 * <p>The DirectML build of {@code onnxruntime.dll} delay-loads {@code DirectML.dll} by name. Without a
 * bundled copy, Windows resolves that name to its own inbox {@code System32\DirectML.dll}. On Windows 10
 * that copy is too old for the feature level ONNX Runtime requests, so {@code DMLCreateDevice1} fails
 * with {@code 0x887A0004} ("The specified device interface or feature level is not supported").
 *
 * <p>Windows reuses an already-loaded module with the same base name when resolving a DLL by name, so
 * loading the bundled copy (file name must be exactly {@code DirectML.dll}) before ONNX Runtime first
 * touches DirectML makes the delay-load bind to it.
 */
final class DirectMLRuntime {
    private static final Logger LOG = LoggerFactory.getLogger(DirectMLRuntime.class);

    private static final String RESOURCE_DIR = "/terrain-diffusion-mc/native/win-x64/";
    private static final String DLL_NAME = "DirectML.dll";
    private static final String VERSION_RESOURCE = RESOURCE_DIR + "directml-version.txt";
    private static final String NATIVE_DIR_NAME = "native";

    private static final Object LOCK = new Object();
    private static volatile boolean attempted = false;
    private static volatile String status = "Windows system DirectML.dll (no bundled copy loaded)";

    private DirectMLRuntime() {
    }

    /**
     * Loads the bundled DirectML.dll once per process. Never throws: if anything goes wrong, ONNX Runtime
     * falls back to the Windows system copy and the failure is logged.
     */
    static void ensureLoaded() {
        if (attempted) {
            return;
        }
        synchronized (LOCK) {
            if (attempted) {
                return;
            }
            attempted = true;
            try {
                loadBundled();
            } catch (Throwable t) {
                status = "Windows system DirectML.dll (loading the bundled copy failed: " + t.getMessage() + ")";
                LOG.warn("Failed to load bundled DirectML.dll: {} - {}. Falling back to the Windows system copy.",
                        t.getClass().getSimpleName(), t.getMessage());
            }
        }
    }

    /** Human-readable description of which DirectML.dll is in use, for error messages. */
    static String describe() {
        return status;
    }

    private static void loadBundled() throws IOException {
        if (!isWindowsX64()) {
            status = "not applicable on this platform";
            return;
        }

        byte[] dllBytes = readResource(RESOURCE_DIR + DLL_NAME);
        if (dllBytes == null) {
            LOG.info("No bundled DirectML.dll in this build; using the Windows system copy.");
            return;
        }
        byte[] versionBytes = readResource(VERSION_RESOURCE);
        String version = versionBytes == null
                ? "unknown"
                : new String(versionBytes, StandardCharsets.UTF_8).trim();

        String sha256 = sha256Hex(dllBytes);
        Path targetDir = ModelAssetManager.resolveAssetPath(NATIVE_DIR_NAME)
                .resolve("directml-" + version + "-" + sha256.substring(0, 12));
        Path target = targetDir.resolve(DLL_NAME);

        if (!isExtracted(target, dllBytes.length, sha256)) {
            Files.createDirectories(targetDir);
            Path temp = Files.createTempFile(targetDir, "DirectML", ".tmp");
            try {
                Files.write(temp, dllBytes);
                try {
                    Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temp);
            }
        }

        System.load(target.toAbsolutePath().toString());
        status = "bundled DirectML " + version + " (" + target.toAbsolutePath() + ")";
        LOG.info("Loaded bundled DirectML {} from {}", version, target.toAbsolutePath());
    }

    private static boolean isExtracted(Path target, long expectedSize, String expectedSha256) {
        try {
            return Files.isRegularFile(target)
                    && Files.size(target) == expectedSize
                    && sha256Hex(Files.readAllBytes(target)).equals(expectedSha256);
        } catch (IOException e) {
            return false;
        }
    }

    private static boolean isWindowsX64() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        return os.startsWith("windows") && (arch.equals("amd64") || arch.equals("x86_64"));
    }

    private static byte[] readResource(String path) throws IOException {
        try (InputStream in = DirectMLRuntime.class.getResourceAsStream(path)) {
            return in == null ? null : in.readAllBytes();
        }
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Missing SHA-256 algorithm", e);
        }
    }
}
