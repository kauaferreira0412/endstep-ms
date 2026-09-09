package com.endstep.ms.service;

import com.endstep.ms.projection.DeckCardRow;
import com.endstep.ms.dto.CustomArtDtos.SetDeckCardArtRequest;
import com.endstep.ms.dto.DeckDtos.ApplyCardsRequest;
import com.endstep.ms.dto.DeckDtos.CreateDeckRequest;
import com.endstep.ms.dto.DeckDtos.CurvePoint;
import com.endstep.ms.dto.DeckDtos.DeckCardChange;
import com.endstep.ms.dto.DeckDtos.DeckCardView;
import com.endstep.ms.dto.DeckDtos.DeckDetail;
import com.endstep.ms.dto.DeckDtos.DeckStats;
import com.endstep.ms.dto.DeckDtos.DeckSummary;
import com.endstep.ms.dto.DeckDtos.UpdateDeckRequest;
import com.endstep.ms.dto.FormatView;
import com.endstep.ms.dto.ValidationResult;
import com.endstep.ms.entity.Deck;
import com.endstep.ms.entity.DeckCard;
import com.endstep.ms.entity.DeckFolder;
import com.endstep.ms.entity.FormatEntity;
import com.endstep.ms.repository.CardOracleRepository;
import com.endstep.ms.repository.CustomArtRepository;
import com.endstep.ms.repository.DeckCardRepository;
import com.endstep.ms.repository.DeckFolderRepository;
import com.endstep.ms.repository.DeckRepository;
import com.endstep.ms.repository.FormatRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Serviço de Deck.
 *
 * @author Kauã Ferreira
 * @since 2026-09-08
 */
@Service
public class DeckService {
    private static final int MAX_QTY = 99;
    private static final List<String> KNOWN_SECTIONS = List.of("COMMANDER", "MAINBOARD", "SIDEBOARD", "MAYBEBOARD");
    private static final List<String> TYPE_ORDER = List.of(
            "Creature", "Planeswalker", "Instant", "Sorcery", "Artifact", "Enchantment", "Battle", "Land");

    private final DeckRepository decks;
    private final DeckCardRepository deckCards;
    private final DeckFolderRepository folders;
    private final FormatRepository formats;
    private final CardOracleRepository oracles;
    private final CustomArtRepository customArts;
    private final DeckValidationService validation;

    public DeckService(DeckRepository decks, DeckCardRepository deckCards, DeckFolderRepository folders,
                       FormatRepository formats, CardOracleRepository oracles, CustomArtRepository customArts,
                       DeckValidationService validation) {
        this.decks = decks;
        this.deckCards = deckCards;
        this.folders = folders;
        this.formats = formats;
        this.oracles = oracles;
        this.customArts = customArts;
        this.validation = validation;
    }

    @Transactional(readOnly = true)
    public List<DeckSummary> list(long userId) {
        List<Deck> all = decks.findByUserIdOrderByPositionAscNameAsc(userId);
        if (all.isEmpty()) {
            return List.of();
        }
        Map<Long, Integer> counts = new LinkedHashMap<>();
        for (DeckCard dc : deckCards.findByDeckIdIn(all.stream().map(Deck::getId).toList())) {
            if ("COMMANDER".equals(dc.getSection()) || "MAINBOARD".equals(dc.getSection())) {
                counts.merge(dc.getDeckId(), dc.getQuantity(), Integer::sum);
            }
        }
        return all.stream()
                .map(d -> new DeckSummary(d.getId(), d.getFolderId(), d.getName(), d.getFormat(), d.getVisibility(),
                        d.getColorIdentity(), d.isFavorite(), d.getPosition(),
                        counts.getOrDefault(d.getId(), 0), d.getUpdatedAt()))
                .toList();
    }

    @Transactional
    public DeckDetail create(long userId, CreateDeckRequest req) {
        FormatEntity fmt = requireFormat(req.format());
        if (req.folderId() != null && req.folderId() != 0) {
            requireFolder(userId, req.folderId());
        }
        Deck d = new Deck();
        d.setUserId(userId);
        d.setFolderId(req.folderId() == null || req.folderId() == 0 ? null : req.folderId());
        d.setName(req.name().trim());
        d.setFormat(fmt.getCode());
        d.setPosition(decks.findByUserIdOrderByPositionAscNameAsc(userId).size());
        d = decks.save(d);
        return detail(userId, d.getId());
    }

