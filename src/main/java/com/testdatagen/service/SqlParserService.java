package com.testdatagen.service;

import com.testdatagen.model.dto.ColumnMetadata;
import com.testdatagen.model.dto.SqlAnalysisResult;
import com.testdatagen.model.dto.SqlAnalysisResult.RelationInfo;
import com.testdatagen.model.dto.SqlAnalysisResult.WhereHint;
import com.testdatagen.model.dto.TableMetadata;
import com.testdatagen.engine.DependencyResolver;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.insert.Insert;
import net.sf.jsqlparser.statement.select.*;
import net.sf.jsqlparser.expression.*;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.conditional.OrExpression;
import net.sf.jsqlparser.expression.operators.relational.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.util.*;

@Service
public class SqlParserService {

    private static final Logger log = LoggerFactory.getLogger(SqlParserService.class);

    private final MetadataService metadataService;
    private final ConnectionService connectionService;

    public SqlParserService(MetadataService metadataService, ConnectionService connectionService) {
        this.metadataService = metadataService;
        this.connectionService = connectionService;
    }

    public SqlAnalysisResult analyze(Long connectionId, String sql) {
        SqlAnalysisResult result = new SqlAnalysisResult();
        Set<String> tables = new LinkedHashSet<>();
        List<RelationInfo> relations = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<WhereHint> whereHints = new ArrayList<>();

        try {
            Statement statement = CCJSqlParserUtil.parse(sql);

            if (statement instanceof Insert) {
                parseInsert((Insert) statement, tables, relations, warnings);
            } else if (statement instanceof Select) {
                parseSelectStatement((Select) statement, tables, relations, whereHints);
            } else {
                warnings.add("暂不支持此SQL类型，将尝试从SQL中提取表名");
                extractTablesFromSql(sql, tables);
            }
        } catch (JSQLParserException e) {
            log.warn("SQL解析失败，尝试从SQL中提取表名: {}", e.getMessage());
            warnings.add("SQL语法解析失败: " + e.getMessage() + "，尝试从SQL中提取表名");
            extractTablesFromSql(sql, tables);
        }

        result.setTables(new ArrayList<>(tables));

        // 使用大小写不敏感的TreeMap，确保不同大小写的同一表名不会重复存储
        Map<String, TableMetadata> metadataMap = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

        // 收集FK发现的新表名，避免在迭代tables时修改导致ConcurrentModificationException
        Set<String> fkDiscoveredTables = new LinkedHashSet<>();

        try (Connection conn = connectionService.getConnection(connectionId)) {
            for (String tableName : tables) {
                try {
                    TableMetadata meta = metadataService.getTableMetadata(conn, tableName);
                    metadataMap.put(tableName, meta);

                    // Discover FK-based relations from metadata
                    if (meta.getColumns() != null) {
                        for (com.testdatagen.model.dto.ColumnMetadata c : meta.getColumns()) {
                            if (c.getReferencedTable() == null) continue;
                            // 统一FK引用表名为小写，确保与tables中的key一致
                            String refTable = c.getReferencedTable().toLowerCase();
                            RelationInfo rel = new RelationInfo();
                            rel.setFromTable(tableName);
                            rel.setFromColumn(c.getColumnName());
                            rel.setToTable(refTable);
                            rel.setToColumn(c.getReferencedColumn());
                            rel.setJoinType("FK");
                            // 如果tables中已存在（忽略大小写），则跳过添加
                            boolean alreadyExists = tables.stream()
                                    .anyMatch(t -> t.equalsIgnoreCase(refTable));
                            if (!alreadyExists) {
                                fkDiscoveredTables.add(refTable);
                            }
                            if (!hasDuplicateRelation(relations, rel)) {
                                relations.add(rel);
                                log.info("发现FK关系: {}.{} → {}.{}", tableName, c.getColumnName(), refTable, c.getReferencedColumn());
                            }
                        }
                    }
                } catch (Exception e) {
                    warnings.add("获取表 " + tableName + " 的元数据失败: " + e.getMessage());
                }
            }

            // 将FK发现的新表加入tables集合
            if (!fkDiscoveredTables.isEmpty()) {
                log.info("FK发现的新表(不在原始SQL中): {}", fkDiscoveredTables);
                tables.addAll(fkDiscoveredTables);
            }

            // Fetch metadata for newly discovered FK-referenced tables
            for (String tableName : fkDiscoveredTables) {
                if (!metadataMap.containsKey(tableName)) {
                    try {
                        metadataMap.put(tableName, metadataService.getTableMetadata(conn, tableName));
                    } catch (Exception e) {
                        warnings.add("获取表 " + tableName + " 的元数据失败: " + e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            warnings.add("连接数据库失败: " + e.getMessage());
        }

        // 将JOIN关系补充到ColumnMetadata的referencedTable/referencedColumn中
        // 仅在该列没有物理外键约束时才补充
        for (RelationInfo rel : relations) {
            if ("FK".equals(rel.getJoinType())) {
                // 物理FK已经在metadata中设置过，跳过
                continue;
            }
            String fromTableName = rel.getFromTable();
            TableMetadata fromMeta = metadataMap.get(fromTableName);
            if (fromMeta == null || fromMeta.getColumns() == null) continue;

            for (ColumnMetadata col : fromMeta.getColumns()) {
                if (col.getColumnName().equalsIgnoreCase(rel.getFromColumn())) {
                    if (col.getReferencedTable() == null) {
                        col.setReferencedTable(rel.getToTable().toLowerCase());
                        col.setReferencedColumn(rel.getToColumn());
                        log.info("通过JOIN关系补充FK引用: {}.{} → {}.{}",
                                fromTableName, col.getColumnName(),
                                rel.getToTable().toLowerCase(), rel.getToColumn());
                    }
                    break;
                }
            }
        }

        log.info("最终tables集合: {}", tables);
        log.info("metadataMap keys: {}", metadataMap.keySet());

        result.setTables(new ArrayList<>(tables));
        result.setTableMetadataMap(metadataMap);
        result.setRelations(relations);

        List<String> order = DependencyResolver.resolve(new ArrayList<>(tables), relations, warnings);
        log.info("生成顺序(generationOrder): {}", order);
        result.setGenerationOrder(order);
        result.setWarnings(warnings);
        result.setWhereHints(mergeRangeHints(whereHints));

        return result;
    }

    private void parseInsert(Insert insert, Set<String> tables, List<RelationInfo> relations, List<String> warnings) {
        Table table = insert.getTable();
        if (table != null) {
            tables.add(table.getName().toLowerCase());
        }
        Select select = insert.getSelect();
        if (select != null) {
            parseSelectStatement(select, tables, relations, new ArrayList<>());
        }
    }

    /**
     * JSqlParser 4.9: Select itself is the select body.
     * PlainSelect extends Select directly.
     * SetOperationList extends Select directly.
     */
    private void parseSelectStatement(Select select, Set<String> tables, List<RelationInfo> relations,
                                       List<WhereHint> whereHints) {
        if (select instanceof PlainSelect) {
            parsePlainSelect((PlainSelect) select, tables, relations, whereHints);
        } else if (select instanceof SetOperationList) {
            SetOperationList setOp = (SetOperationList) select;
            for (Select sb : setOp.getSelects()) {
                if (sb instanceof PlainSelect) {
                    parsePlainSelect((PlainSelect) sb, tables, relations, whereHints);
                }
            }
        } else if (select instanceof ParenthesedSelect) {
            ParenthesedSelect ps = (ParenthesedSelect) select;
            Select inner = ps.getSelect();
            if (inner != null) {
                parseSelectStatement(inner, tables, relations, whereHints);
            }
        }
    }

    private void parsePlainSelect(PlainSelect plainSelect, Set<String> tables, List<RelationInfo> relations,
                                   List<WhereHint> whereHints) {
        Map<String, String> aliasMap = new HashMap<>();

        // FROM clause
        FromItem fromItem = plainSelect.getFromItem();
        extractFromItem(fromItem, tables, aliasMap, relations, whereHints);

        // JOIN clauses
        List<Join> joins = plainSelect.getJoins();
        if (joins != null) {
            for (Join join : joins) {
                FromItem joinItem = join.getRightItem();
                extractFromItem(joinItem, tables, aliasMap, relations, whereHints);

                Collection<Expression> onExpressions = join.getOnExpressions();
                if (onExpressions != null) {
                    for (Expression onExpr : onExpressions) {
                        extractJoinRelation(onExpr, aliasMap, relations,
                                join.isLeft() ? "LEFT" : join.isRight() ? "RIGHT" : "INNER");
                    }
                }
            }
        }

        // WHERE clause
        Expression where = plainSelect.getWhere();
        if (where != null) {
            extractJoinRelation(where, aliasMap, relations, "WHERE");
            extractWhereHints(where, aliasMap, whereHints);
        }
    }

    private void extractFromItem(FromItem fromItem, Set<String> tables,
                                  Map<String, String> aliasMap, List<RelationInfo> relations,
                                  List<WhereHint> whereHints) {
        if (fromItem instanceof Table) {
            Table table = (Table) fromItem;
            String normalizedName = table.getName().toLowerCase();
            tables.add(normalizedName);
            if (table.getAlias() != null) {
                aliasMap.put(table.getAlias().getName(), normalizedName);
            }
            aliasMap.put(table.getName(), normalizedName);
        } else if (fromItem instanceof ParenthesedSelect) {
            ParenthesedSelect ps = (ParenthesedSelect) fromItem;
            Select inner = ps.getSelect();
            if (inner != null) {
                parseSelectStatement(inner, tables, relations, whereHints);
            }
        } else if (fromItem instanceof ParenthesedFromItem) {
            ParenthesedFromItem pfi = (ParenthesedFromItem) fromItem;
            extractFromItem(pfi.getFromItem(), tables, aliasMap, relations, whereHints);
        }
    }

    private void extractJoinRelation(Expression expr, Map<String, String> aliasMap,
                                      List<RelationInfo> relations, String joinType) {
        if (expr instanceof EqualsTo) {
            EqualsTo eq = (EqualsTo) expr;
            Expression left = eq.getLeftExpression();
            Expression right = eq.getRightExpression();
            if (left instanceof Column && right instanceof Column) {
                Column leftCol = (Column) left;
                Column rightCol = (Column) right;

                String leftTable = resolveTableName(leftCol, aliasMap);
                String rightTable = resolveTableName(rightCol, aliasMap);
                if (leftTable != null && rightTable != null && !leftTable.equals(rightTable)) {
                    RelationInfo rel = new RelationInfo();
                    rel.setFromTable(leftTable);
                    rel.setFromColumn(leftCol.getColumnName());
                    rel.setToTable(rightTable);
                    rel.setToColumn(rightCol.getColumnName());
                    rel.setJoinType(joinType);
                    if (!hasDuplicateRelation(relations, rel)) {
                        relations.add(rel);
                    }
                }
            }
        } else if (expr instanceof BinaryExpression) {
            BinaryExpression binary = (BinaryExpression) expr;
            extractJoinRelation(binary.getLeftExpression(), aliasMap, relations, joinType);
            extractJoinRelation(binary.getRightExpression(), aliasMap, relations, joinType);
        }
    }

    private String resolveTableName(Column column, Map<String, String> aliasMap) {
        if (column.getTable() != null && column.getTable().getName() != null) {
            return aliasMap.getOrDefault(column.getTable().getName(), column.getTable().getName());
        }
        return null;
    }

    private boolean hasDuplicateRelation(List<RelationInfo> relations, RelationInfo rel) {
        return relations.stream().anyMatch(r ->
                (r.getFromTable().equalsIgnoreCase(rel.getFromTable()) && r.getFromColumn().equalsIgnoreCase(rel.getFromColumn()) &&
                 r.getToTable().equalsIgnoreCase(rel.getToTable()) && r.getToColumn().equalsIgnoreCase(rel.getToColumn())) ||
                (r.getFromTable().equalsIgnoreCase(rel.getToTable()) && r.getFromColumn().equalsIgnoreCase(rel.getToColumn()) &&
                 r.getToTable().equalsIgnoreCase(rel.getFromTable()) && r.getToColumn().equalsIgnoreCase(rel.getFromColumn())));
    }

    /**
     * 从 WHERE 子句中提取字段值约束，生成预填规则提示。
     * 支持: column = value, column IN (v1, v2, ...)
     * 不支持: IN (SELECT ...)
     */
    private void extractWhereHints(Expression expr, Map<String, String> aliasMap, List<WhereHint> hints) {
        if (expr instanceof AndExpression) {
            AndExpression and = (AndExpression) expr;
            extractWhereHints(and.getLeftExpression(), aliasMap, hints);
            extractWhereHints(and.getRightExpression(), aliasMap, hints);
        } else if (expr instanceof OrExpression) {
            OrExpression or = (OrExpression) expr;
            extractWhereHints(or.getLeftExpression(), aliasMap, hints);
            extractWhereHints(or.getRightExpression(), aliasMap, hints);
        } else if (expr instanceof Parenthesis) {
            extractWhereHints(((Parenthesis) expr).getExpression(), aliasMap, hints);
        } else if (expr instanceof EqualsTo) {
            handleEqualsHint((EqualsTo) expr, aliasMap, hints);
        } else if (expr instanceof InExpression) {
            handleInHint((InExpression) expr, aliasMap, hints);
        } else if (expr instanceof Between) {
            handleBetweenHint((Between) expr, aliasMap, hints);
        } else if (expr instanceof GreaterThan) {
            handleComparisonHint((GreaterThan) expr, aliasMap, hints, "gt");
        } else if (expr instanceof GreaterThanEquals) {
            handleComparisonHint((GreaterThanEquals) expr, aliasMap, hints, "gte");
        } else if (expr instanceof MinorThan) {
            handleComparisonHint((MinorThan) expr, aliasMap, hints, "lt");
        } else if (expr instanceof MinorThanEquals) {
            handleComparisonHint((MinorThanEquals) expr, aliasMap, hints, "lte");
        }
    }

    /**
     * 处理 column = value 形式，生成 ENUM 单值规则。
     * 忽略 column = column (关联条件，已由 extractJoinRelation 处理)
     */
    private void handleEqualsHint(EqualsTo eq, Map<String, String> aliasMap, List<WhereHint> hints) {
        Expression left = eq.getLeftExpression();
        Expression right = eq.getRightExpression();

        Column column = null;
        Expression value = null;

        if (left instanceof Column && !(right instanceof Column)) {
            column = (Column) left;
            value = right;
        } else if (right instanceof Column && !(left instanceof Column)) {
            column = (Column) right;
            value = left;
        }

        if (column == null || value == null) return;

        String tableName = resolveTableName(column, aliasMap);
        String colName = column.getColumnName();
        String val = extractLiteralValue(value);
        if (val == null) return;

        WhereHint hint = new WhereHint();
        hint.setTableName(tableName);
        hint.setColumnName(colName);
        hint.setRuleType("ENUM");
        hint.setRuleConfig("{\"values\":[" + val + "]}");
        hint.setDescription("WHERE " + colName + " = " + val);
        hints.add(hint);
    }

    /**
     * 处理 column IN (v1, v2, ...) 形式，生成 ENUM 多值规则。
     * 忽略 IN (SELECT ...) 子查询。
     */
    private void handleInHint(InExpression in, Map<String, String> aliasMap, List<WhereHint> hints) {
        Expression leftExpr = in.getLeftExpression();
        if (!(leftExpr instanceof Column)) return;

        Expression rightExpr = in.getRightExpression();
        // 跳过 IN (SELECT ...) 子查询
        if (rightExpr instanceof Select || rightExpr instanceof ParenthesedSelect) return;

        // ExpressionList 或带括号的 ExpressionList
        List<Expression> items = null;
        if (rightExpr instanceof ExpressionList) {
            items = ((ExpressionList) rightExpr).getExpressions();
        } else if (rightExpr instanceof Parenthesis) {
            Expression inner = ((Parenthesis) rightExpr).getExpression();
            if (inner instanceof ExpressionList) {
                items = ((ExpressionList) inner).getExpressions();
            }
        }

        if (items == null || items.isEmpty()) return;

        Column column = (Column) leftExpr;
        String tableName = resolveTableName(column, aliasMap);
        String colName = column.getColumnName();

        StringBuilder valuesBuilder = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            String val = extractLiteralValue(items.get(i));
            if (val == null) return; // 包含非字面量表达式，放弃
            if (i > 0) valuesBuilder.append(",");
            valuesBuilder.append(val);
        }

        WhereHint hint = new WhereHint();
        hint.setTableName(tableName);
        hint.setColumnName(colName);
        hint.setRuleType("ENUM");
        hint.setRuleConfig("{\"values\":[" + valuesBuilder + "]}");
        hint.setDescription("WHERE " + colName + " IN (" + valuesBuilder + ")");
        hints.add(hint);
    }

    /**
     * 处理 column BETWEEN a AND b 形式，生成 RANGE 规则。
     */
    private void handleBetweenHint(Between between, Map<String, String> aliasMap, List<WhereHint> hints) {
        Expression leftExpr = between.getLeftExpression();
        if (!(leftExpr instanceof Column)) return;

        Column column = (Column) leftExpr;
        String tableName = resolveTableName(column, aliasMap);
        String colName = column.getColumnName();

        String minVal = extractLiteralValue(between.getBetweenExpressionStart());
        String maxVal = extractLiteralValue(between.getBetweenExpressionEnd());
        if (minVal == null || maxVal == null) return;

        WhereHint hint = new WhereHint();
        hint.setTableName(tableName);
        hint.setColumnName(colName);
        hint.setRuleType("RANGE");
        hint.setRuleConfig("{\"min\":" + minVal + ",\"max\":" + maxVal + "}");
        hint.setDescription("WHERE " + colName + " BETWEEN " + minVal + " AND " + maxVal);
        hints.add(hint);
    }

    /**
     * 处理比较运算符 (>, >=, <, <=) 形式，生成 RANGE 规则。
     * @param operator gt/gte/lt/lte 代表原始运算符方向
     */
    private void handleComparisonHint(ComparisonOperator comp, Map<String, String> aliasMap,
                                       List<WhereHint> hints, String operator) {
        Expression left = comp.getLeftExpression();
        Expression right = comp.getRightExpression();

        Column column = null;
        Expression value = null;
        String effectiveOp = operator;

        if (left instanceof Column && !(right instanceof Column)) {
            column = (Column) left;
            value = right;
        } else if (right instanceof Column && !(left instanceof Column)) {
            column = (Column) right;
            value = left;
            // 翻转运算符方向: "5 > age" 等价于 "age < 5"
            effectiveOp = flipOperator(operator);
        }

        if (column == null || value == null) return;

        String tableName = resolveTableName(column, aliasMap);
        String colName = column.getColumnName();
        String val = extractLiteralValue(value);
        if (val == null) return;

        WhereHint hint = new WhereHint();
        hint.setTableName(tableName);
        hint.setColumnName(colName);
        hint.setRuleType("RANGE");
        if ("gt".equals(effectiveOp) || "gte".equals(effectiveOp)) {
            hint.setRuleConfig("{\"min\":" + val + "}");
        } else {
            hint.setRuleConfig("{\"max\":" + val + "}");
        }
        hint.setDescription("WHERE " + colName + " " + operatorSymbol(effectiveOp) + " " + val);
        hints.add(hint);
    }

    private String flipOperator(String op) {
        switch (op) {
            case "gt": return "lt";
            case "gte": return "lte";
            case "lt": return "gt";
            case "lte": return "gte";
            default: return op;
        }
    }

    private String operatorSymbol(String op) {
        switch (op) {
            case "gt": return ">";
            case "gte": return ">=";
            case "lt": return "<";
            case "lte": return "<=";
            default: return op;
        }
    }

    /**
     * 合并同表同列的 RANGE 提示。
     * 例如: age >= 18 AND age <= 60 → 合并为 {"min":18,"max":60}
     */
    List<WhereHint> mergeRangeHints(List<WhereHint> hints) {
        List<WhereHint> nonRange = new ArrayList<>();
        Map<String, List<WhereHint>> rangeMap = new LinkedHashMap<>();

        for (WhereHint hint : hints) {
            if ("RANGE".equals(hint.getRuleType())) {
                String key = hint.getTableName() + "." + hint.getColumnName();
                rangeMap.computeIfAbsent(key, k -> new ArrayList<>()).add(hint);
            } else {
                nonRange.add(hint);
            }
        }

        List<WhereHint> result = new ArrayList<>(nonRange);
        for (Map.Entry<String, List<WhereHint>> entry : rangeMap.entrySet()) {
            List<WhereHint> rangeHints = entry.getValue();
            if (rangeHints.size() == 1) {
                result.add(rangeHints.get(0));
            } else {
                // 合并同列的多个 RANGE 条件
                String min = null;
                String max = null;
                StringBuilder descParts = new StringBuilder();
                String tableName = rangeHints.get(0).getTableName();
                String colName = rangeHints.get(0).getColumnName();

                for (WhereHint rh : rangeHints) {
                    String config = rh.getRuleConfig();
                    String extractedMin = extractJsonField(config, "min");
                    String extractedMax = extractJsonField(config, "max");
                    if (extractedMin != null) min = extractedMin;
                    if (extractedMax != null) max = extractedMax;
                    if (descParts.length() > 0) descParts.append(" AND ");
                    descParts.append(rh.getDescription());
                }

                WhereHint merged = new WhereHint();
                merged.setTableName(tableName);
                merged.setColumnName(colName);
                merged.setRuleType("RANGE");
                StringBuilder config = new StringBuilder("{");
                if (min != null) {
                    config.append("\"min\":").append(min);
                    if (max != null) config.append(",");
                }
                if (max != null) {
                    config.append("\"max\":").append(max);
                }
                config.append("}");
                merged.setRuleConfig(config.toString());
                merged.setDescription(descParts.toString());
                result.add(merged);
            }
        }
        return result;
    }

    /**
     * 从受控格式的 JSON 字符串中提取指定字段的值。
     */
    private String extractJsonField(String json, String field) {
        String key = "\"" + field + "\":";
        int idx = json.indexOf(key);
        if (idx < 0) return null;
        int start = idx + key.length();
        int end = json.indexOf(",", start);
        if (end < 0) end = json.indexOf("}", start);
        if (end < 0) return null;
        return json.substring(start, end).trim();
    }

    /**
     * 将 JSqlParser 的 Expression 转为 JSON 字面量字符串。
     * 字符串值加引号，数值直接返回，其他类型返回 null。
     */
    private String extractLiteralValue(Expression expr) {
        if (expr instanceof StringValue) {
            return "\"" + ((StringValue) expr).getValue().replace("\"", "\\\"") + "\"";
        } else if (expr instanceof LongValue) {
            return String.valueOf(((LongValue) expr).getValue());
        } else if (expr instanceof DoubleValue) {
            return String.valueOf(((DoubleValue) expr).getValue());
        } else if (expr instanceof NullValue) {
            return "null";
        }
        return null;
    }

    private void extractTablesFromSql(String sql, Set<String> tables) {
        String normalized = sql.replaceAll("\\s+", " ").replaceAll("[`\"\\[\\]]", "");
        String[] tokens = normalized.split("\\s+");
        Set<String> keywords = new HashSet<>(Arrays.asList("FROM", "JOIN", "INTO", "UPDATE"));
        for (int i = 0; i < tokens.length - 1; i++) {
            if (keywords.contains(tokens[i].toUpperCase())) {
                String candidate = tokens[i + 1].replaceAll("[,;()]", "");
                if (!candidate.isEmpty() && !candidate.startsWith("(") && candidate.matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
                    tables.add(candidate.toLowerCase());
                }
            }
        }
    }
}
