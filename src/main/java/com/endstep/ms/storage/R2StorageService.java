package com.endstep.ms.storage;

import com.endstep.ms.config.EndstepProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Storage no Cloudflare R2 via API S3 (assinatura AWS SigV4 feita à mão — sem SDK).
 * Leitura pública pelo domínio pub-xxx.r2.dev (ou custom) configurado em publicBaseUrl.
 *
 * @author Kauã Ferreira
 * @since 2026-09-08
 */
@Service
@ConditionalOnProperty(prefix = "endstep.storage", name = "type", havingValue = "r2")
public class R2StorageService implements StorageService {
    private static final Logger log = LoggerFactory.getLogger(R2StorageService.class);
    private static final String REGION = "auto";
    private static final String SERVICE = "s3";
    private static final DateTimeFormatter AMZ_DATE = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final HttpClient http;
    private final String host;
    private final String bucket;
    private final String accessKey;
    private final String secretKey;
    private final String publicBase;

    public R2StorageService(EndstepProperties props, HttpClient scryfallHttpClient) {
        EndstepProperties.Storage.R2 r2 = props.storage().r2();
        require(r2 != null, "endstep.storage.r2.* obrigatório quando type=r2");
        require(notBlank(r2.accountId()), "endstep.storage.r2.account-id (R2_ACCOUNT_ID)");
        require(notBlank(r2.accessKeyId()), "endstep.storage.r2.access-key-id (R2_ACCESS_KEY_ID)");
        require(notBlank(r2.secretAccessKey()), "endstep.storage.r2.secret-access-key (R2_SECRET_ACCESS_KEY)");
        require(notBlank(r2.bucket()), "endstep.storage.r2.bucket (R2_BUCKET)");
        require(notBlank(r2.publicBaseUrl()), "endstep.storage.r2.public-base-url (R2_PUBLIC_BASE_URL)");

        this.http = scryfallHttpClient;
        this.host = r2.accountId() + ".r2.cloudflarestorage.com";
        this.bucket = r2.bucket();
        this.accessKey = r2.accessKeyId();
        this.secretKey = r2.secretAccessKey();
        this.publicBase = r2.publicBaseUrl().replaceAll("/+$", "");
        log.info("Storage R2: bucket '{}', público em {}", bucket, publicBase);
    }

    @Override
    public Stored put(String folder, String filename, byte[] data, String contentType) {
        String key = sanitize(folder) + "/" + UUID.randomUUID() + extFor(contentType, filename);
        String ct = contentType == null ? "application/octet-stream" : contentType;
        HttpRequest req = signed("PUT", key, data, ct);
        try {
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) {
                log.error("R2 PUT {} -> HTTP {} : {}", key, resp.statusCode(), resp.body());
                throw new IllegalStateException("R2 respondeu HTTP " + resp.statusCode() + ": " + resp.body());
            }
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Falha no upload para o R2: " + e, e);
        }
        return new Stored(publicBase + "/" + key, key);
    }

    @Override
    public void delete(String url) {
        if (url == null || !url.startsWith(publicBase + "/")) {
            return;
        }
        String key = url.substring(publicBase.length() + 1);
        try {
            http.send(signed("DELETE", key, new byte[0], null), HttpResponse.BodyHandlers.discarding());
        } catch (Exception e) {
            log.warn("Falha ao apagar {} do R2: {}", key, e.toString());
        }
    }

    private HttpRequest signed(String method, String key, byte[] body, String contentType) {
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        String amzDate = now.format(AMZ_DATE);
        String dateStamp = now.format(DATE);
        String canonicalUri = "/" + bucket + "/" + encodePath(key);
        String payloadHash = hex(sha256(body));

        java.util.TreeMap<String, String> headers = new java.util.TreeMap<>();
        headers.put("host", host);
        headers.put("x-amz-content-sha256", payloadHash);
        headers.put("x-amz-date", amzDate);
        if (contentType != null) {
            headers.put("content-type", contentType);
        }

        StringBuilder canonicalHeaders = new StringBuilder();
        headers.forEach((k, v) -> canonicalHeaders.append(k).append(':').append(v).append('\n'));
        String signedHeaders = String.join(";", headers.keySet());

        String canonicalRequest = method + "\n" + canonicalUri + "\n\n"
                + canonicalHeaders + "\n" + signedHeaders + "\n" + payloadHash;

        String scope = dateStamp + "/" + REGION + "/" + SERVICE + "/aws4_request";
        String stringToSign = "AWS4-HMAC-SHA256\n" + amzDate + "\n" + scope + "\n"
                + hex(sha256(canonicalRequest.getBytes(StandardCharsets.UTF_8)));

        byte[] kDate = hmac(("AWS4" + secretKey).getBytes(StandardCharsets.UTF_8), dateStamp);
        byte[] kRegion = hmac(kDate, REGION);
        byte[] kService = hmac(kRegion, SERVICE);
        byte[] kSigning = hmac(kService, "aws4_request");
        String signature = hex(hmac(kSigning, stringToSign));

        String authorization = "AWS4-HMAC-SHA256 Credential=" + accessKey + "/" + scope
                + ", SignedHeaders=" + signedHeaders + ", Signature=" + signature;

        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("https://" + host + canonicalUri))
                .timeout(Duration.ofSeconds(30))
                .header("x-amz-date", amzDate)
                .header("x-amz-content-sha256", payloadHash)
                .header("Authorization", authorization);
        if (contentType != null) {
            b.header("Content-Type", contentType);
        }
        return "DELETE".equals(method)
                ? b.DELETE().build()
                : b.method(method, HttpRequest.BodyPublishers.ofByteArray(body)).build();
    }

    private static String encodePath(String key) {
        StringBuilder sb = new StringBuilder();
        for (String seg : key.split("/", -1)) {
            if (sb.length() > 0) {
                sb.append('/');
            }
            for (byte c : seg.getBytes(StandardCharsets.UTF_8)) {
                int ch = c & 0xFF;
                if ((ch >= 'A' && ch <= 'Z') || (ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9')
                        || ch == '-' || ch == '_' || ch == '.' || ch == '~') {
                    sb.append((char) ch);
                } else {
                    sb.append('%').append(String.format("%02X", ch));
                }
            }
        }
        return sb.toString();
    }

    private static byte[] sha256(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] hmac(byte[] key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String hex(byte[] b) {
        return HexFormat.of().formatHex(b);
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

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static void require(boolean cond, String what) {
        if (!cond) {
            throw new IllegalStateException("Config R2 faltando: " + what);
        }
    }
}
