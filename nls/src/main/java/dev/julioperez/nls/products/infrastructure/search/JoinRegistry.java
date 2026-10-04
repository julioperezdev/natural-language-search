package dev.julioperez.nls.products.infrastructure.search;

import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Root;
import java.util.HashMap;
import java.util.Map;

public final class JoinRegistry {
    private final Root<?> root;
    private final Map<String, Join<?, ?>> joins = new HashMap<>();

    public JoinRegistry(Root<?> root) {
        this.root = root;
    }

    public Join<?, ?> join(String associationPath) {
        if (associationPath == null || associationPath.isBlank()) {
            throw new IllegalArgumentException("Association path is required.");
        }

        From<?, ?> parent = root;
        StringBuilder prefix = new StringBuilder();
        Join<?, ?> result = null;
        for (String segment : associationPath.split("\\.")) {
            if (segment.isBlank()) {
                throw new IllegalArgumentException("Association path is invalid.");
            }
            if (!prefix.isEmpty()) {
                prefix.append('.');
            }
            prefix.append(segment);
            String key = prefix.toString();
            result = joins.get(key);
            if (result == null) {
                result = parentJoin(parent, segment);
                joins.put(key, result);
            }
            parent = result;
        }
        return result;
    }

    private Join<?, ?> parentJoin(From<?, ?> parent, String association) {
        return parent.join(association, JoinType.INNER);
    }
}
