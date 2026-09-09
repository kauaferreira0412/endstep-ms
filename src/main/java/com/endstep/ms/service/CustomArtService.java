package com.endstep.ms.service;

import com.endstep.ms.config.EndstepProperties;
import com.endstep.ms.dto.CustomArtDtos.CustomArtView;
import com.endstep.ms.entity.CardOracle;
import com.endstep.ms.entity.CardPrinting;
import com.endstep.ms.entity.CustomArt;
import com.endstep.ms.repository.CardOracleRepository;
import com.endstep.ms.repository.CardPrintingRepository;
import com.endstep.ms.repository.CustomArtRepository;
import com.endstep.ms.storage.StorageService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Serviço de CustomArt.
 *
 * @author Kauã Ferreira
 * @since 2026-09-08
 */
@Service
public class CustomArtService {
    private final CustomArtRepository customArts;
    private final CardOracleRepository oracles;
    private final CardPrintingRepository printings;
    private final ImageCompositor compositor;
    private final StorageService storage;
    private final HttpClient http;
    private final String localBase;

    public CustomArtService(CustomArtRepository customArts, CardOracleRepository oracles,
                            CardPrintingRepository printings, ImageCompositor compositor,
                            StorageService storage, HttpClient scryfallHttpClient, EndstepProperties props) {
        this.customArts = customArts;
        this.oracles = oracles;
        this.printings = printings;
        this.compositor = compositor;
        this.storage = storage;
        this.http = scryfallHttpClient;
        String base = props.storage() != null ? props.storage().publicBaseUrl() : null;
        this.localBase = base != null && !base.isBlank() ? base.replaceAll("/+$", "") : "http://localhost:8080";
    }

    public record CreateArgs(String oracleId, Long basePrintingId, String label,
                             String displayName, String overlayText,
                             boolean nameBar, boolean textBar,
                             double zoom, double offsetX, double offsetY,
                             MultipartFile art) {
    }

    public record UpdateArgs(String label, String displayName, String overlayText,
                             boolean nameBar, boolean textBar,
                             double zoom, double offsetX, double offsetY) {
    }

