package com.endstep.ms.service;
import com.endstep.ms.common.SyncCounters;

import com.endstep.ms.entity.CardFace;
import com.endstep.ms.entity.CardOracle;
import com.endstep.ms.entity.CardPrinting;
import com.endstep.ms.entity.Legality;
import com.endstep.ms.entity.Ruling;
import com.endstep.ms.entity.SetEntity;
import com.endstep.ms.projection.OracleIdMapping;
import com.endstep.ms.repository.CardFaceRepository;
import com.endstep.ms.repository.CardOracleRepository;
import com.endstep.ms.repository.CardPrintingRepository;
import com.endstep.ms.repository.LegalityRepository;
import com.endstep.ms.repository.RulingRepository;
import com.endstep.ms.repository.SetRepository;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static com.endstep.ms.scryfall.ScryfallJson.bigDecimalOrNull;
import static com.endstep.ms.scryfall.ScryfallJson.dateOrNull;
import static com.endstep.ms.scryfall.ScryfallJson.firstFaceOrSelf;
import static com.endstep.ms.scryfall.ScryfallJson.imageNode;
import static com.endstep.ms.scryfall.ScryfallJson.imageUri;
import static com.endstep.ms.scryfall.ScryfallJson.integerOrNull;
import static com.endstep.ms.scryfall.ScryfallJson.joinArray;
import static com.endstep.ms.scryfall.ScryfallJson.resolveOracleId;
import static com.endstep.ms.scryfall.ScryfallJson.str;
import static com.endstep.ms.scryfall.ScryfallJson.strOr;
import static com.endstep.ms.scryfall.ScryfallJson.uuidOrNull;

/**
 * Grava cartas do Scryfall via Spring Data JPA.
 *
 * <p>Estrategia de performance: as gravacoes acontecem em lotes ({@link #ingestBatch})
 * numa unica transacao com {@code synchronous_commit=off} (a base de cartas e
 * reconstruivel, entao vale trocar durabilidade por velocidade). Se um lote falha,
 * o chamador reprocessa carta a carta com {@link #ingestCard} para isolar o registro ruim.
 *
 * @author Kauã Ferreira
 * @since 2026-09-08
 */
@Service
public class CardIngestService {
    private static final int CLEAR_EVERY = 100;

    private final SetRepository setRepo;
    private final CardOracleRepository oracleRepo;
    private final CardPrintingRepository printingRepo;
    private final CardFaceRepository faceRepo;
    private final LegalityRepository legalityRepo;
    private final RulingRepository rulingRepo;

    @PersistenceContext
    private EntityManager em;

    public CardIngestService(SetRepository setRepo,
                             CardOracleRepository oracleRepo,
                             CardPrintingRepository printingRepo,
                             CardFaceRepository faceRepo,
                             LegalityRepository legalityRepo,
                             RulingRepository rulingRepo) {
        this.setRepo = setRepo;
        this.oracleRepo = oracleRepo;
        this.printingRepo = printingRepo;
        this.faceRepo = faceRepo;
        this.legalityRepo = legalityRepo;
        this.rulingRepo = rulingRepo;
    }

    private record PrintingResult(long id, boolean inserted) {
    }

    private record CardOutcome(boolean skipped, boolean printingInserted, int faces) {
        static final CardOutcome SKIPPED = new CardOutcome(true, false, 0);
    }

    public record BatchResult(int inserted, int updated, int faces, int skipped,
                              Set<UUID> oraclesTouched) {
    }

    @Transactional
    public BatchResult ingestBatch(List<JsonNode> cards, Set<UUID> sharedDone) {
        relaxDurability();
        Set<UUID> doneView = new HashSet<>(sharedDone);
        Set<UUID> touched = new LinkedHashSet<>();
        int inserted = 0;
        int updated = 0;
        int faces = 0;
        int skipped = 0;
        int i = 0;
        for (JsonNode card : cards) {
            CardOutcome o = ingestOne(card, doneView, touched);
            if (o.skipped()) {
                skipped++;
            } else {
                if (o.printingInserted()) {
                    inserted++;
                } else {
                    updated++;
                }
                faces += o.faces();
            }
            if (++i % CLEAR_EVERY == 0) {
                em.flush();
                em.clear();
            }
        }
        return new BatchResult(inserted, updated, faces, skipped, touched);
    }

