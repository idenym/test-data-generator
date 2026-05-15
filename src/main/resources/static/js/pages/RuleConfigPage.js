const RuleConfigPage = {
    props: ['connectionId', 'analysisResult', 'sqlScriptId', 'savedRules'],
    emits: ['update-rules', 'prev', 'next'],
    template: `
    <div>
        <div class="page-header">
            <h1 class="page-title">规则配置</h1>
            <p class="page-desc">为每个字段设置数据生成规则。支持 AI 生成、正则表达式、范围值、枚举值等多种方式</p>
        </div>

        <!-- 自动回填加载提示 -->
        <div v-if="autoFilling" class="card" style="text-align: center; padding: var(--space-6);">
            <div class="loading-spinner" style="margin: 0 auto var(--space-3);"></div>
            <div style="color: var(--text-secondary);">正在自动回填规则（历史规则 + 知识库）...</div>
        </div>

        <div v-if="!analysisResult" class="card">
            <div class="empty-state">
                <div class="empty-state-icon">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg>
                </div>
                <div class="empty-state-title">未解析 SQL</div>
                <div class="empty-state-desc">请返回上一步完成 SQL 解析</div>
            </div>
        </div>

        <template v-else>
            <!-- 每个表的规则配置 -->
            <div v-for="tableName in analysisResult.generationOrder" :key="tableName" class="card">
                <div class="table-section-header">
                    <div class="table-section-name">
                        <svg class="table-icon" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="18" height="18" rx="2"/><line x1="3" y1="9" x2="21" y2="9"/><line x1="9" y1="21" x2="9" y2="9"/></svg>
                        {{ tableName }}
                    </div>
                    <div style="display: flex; gap: var(--space-2);">
                        <button class="btn btn-ghost btn-sm" @click="refreshAutoFill(tableName)" :disabled="autoFilling" title="重新自动回填规则">
                            🔄 刷新回填
                        </button>
                        <button class="btn btn-ghost btn-sm" @click="suggestRules(tableName)" :disabled="suggesting === tableName">
                            {{ suggesting === tableName ? '⏳ 分析中...' : '🤖 AI 建议规则' }}
                        </button>
                    </div>
                </div>

                <div class="data-table-wrap">
                    <table class="data-table">
                        <thead>
                            <tr>
                                <th style="width: 130px;">字段名</th>
                                <th style="width: 100px;">类型</th>
                                <th style="width: 100px;">注释</th>
                                <th style="width: 140px;">规则类型</th>
                                <th>规则配置</th>
                                <th style="width: 70px;">来源</th>
                                <th style="width: 60px;">历史</th>
                            </tr>
                        </thead>
                        <tbody>
                            <tr v-for="rule in getTableRules(tableName)" :key="rule.columnName">
                                <td style="font-family: var(--font-mono); font-weight: 500;">{{ rule.columnName }}</td>
                                <td><span class="tag tag-ghost">{{ rule.dataType }}</span></td>
                                <td style="font-size: var(--text-xs); color: var(--text-tertiary);">{{ rule.comment || '—' }}</td>
                                <td>
                                    <template v-if="rule.autoIncrement">
                                        <span class="tag tag-ai">自增</span>
                                    </template>
                                    <template v-else-if="rule.referencedTable">
                                        <span class="tag tag-fk">关联关系</span>
                                    </template>
                                    <template v-else>
                                        <select class="inline-select" v-model="rule.ruleType" @change="rule.source = 'MANUAL'">
                                            <option value="LLM_DESCRIPTION">🤖 AI 生成</option>
                                            <option value="REGEX">📝 正则</option>
                                            <option value="RANGE">📊 范围</option>
                                            <option value="ENUM">📋 枚举</option>
                                        </select>
                                    </template>
                                </td>
                                <td>
                                    <template v-if="rule.autoIncrement">
                                        <span style="color: var(--text-muted); font-size: var(--text-xs);">自增字段，跳过</span>
                                    </template>
                                    <template v-else-if="rule.referencedTable">
                                        <span style="color: var(--primary-light); font-family: var(--font-mono); font-size: var(--text-xs);">
                                            → {{ rule.referencedTable }}.{{ rule.referencedColumn }}
                                        </span>
                                    </template>
                                    <template v-else>
                                        <input v-if="rule.ruleType === 'REGEX'"
                                            class="inline-input inline-input-mono"
                                            v-model="rule.pattern"
                                            @input="rule.source = 'MANUAL'"
                                            placeholder="正则表达式，如: 1[3-9][0-9]{9}">
                                        <div v-else-if="rule.ruleType === 'RANGE'" class="range-group">
                                            <input class="inline-input inline-input-mono" v-model="rule.rangeMin"
                                                @input="rule.source = 'MANUAL'"
                                                placeholder="最小值" style="width: 80px;">
                                            <span class="range-separator">~</span>
                                            <input class="inline-input inline-input-mono" v-model="rule.rangeMax"
                                                @input="rule.source = 'MANUAL'"
                                                placeholder="最大值" style="width: 80px;">
                                            <select class="inline-select" v-model="rule.rangeType" @change="rule.source = 'MANUAL'" style="min-width: 80px;">
                                                <option value="integer">整数</option>
                                                <option value="decimal">小数</option>
                                                <option value="date">日期</option>
                                                <option value="datetime">时间</option>
                                            </select>
                                        </div>
                                        <input v-else-if="rule.ruleType === 'ENUM'"
                                            class="inline-input"
                                            v-model="rule.enumValues"
                                            @input="rule.source = 'MANUAL'"
                                            placeholder="逗号分隔，如: male,female">
                                        <input v-else-if="rule.ruleType === 'LLM_DESCRIPTION'"
                                            class="inline-input"
                                            v-model="rule.llmDesc"
                                            @input="rule.source = 'MANUAL'"
                                            placeholder="描述，如: 中文姓名、公司名称等">
                                        <span v-else style="color: var(--text-muted); font-size: var(--text-xs);">使用默认规则</span>
                                    </template>
                                </td>
                                <td>
                                    <span v-if="!rule.autoIncrement && !rule.referencedTable"
                                        class="tag"
                                        :class="sourceTagClass(rule.source)"
                                        :title="sourceTagTitle(rule.source)"
                                        style="font-size: 10px;">
                                        {{ sourceTagLabel(rule.source) }}
                                    </span>
                                </td>
                                <td>
                                    <button v-if="!rule.autoIncrement && !rule.referencedTable"
                                        class="btn btn-ghost btn-sm btn-icon"
                                        @click="loadFieldHistory(tableName, rule)"
                                        title="查看历史规则">
                                        📜
                                    </button>
                                </td>
                            </tr>
                        </tbody>
                    </table>
                </div>
            </div>

            <!-- 历史规则弹窗 -->
            <div v-if="historyDialogVisible" class="modal-overlay" @click.self="historyDialogVisible = false">
                <div class="modal-content">
                    <div class="modal-header">
                        <h3 class="modal-title">历史规则 — {{ historyTargetRow ? historyTargetRow.columnName : '' }}</h3>
                        <button class="modal-close" @click="historyDialogVisible = false">×</button>
                    </div>

                    <div v-if="historyLoading" class="loading-container" style="padding: var(--space-8);">
                        <div class="loading-spinner"></div>
                        <div class="loading-text">加载历史规则...</div>
                    </div>

                    <div v-else-if="historyList.length === 0" class="empty-state" style="padding: var(--space-8);">
                        <div class="empty-state-title">暂无历史规则</div>
                        <div class="empty-state-desc">该字段还没有使用过的规则记录</div>
                    </div>

                    <template v-else>
                        <div class="data-table-wrap">
                            <table class="data-table">
                                <thead>
                                    <tr>
                                        <th>规则类型</th>
                                        <th>配置</th>
                                        <th style="width: 80px;">使用次数</th>
                                        <th style="width: 100px;">最近使用</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    <tr v-for="h in historyList" :key="h.id" class="clickable-row"
                                        @click="applyHistoryRule(h)">
                                        <td><span class="tag tag-primary">{{ formatRuleTypeLabel(h.ruleType) }}</span></td>
                                        <td style="font-family: var(--font-mono); font-size: var(--text-xs);">{{ formatHistoryConfig(h) }}</td>
                                        <td style="text-align: center;">{{ h.usedCount }}</td>
                                        <td class="history-time">{{ formatDate(h.lastUsedAt) }}</td>
                                    </tr>
                                </tbody>
                            </table>
                        </div>
                        <p style="font-size: var(--text-xs); color: var(--text-muted); margin-top: var(--space-3);">
                            点击行即可应用该规则
                        </p>
                    </template>
                </div>
            </div>

            <!-- Step Navigation -->
            <div class="step-navigation">
                <button class="btn btn-ghost" @click="goPrev">← 上一步</button>
                <button class="btn btn-primary btn-lg" @click="goNext">
                    下一步：数据生成 →
                </button>
            </div>
        </template>
    </div>
    `,
    data() {
        return {
            tableRules: {},
            suggesting: null,
            autoFilling: false,
            historyDialogVisible: false,
            historyLoading: false,
            historyList: [],
            historyTargetTable: '',
            historyTargetRow: null,
        };
    },
    mounted() {
        if (this.analysisResult) {
            this.initRules();
        }
    },
    watch: {
        analysisResult(val) {
            if (val) this.initRules();
        },
    },
    methods: {
        async initRules() {
            if (!this.analysisResult) return;
            const rules = {};
            for (const [tableName, meta] of Object.entries(this.analysisResult.tableMetadataMap)) {
                rules[tableName] = meta.columns.map(col => ({
                    columnName: col.columnName,
                    dataType: col.columnType || col.dataType,
                    comment: col.comment,
                    autoIncrement: col.autoIncrement,
                    referencedTable: col.referencedTable,
                    referencedColumn: col.referencedColumn,
                    ruleType: 'LLM_DESCRIPTION',
                    pattern: '',
                    rangeMin: '',
                    rangeMax: '',
                    rangeType: 'integer',
                    enumValues: '',
                    llmDesc: col.comment || '',
                    source: 'COMMENT',
                }));
            }
            this.tableRules = rules;

            // 如果有已保存的规则（从上一次配置传回），直接恢复
            if (this.savedRules && this.savedRules.length > 0) {
                this.restoreFromSavedRules(rules);
                return;
            }

            // 否则走自动填充流程
            await this.doAutoFill(rules);
            this.applyWhereHints(rules);
        },

        restoreFromSavedRules(rules) {
            for (const saved of this.savedRules) {
                const tableRules = rules[saved.tableName];
                if (!tableRules) continue;
                const rule = tableRules.find(r => r.columnName === saved.columnName);
                if (!rule) continue;

                rule.ruleType = saved.ruleType;
                rule.source = 'MANUAL';
                try {
                    const config = JSON.parse(saved.ruleConfig || '{}');
                    switch (saved.ruleType) {
                        case 'REGEX':
                            rule.pattern = config.pattern || '';
                            break;
                        case 'RANGE':
                            rule.rangeMin = String(config.min || '');
                            rule.rangeMax = String(config.max || '');
                            rule.rangeType = config.type || 'integer';
                            break;
                        case 'ENUM':
                            rule.enumValues = (config.values || []).join(',');
                            break;
                        case 'LLM_DESCRIPTION':
                            rule.llmDesc = config.description || '';
                            break;
                    }
                } catch (e) {
                    console.warn('恢复规则配置失败:', e);
                }
            }
        },

        async doAutoFill(rules) {
            if (!this.connectionId) return;

            // 构建请求
            const tables = [];
            for (const [tableName, tableRules] of Object.entries(rules)) {
                const columns = tableRules
                    .filter(r => !r.autoIncrement && !r.referencedTable)
                    .map(r => ({ columnName: r.columnName, comment: r.comment || '', dataType: r.dataType || '' }));
                if (columns.length > 0) {
                    tables.push({ tableName, columns });
                }
            }

            if (tables.length === 0) return;

            this.autoFilling = true;
            try {
                const result = await API.autoFillRules({
                    connectionId: this.connectionId,
                    sqlScriptId: this.sqlScriptId || null,
                    tables,
                });

                // 按优先级应用：auto-fill API 已按 知识库(低) -> 历史(高) 排序合并
                // 直接遍历结果覆盖即可
                for (const [tableName, suggestions] of Object.entries(result || {})) {
                    const tableRules = rules[tableName];
                    if (!tableRules) continue;

                    for (const sug of suggestions) {
                        const rule = tableRules.find(r => r.columnName === sug.columnName);
                        if (!rule || rule.autoIncrement || rule.referencedTable) continue;

                        this.applyRuleConfig(rule, sug.ruleType, sug.ruleConfig, sug.source);
                    }
                }
            } catch (e) {
                console.warn('自动回填规则失败:', e.message);
            }
            this.autoFilling = false;
        },

        applyWhereHints(rules) {
            const hints = this.analysisResult.whereHints;
            if (!hints || hints.length === 0) return;

            for (const hint of hints) {
                const tableName = hint.tableName;
                const tableRules = rules[tableName];
                if (!tableRules) continue;
                const rule = tableRules.find(r => r.columnName === hint.columnName);
                if (!rule || rule.autoIncrement || rule.referencedTable) continue;
                if (hint.ruleType === 'ENUM') {
                    try {
                        const config = JSON.parse(hint.ruleConfig);
                        rule.ruleType = 'ENUM';
                        rule.enumValues = (config.values || []).join(',');
                        rule.source = 'WHERE';
                    } catch (e) { /* ignore */ }
                } else if (hint.ruleType === 'RANGE') {
                    try {
                        const config = JSON.parse(hint.ruleConfig);
                        rule.ruleType = 'RANGE';
                        rule.rangeMin = config.min != null ? String(config.min) : '';
                        rule.rangeMax = config.max != null ? String(config.max) : '';
                        const sample = config.min != null ? String(config.min) : String(config.max || '');
                        if (/^\d{4}-\d{2}-\d{2}T/.test(sample) || /^\d{4}-\d{2}-\d{2} \d{2}:/.test(sample)) {
                            rule.rangeType = 'datetime';
                        } else if (/^\d{4}-\d{2}-\d{2}$/.test(sample)) {
                            rule.rangeType = 'date';
                        } else if (sample.includes('.')) {
                            rule.rangeType = 'decimal';
                        } else {
                            rule.rangeType = 'integer';
                        }
                        rule.source = 'WHERE';
                    } catch (e) { /* ignore */ }
                }
            }
        },

        applyRuleConfig(rule, ruleType, ruleConfig, source) {
            rule.ruleType = ruleType || 'LLM_DESCRIPTION';
            rule.source = source || 'COMMENT';

            if (!ruleConfig) return;

            try {
                const config = typeof ruleConfig === 'string' ? JSON.parse(ruleConfig) : ruleConfig;
                switch (rule.ruleType) {
                    case 'REGEX':
                        rule.pattern = config.pattern || '';
                        break;
                    case 'RANGE':
                        rule.rangeMin = config.min != null ? String(config.min) : '';
                        rule.rangeMax = config.max != null ? String(config.max) : '';
                        rule.rangeType = config.type || 'integer';
                        break;
                    case 'ENUM':
                        rule.enumValues = (config.values || []).join(',');
                        break;
                    case 'LLM_DESCRIPTION':
                        rule.llmDesc = config.description || '';
                        break;
                }
            } catch (e) {
                console.warn('解析规则配置失败:', e);
            }
        },

        async refreshAutoFill(tableName) {
            if (!this.connectionId) return;

            const tableRules = this.tableRules[tableName];
            if (!tableRules) return;

            // 重置为 comment 默认值
            for (const rule of tableRules) {
                if (rule.autoIncrement || rule.referencedTable) continue;
                rule.ruleType = 'LLM_DESCRIPTION';
                rule.pattern = '';
                rule.rangeMin = '';
                rule.rangeMax = '';
                rule.rangeType = 'integer';
                rule.enumValues = '';
                rule.llmDesc = rule.comment || '';
                rule.source = 'COMMENT';
            }

            // 重新调用自动回填（仅该表）
            const singleTableRules = {};
            singleTableRules[tableName] = tableRules;
            await this.doAutoFill(singleTableRules);

            // 重新应用 WHERE 提示
            this.applyWhereHints(singleTableRules);

            Toast.success('已刷新 ' + tableName + ' 的规则');
        },

        getTableRules(tableName) {
            return this.tableRules[tableName] || [];
        },
        async suggestRules(tableName) {
            if (!this.connectionId) return;
            this.suggesting = tableName;
            try {
                const suggestions = await API.suggestRules(this.connectionId, tableName);
                if (suggestions && suggestions.length > 0) {
                    const rules = this.tableRules[tableName];
                    for (const sug of suggestions) {
                        const rule = rules.find(r => r.columnName === sug.columnName);
                        if (rule && !rule.autoIncrement && !rule.referencedTable) {
                            rule.ruleType = sug.ruleType || '';
                            rule.source = 'AI';
                            if (sug.ruleConfig) {
                                if (sug.ruleType === 'REGEX') rule.pattern = sug.ruleConfig.pattern || '';
                                if (sug.ruleType === 'RANGE') {
                                    rule.rangeMin = String(sug.ruleConfig.min || '');
                                    rule.rangeMax = String(sug.ruleConfig.max || '');
                                    rule.rangeType = sug.ruleConfig.type || 'integer';
                                }
                                if (sug.ruleType === 'ENUM') rule.enumValues = (sug.ruleConfig.values || []).join(',');
                                if (sug.ruleType === 'LLM_DESCRIPTION') rule.llmDesc = sug.ruleConfig.description || '';
                            }
                        }
                    }
                    Toast.success('AI 建议已应用');
                }
            } catch (e) {
                Toast.error('获取 AI 建议失败: ' + e.message);
            }
            this.suggesting = null;
        },
        buildFieldRules() {
            const fieldRules = [];
            for (const [tableName, rules] of Object.entries(this.tableRules)) {
                for (const rule of rules) {
                    if (rule.autoIncrement || rule.referencedTable || !rule.ruleType) continue;
                    let ruleConfig = '';
                    let description = '';
                    switch (rule.ruleType) {
                        case 'REGEX':
                            ruleConfig = JSON.stringify({ pattern: rule.pattern });
                            description = rule.pattern;
                            break;
                        case 'RANGE':
                            ruleConfig = JSON.stringify({ type: rule.rangeType, min: rule.rangeMin, max: rule.rangeMax });
                            description = rule.rangeMin + '~' + rule.rangeMax + ' (' + rule.rangeType + ')';
                            break;
                        case 'ENUM':
                            ruleConfig = JSON.stringify({ values: rule.enumValues.split(',').map(v => v.trim()).filter(v => v) });
                            description = rule.enumValues;
                            break;
                        case 'LLM_DESCRIPTION':
                            const desc = rule.llmDesc || rule.comment || 'Generate realistic ' + rule.columnName + ' values';
                            ruleConfig = JSON.stringify({ description: desc, batchSize: 50 });
                            description = desc;
                            break;
                    }
                    fieldRules.push({ tableName, columnName: rule.columnName, ruleType: rule.ruleType, ruleConfig, description });
                }
            }
            return fieldRules;
        },
        goPrev() {
            this.$emit('update-rules', this.buildFieldRules());
            this.$emit('prev');
        },
        goNext() {
            this.$emit('update-rules', this.buildFieldRules());
            this.$emit('next');
        },
        async loadFieldHistory(tableName, row) {
            this.historyTargetTable = tableName;
            this.historyTargetRow = row;
            this.historyDialogVisible = true;
            this.historyLoading = true;
            this.historyList = [];
            try {
                this.historyList = await API.getFieldRuleHistory(tableName, row.columnName, this.sqlScriptId);
            } catch (e) {
                Toast.error('加载历史规则失败: ' + e.message);
            }
            this.historyLoading = false;
        },
        applyHistoryRule(historyRow) {
            const row = this.historyTargetRow;
            if (!row) return;
            row.ruleType = historyRow.ruleType;
            row.source = 'HISTORY';
            try {
                const config = JSON.parse(historyRow.ruleConfig || '{}');
                switch (historyRow.ruleType) {
                    case 'REGEX': row.pattern = config.pattern || ''; break;
                    case 'RANGE':
                        row.rangeMin = String(config.min || '');
                        row.rangeMax = String(config.max || '');
                        row.rangeType = config.type || 'integer';
                        break;
                    case 'ENUM': row.enumValues = (config.values || []).join(','); break;
                    case 'LLM_DESCRIPTION': row.llmDesc = config.description || ''; break;
                }
            } catch (e) { console.warn('解析历史规则配置失败:', e); }
            this.historyDialogVisible = false;
            Toast.success('已应用历史规则');
        },
        sourceTagLabel(source) {
            const map = { WHERE: 'WHERE', HISTORY: '历史', KNOWLEDGE_BASE: '知识库', COMMENT: '注释', AI: 'AI', MANUAL: '手动' };
            return map[source] || '默认';
        },
        sourceTagClass(source) {
            const map = {
                WHERE: 'tag-warning',
                HISTORY: 'tag-primary',
                KNOWLEDGE_BASE: 'tag-ai',
                AI: 'tag-ai',
                COMMENT: 'tag-ghost',
                MANUAL: 'tag-ghost',
            };
            return map[source] || 'tag-ghost';
        },
        sourceTagTitle(source) {
            const map = {
                WHERE: '来自 SQL WHERE 子句解析（优先级 1）',
                HISTORY: '来自最近使用的历史规则（优先级 2）',
                KNOWLEDGE_BASE: '来自知识库匹配（优先级 3）',
                COMMENT: '来自字段注释（默认）',
                AI: '来自 AI 建议',
                MANUAL: '手动修改',
            };
            return map[source] || '';
        },
        formatHistoryConfig(row) {
            try {
                const config = JSON.parse(row.ruleConfig || '{}');
                switch (row.ruleType) {
                    case 'REGEX': return config.pattern || '';
                    case 'RANGE': return config.min + '~' + config.max + ' (' + (config.type || 'integer') + ')';
                    case 'ENUM': return (config.values || []).join(', ');
                    case 'LLM_DESCRIPTION': return config.description || '';
                    default: return row.ruleConfig || '';
                }
            } catch (e) { return row.ruleConfig || ''; }
        },
        formatRuleTypeLabel(type) {
            const map = { 'LLM_DESCRIPTION': 'AI 生成', 'REGEX': '正则', 'RANGE': '范围', 'ENUM': '枚举' };
            return map[type] || type;
        },
        formatDate(dateStr) {
            if (!dateStr) return '';
            return dateStr.substring(0, 10);
        },
    },
};
