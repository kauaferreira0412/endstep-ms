package com.endstep.ms.service;

import com.endstep.ms.projection.DeckCardRow;
import com.endstep.ms.dto.ValidationResult;
import com.endstep.ms.dto.ValidationResult.Issue;
import com.endstep.ms.dto.ValidationResult.Level;
import com.endstep.ms.entity.FormatEntity;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

/**
 * Validacao de deck por formato (endstep.txt secao 16). As regras numericas vem
 * da tabela {@code formats}; as regras "difíceis" (elegibilidade de comandante,
 * identidade de cor, singleton com excecoes, banlist) ficam aqui.
 *
 * @author Kauã Ferreira
 * @since 2026-09-08
 */
@Service
public class DeckValidationService {
    private static final int LIST_CAP = 20;

    public ValidationResult validate(FormatEntity fmt, List<DeckCardRow> rows) {
        List<Issue> issues = new ArrayList<>();

        List<DeckCardRow> commanders = rows.stream().filter(r -> "COMMANDER".equals(r.getSection())).toList();
        List<DeckCardRow> main = rows.stream().filter(r -> "MAINBOARD".equals(r.getSection())).toList();
        List<DeckCardRow> side = rows.stream().filter(r -> "SIDEBOARD".equals(r.getSection())).toList();

        int commanderCount = sum(commanders);
        int mainCount = sum(main);
        int sideCount = sum(side);

        boolean cmd = fmt.isUsesCommandZone();
        int deckSize = cmd ? commanderCount + mainCount : mainCount;
        String colorIdentity = "";

        if (cmd) {
            if (commanderCount == 0) {
                issues.add(Issue.error("no_commander", "Nenhum comandante definido."));
            } else if (commanderCount > 2) {
                issues.add(Issue.error("too_many_commanders",
                        "No máximo 2 comandantes (apenas com Partner ou Background)."));
            }
            for (DeckCardRow c : commanders) {
                if (c.getQuantity() > 1) {
                    issues.add(Issue.error("commander_qty", "O comandante deve ter quantidade 1.", List.of(c.getName())));
                }
                if (!canBeCommander(c)) {
                    issues.add(Issue.error("invalid_commander",
                            c.getName() + " não pode ser comandante (precisa ser criatura lendária, "
                                    + "Background, ou dizer \"can be your commander\").",
                            List.of(c.getName())));
                }
            }
            if (commanderCount == 2 && !validPair(commanders)) {
                issues.add(Issue.warn("commander_pair",
                        "Dois comandantes exigem Partner, Friends forever, ou um par de Background.",
                        names(commanders)));
            }

            TreeSet<Character> ci = new TreeSet<>();
            commanders.forEach(c -> ci.addAll(letters(c.getColorIdentity())));
            colorIdentity = charsToString(ci);

            if (fmt.isEnforceColorIdentity() && commanderCount > 0) {
                List<String> offenders = new ArrayList<>();
                for (DeckCardRow r : concat(commanders, main)) {
                    if (!containsAll(ci, letters(r.getColorIdentity()))) {
                        offenders.add(r.getName());
                    }
                }
                if (!offenders.isEmpty()) {
                    issues.add(Issue.error("color_identity",
                            "Fora da identidade de cor do comandante (" + display(colorIdentity) + ").",
                            cap(offenders)));
                }
            }
        }

        if (fmt.getMinDeck() != null && deckSize < fmt.getMinDeck()) {
            issues.add(Issue.error("deck_min",
                    "Deck com " + deckSize + " cartas — o formato exige no mínimo " + fmt.getMinDeck() + "."));
        }
        if (fmt.getMaxDeck() != null && deckSize > fmt.getMaxDeck()) {
            issues.add(Issue.error("deck_max",
                    "Deck com " + deckSize + " cartas — o formato permite no máximo " + fmt.getMaxDeck() + "."));
        }
        if (sideCount > fmt.getSideboardMax()) {
            issues.add(Issue.error("sideboard_max",
                    "Sideboard com " + sideCount + " cartas — máximo " + fmt.getSideboardMax() + "."));
        }

        int maxCopies = fmt.isSingleton() ? 1 : fmt.getMaxCopies();
        List<String> overLimit = new ArrayList<>();
        for (DeckCardRow r : concat(main, side)) {
            if (isBasicLand(r) || allowsAnyNumber(r)) {
                continue;
            }
            boolean restricted = "restricted".equalsIgnoreCase(nz(r.getFormatStatus()));
            if (restricted && r.getQuantity() > 1) {
                overLimit.add(r.getName() + " (restrita: 1)");
            } else if (!restricted && r.getQuantity() > maxCopies) {
                overLimit.add(r.getName() + " (" + r.getQuantity() + "/" + maxCopies + ")");
            }
        }
        if (!overLimit.isEmpty()) {
            issues.add(Issue.error("copies",
                    fmt.isSingleton() ? "Formato singleton: no máximo 1 cópia por carta (fora terrenos básicos)."
                            : "Acima do limite de cópias.",
                    cap(overLimit)));
        }

        List<String> banned = new ArrayList<>();
        List<String> notLegal = new ArrayList<>();
        for (DeckCardRow r : concat(concat(commanders, main), side)) {
            String status = nz(cmd ? r.getCommanderStatus() : r.getFormatStatus()).toLowerCase(Locale.ROOT);
            if (status.isEmpty()) {
                continue;
            }
            if (status.equals("banned")) {
                banned.add(r.getName());
            } else if (!status.equals("legal") && !status.equals("restricted")) {
                notLegal.add(r.getName());
            }
        }
        if (!banned.isEmpty()) {
            issues.add(Issue.error("banned", "Cartas banidas neste formato.", cap(banned)));
        }
        if (!notLegal.isEmpty()) {
            issues.add(Issue.warn("not_legal",
                    "Cartas não legais no formato (normalmente acorn / silver-border).", cap(notLegal)));
        }

        boolean legal = issues.stream().noneMatch(i -> i.level() == Level.ERROR);
        return new ValidationResult(fmt.getCode(), legal, deckSize, commanderCount, colorIdentity, issues);
    }