    @Transactional
    public void ingestCard(JsonNode card, Set<UUID> sharedDone, SyncCounters c) {
        relaxDurability();
        Set<UUID> touched = new LinkedHashSet<>();
        CardOutcome o = ingestOne(card, sharedDone, touched);
        if (o.skipped()) {
            throw new SkippedCardException("carta ignorada: " + str(card, "name"));
        }
        if (o.printingInserted()) {
            c.printingsInserted++;
        } else {
            c.printingsUpdated++;
        }
        c.facesWritten += o.faces();
        sharedDone.addAll(touched);
        c.oraclesTouched += touched.size();
    }

    private CardOutcome ingestOne(JsonNode card, Set<UUID> doneView, Set<UUID> touchedOut) {
        if (!"card".equals(card.path("object").asText("card"))) {
            return CardOutcome.SKIPPED;
        }
        UUID oracleId = resolveOracleId(card);
        UUID scryfallId = uuidOrNull(card, "id");
        if (oracleId == null || scryfallId == null) {
            return CardOutcome.SKIPPED;
        }

        Long setId = upsertSet(card);
        CardOracle oracle = upsertOracle(card, oracleId);
        PrintingResult printing = upsertPrinting(card, scryfallId, oracle.getId(), setId);
        int faces = replaceFaces(printing.id(), card);

        if (!doneView.contains(oracleId) && !touchedOut.contains(oracleId)) {
            replaceLegalities(oracle.getId(), card.get("legalities"));
            touchedOut.add(oracleId);
        }
        return new CardOutcome(false, printing.inserted(), faces);
    }

    private Long upsertSet(JsonNode card) {
        String code = str(card, "set");
        if (code == null) {
            return null;
        }
        SetEntity set = setRepo.findByCode(code).orElseGet(SetEntity::new);
        set.setCode(code);
        set.setName(strOr(card, "set_name", code));
        set.setSetType(coalesce(str(card, "set_type"), set.getSetType()));
        set.setReleasedAt(coalesce(dateOrNull(card, "released_at"), set.getReleasedAt()));
        set.setScryfallId(coalesce(uuidOrNull(card, "set_id"), set.getScryfallId()));
        return setRepo.save(set).getId();
    }

    private CardOracle upsertOracle(JsonNode card, UUID oracleId) {
        CardOracle o = oracleRepo.findByOracleId(oracleId).orElseGet(CardOracle::new);
        JsonNode faceForOracle = firstFaceOrSelf(card);
        o.setOracleId(oracleId);
        o.setName(strOr(card, "name", "?"));
        o.setManaCost(str(card, "mana_cost"));
        o.setManaValue(bigDecimalOrNull(card, "cmc"));
        o.setTypeLine(str(card, "type_line"));
        o.setOracleText(coalesce(str(card, "oracle_text"), str(faceForOracle, "oracle_text")));
        o.setColors(joinArray(card.get("colors"), ""));
        o.setColorIdentity(joinArray(card.get("color_identity"), ""));
        o.setPower(str(card, "power"));
        o.setToughness(str(card, "toughness"));
        o.setLoyalty(str(card, "loyalty"));
        o.setKeywords(joinArray(card.get("keywords"), ", "));
        o.setLayout(str(card, "layout"));
        o.setReserved(card.path("reserved").asBoolean(false));
        o.setEdhrecRank(integerOrNull(card, "edhrec_rank"));
        return oracleRepo.save(o);
    }

    private PrintingResult upsertPrinting(JsonNode card, UUID scryfallId, long oracleCardId, Long setId) {
        Optional<CardPrinting> existing = printingRepo.findByScryfallId(scryfallId);
        CardPrinting p = existing.orElseGet(CardPrinting::new);
        boolean inserted = existing.isEmpty();

        JsonNode img = imageNode(card);
        p.setScryfallId(scryfallId);
        p.setOracleCardId(oracleCardId);
        p.setSetId(setId);
        p.setSetCode(str(card, "set"));
        p.setCollectorNumber(str(card, "collector_number"));
        p.setRarity(str(card, "rarity"));
        p.setArtist(str(card, "artist"));
        p.setFlavorText(str(card, "flavor_text"));
        p.setLang(strOr(card, "lang", "en"));
        p.setLayout(str(card, "layout"));
        p.setBorderColor(str(card, "border_color"));
        p.setFrame(str(card, "frame"));
        p.setPromo(card.path("promo").asBoolean(false));
        p.setVariation(card.path("variation").asBoolean(false));
        p.setFullArt(card.path("full_art").asBoolean(false));
        p.setImageSmall(imageUri(img, "small"));
        p.setImageNormal(imageUri(img, "normal"));
        p.setImageLarge(imageUri(img, "large"));
        p.setImagePng(imageUri(img, "png"));
        p.setImageArtCrop(imageUri(img, "art_crop"));
        p.setImageBorderCrop(imageUri(img, "border_crop"));
        p.setReleasedAt(dateOrNull(card, "released_at"));
        p.setScryfallUri(str(card, "scryfall_uri"));

        CardPrinting saved = printingRepo.save(p);
        return new PrintingResult(saved.getId(), inserted);
    }

