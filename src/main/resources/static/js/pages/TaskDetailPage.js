const TaskDetailPage = {
    props: ['taskId'],
    emits: ['back'],
    template: `
    <div class="detail-page detail-page-v2">
        <!-- 紧凑头部 -->
        <div class="detail-topbar">
            <div class="detail-topbar-left">
                <button class="btn btn-ghost btn-sm" @click="$emit('back')">← 返回</button>
                <h1 class="detail-topbar-title">任务 #{{ taskId }}</h1>
                <div v-if="task" class="detail-status-badge" :class="badgeClass(task.status)">
                    <span class="status-dot" :class="statusDotClass(task.status)"></span>
                    {{ statusLabel(task.status) }}
                </div>
            </div>
            <div v-if="task" class="detail-topbar-stats">
                <div class="detail-topbar-stat">
                    <span class="detail-topbar-stat-val">{{ task.rowCount || '—' }}</span>
                    <span class="detail-topbar-stat-lbl">目标</span>
                </div>
                <div class="detail-topbar-stat">
                    <span class="detail-topbar-stat-val">{{ task.rowsGenerated || 0 }}</span>
                    <span class="detail-topbar-stat-lbl">已生成</span>
                </div>
                <div class="detail-topbar-stat">
                    <span class="detail-topbar-stat-val detail-topbar-stat-time">{{ formatTime(task.startedAt) }}</span>
                    <span class="detail-topbar-stat-lbl">开始</span>
                </div>
                <div class="detail-topbar-stat">
                    <span class="detail-topbar-stat-val detail-topbar-stat-time">{{ formatTime(task.completedAt) }}</span>
                    <span class="detail-topbar-stat-lbl">完成</span>
                </div>
                <div v-if="task.totalCellCount > 0" class="detail-topbar-stat">
                    <span class="detail-topbar-stat-val">{{ task.editedCellCount || 0 }}</span>
                    <span class="detail-topbar-stat-lbl">手动编辑</span>
                </div>
                <div v-if="task.totalCellCount > 0" class="detail-topbar-stat">
                    <span class="detail-topbar-stat-val">{{ task.regeneratedCellCount || 0 }}</span>
                    <span class="detail-topbar-stat-lbl">重新生成</span>
                </div>
                <div v-if="task.totalCellCount > 0" class="detail-topbar-stat">
                    <span class="detail-topbar-stat-val">{{ getAdoptionRate() }}%</span>
                    <span class="detail-topbar-stat-lbl">采纳率</span>
                </div>
                <button class="btn btn-danger btn-sm" @click="deleteTask" style="margin-left: var(--space-4);">&#128465;</button>
            </div>
        </div>

        <!-- 重新生成列详情 -->
        <div v-if="task && task.regeneratedColumns && task.hasRegeneration" class="stats-panel" style="margin-top: var(--space-3);">
            <span class="stats-label">重新生成列:</span>
            <span class="stats-item">{{ formatRegeneratedColumns(task.regeneratedColumns) }}</span>
        </div>

        <!-- Loading -->
        <div v-if="loading" class="card">
            <div class="loading-container" style="padding: var(--space-8);">
                <div class="loading-spinner"></div>
                <div class="loading-text">加载任务详情...</div>
            </div>
        </div>

        <template v-else-if="task">
            <!-- Tab 切换 -->
            <div class="detail-tabbar" role="tablist">
                <button class="detail-tab" :class="{ active: activeTab === 'schema' }"
                        @click="setTab('schema')" role="tab">
                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="18" height="18" rx="2"/><line x1="3" y1="9" x2="21" y2="9"/><line x1="9" y1="21" x2="9" y2="9"/></svg>
                    库表结构
                    <span class="detail-tab-count" v-if="tableCount > 0">{{ tableCount }}</span>
                </button>
                <button class="detail-tab" :class="{ active: activeTab === 'sql' }"
                        @click="setTab('sql')" role="tab" v-if="task.inputSql">
                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="16 18 22 12 16 6"/><polyline points="8 6 2 12 8 18"/></svg>
                    SQL 语句
                </button>
                <button class="detail-tab" :class="{ active: activeTab === 'rules' }"
                        @click="setTab('rules')" role="tab">
                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/></svg>
                    造数规则
                    <span class="detail-tab-count" v-if="parsedRules.length > 0">{{ parsedRules.length }}</span>
                </button>
                <button class="detail-tab" :class="{ active: activeTab === 'data' }"
                        @click="setTab('data')" role="tab" v-if="task.status === 'SUCCESS'">
                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/></svg>
                    写入数据
                    <span class="detail-tab-count" v-if="generatedData && totalDataRows > 0">{{ totalDataRows }}</span>
                </button>
                <button class="detail-tab detail-tab-danger" :class="{ active: activeTab === 'error' }"
                        @click="setTab('error')" role="tab" v-if="task.errorMessage">
                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><line x1="15" y1="9" x2="9" y2="15"/><line x1="9" y1="9" x2="15" y2="15"/></svg>
                    错误日志
                </button>
            </div>

            <!-- Schema 图 Tab -->
            <div v-if="activeTab === 'schema'" class="detail-tab-pane detail-tab-pane-graph">
                <schema-graph
                    v-if="analysis && analysis.tableMetadataMap"
                    :task-id="taskId"
                    :table-metadata-map="analysis.tableMetadataMap"
                    :relations="analysis.relations || []"
                    :generation-order="analysis.generationOrder || []">
                </schema-graph>
                <div v-else class="empty-state" style="padding: var(--space-8);">
                    <div class="empty-state-title">暂无库表结构</div>
                    <div class="empty-state-desc">该任务未保存解析快照</div>
                </div>
            </div>

            <!-- SQL Tab -->
            <div v-if="activeTab === 'sql'" class="detail-tab-pane">
                <div class="detail-tab-pane-title">
                    <svg class="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="16 18 22 12 16 6"/><polyline points="8 6 2 12 8 18"/></svg>
                    SQL 语句
                </div>
                <div class="sql-editor-wrap" style="flex:1; min-height: 0;">
                    <div class="sql-editor-header">
                        <span class="sql-editor-dot"></span>
                        <span class="sql-editor-dot"></span>
                        <span class="sql-editor-dot"></span>
                        <span class="sql-editor-label">SQL</span>
                    </div>
                    <pre class="detail-sql-content">{{ task.inputSql }}</pre>
                </div>
            </div>

            <!-- 规则 Tab -->
            <div v-if="activeTab === 'rules'" class="detail-tab-pane">
                <div class="detail-tab-pane-title">
                    <svg class="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/></svg>
                    造数规则
                    <span class="tag tag-ghost" style="margin-left:auto;">{{ parsedRules.length }} 条</span>
                </div>
                <template v-if="Object.keys(rulesGroupedByTable).length > 0">
                    <div class="detail-rules-scroll">
                        <div v-for="(group, tableName) in rulesGroupedByTable" :key="tableName" class="rules-table-section">
                            <div class="rules-table-header">
                                <span class="rules-table-name">{{ tableName }}</span>
                                <span class="rules-table-count">{{ group.length }} 字段</span>
                            </div>
                            <div class="data-table-wrap">
                                <table class="data-table data-table-compact">
                                    <thead>
                                        <tr>
                                            <th>字段</th>
                                            <th>规则</th>
                                            <th>配置</th>
                                        </tr>
                                    </thead>
                                    <tbody>
                                        <tr v-for="(r, i) in group" :key="i">
                                            <td style="font-family: var(--font-mono); font-weight: 500;">{{ r.columnName }}</td>
                                            <td><span class="tag tag-primary">{{ formatRuleType(r.ruleType) }}</span></td>
                                            <td style="font-size: var(--text-xs); color: var(--text-secondary); font-family: var(--font-mono); max-width: 320px; overflow:hidden; text-overflow:ellipsis; white-space:nowrap;">
                                                {{ r.description || r.ruleConfig || '—' }}
                                            </td>
                                        </tr>
                                    </tbody>
                                </table>
                            </div>
                        </div>
                    </div>
                </template>
                <div v-else class="empty-state" style="padding: var(--space-6);">
                    <div class="empty-state-title" style="font-size: var(--text-sm);">暂无造数规则</div>
                    <div class="empty-state-desc" style="font-size: var(--text-xs);">此任务使用默认生成策略</div>
                </div>
            </div>

            <!-- 写入数据 Tab -->
            <div v-if="activeTab === 'data'" class="detail-tab-pane">
                <div class="detail-tab-pane-title">
                    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/></svg>
                    写入数据
                    <span class="tag tag-ghost" style="margin-left:auto;" v-if="generatedData && totalDataRows > 0">{{ totalDataRows }} 行 / {{ generatedDataOrder.length }} 表</span>
                </div>
                <div v-if="generatedDataLoading" class="loading-container" style="padding: var(--space-8);">
                    <div class="loading-spinner"></div>
                    <div class="loading-text">加载数据...</div>
                </div>
                <template v-else-if="generatedData && generatedDataOrder.length > 0">
                    <div class="detail-rules-scroll">
                        <div v-for="tableName in generatedDataOrder" :key="'data-' + tableName" class="rules-table-section">
                            <div class="rules-table-header">
                                <span class="rules-table-name">{{ tableName }}</span>
                                <span class="rules-table-count">{{ getTableDataRows(tableName).length }} 行</span>
                            </div>
                            <div class="data-table-wrap data-table-scrollable">
                                <table class="data-table data-table-compact data-table-sticky">
                                    <thead>
                                        <tr>
                                            <th class="col-row-num">#</th>
                                            <th v-for="col in getTableColumns(tableName)" :key="col">{{ col }}</th>
                                        </tr>
                                    </thead>
                                    <tbody>
                                        <tr v-for="(row, ri) in getTableDataRows(tableName)" :key="ri">
                                            <td class="col-row-num">{{ ri + 1 }}</td>
                                            <td v-for="col in getTableColumns(tableName)" :key="col"
                                                :title="formatCellValue(row[col])">
                                                {{ formatCellValue(row[col]) }}
                                            </td>
                                        </tr>
                                    </tbody>
                                </table>
                            </div>
                        </div>
                    </div>
                </template>
                <div v-else class="empty-state" style="padding: var(--space-6);">
                    <div class="empty-state-title" style="font-size: var(--text-sm);">暂无数据快照</div>
                    <div class="empty-state-desc" style="font-size: var(--text-xs);">该任务可能未生成数据，或数据快照已过期</div>
                </div>
            </div>

            <!-- 错误 Tab -->
            <div v-if="activeTab === 'error'" class="detail-tab-pane">
                <div class="detail-tab-pane-title" style="color: var(--danger);">
                    <svg class="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><line x1="15" y1="9" x2="9" y2="15"/><line x1="9" y1="9" x2="15" y2="15"/></svg>
                    错误信息
                </div>
                <div class="alert-box alert-danger">
                    <span>&#10005;</span>
                    <pre style="font-family: var(--font-mono); font-size: var(--text-xs); word-break: break-all; white-space: pre-wrap; margin: 0;">{{ task.errorMessage }}</pre>
                </div>
            </div>
        </template>

        <div v-else class="card">
            <div class="empty-state">
                <div class="empty-state-title">任务不存在</div>
                <div class="empty-state-desc">无法找到 ID 为 {{ taskId }} 的任务记录</div>
            </div>
        </div>
    </div>
    `,
    data() {
        return {
            task: null,
            loading: false,
            activeTab: 'schema',
            generatedData: null,
            generatedDataLoading: false,
        };
    },
    computed: {
        analysis() {
            if (!this.task || !this.task.analysisSnapshot) return null;
            try {
                return JSON.parse(this.task.analysisSnapshot);
            } catch (e) {
                return null;
            }
        },
        parsedRules() {
            if (!this.task || !this.task.rulesSnapshot) return [];
            try {
                return JSON.parse(this.task.rulesSnapshot);
            } catch (e) {
                return [];
            }
        },
        rulesGroupedByTable() {
            const groups = {};
            this.parsedRules.forEach(r => {
                const key = r.tableName || 'unknown';
                if (!groups[key]) groups[key] = [];
                groups[key].push(r);
            });
            return groups;
        },
        tableCount() {
            if (!this.analysis || !this.analysis.tableMetadataMap) return 0;
            return Object.keys(this.analysis.tableMetadataMap).length;
        },
        sessionTabKey() {
            return 'detail-tab-' + this.taskId;
        },
        generatedDataOrder() {
            if (!this.generatedData || !this.generatedData.generationOrder) return [];
            return this.generatedData.generationOrder;
        },
        totalDataRows() {
            if (!this.generatedData || !this.generatedData.tableData) return 0;
            let total = 0;
            Object.values(this.generatedData.tableData).forEach(rows => { total += rows.length; });
            return total;
        },
    },
    watch: {
        taskId: {
            handler() {
                this.loadTask();
                this.restoreActiveTab();
            },
            immediate: true,
        },
    },
    methods: {
        async loadTask() {
            if (!this.taskId) return;
            this.loading = true;
            try {
                this.task = await API.getHistory(this.taskId);
                this.adjustActiveTabIfNeeded();
            } catch (e) {
                Toast.error('加载任务详情失败: ' + e.message);
                this.task = null;
            }
            this.loading = false;
        },
        restoreActiveTab() {
            try {
                const saved = sessionStorage.getItem(this.sessionTabKey);
                if (saved) this.activeTab = saved;
                else this.activeTab = 'schema';
            } catch (e) {
                this.activeTab = 'schema';
            }
        },
        adjustActiveTabIfNeeded() {
            // 如果保存的 Tab 当前不可用，回退到 schema
            if (this.activeTab === 'sql' && !this.task.inputSql) this.setTab('schema');
            else if (this.activeTab === 'error' && !this.task.errorMessage) this.setTab('schema');
            else if (this.activeTab === 'data' && this.task.status !== 'SUCCESS') this.setTab('schema');
            else if (this.activeTab === 'schema' && !this.analysis) {
                if (this.task.inputSql) this.setTab('sql');
            }
        },
        setTab(tab) {
            this.activeTab = tab;
            try { sessionStorage.setItem(this.sessionTabKey, tab); } catch (e) {}
            if (tab === 'data' && !this.generatedData) {
                this.loadGeneratedData();
            }
        },
        async loadGeneratedData() {
            if (!this.taskId) return;
            this.generatedDataLoading = true;
            try {
                this.generatedData = await API.getGeneratedData(this.taskId);
            } catch (e) {
                Toast.error('加载数据失败: ' + e.message);
                this.generatedData = null;
            }
            this.generatedDataLoading = false;
        },
        getTableColumns(tableName) {
            if (!this.generatedData || !this.generatedData.tableData) return [];
            const rows = this.generatedData.tableData[tableName];
            if (!rows || rows.length === 0) return [];
            return Object.keys(rows[0]);
        },
        getTableDataRows(tableName) {
            if (!this.generatedData || !this.generatedData.tableData) return [];
            return this.generatedData.tableData[tableName] || [];
        },
        formatCellValue(val) {
            if (val === null || val === undefined) return 'NULL';
            if (typeof val === 'boolean') return val ? 'true' : 'false';
            return String(val);
        },
        async deleteTask() {
            if (!confirm('确定要删除这条记录吗？删除后不可恢复。')) return;
            try {
                await API.deleteHistory(this.taskId);
                Toast.success('删除成功');
                this.$emit('back');
            } catch (e) {
                Toast.error('删除失败: ' + e.message);
            }
        },
        badgeClass(status) {
            switch (status) {
                case 'SUCCESS': return 'badge-success';
                case 'FAILED': return 'badge-failed';
                case 'RUNNING': return 'badge-running';
                default: return 'badge-running';
            }
        },
        statusDotClass(status) {
            switch (status) {
                case 'SUCCESS': return 'status-success';
                case 'FAILED': return 'status-danger';
                case 'RUNNING': return 'status-warning';
                default: return 'status-info';
            }
        },
        statusLabel(status) {
            switch (status) {
                case 'SUCCESS': return '执行成功';
                case 'FAILED': return '执行失败';
                case 'RUNNING': return '运行中';
                case 'PENDING': return '等待中';
                default: return status;
            }
        },
        formatRuleType(type) {
            const map = {
                'LLM_DESCRIPTION': 'AI 生成',
                'REGEX': '正则',
                'RANGE': '范围',
                'ENUM': '枚举',
                'SEQUENCE': '序列',
                'FOREIGN_KEY': '外键引用',
            };
            return map[type] || type;
        },
        formatTime(timeStr) {
            if (!timeStr) return '—';
            if (Array.isArray(timeStr)) {
                const [y, m, d, h, mi, s] = timeStr;
                return y + '-' + String(m).padStart(2, '0') + '-' + String(d).padStart(2, '0')
                    + ' ' + String(h).padStart(2, '0') + ':' + String(mi).padStart(2, '0') + ':' + String(s || 0).padStart(2, '0');
            }
            if (typeof timeStr === 'string') {
                return timeStr.replace('T', ' ').substring(0, 19);
            }
            return String(timeStr);
        },
        getAdoptionRate() {
            if (!this.task || !this.task.totalCellCount || this.task.totalCellCount === 0) return '—';
            const edited = this.task.editedCellCount || 0;
            const regen = this.task.regeneratedCellCount || 0;
            const rate = (this.task.totalCellCount - edited - regen) / this.task.totalCellCount * 100;
            return Math.max(0, rate).toFixed(1);
        },
        formatRegeneratedColumns(regenColsStr) {
            if (!regenColsStr) return '';
            try {
                const data = typeof regenColsStr === 'string' ? JSON.parse(regenColsStr) : regenColsStr;
                const parts = [];
                for (const [table, cols] of Object.entries(data)) {
                    for (const col of cols) {
                        parts.push(table + '.' + col);
                    }
                }
                return parts.join(', ') || '—';
            } catch (e) {
                return String(regenColsStr);
            }
        },
    },
};
