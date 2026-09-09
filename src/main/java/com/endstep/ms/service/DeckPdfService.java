package com.endstep.ms.service;

import com.endstep.ms.config.EndstepProperties;
import com.endstep.ms.projection.DeckCardRow;
import com.endstep.ms.entity.Deck;
import com.endstep.ms.repository.DeckCardRepository;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Export de deck em PDF: comandante em seção própria + grid com a imagem de
 * cada carta. As imagens do Scryfall são baixadas em paralelo e embutidas
 * (data URI), então o openhtmltopdf não faz rede.
 *
 * @author Kauã Ferreira
 * @since 2026-09-08
 */
@Service
public class DeckPdfService {
    private static final Logger log = LoggerFactory.getLogger(DeckPdfService.class);

    private static final List<String> TYPE_ORDER = List.of(
            "Creature", "Planeswalker", "Instant", "Sorcery", "Artifact",
            "Enchantment", "Battle", "Land", "Outro");

    private final DeckService deckService;
    private final DeckCardRepository deckCards;
    private final HttpClient http;
    private final String localBase;

    public DeckPdfService(DeckService deckService, DeckCardRepository deckCards,
                          HttpClient scryfallHttpClient, EndstepProperties props) {
        this.deckService = deckService;
        this.deckCards = deckCards;
        this.http = scryfallHttpClient;
        String base = props.storage() != null ? props.storage().publicBaseUrl() : null;
        this.localBase = base != null && !base.isBlank() ? base.replaceAll("/+$", "") : "http://localhost:8080";
    }

    private String absolute(String url) {
        if (url == null) {
            return null;
        }
        return url.startsWith("/") ? localBase + url : url;
    }

