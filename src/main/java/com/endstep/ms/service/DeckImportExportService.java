package com.endstep.ms.service;

import com.endstep.ms.projection.DeckCardRow;
import com.endstep.ms.dto.DeckDtos.ApplyCardsRequest;
import com.endstep.ms.dto.DeckDtos.CreateDeckRequest;
import com.endstep.ms.dto.DeckDtos.DeckDetail;
import com.endstep.ms.dto.DeckDtos.ImportDeckRequest;
import com.endstep.ms.dto.DeckDtos.ImportResult;
import com.endstep.ms.entity.CardOracle;
import com.endstep.ms.entity.DeckCard;
import com.endstep.ms.repository.CardOracleRepository;
import com.endstep.ms.repository.DeckCardRepository;
import com.endstep.ms.repository.DeckRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Serviço de DeckImportExport.
 *
 * @author Kauã Ferreira
 * @since 2026-09-08
 */
@Service
public class DeckImportExportService {
    private static final Pattern QTY_LINE = Pattern.compile("^(\\d{1,3})\\s*[xX]?\\s+(.+)$");
    private static final Pattern SET_SUFFIX = Pattern.compile("\\s+\\([A-Za-z0-9]{2,6}\\)\\s+\\S+\\s*$");
    private static final Pattern FLAG_SUFFIX = Pattern.compile("\\s*\\*[^*]*\\*\\s*$");

    private final DeckService deckService;
    private final DeckRepository decks;
    private final DeckCardRepository deckCards;
    private final CardOracleRepository oracles;
    private final ObjectMapper objectMapper;

    public DeckImportExportService(DeckService deckService, DeckRepository decks, DeckCardRepository deckCards,
                                   CardOracleRepository oracles, ObjectMapper objectMapper) {
        this.deckService = deckService;
        this.decks = decks;
        this.deckCards = deckCards;
        this.oracles = oracles;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ImportResult importText(long userId, ImportDeckRequest req) {
        String deckName = req.name() != null && !req.name().isBlank() ? req.name().trim() : "Deck importado";
        DeckDetail created = deckService.create(userId,
                new CreateDeckRequest(deckName, req.format(), req.folderId()));
        long deckId = created.id();

        List<String> notFound = new ArrayList<>();
        List<String> ambiguous = new ArrayList<>();

        Map<String, int[]> acc = new LinkedHashMap<>();
        Map<String, Long> oracleByKey = new LinkedHashMap<>();

        String section = "MAINBOARD";
        int importedLines = 0;

        for (String raw : req.text().split("\\r?\\n")) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("//") || line.startsWith("#")) {
                continue;
            }
            String lower = line.toLowerCase(Locale.ROOT);
            String header = sectionHeader(lower);
            if (header != null) {
                section = header;
                continue;
            }

            String work = line;
            String lineSection = section;
            if (lower.startsWith("sb:")) {
                lineSection = "SIDEBOARD";
                work = line.substring(3).trim();
            }

            int qty = 1;
            String name = work;
            Matcher m = QTY_LINE.matcher(work);
            if (m.matches()) {
                qty = Integer.parseInt(m.group(1));
                name = m.group(2).trim();
            }
            name = FLAG_SUFFIX.matcher(name).replaceAll("").trim();
            name = SET_SUFFIX.matcher(name).replaceAll("").trim();
            if (name.isEmpty()) {
                continue;
            }
            importedLines++;

            CardOracle oracle = resolve(name);
            if (oracle == null) {
                notFound.add(name);
                continue;
            }
            if (oracle.getId() == null) {
                ambiguous.add(name);
                continue;
            }
            String key = lineSection + "|" + oracle.getId();
            acc.computeIfAbsent(key, k -> new int[]{0})[0] += qty;
            oracleByKey.put(key, oracle.getId());
        }

        List<DeckCard> toSave = new ArrayList<>();
        int importedCards = 0;
        for (Map.Entry<String, int[]> e : acc.entrySet()) {
            String[] parts = e.getKey().split("\\|", 2);
            DeckCard dc = new DeckCard();
            dc.setDeckId(deckId);
            dc.setOracleCardId(oracleByKey.get(e.getKey()));
            dc.setSection(parts[0]);
            dc.setQuantity(Math.min(e.getValue()[0], 99));
            toSave.add(dc);
            importedCards += dc.getQuantity();
        }
        deckCards.saveAll(toSave);

        deckService.applyCards(userId, deckId, new ApplyCardsRequest(List.of()));

