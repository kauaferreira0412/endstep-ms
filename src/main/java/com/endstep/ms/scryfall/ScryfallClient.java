package com.endstep.ms.scryfall;

import com.endstep.ms.config.EndstepProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Cliente do Scryfall. Respeita as exigencias da API: User-Agent
 * identificavel e header Accept explicito.
 *
 * @author Kauã Ferreira
 * @since 2026-09-08
 */
@Component
public class ScryfallClient {
    private static final Logger log = LoggerFactory.getLogger(ScryfallClient.class);

    private final HttpClient http;
    private final ObjectMapper om;
    private final EndstepProperties props;

    public ScryfallClient(HttpClient scryfallHttpClient, ObjectMapper om, EndstepProperties props) {
        this.http = scryfallHttpClient;
        this.om = om;
        this.props = props;
    }

    public BulkDataEntry getBulkEntry(String type) throws IOException, InterruptedException {
        URI uri = URI.create(props.scryfall().apiBase() + "/bulk-data");
        HttpRequest req = baseRequest(uri).build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw new IOException("Scryfall GET /bulk-data retornou HTTP " + resp.statusCode());
        }
        JsonNode root = om.readTree(resp.body());
        for (JsonNode n : root.path("data")) {
            if (type.equals(n.path("type").asText())) {
                String downloadUri = n.path("jsonl_download_uri").asText(n.path("download_uri").asText(""));
                if (downloadUri.isEmpty()) {
                    throw new IOException("Bulk '" + type + "' sem download uri na resposta do Scryfall");
                }
                BulkDataEntry entry = new BulkDataEntry(
                        type,
                        downloadUri,
                        n.path("compressed_size").asLong(n.path("size").asLong(0)),
                        n.path("updated_at").asText("")
                );
                log.info("Bulk '{}' -> {} ({} bytes comprimidos, atualizado {})",
                        type, entry.downloadUri(), entry.size(), entry.updatedAt());
                return entry;
            }
        }
        throw new IOException("Tipo de Bulk Data nao encontrado no Scryfall: " + type);
    }

    public InputStream openDownloadStream(String downloadUri) throws IOException, InterruptedException {
        HttpRequest req = baseRequest(URI.create(downloadUri)).build();
        HttpResponse<InputStream> resp = http.send(req, HttpResponse.BodyHandlers.ofInputStream());
        if (resp.statusCode() != 200) {
            resp.body().close();
            throw new IOException("Scryfall download retornou HTTP " + resp.statusCode());
        }
        return resp.body();
    }

    private HttpRequest.Builder baseRequest(URI uri) {
        return HttpRequest.newBuilder(uri)
                .timeout(Duration.ofMinutes(30))
                .header("User-Agent", props.scryfall().userAgent())
                .header("Accept", "application/json")
                .GET();
    }
}
