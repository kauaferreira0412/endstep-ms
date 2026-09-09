package com.endstep.ms.service;

import com.endstep.ms.common.NotFoundException;
import com.endstep.ms.config.EndstepProperties;
import com.endstep.ms.dto.CardTranslationView;
import com.endstep.ms.entity.CardOracle;
import com.endstep.ms.entity.CardTranslation;
import com.endstep.ms.repository.CardOracleRepository;
import com.endstep.ms.repository.CardTranslationRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Traducao das cartas para PT-BR (ou outro idioma) com cache no banco.
 * <ol>
 *   <li>Impressao oficial no idioma via Scryfall ({@code printed_name/type/text}).</li>
 *   <li>Fallback: Google Cloud Translate v2, so o texto de regras.</li>
 *   <li>Sem nenhum: grava {@code source='none'} e devolve o texto original.</li>
 * </ol>
 * Uma carta e traduzida uma unica vez; as chamadas seguintes leem o cache.
 *
 * @author Kauã Ferreira
 * @since 2026-09-08
 */
@Service
public class TranslationService {
    private static final Logger log = LoggerFactory.getLogger(TranslationService.class);
    private static final String GOOGLE_URL = "https://translation.googleapis.com/language/translate/v2";

    private final CardOracleRepository oracleRepo;
    private final CardTranslationRepository translationRepo;
    private final EndstepProperties props;
    private final ObjectMapper om;
    private final HttpClient http;

    public TranslationService(CardOracleRepository oracleRepo,
                              CardTranslationRepository translationRepo,
                              EndstepProperties props,
                              ObjectMapper om,
                              HttpClient scryfallHttpClient) {
        this.oracleRepo = oracleRepo;
        this.translationRepo = translationRepo;
        this.props = props;
        this.om = om;
        this.http = scryfallHttpClient;
    }

    private static String normLang(String lang) {
        if (lang == null || lang.isBlank()) {
            return "pt";
        }
        String l = lang.trim().toLowerCase();
        int dash = l.indexOf('-');
        return dash > 0 ? l.substring(0, dash) : l;
    }

    @Transactional
    public CardTranslationView byOracleUuid(UUID oracleId, String lang) {
        CardOracle o = oracleRepo.findByOracleId(oracleId)
                .orElseThrow(() -> new NotFoundException("Carta nao encontrada: " + oracleId));
        return translate(o, normLang(lang));
    }

    @Transactional
    public CardTranslationView byOracleCardId(Long oracleCardId, String lang) {
        CardOracle o = oracleRepo.findById(oracleCardId)
                .orElseThrow(() -> new NotFoundException("Carta nao encontrada: id=" + oracleCardId));
        return translate(o, normLang(lang));
    }

    private CardTranslationView translate(CardOracle o, String lang) {
        Optional<CardTranslation> cached = translationRepo.findByOracleCardIdAndLang(o.getId(), lang);
        if (cached.isPresent()) {
            return toView(o, cached.get());
        }

        CardTranslation t = new CardTranslation();
        t.setOracleCardId(o.getId());
        t.setLang(lang);

        boolean filled = fillFromScryfall(o, lang, t);
        if (!filled) {
            filled = fillFromGoogle(o, lang, t);
        }
        if (!filled) {
            t.setSource("none");
        }

        try {
            translationRepo.save(t);
        } catch (DataIntegrityViolationException race) {
            return translationRepo.findByOracleCardIdAndLang(o.getId(), lang)
                    .map(x -> toView(o, x))
                    .orElseGet(() -> toView(o, t));
        }
        return toView(o, t);
    }