    @Transactional
    public CustomArtView create(long userId, CreateArgs a) {
        CardOracle oracle = oracles.findByOracleId(parseUuid(a.oracleId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Carta não encontrada"));
        if (a.art() == null || a.art().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Envie um arquivo de imagem");
        }

        String baseUrl = baseImageUrl(oracle.getId(), a.basePrintingId());
        byte[] baseBytes = fetch(baseUrl);
        byte[] artBytes = readAll(a.art());

        StorageService.Stored src = storage.put("custom-art",
                orDefault(a.art().getOriginalFilename(), "art"), artBytes, a.art().getContentType());

        ImageCompositor.Result r = compositor.compose(new ImageCompositor.Params(
                baseBytes, artBytes, a.zoom(), a.offsetX(), a.offsetY(),
                a.displayName(), a.overlayText(), a.nameBar(), a.textBar()));

        StorageService.Stored full = storage.put("custom-art", "card.png", r.fullPng(), "image/png");
        StorageService.Stored thumb = storage.put("custom-art", "thumb.png", r.thumbPng(), "image/png");

        CustomArt ca = new CustomArt();
        ca.setUserId(userId);
        ca.setOracleCardId(oracle.getId());
        ca.setBasePrintingId(a.basePrintingId());
        ca.setLabel(blankTo(a.label(), "Arte customizada"));
        ca.setDisplayName(nullIfBlank(a.displayName()));
        ca.setOverlayText(nullIfBlank(a.overlayText()));
        ca.setImageUrl(full.url());
        ca.setThumbUrl(thumb.url());
        ca.setSourceUrl(src.url());
        ca.setArtZoom(BigDecimal.valueOf(a.zoom()));
        ca.setArtOffsetX(BigDecimal.valueOf(a.offsetX()));
        ca.setArtOffsetY(BigDecimal.valueOf(a.offsetY()));
        ca.setNameBar(a.nameBar());
        ca.setTextBar(a.textBar());
        ca = customArts.save(ca);
        return CustomArtView.of(ca, oracle.getOracleId().toString(), oracle.getName());
    }

    @Transactional
    public CustomArtView update(long userId, long id, UpdateArgs a) {
        CustomArt ca = customArts.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Arte não encontrada"));
        CardOracle oracle = oracles.findById(ca.getOracleCardId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Carta não encontrada"));

        if (ca.getSourceUrl() == null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Esta arte não guardou o upload original — recrie para editar a composição");
        }
        byte[] baseBytes = fetch(baseImageUrl(oracle.getId(), ca.getBasePrintingId()));
        byte[] artBytes = fetch(absolute(ca.getSourceUrl()));

        ImageCompositor.Result r = compositor.compose(new ImageCompositor.Params(
                baseBytes, artBytes, a.zoom(), a.offsetX(), a.offsetY(),
                a.displayName(), a.overlayText(), a.nameBar(), a.textBar()));

        storage.delete(ca.getImageUrl());
        storage.delete(ca.getThumbUrl());
        StorageService.Stored full = storage.put("custom-art", "card.png", r.fullPng(), "image/png");
        StorageService.Stored thumb = storage.put("custom-art", "thumb.png", r.thumbPng(), "image/png");

        ca.setLabel(blankTo(a.label(), ca.getLabel()));
        ca.setDisplayName(nullIfBlank(a.displayName()));
        ca.setOverlayText(nullIfBlank(a.overlayText()));
        ca.setImageUrl(full.url());
        ca.setThumbUrl(thumb.url());
        ca.setArtZoom(BigDecimal.valueOf(a.zoom()));
        ca.setArtOffsetX(BigDecimal.valueOf(a.offsetX()));
        ca.setArtOffsetY(BigDecimal.valueOf(a.offsetY()));
        ca.setNameBar(a.nameBar());
        ca.setTextBar(a.textBar());
        customArts.save(ca);
        return CustomArtView.of(ca, oracle.getOracleId().toString(), oracle.getName());
    }

    @Transactional(readOnly = true)
    public List<CustomArtView> list(long userId, String oracleId) {
        List<CustomArt> arts = (oracleId == null || oracleId.isBlank())
                ? customArts.findByUserIdOrderByCreatedAtDesc(userId)
                : customArts.findByUserIdAndOracleCardIdOrderByCreatedAtDesc(userId,
                        oracles.findByOracleId(parseUuid(oracleId))
                                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Carta não encontrada"))
                                .getId());
        Map<Long, CardOracle> byId = new LinkedHashMap<>();
        oracles.findAllById(arts.stream().map(CustomArt::getOracleCardId).distinct().toList())
                .forEach(o -> byId.put(o.getId(), o));
        return arts.stream()
                .map(a -> {
                    CardOracle o = byId.get(a.getOracleCardId());
                    return CustomArtView.of(a,
                            o != null ? o.getOracleId().toString() : null,
                            o != null ? o.getName() : "?");
                })
                .toList();
    }

    @Transactional
    public void delete(long userId, long id) {
        CustomArt ca = customArts.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Arte não encontrada"));
        storage.delete(ca.getImageUrl());
        storage.delete(ca.getThumbUrl());
        storage.delete(ca.getSourceUrl());
        customArts.delete(ca);
    }

    private String baseImageUrl(long oracleDbId, Long basePrintingId) {
        CardPrinting p = basePrintingId != null
                ? printings.findById(basePrintingId).orElse(null)
                : printings.findFirstByOracleCardIdOrderByReleasedAtDescIdDesc(oracleDbId).orElse(null);
        if (p == null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Carta sem impressão com imagem");
        }
        String url = firstNonBlank(p.getImagePng(), p.getImageLarge(), p.getImageNormal());
        if (url == null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Impressão sem imagem");
        }
        return url;
    }

    private byte[] fetch(String url) {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .header("User-Agent", "endstep-ms/0.1 (custom art)")
                    .GET().build();
            HttpResponse<byte[]> resp = http.send(req, HttpResponse.BodyHandlers.ofByteArray());
            if (resp.statusCode() != 200) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Não consegui baixar a imagem base (HTTP " + resp.statusCode() + ")");
            }
            return resp.body();
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Falha ao baixar imagem: " + e.getMessage());
        }
    }

    private String absolute(String url) {
        return url != null && url.startsWith("/") ? localBase + url : url;
    }

    private static byte[] readAll(MultipartFile f) {
        try {
            return f.getBytes();
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Arquivo inválido");
        }
    }

    private static UUID parseUuid(String s) {
        try {
            return UUID.fromString(s);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "oracleId inválido");
        }
    }

    private static String firstNonBlank(String... v) {
        for (String s : v) {
            if (s != null && !s.isBlank()) {
                return s;
            }
        }
        return null;
    }

    private static String nullIfBlank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String blankTo(String s, String fallback) {
        return s == null || s.isBlank() ? fallback : s.trim();
    }

    private static String orDefault(String s, String d) {
        return s == null || s.isBlank() ? d : s;
    }
}
