package com.endstep.ms.service;

import com.endstep.ms.service.CardQueryParser.CardQuery;

import java.util.ArrayList;
import java.util.List;

/**
 * Monta o WHERE dinamico (native SQL, params posicionais) a partir de uma
 * {@link CardQuery}. Usado pelo {@link CardQueryService}.
 *
 * @author Kauã Ferreira
 * @since 2026-09-10
 */
final class CardSearchSql {

    final String where;
    final String orderBy;
    final List<Object> params = new ArrayList<>();
    final int whereParamCount;

    CardSearchSql(CardQuery q) {
        List<String> cond = new ArrayList<>();

        for (String term : q.nameTerms()) {
            int a = bind("%" + term + "%");
            int b = bind("%" + term + "%");
            cond.add("(o.name ilike ?" + a + " or o.type_line ilike ?" + b + ")");
        }
        for (String t : q.typeTerms()) {
            cond.add("o.type_line ilike ?" + bind("%" + t + "%"));
        }
        for (String t : q.textTerms()) {
            cond.add("o.oracle_text ilike ?" + bind("%" + t + "%"));
        }

        if (q.colors() != null) {
            if (q.colors().isEmpty()) {
                cond.add("coalesce(o.colors, '') = ''");
            } else if (q.colorsExact()) {
                cond.add("regexp_replace(coalesce(o.colors, ''), '[^WUBRG]', '', 'g') = ?"
                        + bind(sorted(q.colors())));
            } else {
                for (char c : q.colors().toCharArray()) {
                    cond.add("o.colors like ?" + bind("%" + c + "%"));
                }
            }
        }

        if (q.identity() != null) {
            if (q.identity().isEmpty()) {
                cond.add("coalesce(o.color_identity, '') = ''");
            } else {
                cond.add("coalesce(o.color_identity, '') ~ ?" + bind("^[" + q.identity() + "]*$"));
            }
        }

        if (q.mv() != null && q.mvOp() != null) {
            cond.add("o.mana_value " + q.mvOp() + " ?" + bind(q.mv()));
        }

        for (String flag : q.isFlags()) {
            if (flag.equals("commander")) {
                cond.add("((o.type_line ilike '%Legendary%' and o.type_line ilike '%Creature%')"
                        + " or o.oracle_text ilike '%can be your commander%')");
            } else if (flag.equals("legendary")) {
                cond.add("o.type_line ilike '%Legendary%'");
            }
        }

        this.where = cond.isEmpty() ? "true" : String.join(" and ", cond);
        this.whereParamCount = params.size();

        if (!q.nameTerms().isEmpty()) {
            String joined = String.join(" ", q.nameTerms());
            int x = bind(joined);
            int y = bind(joined + "%");
            this.orderBy = "case when lower(o.name) = lower(?" + x + ") then 0"
                    + " when o.name ilike ?" + y + " then 1 else 2 end, o.name asc";
        } else {
            this.orderBy = "o.name asc";
        }
    }

    private int bind(Object v) {
        params.add(v);
        return params.size();
    }

    private static String sorted(String letters) {
        char[] c = letters.toCharArray();
        java.util.Arrays.sort(c);
        return new String(c);
    }
}