        return new ImportResult(deckId, importedLines, importedCards,
                notFound.stream().distinct().toList(), ambiguous.stream().distinct().toList());
    }

    private static String sectionHeader(String lower) {
        return switch (lower) {
            case "commander", "commanders", "comandante" -> "COMMANDER";
            case "deck", "mainboard", "main", "decklist" -> "MAINBOARD";
            case "sideboard", "sb", "reserva" -> "SIDEBOARD";
            case "maybeboard", "maybe", "considerando" -> "MAYBEBOARD";
            case "companion" -> "SIDEBOARD";
            default -> null;
        };
    }

    private CardOracle resolve(String name) {
        List<CardOracle> exact = oracles.findAllByNameIgnoreCase(name);
        if (exact.size() == 1) {
            return exact.get(0);
        }
        if (exact.size() > 1) {
            return ambiguous();
        }
        if (name.contains(" // ")) {
            String front = name.split(" // ", 2)[0].trim();
            List<CardOracle> byFront = oracles.findAllByNameIgnoreCase(front);
            if (byFront.size() == 1) {
                return byFront.get(0);
            }
            if (byFront.size() > 1) {
                return ambiguous();
            }
        }
        List<CardOracle> dfc = oracles.findByFrontFaceName(name);
        if (dfc.size() == 1) {
            return dfc.get(0);
        }
        if (dfc.size() > 1) {
            return ambiguous();
        }
        return null;
    }

    private static CardOracle ambiguous() {
        CardOracle sentinel = new CardOracle();
        sentinel.setId(null);
        return sentinel;
    }

    public record Export(String filename, String contentType, String body) {
    }

    @Transactional(readOnly = true)
    public Export export(long userId, long deckId, String fmt) {
        var deck = deckService.requireDeck(userId, deckId);
        List<DeckCardRow> rows = deckCards.loadRows(deckId, deck.getFormat());
        String slug = deck.getName().replaceAll("[^A-Za-z0-9]+", "_").replaceAll("^_+|_+$", "");
        if (slug.isEmpty()) {
            slug = "deck";
        }

        return switch (fmt == null ? "txt" : fmt.toLowerCase(Locale.ROOT)) {
            case "csv" -> new Export(slug + ".csv", "text/csv", toCsv(rows));
            case "json" -> new Export(slug + ".json", "application/json", toJson(deck.getName(), deck.getFormat(), rows));
            default -> new Export(slug + ".txt", "text/plain", toTxt(rows));
        };
    }

    private static String toTxt(List<DeckCardRow> rows) {
        StringBuilder sb = new StringBuilder();
        appendSection(sb, "Commander", rows, "COMMANDER");
        appendSection(sb, "Deck", rows, "MAINBOARD");
        appendSection(sb, "Sideboard", rows, "SIDEBOARD");
        appendSection(sb, "Maybeboard", rows, "MAYBEBOARD");
        return sb.toString().trim() + "\n";
    }

    private static void appendSection(StringBuilder sb, String label, List<DeckCardRow> rows, String section) {
        List<DeckCardRow> sec = rows.stream().filter(r -> section.equals(r.getSection())).toList();
        if (sec.isEmpty()) {
            return;
        }
        sb.append(label).append('\n');
        for (DeckCardRow r : sec) {
            sb.append(r.getQuantity()).append(' ').append(r.getName()).append('\n');
        }
        sb.append('\n');
    }

    private static String toCsv(List<DeckCardRow> rows) {
        StringBuilder sb = new StringBuilder("section,quantity,name,mana_value,type_line\n");
        for (DeckCardRow r : rows) {
            sb.append(r.getSection()).append(',')
                    .append(r.getQuantity()).append(',')
                    .append(csv(r.getName())).append(',')
                    .append(r.getManaValue() == null ? "" : r.getManaValue()).append(',')
                    .append(csv(r.getTypeLine())).append('\n');
        }
        return sb.toString();
    }

    private static String csv(String v) {
        if (v == null) {
            return "";
        }
        if (v.contains(",") || v.contains("\"") || v.contains("\n")) {
            return "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }

    private String toJson(String name, String format, List<DeckCardRow> rows) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("name", name);
        out.put("format", format);
        for (String section : List.of("COMMANDER", "MAINBOARD", "SIDEBOARD", "MAYBEBOARD")) {
            List<Map<String, Object>> list = rows.stream()
                    .filter(r -> section.equals(r.getSection()))
                    .map(r -> {
                        Map<String, Object> e = new LinkedHashMap<>();
                        e.put("name", r.getName());
                        e.put("quantity", r.getQuantity());
                        return e;
                    })
                    .toList();
            out.put(section.toLowerCase(Locale.ROOT), list);
        }
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(out);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Falha ao gerar JSON");
        }
    }
}
