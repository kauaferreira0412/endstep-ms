package com.endstep.ms.controller;

import com.endstep.ms.dto.CustomArtDtos.CustomArtView;
import com.endstep.ms.service.AuthPrincipal;
import com.endstep.ms.service.CustomArtService;
import com.endstep.ms.service.CustomArtService.CreateArgs;
import com.endstep.ms.service.CustomArtService.UpdateArgs;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Endpoints REST de CustomArt.
 *
 * @author Kauã Ferreira
 * @since 2026-09-08
 */
@RestController
@RequestMapping("/api/custom-arts")
public class CustomArtController {
    private final CustomArtService service;

    public CustomArtController(CustomArtService service) {
        this.service = service;
    }

    @GetMapping
    public List<CustomArtView> list(@AuthenticationPrincipal AuthPrincipal me,
                                    @RequestParam(required = false) String oracleId) {
        return service.list(me.id(), oracleId);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public CustomArtView create(@AuthenticationPrincipal AuthPrincipal me,
                                @RequestParam String oracleId,
                                @RequestParam(required = false) Long basePrintingId,
                                @RequestParam(required = false) String label,
                                @RequestParam(required = false) String displayName,
                                @RequestParam(required = false) String overlayText,
                                @RequestParam(defaultValue = "false") boolean nameBar,
                                @RequestParam(defaultValue = "false") boolean textBar,
                                @RequestParam(defaultValue = "1") double zoom,
                                @RequestParam(defaultValue = "0") double offsetX,
                                @RequestParam(defaultValue = "0") double offsetY,
                                @RequestPart("art") MultipartFile art) {
        return service.create(me.id(), new CreateArgs(oracleId, basePrintingId, label, displayName, overlayText,
                nameBar, textBar, zoom, offsetX, offsetY, art));
    }

    @PutMapping("/{id}")
    public CustomArtView update(@AuthenticationPrincipal AuthPrincipal me,
                                @PathVariable long id,
                                @RequestParam(required = false) String label,
                                @RequestParam(required = false) String displayName,
                                @RequestParam(required = false) String overlayText,
                                @RequestParam(defaultValue = "false") boolean nameBar,
                                @RequestParam(defaultValue = "false") boolean textBar,
                                @RequestParam(defaultValue = "1") double zoom,
                                @RequestParam(defaultValue = "0") double offsetX,
                                @RequestParam(defaultValue = "0") double offsetY) {
        return service.update(me.id(), id, new UpdateArgs(label, displayName, overlayText,
                nameBar, textBar, zoom, offsetX, offsetY));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthPrincipal me, @PathVariable long id) {
        service.delete(me.id(), id);
    }
}
