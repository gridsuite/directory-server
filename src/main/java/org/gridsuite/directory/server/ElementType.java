package org.gridsuite.directory.server;

import java.util.List;

public enum ElementType {
    STUDY,
    DIRECTORY,
    CASE,
    CONTINGENCY_LIST,
    FILTER;

    public static List<String> names(List<ElementType> types) {
        return types.stream().map(ElementType::name).toList();
    }
}
