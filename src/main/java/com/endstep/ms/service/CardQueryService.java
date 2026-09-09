package com.endstep.ms.service;

import com.endstep.ms.dto.CardDetail;
import com.endstep.ms.dto.CardSummary;
import com.endstep.ms.dto.FaceView;
import com.endstep.ms.dto.LegalityView;
import com.endstep.ms.dto.PrintingView;
import com.endstep.ms.dto.RulingView;
import com.endstep.ms.dto.SetView;
import com.endstep.ms.entity.CardOracle;
import com.endstep.ms.entity.SetEntity;
import com.endstep.ms.projection.PrintingProjection;
import com.endstep.ms.repository.CardFaceRepository;
import com.endstep.ms.repository.CardOracleRepository;
import com.endstep.ms.repository.CardPrintingRepository;
import com.endstep.ms.repository.LegalityRepository;
import com.endstep.ms.repository.RulingRepository;
import com.endstep.ms.repository.SetRepository;
import com.endstep.ms.common.NotFoundException;
import com.endstep.ms.common.PageResponse;
import com.endstep.ms.service.CardQueryParser.CardQuery;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Serviço de CardQuery.
 *
 * @author Kauã Ferreira
 * @since 2026-09-08
 */
@Service
@Transactional(readOnly = true)
public class CardQueryService {

    @PersistenceContext
    private EntityManager em;

    private final CardOracleRepository oracleRepo;
    private final CardPrintingRepository printingRepo;
    private final CardFaceRepository faceRepo;
    private final LegalityRepository legalityRepo;
    private final RulingRepository rulingRepo;
    private final SetRepository setRepo;

    public CardQueryService(CardOracleRepository oracleRepo,
                            CardPrintingRepository printingRepo,
                            CardFaceRepository faceRepo,
                            LegalityRepository legalityRepo,
                            RulingRepository rulingRepo,
                            SetRepository setRepo) {
        this.oracleRepo = oracleRepo;
        this.printingRepo = printingRepo;
        this.faceRepo = faceRepo;
        this.legalityRepo = legalityRepo;
        this.rulingRepo = rulingRepo;
        this.setRepo = setRepo;
    }

    private static final String SELECT_ROWS = """
            select o.oracle_id, o.name, o.mana_cost, o.mana_value, o.type_line, o.color_identity,
                   coalesce(p.printing_count, 0), p.printing_id, p.set_code, p.image_small, p.image_normal
            from card_oracles o
            left join lateral (
                select cp.id as printing_id, cp.set_code, cp.image_small, cp.image_normal,
                       (count(*) over ())::int as printing_count
                from card_printings cp
                where cp.oracle_card_id = o.id
                order by (cp.lang = 'en') desc, cp.released_at desc nulls last, cp.id desc
                limit 1
            ) p on true
            where %s
            order by %s
            limit ?%d offset ?%d
            """;

    public PageResponse<CardSummary> search(String q, int page, int size) {
        CardQuery parsed = CardQueryParser.parse(q);
        CardSearchSql b = new CardSearchSql(parsed);
        int n = b.params.size();

        Query rowQ = em.createNativeQuery(
                SELECT_ROWS.formatted(b.where, b.orderBy, n + 1, n + 2));
        bind(rowQ, b.params);
        rowQ.setParameter(n + 1, size);
        rowQ.setParameter(n + 2, (long) page * size);

        @SuppressWarnings("unchecked")
        List<Object[]> rows = rowQ.getResultList();
        List<CardSummary> content = rows.stream().map(CardQueryService::rowToSummary).toList();

        Query countQ = em.createNativeQuery("select count(*) from card_oracles o where " + b.where);
        bind(countQ, b.params);
        long total = ((Number) countQ.getSingleResult()).longValue();

        return PageResponse.of(content, page, size, total);
    }

    private static void bind(Query query, List<Object> params) {
        for (int i = 0; i < params.size(); i++) {
            query.setParameter(i + 1, params.get(i));
        }
    }

