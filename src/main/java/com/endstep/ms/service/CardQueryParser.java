package com.endstep.ms.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Interpreta uma busca no estilo Scryfall (subset): {@code t:human},
 * {@code type:creature}, {@code o:flying}, {@code c:rg}, {@code c=r},
 * {@code id:wubrg}, {@code mv>=3}, {@code cmc<=2}, {@code is:commander},
 * {@code is:legendary}. Termos soltos casam nome OU linha de tipo.
 *
 * @author Kauã Ferreira
 * @since 2026-09-10
 */
public final class CardQueryParser {

    private CardQueryParser() {
    }

    public record CardQuery(
            List<String> nameTerms,
            List<String> typeTerms,
            List<String> textTerms,
            String colors,
            boolean colorsExact,
            String identity,
            String mvOp,
            Double mv,
            Set<String> isFlags
    ) {
        public boolean isEmpty() {
            return nameTerms.isEmpty() && typeTerms.isEmpty() && textTerms.isEmpty()
                    && colors == null && identity == null && mv == null && isFlags.isEmpty();
        }
    }

    private static final Pattern TOKEN = Pattern.compile("\"([^\"]*)\"|(\\S+)");
    private static final Pattern FIELD = Pattern.compile(
            "^(?<key>[a-zA-Z]+)(?<op>[:=]|>=|<=|>|<)(?<val>.+)$");

    public static CardQuery parse(String raw) {
        List<String> names = new ArrayList<>();
        List<String> types = new ArrayList<>();
        List<String> texts = new ArrayList<>();
        Set<String> isFlags = new LinkedHashSet<>();
        String colors = null;
        boolean colorsExact = false;
        String identity = null;
        String mvOp = null;
        Double mv = null;

        if (raw == null) {
            raw = "";
        }
        Matcher tm = TOKEN.matcher(raw.trim());
        while (tm.find()) {
            String tok = tm.group(1) != null ? tm.group(1) : tm.group(2);
            if (tok.isBlank()) {
                continue;
            }
            Matcher fm = FIELD.matcher(tok);
            if (!fm.matches()) {
                names.add(tok);
                continue;
            }
            String key = fm.group("key").toLowerCase();
            String op = fm.group("op");
            String val = fm.group("val").trim();

            switch (key) {
                case "t", "type" -> types.add(val);
                case "o", "oracle", "text" -> texts.add(val);
                case "c", "color", "colors" -> {
                    colorsExact = "=".equals(op);
                    colors = colorLetters(val);
                }
                case "id", "identity", "ci" -> identity = colorLetters(val);
                case "mv", "cmc", "manavalue" -> {
                    try {
                        mv = Double.parseDouble(val);
                        mvOp = switch (op) {
                            case ">" -> ">";
                            case "<" -> "<";
                            case ">=" -> ">=";
                            case "<=" -> "<=";
                            default -> "=";
                        };
                    } catch (NumberFormatException ignored) {
                        names.add(tok);
                    }
                }
                case "is" -> {
                    String v = val.toLowerCase();
                    if (v.equals("commander") || v.equals("legendary")) {
                        isFlags.add(v);
                    } else {
                        types.add(val);
                    }
                }
                default -> names.add(tok);
            }
        }

        return new CardQuery(names, types, texts, colors, colorsExact, identity, mvOp, mv, isFlags);
    }

    /** "rg" / "red green" / "wubrg" / "colorless" -> "RG" (ou "" para incolor). */
    private static String colorLetters(String v) {
        String s = v.toLowerCase().trim();
        if (s.equals("c") || s.equals("colorless") || s.equals("incolor")) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        for (String word : s.split("[\\s,]+")) {
            switch (word) {
                case "white", "w" -> append(out, 'W');
                case "blue", "u" -> append(out, 'U');
                case "black", "b" -> append(out, 'B');
                case "red", "r" -> append(out, 'R');
                case "green", "g" -> append(out, 'G');
                default -> {
                    for (char ch : word.toUpperCase().toCharArray()) {
                        if ("WUBRG".indexOf(ch) >= 0) {
                            append(out, ch);
                        }
                    }
                }
            }
        }
        return out.toString();
    }

    private static void append(StringBuilder sb, char c) {
        if (sb.indexOf(String.valueOf(c)) < 0) {
            sb.append(c);
        }
    }
}
