package com.endstep.ms.service;

import com.endstep.ms.common.NotFoundException;
import com.endstep.ms.dto.SocialDtos.FriendView;
import com.endstep.ms.entity.Friendship;
import com.endstep.ms.entity.User;
import com.endstep.ms.repository.FriendshipRepository;
import com.endstep.ms.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Amizades: adicionar por username ou id, listar, remover.
 * Adicao e imediata e mutua (sem pedido/aceite).
 *
 * @author Kauã Ferreira
 * @since 2026-09-09
 */
@Service
public class FriendService {

    private final FriendshipRepository friendships;
    private final UserRepository users;

    public FriendService(FriendshipRepository friendships, UserRepository users) {
        this.friendships = friendships;
        this.users = users;
    }

    @Transactional
    public FriendView add(long meId, String query) {
        User other = resolve(query);
        if (other.getId() == meId) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Você não pode se adicionar");
        }
        long low = Math.min(meId, other.getId());
        long high = Math.max(meId, other.getId());
        if (friendships.findByUserLowIdAndUserHighId(low, high).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Vocês já são amigos");
        }
        Friendship f = new Friendship();
        f.setUserLowId(low);
        f.setUserHighId(high);
        f.setCreatedBy(meId);
        friendships.save(f);
        return toView(other, f.getCreatedAt() == null ? null : f.getCreatedAt());
    }

    @Transactional
    public void remove(long meId, long otherId) {
        long low = Math.min(meId, otherId);
        long high = Math.max(meId, otherId);
        friendships.findByUserLowIdAndUserHighId(low, high).ifPresent(friendships::delete);
    }

    @Transactional(readOnly = true)
    public List<FriendView> list(long meId) {
        List<Friendship> rows = friendships.findAllForUser(meId);
        List<Long> otherIds = rows.stream()
                .map(f -> f.getUserLowId() == meId ? f.getUserHighId() : f.getUserLowId())
                .toList();
        Map<Long, User> byId = users.findAllById(otherIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
        List<FriendView> out = new ArrayList<>();
        for (Friendship f : rows) {
            long otherId = f.getUserLowId() == meId ? f.getUserHighId() : f.getUserLowId();
            User u = byId.get(otherId);
            if (u != null) {
                out.add(toView(u, f.getCreatedAt()));
            }
        }
        out.sort(Comparator.comparing(FriendView::displayName, String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    @Transactional(readOnly = true)
    public boolean areFriends(long a, long b) {
        long low = Math.min(a, b);
        long high = Math.max(a, b);
        return friendships.findByUserLowIdAndUserHighId(low, high).isPresent();
    }

    private User resolve(String query) {
        String q = query.trim();
        if (q.matches("\\d+")) {
            return users.findById(Long.parseLong(q))
                    .orElseThrow(() -> new NotFoundException("Usuário não encontrado: " + q));
        }
        String uname = q.startsWith("@") ? q.substring(1) : q;
        return users.findByUsernameIgnoreCase(uname)
                .orElseThrow(() -> new NotFoundException("Usuário não encontrado: " + q));
    }

    private FriendView toView(User u, java.time.Instant since) {
        return new FriendView(u.getId(), u.getUsername(), u.getDisplayName(),
                u.getAvatarUrl(), u.getStatus(), since);
    }
}
