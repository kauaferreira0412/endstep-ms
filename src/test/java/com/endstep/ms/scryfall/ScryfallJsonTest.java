package com.endstep.ms.scryfall;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes de parsing do JSON do Scryfall (ScryfallJson).
 *
 * @author Kauã Ferreira
 * @since 2026-09-08
 */
class ScryfallJsonTest {
    private final ObjectMapper om = new ObjectMapper();

    private JsonNode parse(String json) {
        try {
            return om.readTree(json);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void extraiCamposDeCartaSimples() {
        JsonNode card = parse("""
                {
                  "object": "card",
                  "id": "aaaaaaaa-0000-0000-0000-000000000001",
                  "oracle_id": "bbbbbbbb-0000-0000-0000-000000000002",
                  "name": "Sol Ring",
                  "mana_cost": "{1}",
                  "cmc": 1.0,
                  "type_line": "Artifact",
                  "oracle_text": "{T}: Add {C}{C}.",
                  "color_identity": [],
                  "keywords": [],
                  "set": "cmm",
                  "set_name": "Commander Masters",
                  "collector_number": "410",
                  "released_at": "2023-08-04",
                  "image_uris": { "normal": "https://cards.scryfall.io/normal/x.jpg", "png": "https://cards.scryfall.io/png/x.png" },
                  "legalities": { "commander": "legal", "modern": "not_legal" }
                }
                """);

        assertThat(ScryfallJson.resolveOracleId(card))
                .isEqualTo(UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002"));
        assertThat(ScryfallJson.str(card, "name")).isEqualTo("Sol Ring");
        assertThat(ScryfallJson.bigDecimalOrNull(card, "cmc")).isNotNull();
        assertThat(ScryfallJson.dateOrNull(card, "released_at")).isNotNull();
        assertThat(ScryfallJson.imageUri(ScryfallJson.imageNode(card), "normal"))
                .isEqualTo("https://cards.scryfall.io/normal/x.jpg");
    }

    @Test
    void usaOracleIdDaFaceQuandoAusenteNoTopo() {
        JsonNode card = parse("""
                {
                  "object": "card",
                  "name": "Fire // Ice",
                  "layout": "split",
                  "card_faces": [
                    { "name": "Fire", "oracle_id": "cccccccc-0000-0000-0000-000000000003",
                      "image_uris": { "normal": "https://cards.scryfall.io/normal/fire.jpg" } },
                    { "name": "Ice" }
                  ]
                }
                """);

        assertThat(ScryfallJson.resolveOracleId(card))
                .isEqualTo(UUID.fromString("cccccccc-0000-0000-0000-000000000003"));
        assertThat(ScryfallJson.imageUri(ScryfallJson.imageNode(card), "normal"))
                .isEqualTo("https://cards.scryfall.io/normal/fire.jpg");
    }

    @Test
    void camposAusentesViramNull() {
        JsonNode card = parse("{ \"object\": \"card\", \"name\": \"X\" }");
        assertThat(ScryfallJson.str(card, "mana_cost")).isNull();
        assertThat(ScryfallJson.bigDecimalOrNull(card, "cmc")).isNull();
        assertThat(ScryfallJson.dateOrNull(card, "released_at")).isNull();
        assertThat(ScryfallJson.uuidOrNull(card, "oracle_id")).isNull();
        assertThat(ScryfallJson.joinArray(card.get("colors"), "")).isNull();
    }
}
