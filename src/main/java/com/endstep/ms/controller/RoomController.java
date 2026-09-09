package com.endstep.ms.controller;

import com.endstep.ms.dto.RoomDtos.ChooseDeckRequest;
import com.endstep.ms.dto.RoomDtos.CreateRoomRequest;
import com.endstep.ms.dto.RoomDtos.JoinRoomRequest;
import com.endstep.ms.dto.RoomDtos.RoomSummary;
import com.endstep.ms.dto.RoomDtos.RoomView;
import com.endstep.ms.dto.RoomDtos.StartGameResponse;
import com.endstep.ms.service.AuthPrincipal;
import com.endstep.ms.service.GameService;
import com.endstep.ms.service.RoomService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Endpoints REST de Room.
 *
 * @author Kauã Ferreira
 * @since 2026-09-08
 */
@RestController
@RequestMapping("/api/rooms")
public class RoomController {
    private final RoomService rooms;
    private final GameService gameService;

    public RoomController(RoomService rooms, GameService gameService) {
        this.rooms = rooms;
        this.gameService = gameService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RoomView create(@AuthenticationPrincipal AuthPrincipal me, @Valid @RequestBody CreateRoomRequest req) {
        return rooms.create(me.id(), req);
    }

    @GetMapping
    public List<RoomSummary> listPublic() {
        return rooms.listPublic();
    }

    @GetMapping("/{code}")
    public RoomView get(@PathVariable String code) {
        return rooms.getByCode(code);
    }

    @PostMapping("/{code}/join")
    public RoomView join(@AuthenticationPrincipal AuthPrincipal me, @PathVariable String code,
                         @RequestBody(required = false) JoinRoomRequest req) {
        return rooms.join(me.id(), code, req);
    }

    @PostMapping("/{code}/leave")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void leave(@AuthenticationPrincipal AuthPrincipal me, @PathVariable String code) {
        rooms.leave(me.id(), code);
    }

    @PostMapping("/{code}/deck")
    public RoomView chooseDeck(@AuthenticationPrincipal AuthPrincipal me, @PathVariable String code,
                              @RequestBody ChooseDeckRequest req) {
        return rooms.chooseDeck(me.id(), code, req);
    }

    @PostMapping("/{code}/start")
    public StartGameResponse start(@AuthenticationPrincipal AuthPrincipal me, @PathVariable String code) {
        return new StartGameResponse(gameService.startFromRoom(me.id(), code));
    }

    @PostMapping("/{code}/sit")
    public StartGameResponse sit(@AuthenticationPrincipal AuthPrincipal me, @PathVariable String code) {
        return new StartGameResponse(gameService.sitDown(me.id(), code));
    }
}