    @Transactional
    public DeckDetail update(long userId, long deckId, UpdateDeckRequest req) {
        Deck d = requireDeck(userId, deckId);
        if (req.name() != null && !req.name().isBlank()) {
            d.setName(req.name().trim());
        }
        if (req.format() != null && !req.format().isBlank()) {
            d.setFormat(requireFormat(req.format()).getCode());
        }
        if (req.folderId() != null) {
            if (req.folderId() == 0) {
                d.setFolderId(null);
            } else {
                requireFolder(userId, req.folderId());
                d.setFolderId(req.folderId());
            }
        }
        if (req.description() != null) {
            d.setDescription(req.description().isBlank() ? null : req.description());
        }
        if (req.visibility() != null) {
            d.setVisibility(Deck.Visibility.valueOf(req.visibility().toUpperCase(Locale.ROOT)).name());
        }
        if (req.favorite() != null) {
            d.setFavorite(req.favorite());
        }
        if (req.position() != null) {
            d.setPosition(req.position());
        }
        decks.save(d);
        return detail(userId, deckId);
    }

    @Transactional
    public void delete(long userId, long deckId) {
        requireDeck(userId, deckId);
        decks.deleteById(deckId);
    }

    @Transactional
    public DeckDetail duplicate(long userId, long deckId) {
        Deck src = requireDeck(userId, deckId);
        Deck copy = new Deck();
        copy.setUserId(userId);
        copy.setFolderId(src.getFolderId());
        copy.setName(src.getName() + " (cópia)");
        copy.setFormat(src.getFormat());
        copy.setDescription(src.getDescription());
        copy.setVisibility(src.getVisibility());
        copy.setColorIdentity(src.getColorIdentity());
        copy.setPosition(decks.findByUserIdOrderByPositionAscNameAsc(userId).size());
        copy = decks.save(copy);

        List<DeckCard> newCards = new ArrayList<>();
        for (DeckCard c : deckCards.findByDeckId(deckId)) {
            DeckCard nc = new DeckCard();
            nc.setDeckId(copy.getId());
            nc.setOracleCardId(c.getOracleCardId());
            nc.setPrintingId(c.getPrintingId());
            nc.setQuantity(c.getQuantity());
            nc.setSection(c.getSection());
            newCards.add(nc);
        }
        deckCards.saveAll(newCards);
        return detail(userId, copy.getId());
    }

    @Transactional
    public DeckDetail applyCards(long userId, long deckId, ApplyCardsRequest req) {
        Deck d = requireDeck(userId, deckId);
        if (req.changes() != null) {
            for (DeckCardChange ch : req.changes()) {
                applyOne(deckId, ch);
            }
        }
        recomputeColorIdentity(d);
        decks.save(d);
        return detail(userId, deckId);
    }

