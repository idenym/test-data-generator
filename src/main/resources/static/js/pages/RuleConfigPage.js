const RuleConfigPage = {
    props: ['connectionId', 'analysisResult', 'sqlScriptId'],
    emits: ['update-rules', 'prev', 'next'],
    template: `
    <div>
        <div class="page-header">
            <h1 class="page-title">规则配置</h1>
            <p class="page-desc">为每个字段设置数据生成规则。支持 AI 生成、正则表达式、范围值、枚举值等多种方式</p>
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
                    <button class="btn btn-ghost btn-sm" @click="suggestRules(tableName)" :disabled="suggesting === tableName">
                        {{ suggesting === tableName ? '⏳ 分析中...' : '🤖 AI 建议规则' }}
                    </button>
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
                                        <span class="tag tag-fk">外键</span>
                                    </template>
                                    <template v-else>
                                        <select class="inline-select" v-model="rule.ruleType">
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
                                            placeholder="正则表达式，如: 1[3-9][0-9]{9}">
                                        <div v-else-if="rule.ruleType === 'RANGE'" class="range-group">
                                            <input class="inline-input inline-input-mono" v-model="rule.rangeMin"
                                                placeholder="最小值" style="width: 80px;">
                                            <span class="range-separator">~</span>
                                            <input class="inline-input inline-input-mono" v-model="rule.rangeMax"
                                                placeholder="最大值" style="width: 80px;">
                                            <select class="inline-select" v-model="rule.rangeType" style="min-width: 80px;">
                                                <option value="integer">整数</option>
                                                <option value="decimal">小数</option>
                                                <option value="date">日期</option>
                                                <option value="datetime">时间</option>
                                            </select>
                                        </div>
                                        <input v-else-if="rule.ruleType === 'ENUM'"
                                            class="inline-input"
                                            v-model="rule.enumValues"
                                            placeholder="逗号分隔，如: male,female">
                                        <input v-else-if="rule.ruleType === 'LLM_DESCRIPTION'"
                                            class="inline-input"
                                            v-model="rule.llmDesc"
                                            placeholder="描述，如: 中文姓名、公司名称等">
                                        <span v-else style="color: var(--text-muted); font-size: var(--text-xs);">使用默认规则</span>
                                    </template>
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
                <button class="btn btn-ghost" @click="$emit('prev')">← 上一步</button>
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
            historyDialogVisible: false,
            historyLoading: false,
            historyList: [],
            historyTargetTable: '',
            historyTargetRow: null,
        };
    },
    watch: {
        analysisResult: {
            handler(val) {
                if (val) this.initRules();
            },
            immediate: true,
        },
    },
    methods: {
        initRules() {
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
                }));
            }
            this.tableRules = rules;

            // 预填 WHERE 子句中提取的约束规则
            const hints = this.analysisResult.whereHints;
            if (hints && hints.length > 0) {
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
                        } catch (e) { /* ignore */ }
                    } else if (hint.ruleType === 'RANGE') {
                        try {
                            const config = JSON.parse(hint.ruleConfig);
                            rule.ruleType = 'RANGE';
                            rule.rangeMin = config.min != null ? String(config.min) : '';
                            rule.rangeMax = config.max != null ? String(config.max) : '';
                            // 根据值格式推断范围类型
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
                        } catch (e) { /* ignore */ }
                    }
                }
            }
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
