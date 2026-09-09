package com.endstep.ms.storage;

import com.endstep.ms.config.EndstepProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Componente de armazenamento de arquivos: LocalStorageService.
 *
 * @author Kauã Ferreira
 * @since 2026-09-08
 */
@Service
@ConditionalOnProperty(prefix = "endstep.storage", name = "type", havingValue = "local", matchIfMissing = true)
public class LocalStorageService implements StorageService {
    private static final Logger log = LoggerFactory.getLogger(LocalStorageService.class);

    private final Path root;
    private final String publicBase;

    public LocalStorageService(EndstepProperties props) {
        String dir = props.storage() != null && props.storage().local() != null
                ? props.storage().local().dir() : "./.data/uploads";
        this.root = Path.of(dir).toAbsolutePath().normalize();
        this.publicBase = props.storage() != null && props.storage().publicBaseUrl() != null
                ? props.storage().publicBaseUrl().replaceAll("/+$", "") : "";
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new IllegalStateException("Nao consegui criar " + root, e);
        }
        log.info("Storage LOCAL em {} (URLs sob {}/uploads)", root, publicBase.isEmpty() ? "" : publicBase);
    }

    public Path root() {
        return root;
    }

    @Override
    public Stored put(String folder, String filename, byte[] data, String contentType) {
        String ext = extFor(contentType, filename);
        String key = sanitize(folder) + "/" + UUID.randomUUID() + ext;
        Path target = root.resolve(key).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("key invalida");
        }
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, data);
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao gravar " + key, e);
        }
        return new Stored(publicBase + "/uploads/" + key, key);
    }

    @Override
    public void delete(String url) {
        if (url == null) {
            return;
        }
        int idx = url.indexOf("/uploads/");
        if (idx < 0) {
            return;
        }
        String key = url.substring(idx + "/uploads/".length());
        Path target = root.resolve(key).normalize();
        if (target.startsWith(root)) {
            try {
                Files.deleteIfExists(target);
            } catch (IOException e) {
                log.warn("Falha ao apagar {}: {}", key, e.toString());
            }
        }
    }

    private static String sanitize(String s) {
        return s == null ? "misc" : s.replaceAll("[^A-Za-z0-9_-]", "_");
    }

    private static String extFor(String contentType, String filename) {
        if (contentType != null) {
            if (contentType.contains("png")) {
                return ".png";
            }
            if (contentType.contains("jpeg") || contentType.contains("jpg")) {
                return ".jpg";
            }
            if (contentType.contains("webp")) {
                return ".webp";
            }
        }
        int dot = filename == null ? -1 : filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot).toLowerCase() : ".bin";
    }
}
