package com.endstep.ms.controller;

import com.endstep.ms.dto.FormatView;
import com.endstep.ms.repository.FormatRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Endpoints REST de Format.
 *
 * @author Kauã Ferreira
 * @since 2026-09-08
 */
@RestController
@RequestMapping("/api/formats")
public class FormatController {
    private final FormatRepository formats;

    public FormatController(FormatRepository formats) {
        this.formats = formats;
    }

    @GetMapping
    public List<FormatView> list() {
        return formats.findAllByOrderBySortOrderAsc().stream().map(FormatView::of).toList();
    }
}