    @Transactional(readOnly = true)
    public byte[] render(long userId, long deckId) {
        Deck deck = deckService.requireDeck(userId, deckId);
        List<DeckCardRow> rows = deckCards.loadRows(deckId, deck.getFormat());

        Map<String, String> imgById = fetchImages(rows);
        String html = buildHtml(deck, rows, imgById);

        try {
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.useFont(() -> DeckPdfService.class.getResourceAsStream("/fonts/DejaVuSans.ttf"), "DejaVu Sans");
            builder.withHtmlContent(html, null);
            builder.toStream(os);
            builder.run();
            return os.toByteArray();
        } catch (Exception e) {
            log.error("Falha ao gerar PDF do deck {}", deckId, e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Falha ao gerar o PDF");
        }
    }

    private Map<String, String> fetchImages(List<DeckCardRow> rows) {
        Map<String, String> urlByOracle = new LinkedHashMap<>();
        for (DeckCardRow r : rows) {
            String url = absolute(firstNonBlank(r.getImageNormal(), r.getImageLarge(), r.getImageSmall()));
            if (url != null) {
                urlByOracle.putIfAbsent(r.getOracleId().toString() + ":" + r.getSection(), url);
            }
        }

        Map<String, CompletableFuture<String>> futures = new LinkedHashMap<>();
        urlByOracle.forEach((oracleId, url) -> {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("User-Agent", "endstep-ms/0.1 (pdf export)")
                    .GET().build();
            futures.put(oracleId, http.sendAsync(req, HttpResponse.BodyHandlers.ofByteArray())
                    .thenApply(resp -> {
                        if (resp.statusCode() != 200) {
                            return null;
                        }
                        String ct = resp.headers().firstValue("content-type").orElse("image/jpeg");
                        return "data:" + ct + ";base64," + Base64.getEncoder().encodeToString(resp.body());
                    })
                    .exceptionally(ex -> null));
        });

        Map<String, String> out = new LinkedHashMap<>();
        long deadline = System.nanoTime() + Duration.ofSeconds(60).toNanos();
        for (Map.Entry<String, CompletableFuture<String>> e : futures.entrySet()) {
            try {
                long left = Math.max(0, deadline - System.nanoTime());
                String data = e.getValue().get(left, TimeUnit.NANOSECONDS);
                if (data != null) {
                    out.put(e.getKey(), data);
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            } catch (ExecutionException | TimeoutException ex) {
            }
        }
        log.info("PDF: {} de {} imagens embutidas", out.size(), urlByOracle.size());
        return out;
    }

    private String buildHtml(Deck deck, List<DeckCardRow> rows, Map<String, String> imgByOracle) {
        List<DeckCardRow> commander = rows.stream().filter(r -> "COMMANDER".equals(r.getSection())).toList();
        List<DeckCardRow> main = rows.stream().filter(r -> "MAINBOARD".equals(r.getSection())).toList();
        List<DeckCardRow> side = rows.stream().filter(r -> "SIDEBOARD".equals(r.getSection())).toList();
        List<DeckCardRow> maybe = rows.stream().filter(r -> "MAYBEBOARD".equals(r.getSection())).toList();

        int mainCount = main.stream().mapToInt(DeckCardRow::getQuantity).sum();

        StringBuilder sb = new StringBuilder();
        sb.append("""
                <!DOCTYPE html><html><head><meta charset="utf-8"/><style>
                @page { size: A4; margin: 7mm; }
                * { box-sizing: border-box; }
                body { font-family: "DejaVu Sans", sans-serif; color: #1a1a1a; font-size: 9pt; }
                h1 { font-size: 13pt; margin: 0 0 0.5mm; }
                .meta { color: #666; font-size: 8pt; margin-bottom: 2.5mm; }
                h2 { font-size: 10pt; margin: 3mm 0 1.5mm; border-bottom: 0.3mm solid #ccc; padding-bottom: 0.6mm; }
                .grid { font-size: 0; }
                .c { display: inline-block; width: 35mm; margin: 0 0.8mm 0.8mm 0; vertical-align: top; page-break-inside: avoid; }
                .c img { display: block; width: 35mm; height: 48.9mm; border-radius: 1.2mm; }
                .c .noimg { display: table-cell; width: 35mm; height: 48.9mm; border: 0.3mm solid #bbb; border-radius: 1.2mm;
                            vertical-align: middle; text-align: center; font-size: 7pt; color: #777; }
                .big { width: 63mm; margin: 0 2mm 2mm 0; }
                .big img, .big .noimg { width: 63mm; height: 88mm; }
                </style></head><body>
                """);

        sb.append("<h1>").append(esc(deck.getName())).append("</h1>");
        sb.append("<div class=\"meta\">")
                .append(esc(capitalize(deck.getFormat())))
                .append(deck.getColorIdentity().isEmpty() ? " · incolor" : " · " + deck.getColorIdentity())
                .append(" · ").append(mainCount + commander.stream().mapToInt(DeckCardRow::getQuantity).sum())
                .append(" cartas · ").append(LocalDate.now())
                .append("</div>");

        if (!commander.isEmpty()) {
            sb.append("<h2>Comandante</h2><div class=\"grid\">");
            for (DeckCardRow r : commander) {
                sb.append(cardHtml(r, imgByOracle, true));
            }
            sb.append("</div>");
        }

        sb.append("<h2>Deck");
        if (mainCount > 0) {
            sb.append(" — ").append(mainCount).append(" cartas");
        }
        sb.append("</h2><div class=\"grid\">");
        appendCards(sb, main, imgByOracle);
        sb.append("</div>");

        if (!side.isEmpty()) {
            sb.append("<h2>Sideboard</h2><div class=\"grid\">");
            appendCards(sb, side, imgByOracle);
            sb.append("</div>");
        }
        if (!maybe.isEmpty()) {
            sb.append("<h2>Talvez</h2><div class=\"grid\">");
            appendCards(sb, maybe, imgByOracle);
            sb.append("</div>");
        }

        sb.append("</body></html>");
        return sb.toString();
    }

    private void appendCards(StringBuilder sb, List<DeckCardRow> rows, Map<String, String> imgByOracle) {
        rows.stream()
                .sorted((a, b) -> {
                    int t = Integer.compare(TYPE_ORDER.indexOf(primaryType(a.getTypeLine())),
                            TYPE_ORDER.indexOf(primaryType(b.getTypeLine())));
                    return t != 0 ? t : a.getName().compareToIgnoreCase(b.getName());
                })
                .forEach(r -> sb.append(cardHtml(r, imgByOracle, false)));
    }

    private String cardHtml(DeckCardRow r, Map<String, String> imgByOracle, boolean big) {
        String img = imgByOracle.get(r.getOracleId().toString() + ":" + r.getSection());
        String cls = big ? "c big" : "c";
        String label = r.getDisplayName() != null && !r.getDisplayName().isBlank()
                ? r.getDisplayName() : r.getName();
        String one = img != null
                ? "<div class=\"" + cls + "\"><img src=\"" + img + "\"/></div>"
                : "<div class=\"" + cls + "\"><div class=\"noimg\">" + esc(label) + "</div></div>";
        int copies = big ? 1 : Math.max(1, r.getQuantity());
        return one.repeat(copies);
    }

    private static String primaryType(String typeLine) {
        String t = typeLine == null ? "" : typeLine;
        for (String known : TYPE_ORDER) {
            if (t.contains(known)) {
                return known;
            }
        }
        return "Outro";
    }

    private static String firstNonBlank(String... vals) {
        for (String v : vals) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }

    private static String capitalize(String s) {
        return s == null || s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase(Locale.ROOT);
    }

    private static String esc(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