    @Transactional
    public DeckDetail setCardArt(long userId, long deckId, SetDeckCardArtRequest req) {
        Deck d = requireDeck(userId, deckId);
        String section = req.section() == null ? "MAINBOARD" : req.section().toUpperCase(Locale.ROOT);
        if (!KNOWN_SECTIONS.contains(section)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Seção inválida");
        }
        if (req.printingId() != null && req.customArtId() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Escolha impressão OU arte customizada, não os dois");
        }
        UUID oracleId;
        try {
            oracleId = UUID.fromString(req.oracleId());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "oracleId inválido");
        }
        long oracleDbId = oracles.findByOracleId(oracleId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Carta não encontrada"))
                .getId();
        DeckCard dc = deckCards.findByDeckIdAndOracleCardIdAndSection(deckId, oracleDbId, section)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Adicione a carta ao deck primeiro"));

        if (req.customArtId() != null) {
            var ca = customArts.findByIdAndUserId(req.customArtId(), userId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Arte não encontrada"));
            if (!ca.getOracleCardId().equals(oracleDbId)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Essa arte é de outra carta");
            }
        }
        dc.setPrintingId(req.printingId());
        dc.setCustomArtId(req.customArtId());
        deckCards.save(dc);
        decks.save(d);
        return detail(userId, deckId);
    }

    private void applyOne(long deckId, DeckCardChange ch) {
        String section = ch.section().toUpperCase(Locale.ROOT);
        if (!KNOWN_SECTIONS.contains(section)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Seção inválida: " + ch.section());
        }
        UUID oracleId;
        try {
            oracleId = UUID.fromString(ch.oracleId());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "oracleId inválido");
        }
        Long oracleDbId = oracles.findByOracleId(oracleId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Carta não encontrada"))
                .getId();

        var existing = deckCards.findByDeckIdAndOracleCardIdAndSection(deckId, oracleDbId, section);
        int qty = Math.min(Math.max(ch.quantity(), 0), MAX_QTY);
        if (qty <= 0) {
            existing.ifPresent(deckCards::delete);
            return;
        }
        DeckCard dc = existing.orElseGet(DeckCard::new);
        dc.setDeckId(deckId);
        dc.setOracleCardId(oracleDbId);
        dc.setSection(section);
        dc.setQuantity(qty);
        dc.setPrintingId(ch.printingId());
        deckCards.save(dc);
    }

    private void recomputeColorIdentity(Deck d) {
        TreeSet<Character> ci = new TreeSet<>();
        for (DeckCardRow r : deckCards.loadRows(d.getId(), d.getFormat())) {
            if (!"COMMANDER".equals(r.getSection()) && !"MAINBOARD".equals(r.getSection())) {
                continue;
            }
            for (char c : String.valueOf(r.getColorIdentity() == null ? "" : r.getColorIdentity())
                    .toUpperCase(Locale.ROOT).toCharArray()) {
                if ("WUBRG".indexOf(c) >= 0) {
                    ci.add(c);
                }
            }
        }
        StringBuilder sb = new StringBuilder();
        for (char c : "WUBRG".toCharArray()) {
            if (ci.contains(c)) {
                sb.append(c);
            }
        }
        d.setColorIdentity(sb.toString());
    }

    @Transactional(readOnly = true)
    public DeckDetail detail(long userId, long deckId) {
        Deck d = requireDeck(userId, deckId);
        FormatEntity fmt = requireFormat(d.getFormat());
        List<DeckCardRow> rows = deckCards.loadRows(deckId, d.getFormat());

        List<DeckCardView> cards = rows.stream().map(r -> new DeckCardView(
                r.getOracleId().toString(), r.getName(), r.getDisplayName(), r.getManaCost(),
                r.getManaValue() == null ? null : r.getManaValue().doubleValue(),
                r.getTypeLine(), r.getColorIdentity(), r.getOracleText(),
                r.getSection(), r.getQuantity(), r.getPrintingId(), r.getCustomArtId(),
                r.getImageSmall(), r.getImageNormal(), r.getImageLarge(),
                r.getFormatStatus(), r.getCommanderStatus()
        )).toList();

        DeckStats stats = computeStats(rows);
        ValidationResult val = validation.validate(fmt, rows);

        return new DeckDetail(d.getId(), d.getFolderId(), folderPath(userId, d.getFolderId()),
                d.getName(), d.getFormat(), d.getDescription(), d.getVisibility(), d.getColorIdentity(),
                d.isFavorite(), FormatView.of(fmt), cards, stats, val, d.getCreatedAt(), d.getUpdatedAt());
    }

    private DeckStats computeStats(List<DeckCardRow> rows) {
        int total = 0, lands = 0;
        TreeMap<Integer, Integer> curve = new TreeMap<>();
        Map<String, Integer> colors = new LinkedHashMap<>();
        for (String c : List.of("W", "U", "B", "R", "G", "C")) {
            colors.put(c, 0);
        }
        Map<String, Integer> types = new LinkedHashMap<>();

        for (DeckCardRow r : rows) {
            if (!"COMMANDER".equals(r.getSection()) && !"MAINBOARD".equals(r.getSection())) {
                continue;
            }
            int q = r.getQuantity();
            total += q;
            String tl = r.getTypeLine() == null ? "" : r.getTypeLine();
            boolean isLand = tl.toLowerCase(Locale.ROOT).contains("land");
            if (isLand) {
                lands += q;
            } else {
                int mv = r.getManaValue() == null ? 0 : r.getManaValue().intValue();
                int bucket = Math.min(mv, 7);
                curve.merge(bucket, q, Integer::sum);
            }
            String ci = r.getColorIdentity() == null ? "" : r.getColorIdentity().toUpperCase(Locale.ROOT);
            if (ci.isEmpty()) {
                colors.merge("C", q, Integer::sum);
            } else {
                for (char ch : ci.toCharArray()) {
                    if (colors.containsKey(String.valueOf(ch))) {
                        colors.merge(String.valueOf(ch), q, Integer::sum);
                    }
                }
            }
            types.merge(primaryType(tl), q, Integer::sum);
        }

        List<CurvePoint> curvePoints = curve.entrySet().stream()
                .map(e -> new CurvePoint(e.getKey(), e.getValue()))
                .toList();
        return new DeckStats(total, total, lands, total - lands, curvePoints, colors, types);
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

    private List<String> folderPath(long userId, Long folderId) {
        if (folderId == null) {
            return List.of();
        }
        List<String> path = new ArrayList<>();
        Long cur = folderId;
        int guard = 0;
        while (cur != null && guard++ < 16) {
            DeckFolder f = folders.findByIdAndUserId(cur, userId).orElse(null);
            if (f == null) {
                break;
            }
            path.add(0, f.getName());
            cur = f.getParentId();
        }
        return path;
    }

    Deck requireDeck(long userId, long deckId) {
        return decks.findByIdAndUserId(deckId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Deck não encontrado"));
    }

    private FormatEntity requireFormat(String code) {
        return formats.findById(code.toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Formato inválido: " + code));
    }

    private DeckFolder requireFolder(long userId, long folderId) {
        return folders.findByIdAndUserId(folderId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pasta não encontrada"));
    }

    String allFormatCodes() {
        return formats.findAll().stream().map(FormatEntity::getCode).collect(Collectors.joining(", "));
    }
}
