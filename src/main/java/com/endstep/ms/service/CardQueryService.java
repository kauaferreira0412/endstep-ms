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
import com.endstep.ms.projection.CardSummaryProjection;
import com.endstep.ms.projection.PrintingProjection;
import com.endstep.ms.repository.CardFaceRepository;
import com.endstep.ms.repository.CardOracleRepository;
import com.endstep.ms.repository.CardPrintingRepository;
import com.endstep.ms.repository.LegalityRepository;
import com.endstep.ms.repository.RulingRepository;
import com.endstep.ms.repository.SetRepository;
import com.endstep.ms.common.NotFoundException;
import com.endstep.ms.common.PageResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    public PageResponse<CardSummary> search(String q, int page, int size) {
        List<CardSummary> content = oracleRepo.search(q, size, (long) page * size).stream()
                .map(CardQueryService::toSummary)
                .toList();
        long total = oracleRepo.countSearch(q);
        return PageResponse.of(content, page, size, total);
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

    private static CardSummary toSummary(CardSummaryProjection p) {
        return new CardSummary(
                p.getOracleId(), p.getName(), p.getManaCost(), p.getManaValue(), p.getTypeLine(),
                p.getColorIdentity(), p.getPrintingCount(), p.getRepresentativePrintingId(),
                p.getSetCode(), p.getImageSmall(), p.getImageNormal());
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
