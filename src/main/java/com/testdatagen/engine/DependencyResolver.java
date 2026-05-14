package com.testdatagen.engine;

import com.testdatagen.model.dto.SqlAnalysisResult.RelationInfo;

import java.util.*;

public class DependencyResolver {

    /**
     * Topological sort of tables based on FK relationships.
     * Returns tables in order: parent tables first, child tables later.
     */
    public static List<String> resolve(List<String> tables, List<RelationInfo> relations, List<String> warnings) {
        // Build adjacency list: edge from parent -> child (parent must come first)
        Map<String, Set<String>> graph = new LinkedHashMap<>();
        Map<String, Integer> inDegree = new LinkedHashMap<>();

        for (String table : tables) {
            graph.putIfAbsent(table, new LinkedHashSet<>());
            inDegree.putIfAbsent(table, 0);
        }

        for (RelationInfo rel : relations) {
            if ("FK".equals(rel.getJoinType())) {
                // fromTable has FK referencing toTable -> toTable must be generated first
                String parent = rel.getToTable();
                String child = rel.getFromTable();
                if (tables.contains(parent) && tables.contains(child) && !parent.equals(child)) {
                    graph.putIfAbsent(parent, new LinkedHashSet<>());
                    graph.putIfAbsent(child, new LinkedHashSet<>());
                    inDegree.putIfAbsent(parent, 0);
                    inDegree.putIfAbsent(child, 0);
                    if (graph.get(parent).add(child)) {
                        inDegree.merge(child, 1, Integer::sum);
                    }
                }
            }
        }

        // Also treat JOIN relations as potential dependency hints
        for (RelationInfo rel : relations) {
            if (!"FK".equals(rel.getJoinType())) {
                String t1 = rel.getFromTable();
                String t2 = rel.getToTable();
                // If column name ends with _id, it's likely a FK
                if (rel.getFromColumn() != null && rel.getFromColumn().toLowerCase().endsWith("_id")) {
                    if (tables.contains(t1) && tables.contains(t2) && !t1.equals(t2)) {
                        if (graph.get(t2).add(t1)) {
                            inDegree.merge(t1, 1, Integer::sum);
                        }
                    }
                }
            }
        }

        // Kahn's algorithm
        Queue<String> queue = new LinkedList<>();
        for (Map.Entry<String, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.add(entry.getKey());
            }
        }

        List<String> result = new ArrayList<>();
        while (!queue.isEmpty()) {
            String node = queue.poll();
            result.add(node);
            for (String neighbor : graph.getOrDefault(node, Collections.emptySet())) {
                int newDegree = inDegree.merge(neighbor, -1, Integer::sum);
                if (newDegree == 0) {
                    queue.add(neighbor);
                }
            }
        }

        if (result.size() < tables.size()) {
            warnings.add("检测到循环依赖，部分表的生成顺序可能不正确");
            // Add remaining tables
            for (String table : tables) {
                if (!result.contains(table)) {
                    result.add(table);
                }
            }
        }

        return result;
    }
}
