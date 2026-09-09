package com.endstep.ms.service;

import com.endstep.ms.common.NotFoundException;
import com.endstep.ms.dto.DeckDtos.DeckDetail;
import com.endstep.ms.dto.SocialDtos.SuggestionCardLine;
import com.endstep.ms.dto.SocialDtos.SuggestionView;
import com.endstep.ms.entity.CardOracle;
import com.endstep.ms.entity.Deck;
import com.endstep.ms.entity.DeckCard;
import com.endstep.ms.entity.DeckSuggestion;
import com.endstep.ms.entity.DeckSuggestion.CardSnapshot;
import com.endstep.ms.entity.User;
import com.endstep.ms.repository.CardOracleRepository;
import com.endstep.ms.repository.DeckCardRepository;
import com.endstep.ms.repository.DeckRepository;
import com.endstep.ms.repository.DeckSuggestionRepository;
import com.endstep.ms.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Sugestoes de deck entre amigos: sugerir (snapshot das cartas), listar as
 * recebidas, importar como deck proprio, dispensar.
 *
 * @author Kauã Ferreira
 * @since 2026-09-09
 */
@Service
public class SuggestionService {

    private final DeckSuggestionRepository suggestions;
    private final DeckRepository decks;
    private final DeckCardRepository deckCards;
    private final CardOracleRepository oracles;
    private final UserRepository users;
    private final FriendService friends;
    private final DeckService deckService;

    public SuggestionService(DeckSuggestionRepository suggestions, DeckRepository decks,
                             DeckCardRepository deckCards, CardOracleRepository oracles,
                             UserRepository users, FriendService friends, DeckService deckService) {
        this.suggestions = suggestions;
        this.decks = decks;
        this.deckCards = deckCards;
        this.oracles = oracles;
        this.users = users;
        this.friends = friends;
        this.deckService = deckService;
    }

    @Transactional
    public void suggest(long fromId, long deckId, long toUserId, String message) {
        if (fromId == toUserId || !friends.areFriends(fromId, toUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Você só pode sugerir para amigos");
        }
        Deck deck = decks.findByIdAndUserId(deckId, fromId)
                .orElseThrow(() -> new NotFoundException("Deck não encontrado"));

        List<CardSnapshot> snap = new ArrayList<>();
        for (DeckCard c : deckCards.findByDeckId(deckId)) {
            CardSnapshot s = new CardSnapshot();
            s.oracleCardId = c.getOracleCardId();
            s.printingId = c.getPrintingId();
            s.section = c.getSection();
            s.quantity = c.getQuantity();
            snap.add(s);
        }
        if (snap.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O deck está vazio");
        }

        DeckSuggestion d = new DeckSuggestion();
        d.setFromUserId(fromId);
        d.setToUserId(toUserId);
        d.setSourceDeckId(deckId);
        d.setDeckName(deck.getName());
        d.setFormat(deck.getFormat());
        d.setMessage(message == null || message.isBlank() ? null : message.trim());
        d.setCards(snap);
        suggestions.save(d);
    }

    @Transactional(readOnly = true)
    public List<SuggestionView> listIncoming(long meId) {
        List<DeckSuggestion> rows = suggestions.findByToUserIdOrderByCreatedAtDesc(meId);
        Map<Long, User> byId = users.findAllById(
                rows.stream().map(DeckSuggestion::getFromUserId).toList()).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
        return rows.stream().map(s -> {
            User f = byId.get(s.getFromUserId());
            return new SuggestionView(
                    s.getId(), s.getFromUserId(),
                    f != null ? f.getUsername() : "?",
                    f != null ? f.getDisplayName() : "usuário removido",
                    s.getDeckName(), s.getFormat(), s.getMessage(),
                    s.getCards() == null ? 0 : s.getCards().stream().mapToInt(c -> c.quantity).sum(),
                    s.getStatus(), s.getImportedDeckId(), s.getCreatedAt());
        }).toList();
    }

    @Transactional(readOnly = true)
    public long unreadCount(long meId) {
        return suggestions.countByToUserIdAndStatus(meId, "NEW");
    }

    @Transactional(readOnly = true)
    public List<SuggestionCardLine> cards(long meId, long suggestionId) {
        DeckSuggestion s = mine(meId, suggestionId);
        List<CardSnapshot> snap = s.getCards() == null ? List.of() : s.getCards();
        Map<Long, CardOracle> byId = oracles.findAllById(
                snap.stream().map(c -> c.oracleCardId).filter(java.util.Objects::nonNull).toList()).stream()
                .collect(Collectors.toMap(CardOracle::getId, o -> o));
        return snap.stream().map(c -> {
            CardOracle o = c.oracleCardId == null ? null : byId.get(c.oracleCardId);
            return new SuggestionCardLine(
                    o != null ? o.getName() : "carta #" + c.oracleCardId,
                    o != null ? o.getTypeLine() : null,
                    o != null ? o.getManaCost() : null,
                    c.section, c.quantity);
        }).toList();
    }

    @Transactional
    public DeckDetail importAsDeck(long meId, long suggestionId) {
        DeckSuggestion s = mine(meId, suggestionId);
        if ("IMPORTED".equals(s.getStatus()) && s.getImportedDeckId() != null) {
            return deckService.detail(meId, s.getImportedDeckId());
        }

        Deck deck = new Deck();
        deck.setUserId(meId);
        deck.setName(s.getDeckName());
        deck.setFormat(s.getFormat());
        deck.setSuggestedByUserId(s.getFromUserId());
        deck.setPosition(decks.findByUserIdOrderByPositionAscNameAsc(meId).size());
        deck = decks.save(deck);

        List<DeckCard> cards = new ArrayList<>();
        for (CardSnapshot c : s.getCards() == null ? List.<CardSnapshot>of() : s.getCards()) {
            if (c.oracleCardId == null) {
                continue;
            }
            DeckCard dc = new DeckCard();
            dc.setDeckId(deck.getId());
            dc.setOracleCardId(c.oracleCardId);
            dc.setPrintingId(c.printingId);
            dc.setQuantity(Math.max(1, c.quantity));
            dc.setSection(c.section == null ? "MAINBOARD" : c.section);
            cards.add(dc);
        }
        deckCards.saveAll(cards);

        s.setStatus("IMPORTED");
        s.setImportedDeckId(deck.getId());
        s.setResolvedAt(Instant.now());
        suggestions.save(s);

        return deckService.detail(meId, deck.getId());
    }

    @Transactional
    public void dismiss(long meId, long suggestionId) {
        DeckSuggestion s = mine(meId, suggestionId);
        s.setStatus("DISMISSED");
        s.setResolvedAt(Instant.now());
        suggestions.save(s);
    }

    private DeckSuggestion mine(long meId, long suggestionId) {
        DeckSuggestion s = suggestions.findById(suggestionId)
                .orElseThrow(() -> new NotFoundException("Sugestão não encontrada"));
        if (s.getToUserId() != meId) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Sugestão de outro usuário");
        }
        return s;
    }
}
