package com.endstep.ms.controller;

import com.endstep.ms.dto.FolderDtos.CreateFolderRequest;
import com.endstep.ms.dto.FolderDtos.FolderView;
import com.endstep.ms.dto.FolderDtos.UpdateFolderRequest;
import com.endstep.ms.service.AuthPrincipal;
import com.endstep.ms.service.FolderService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Endpoints REST de DeckFolder.
 *
 * @author Kauã Ferreira
 * @since 2026-09-08
 */
@RestController
@RequestMapping("/api/deck-folders")
public class DeckFolderController {
    private final FolderService folders;

    public DeckFolderController(FolderService folders) {
        this.folders = folders;
    }

    @GetMapping
    public List<FolderView> list(@AuthenticationPrincipal AuthPrincipal me) {
        return folders.list(me.id());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FolderView create(@AuthenticationPrincipal AuthPrincipal me,
                             @Valid @RequestBody CreateFolderRequest req) {
        return folders.create(me.id(), req);
    }

    @PutMapping("/{id}")
    public FolderView update(@AuthenticationPrincipal AuthPrincipal me,
                             @PathVariable long id,
                             @Valid @RequestBody UpdateFolderRequest req) {
        return folders.update(me.id(), id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthPrincipal me, @PathVariable long id) {
        folders.delete(me.id(), id);
    }
}
