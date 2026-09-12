package com.endstep.ms.controller;

import com.endstep.ms.dto.DeckDtos.ApplyCardsRequest;
import com.endstep.ms.dto.DeckDtos.CreateDeckRequest;
import com.endstep.ms.dto.DeckDtos.DeckDetail;
import com.endstep.ms.dto.DeckDtos.DeckSummary;
import com.endstep.ms.dto.DeckDtos.ImportDeckRequest;
import com.endstep.ms.dto.DeckDtos.ImportResult;
import com.endstep.ms.dto.DeckDtos.UpdateDeckRequest;
import com.endstep.ms.dto.CustomArtDtos.SetDeckCardArtRequest;
import com.endstep.ms.dto.ValidationResult;
import com.endstep.ms.service.AuthPrincipal;
import com.endstep.ms.service.DeckImportExportService;
import com.endstep.ms.service.DeckImportExportService.Export;
import com.endstep.ms.service.DeckPdfService;
import com.endstep.ms.service.DeckService;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Endpoints REST de Deck.
 *
 * @author Kauã Ferreira
 * @since 2026-09-08
 */
@RestController
@RequestMapping("/api/decks")
public class DeckController {
    private final DeckService decks;
    private final DeckImportExportService importExport;
    private final DeckPdfService pdf;

    public DeckController(DeckService decks, DeckImportExportService importExport, DeckPdfService pdf) {
        this.decks = decks;
        this.importExport = importExport;
        this.pdf = pdf;
    }

    @GetMapping
    public List<DeckSummary> list(@AuthenticationPrincipal AuthPrincipal me) {
        return decks.list(me.id());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DeckDetail create(@AuthenticationPrincipal AuthPrincipal me,
                             @Valid @RequestBody CreateDeckRequest req) {
        return decks.create(me.id(), req);
    }

    @GetMapping("/{id}")
    public DeckDetail get(@AuthenticationPrincipal AuthPrincipal me, @PathVariable long id) {
        return decks.detail(me.id(), id);
    }

    @PutMapping("/{id}")
    public DeckDetail update(@AuthenticationPrincipal AuthPrincipal me,
                             @PathVariable long id,
                             @Valid @RequestBody UpdateDeckRequest req) {
        return decks.update(me.id(), id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthPrincipal me, @PathVariable long id) {
        decks.delete(me.id(), id);
    }

    @PostMapping("/{id}/duplicate")
    @ResponseStatus(HttpStatus.CREATED)
    public DeckDetail duplicate(@AuthenticationPrincipal AuthPrincipal me, @PathVariable long id) {
        return decks.duplicate(me.id(), id);
    }

    @PutMapping("/{id}/cards")
    public DeckDetail applyCards(@AuthenticationPrincipal AuthPrincipal me,
                                @PathVariable long id,
                                @Valid @RequestBody ApplyCardsRequest req) {
        return decks.applyCards(me.id(), id, req);
    }

    @GetMapping("/{id}/validate")
    public ValidationResult validate(@AuthenticationPrincipal AuthPrincipal me, @PathVariable long id) {
        return decks.detail(me.id(), id).validation();
    }

    @PutMapping("/{id}/cards/art")
    public DeckDetail setCardArt(@AuthenticationPrincipal AuthPrincipal me,
                                 @PathVariable long id,
                                 @RequestBody SetDeckCardArtRequest req) {
        return decks.setCardArt(me.id(), id, req);
    }

    @PostMapping("/import")
    @ResponseStatus(HttpStatus.CREATED)
    public ImportResult importDeck(@AuthenticationPrincipal AuthPrincipal me,
                                   @Valid @RequestBody ImportDeckRequest req) {
        return importExport.importText(me.id(), req);
    }

    @GetMapping("/{id}/export")
    public ResponseEntity<byte[]> export(@AuthenticationPrincipal AuthPrincipal me,
                                         @PathVariable long id,
                                         @RequestParam(defaultValue = "txt") String format) {
        if ("pdf".equalsIgnoreCase(format) || "pdf-proxy".equalsIgnoreCase(format)) {
            boolean proxy = "pdf-proxy".equalsIgnoreCase(format);
            DeckDetail d = decks.detail(me.id(), id);
            byte[] body = proxy ? pdf.renderProxy(me.id(), id) : pdf.render(me.id(), id);
            String filename = slug(d.name()) + (proxy ? "_proxy" : "") + ".pdf";
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            ContentDisposition.attachment().filename(filename).build().toString())
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(body);
        }
        Export e = importExport.export(me.id(), id, format);
        byte[] body = e.body().getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(e.filename()).build().toString())
                .contentType(MediaType.parseMediaType(e.contentType() + ";charset=UTF-8"))
                .body(body);
    }

    private static String slug(String name) {
        String s = name == null ? "" : name.replaceAll("[^A-Za-z0-9]+", "_").replaceAll("^_+|_+$", "");
        return s.isEmpty() ? "deck" : s;
    }
}