    private boolean fillFromScryfall(CardOracle o, String lang, CardTranslation t) {
        String scryLang = "pt".equals(lang) ? props.translation().scryfallLang() : lang;
        try {
            String q = "oracleid:" + o.getOracleId() + " lang:" + scryLang;
            URI uri = URI.create(props.scryfall().apiBase() + "/cards/search?unique=prints&include_multilingual=true&q="
                    + URLEncoder.encode(q, StandardCharsets.UTF_8));
            HttpRequest req = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(15))
                    .header("User-Agent", props.scryfall().userAgent())
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            sleepPolite();
            if (resp.statusCode() == 404) {
                return false;
            }
            if (resp.statusCode() != 200) {
                log.warn("Scryfall lang search HTTP {} para {}", resp.statusCode(), o.getName());
                return false;
            }
            JsonNode data = om.readTree(resp.body()).path("data");
            if (!data.isArray() || data.isEmpty()) {
                return false;
            }
            JsonNode card = data.get(0);

            String name = textOrNull(card, "printed_name");
            String typeLine = textOrNull(card, "printed_type_line");
            String oracleText = textOrNull(card, "printed_text");

            if (oracleText == null && card.path("card_faces").isArray() && !card.path("card_faces").isEmpty()) {
                StringBuilder nm = new StringBuilder();
                StringBuilder tp = new StringBuilder();
                StringBuilder tx = new StringBuilder();
                for (JsonNode f : card.path("card_faces")) {
                    join(nm, textOrNull(f, "printed_name"), " // ");
                    join(tp, textOrNull(f, "printed_type_line"), " // ");
                    join(tx, textOrNull(f, "printed_text"), "\n\n//\n\n");
                }
                if (name == null && nm.length() > 0) name = nm.toString();
                if (typeLine == null && tp.length() > 0) typeLine = tp.toString();
                if (tx.length() > 0) oracleText = tx.toString();
            }

            if (oracleText == null && name == null && typeLine == null) {
                return false;
            }
            t.setName(name);
            t.setTypeLine(typeLine);
            t.setOracleText(oracleText);
            t.setSource("scryfall");
            return true;
        } catch (Exception e) {
            log.warn("Falha na busca de idioma no Scryfall para {}: {}", o.getName(), e.toString());
            return false;
        }
    }

    private boolean fillFromGoogle(CardOracle o, String lang, CardTranslation t) {
        String key = props.translation().googleApiKey();
        if (!props.translation().enabled() || key == null || key.isBlank()) {
            return false;
        }
        String src = o.getOracleText();
        if (src == null || src.isBlank()) {
            return false;
        }
        try {
            ObjectNode body = om.createObjectNode();
            body.put("q", src);
            body.put("source", "en");
            body.put("target", props.translation().targetLang());
            body.put("format", "text");

            URI uri = URI.create(GOOGLE_URL + "?key=" + URLEncoder.encode(key, StandardCharsets.UTF_8));
            HttpRequest req = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(om.writeValueAsString(body), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                log.warn("Google Translate HTTP {} para {}: {}", resp.statusCode(), o.getName(),
                        resp.body() == null ? "" : resp.body().substring(0, Math.min(200, resp.body().length())));
                return false;
            }
            JsonNode tr = om.readTree(resp.body()).path("data").path("translations");
            if (!tr.isArray() || tr.isEmpty()) {
                return false;
            }
            String out = tr.get(0).path("translatedText").asText(null);
            if (out == null || out.isBlank()) {
                return false;
            }
            t.setName(null);
            t.setTypeLine(null);
            t.setOracleText(out);
            t.setSource("google");
            return true;
        } catch (Exception e) {
            log.warn("Falha no Google Translate para {}: {}", o.getName(), e.toString());
            return false;
        }
    }

    private CardTranslationView toView(CardOracle o, CardTranslation t) {
        String name = t.getName() != null ? t.getName() : o.getName();
        String typeLine = t.getTypeLine() != null ? t.getTypeLine() : o.getTypeLine();
        String oracleText = t.getOracleText() != null ? t.getOracleText() : o.getOracleText();
        return new CardTranslationView(o.getId(), o.getOracleId(), t.getLang(),
                name, typeLine, oracleText, t.getSource());
    }

    private void sleepPolite() {
        try {
            Thread.sleep(Math.max(0, props.scryfall().requestDelayMs()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull()) {
            return null;
        }
        String s = v.asText();
        return s == null || s.isBlank() ? null : s;
    }

    private static void join(StringBuilder sb, String piece, String sep) {
        if (piece == null || piece.isBlank()) {
            return;
        }
        if (sb.length() > 0) {
            sb.append(sep);
        }
        sb.append(piece);
    }
}
