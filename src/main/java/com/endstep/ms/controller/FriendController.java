package com.endstep.ms.controller;

import com.endstep.ms.dto.SocialDtos.AddFriendRequest;
import com.endstep.ms.dto.SocialDtos.FriendView;
import com.endstep.ms.service.AuthPrincipal;
import com.endstep.ms.service.FriendService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Amigos: listar, adicionar por username/id, remover.
 *
 * @author Kauã Ferreira
 * @since 2026-09-09
 */
@RestController
@RequestMapping("/api/friends")
public class FriendController {

    private final FriendService friends;

    public FriendController(FriendService friends) {
        this.friends = friends;
    }

    @GetMapping
    public List<FriendView> list(@AuthenticationPrincipal AuthPrincipal me) {
        return friends.list(me.id());
    }

    @PostMapping
    public FriendView add(@AuthenticationPrincipal AuthPrincipal me, @Valid @RequestBody AddFriendRequest req) {
        return friends.add(me.id(), req.query());
    }

    @DeleteMapping("/{userId}")
    public void remove(@AuthenticationPrincipal AuthPrincipal me, @PathVariable long userId) {
        friends.remove(me.id(), userId);
    }
}