    private static boolean canBeCommander(DeckCardRow r) {
        String tl = nz(r.getTypeLine()).toLowerCase(Locale.ROOT);
        String text = nz(r.getOracleText()).toLowerCase(Locale.ROOT);
        boolean legendaryCreature = tl.contains("legendary") && tl.contains("creature");
        boolean background = tl.contains("background");
        return legendaryCreature || background || text.contains("can be your commander");
    }

    private static boolean validPair(List<DeckCardRow> commanders) {
        boolean anyPartner = commanders.stream().anyMatch(c -> {
            String t = nz(c.getOracleText()).toLowerCase(Locale.ROOT);
            return t.contains("partner") || t.contains("friends forever");
        });
        boolean bgPair = commanders.stream().anyMatch(c -> nz(c.getTypeLine()).toLowerCase(Locale.ROOT).contains("background"))
                && commanders.stream().anyMatch(c -> nz(c.getOracleText()).toLowerCase(Locale.ROOT).contains("choose a background"));
        return anyPartner || bgPair;
    }

    private static boolean isBasicLand(DeckCardRow r) {
        String tl = nz(r.getTypeLine()).toLowerCase(Locale.ROOT);
        return tl.contains("basic") && tl.contains("land");
    }

    private static boolean allowsAnyNumber(DeckCardRow r) {
        return nz(r.getOracleText()).toLowerCase(Locale.ROOT)
                .contains("a deck can have any number of cards named");
    }

    private static Set<Character> letters(String s) {
        Set<Character> out = new LinkedHashSet<>();
        if (s == null) {
            return out;
        }
        for (char c : s.toUpperCase(Locale.ROOT).toCharArray()) {
            if (c == 'W' || c == 'U' || c == 'B' || c == 'R' || c == 'G') {
                out.add(c);
            }
        }
        return out;
    }

    private static boolean containsAll(Set<Character> superset, Set<Character> sub) {
        return superset.containsAll(sub);
    }

    private static String charsToString(Set<Character> chars) {
        StringBuilder sb = new StringBuilder();
        for (char c : "WUBRG".toCharArray()) {
            if (chars.contains(c)) {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String display(String ci) {
        return ci.isEmpty() ? "incolor" : ci;
    }

    private static int sum(List<DeckCardRow> rows) {
        return rows.stream().mapToInt(DeckCardRow::getQuantity).sum();
    }

    private static List<String> names(List<DeckCardRow> rows) {
        return rows.stream().map(DeckCardRow::getName).toList();
    }

    private static <T> List<T> concat(List<T> a, List<T> b) {
        return Stream.concat(a.stream(), b.stream()).toList();
    }

    private static List<String> cap(List<String> list) {
        List<String> distinct = list.stream().distinct().toList();
        if (distinct.size() <= LIST_CAP) {
            return distinct;
        }
        List<String> out = new ArrayList<>(distinct.subList(0, LIST_CAP));
        out.add("… e mais " + (distinct.size() - LIST_CAP));
        return out;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
