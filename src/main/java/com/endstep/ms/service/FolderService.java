package com.endstep.ms.service;

import com.endstep.ms.dto.FolderDtos.CreateFolderRequest;
import com.endstep.ms.dto.FolderDtos.FolderView;
import com.endstep.ms.dto.FolderDtos.UpdateFolderRequest;
import com.endstep.ms.entity.DeckFolder;
import com.endstep.ms.repository.DeckFolderRepository;
import com.endstep.ms.repository.DeckRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Serviço de Folder.
 *
 * @author Kauã Ferreira
 * @since 2026-09-08
 */
@Service
public class FolderService {
    private static final int MAX_DEPTH = 8;

    private final DeckFolderRepository folders;
    private final DeckRepository decks;

    public FolderService(DeckFolderRepository folders, DeckRepository decks) {
        this.folders = folders;
        this.decks = decks;
    }

    @Transactional(readOnly = true)
    public List<FolderView> list(long userId) {
        return folders.findByUserIdOrderByPositionAscNameAsc(userId).stream()
                .map(f -> new FolderView(f.getId(), f.getParentId(), f.getName(), f.getPosition(),
                        decks.countByFolderId(f.getId()), f.getCreatedAt()))
                .toList();
    }

    @Transactional
    public FolderView create(long userId, CreateFolderRequest req) {
        String name = req.name().trim();
        Long parentId = req.parentId();
        if (parentId != null) {
            requireOwned(userId, parentId);
            checkDepth(userId, parentId);
        }
        if (folders.siblingNameTaken(userId, parentId, name, null)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Já existe uma pasta com esse nome aqui");
        }
        DeckFolder f = new DeckFolder();
        f.setUserId(userId);
        f.setParentId(parentId);
        f.setName(name);
        f.setPosition(nextPosition(userId, parentId));
        f = folders.save(f);
        return new FolderView(f.getId(), f.getParentId(), f.getName(), f.getPosition(), 0, f.getCreatedAt());
    }

    @Transactional
    public FolderView update(long userId, long folderId, UpdateFolderRequest req) {
        DeckFolder f = requireOwned(userId, folderId);

        if (req.parentId() != null) {
            Long newParent = req.parentId() == 0 ? null : req.parentId();
            if (newParent != null) {
                if (newParent.equals(folderId) || isDescendant(userId, folderId, newParent)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Não dá para mover uma pasta para dentro dela mesma");
                }
                requireOwned(userId, newParent);
            }
            f.setParentId(newParent);
        }
        if (req.name() != null && !req.name().isBlank()) {
            String name = req.name().trim();
            if (folders.siblingNameTaken(userId, f.getParentId(), name, folderId)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Já existe uma pasta com esse nome aqui");
            }
            f.setName(name);
        }
        if (req.position() != null) {
            f.setPosition(req.position());
        }
        folders.save(f);
        return new FolderView(f.getId(), f.getParentId(), f.getName(), f.getPosition(),
                decks.countByFolderId(f.getId()), f.getCreatedAt());
    }

    @Transactional
    public void delete(long userId, long folderId) {
        requireOwned(userId, folderId);

        folders.deleteById(folderId);
    }

    private DeckFolder requireOwned(long userId, long folderId) {
        return folders.findByIdAndUserId(folderId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pasta não encontrada"));
    }

    private void checkDepth(long userId, long parentId) {
        int depth = 1;
        Long cur = parentId;
        Set<Long> seen = new HashSet<>();
        while (cur != null && seen.add(cur)) {
            DeckFolder f = folders.findByIdAndUserId(cur, userId).orElse(null);
            if (f == null) {
                break;
            }
            depth++;
            cur = f.getParentId();
            if (depth > MAX_DEPTH) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Limite de " + MAX_DEPTH + " níveis de pasta");
            }
        }
    }

    private boolean isDescendant(long userId, long ancestorId, long candidateId) {
        Long cur = candidateId;
        Set<Long> seen = new HashSet<>();
        while (cur != null && seen.add(cur)) {
            if (cur == ancestorId) {
                return true;
            }
            DeckFolder f = folders.findByIdAndUserId(cur, userId).orElse(null);
            cur = f == null ? null : f.getParentId();
        }
        return false;
    }

    private int nextPosition(long userId, Long parentId) {
        return folders.findChildren(userId, parentId).stream()
                .mapToInt(DeckFolder::getPosition).max().orElse(-1) + 1;
    }
}