    private int replaceFaces(long printingId, JsonNode card) {
        faceRepo.deleteByPrintingId(printingId);
        JsonNode faces = card.get("card_faces");
        if (faces == null || !faces.isArray() || faces.isEmpty()) {
            return 0;
        }
        List<CardFace> toSave = new ArrayList<>();
        int index = 0;
        for (JsonNode face : faces) {
            JsonNode img = face.get("image_uris");
            CardFace cf = new CardFace();
            cf.setPrintingId(printingId);
            cf.setFaceIndex(index++);
            cf.setName(strOr(face, "name", "?"));
            cf.setManaCost(str(face, "mana_cost"));
            cf.setTypeLine(str(face, "type_line"));
            cf.setOracleText(str(face, "oracle_text"));
            cf.setColors(joinArray(face.get("colors"), ""));
            cf.setPower(str(face, "power"));
            cf.setToughness(str(face, "toughness"));
            cf.setLoyalty(str(face, "loyalty"));
            cf.setImageSmall(imageUri(img, "small"));
            cf.setImageNormal(imageUri(img, "normal"));
            cf.setImageLarge(imageUri(img, "large"));
            cf.setImagePng(imageUri(img, "png"));
            toSave.add(cf);
        }
        faceRepo.saveAll(toSave);
        return toSave.size();
    }

    private void replaceLegalities(long oracleCardId, JsonNode legalities) {
        legalityRepo.deleteByOracleCardId(oracleCardId);
        if (legalities == null || !legalities.isObject()) {
            return;
        }
        List<Legality> toSave = new ArrayList<>();
        Iterator<Map.Entry<String, JsonNode>> it = legalities.fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> e = it.next();
            Legality l = new Legality();
            l.setOracleCardId(oracleCardId);
            l.setFormat(e.getKey());
            l.setStatus(e.getValue().asText("not_legal"));
            toSave.add(l);
        }
        legalityRepo.saveAll(toSave);
    }

    @Transactional
    public void clearRulings() {
        rulingRepo.deleteAllRulings();
    }

    @Transactional(readOnly = true)
    public Map<UUID, Long> loadOracleIdIndex() {
        Map<UUID, Long> index = new HashMap<>(120_000);
        for (OracleIdMapping row : oracleRepo.findAllIdMappings()) {
            index.put(row.getOracleId(), row.getId());
        }
        return index;
    }

    @Transactional
    public int saveRulingsBatch(List<JsonNode> nodes, Map<UUID, Long> oracleIdIndex) {
        relaxDurability();
        List<Ruling> toSave = new ArrayList<>(nodes.size());
        for (JsonNode r : nodes) {
            UUID oracleId = uuidOrNull(r, "oracle_id");
            if (oracleId == null) {
                continue;
            }
            Long oracleCardId = oracleIdIndex.get(oracleId);
            if (oracleCardId == null) {
                continue;
            }
            Ruling ruling = new Ruling();
            ruling.setOracleCardId(oracleCardId);
            ruling.setSource(str(r, "source"));
            ruling.setPublishedAt(dateOrNull(r, "published_at"));
            ruling.setComment(strOr(r, "comment", ""));
            toSave.add(ruling);
        }
        rulingRepo.saveAll(toSave);
        return toSave.size();
    }

    private void relaxDurability() {
        em.createNativeQuery("set local synchronous_commit = off").executeUpdate();
    }

    private static <T> T coalesce(T primary, T fallback) {
        return primary != null ? primary : fallback;
    }
}