    private static CardSummary rowToSummary(Object[] r) {
        return new CardSummary(
                r[0] instanceof UUID u ? u : UUID.fromString(String.valueOf(r[0])),
                (String) r[1],
                (String) r[2],
                r[3] == null ? null
                        : (r[3] instanceof BigDecimal bd ? bd : new BigDecimal(r[3].toString())),
                (String) r[4],
                (String) r[5],
                r[6] == null ? 0 : ((Number) r[6]).intValue(),
                r[7] == null ? null : ((Number) r[7]).longValue(),
                (String) r[8],
                (String) r[9],
                (String) r[10]);
    }

    public CardDetail detail(UUID oracleId) {
        CardOracle o = oracleRepo.findByOracleId(oracleId)
                .orElseThrow(() -> new NotFoundException("Carta nao encontrada: " + oracleId));

        List<LegalityView> legalities = legalityRepo.findByOracleCardIdOrderByFormatAsc(o.getId()).stream()
                .map(l -> new LegalityView(l.getFormat(), l.getStatus()))
                .toList();
        List<RulingView> rulings = rulingRepo.findByOracleCardIdOrderByPublishedAtAsc(o.getId()).stream()
                .map(r -> new RulingView(r.getPublishedAt(), r.getComment()))
                .toList();
        List<PrintingView> printings = printingRepo.findPrintingViews(o.getId()).stream()
                .map(CardQueryService::toPrintingView)
                .toList();
        List<FaceView> faces = faceRepo.findFaceViewsForNewestPrinting(o.getId()).stream()
                .map(f -> new FaceView(f.getFaceIndex(), f.getName(), f.getManaCost(), f.getTypeLine(),
                        f.getOracleText(), f.getImageNormal(), f.getImageLarge()))
                .toList();

        return new CardDetail(
                o.getOracleId(), o.getName(), o.getManaCost(), o.getManaValue(), o.getTypeLine(), o.getOracleText(),
                o.getColors(), o.getColorIdentity(), o.getPower(), o.getToughness(), o.getLoyalty(),
                o.getKeywords(), o.getLayout(),
                legalities, rulings, printings, faces);
    }

    public List<PrintingView> printings(UUID oracleId) {
        CardOracle o = oracleRepo.findByOracleId(oracleId)
                .orElseThrow(() -> new NotFoundException("Carta nao encontrada: " + oracleId));
        return printingRepo.findPrintingViews(o.getId()).stream()
                .map(CardQueryService::toPrintingView)
                .toList();
    }

    public PageResponse<SetView> listSets(int page, int size) {
        Page<SetEntity> result = setRepo.findAllByOrderByReleasedAtDescCodeAsc(PageRequest.of(page, size));
        List<SetView> content = result.getContent().stream()
                .map(CardQueryService::toSetView)
                .toList();
        return PageResponse.of(content, page, size, result.getTotalElements());
    }

    public SetView getSet(String code) {
        return setRepo.findByCodeIgnoreCase(code)
                .map(CardQueryService::toSetView)
                .orElseThrow(() -> new NotFoundException("Set nao encontrado: " + code));
    }

    private static PrintingView toPrintingView(PrintingProjection p) {
        LocalDate releasedAt = p.getReleasedAt() == null ? null : LocalDate.parse(p.getReleasedAt());
        return new PrintingView(
                p.getId(), p.getScryfallId(), p.getSetCode(), p.getSetName(), p.getCollectorNumber(),
                p.getRarity(), p.getArtist(), p.getLang(), releasedAt,
                p.getImageSmall(), p.getImageNormal(), p.getImageLarge(), p.getImagePng(), p.getScryfallUri());
    }

    private static SetView toSetView(SetEntity s) {
        return new SetView(s.getCode(), s.getName(), s.getSetType(), s.getReleasedAt(),
                s.getCardCount(), s.getIconSvgUri());
    }
}
