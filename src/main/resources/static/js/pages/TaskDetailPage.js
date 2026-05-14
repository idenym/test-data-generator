const TaskDetailPage = {
    props: ['taskId'],
    emits: ['back'],
    template: `
    <div class="detail-page">
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
                <button class="btn btn-danger btn-sm" @click="deleteTask" style="margin-left: var(--space-4);">&#128465;</button>
            </div>
        </div>

        <!-- Loading -->
        <div v-if="loading" class="card">
            <div class="loading-container" style="padding: var(--space-8);">
                <div class="loading-spinner"></div>
                <div class="loading-text">加载任务详情...</div>
            </div>
        </div>

        <template v-else-if="task">
            <!-- 双列网格布局 -->
            <div class="detail-grid">
                <!-- 左列：SQL + ER图(含关联图注) -->
                <div class="detail-col">
                    <!-- SQL 语句 -->
                    <div class="card card-compact" v-if="task.inputSql">
                        <div class="card-title">
                            <svg class="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="16 18 22 12 16 6"/><polyline points="8 6 2 12 8 18"/></svg>
                            SQL 语句
                        </div>
                        <div class="sql-editor-wrap">
                            <div class="sql-editor-header">
                                <span class="sql-editor-dot"></span>
                                <span class="sql-editor-dot"></span>
                                <span class="sql-editor-dot"></span>
                                <span class="sql-editor-label">SQL</span>
                            </div>
                            <pre class="detail-sql-content">{{ task.inputSql }}</pre>
                        </div>
                    </div>

                    <!-- 库表结构 ER 图 + 关联图注 -->
                    <div class="card card-compact" v-if="analysis && analysis.tableMetadataMap">
                        <div class="card-title">
                            <svg class="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="18" height="18" rx="2"/><line x1="3" y1="9" x2="21" y2="9"/><line x1="9" y1="21" x2="9" y2="9"/></svg>
                            库表结构
                            <span class="tag tag-ghost" style="margin-left:auto;">{{ Object.keys(analysis.tableMetadataMap).length }} 张表</span>
                        </div>
                        <schema-graph
                            :table-metadata-map="analysis.tableMetadataMap"
                            :relations="analysis.relations || []"
                            :generation-order="analysis.generationOrder || []">
                        </schema-graph>

                        <!-- 图注：关联关系 -->
                        <div class="er-legend" v-if="analysis.relations && analysis.relations.length > 0">
                            <div class="er-legend-title">
                                <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="5" cy="12" r="3"/><circle cx="19" cy="12" r="3"/><line x1="8" y1="12" x2="16" y2="12"/></svg>
                                关联关系
                            </div>
                            <div class="er-legend-items">
                                <div v-for="(rel, i) in analysis.relations" :key="i" class="er-legend-item">
                                    <span class="er-legend-from">{{ rel.fromTable }}.{{ rel.fromColumn }}</span>
                                    <span class="er-legend-arrow">&rarr;</span>
                                    <span class="er-legend-to">{{ rel.toTable }}.{{ rel.toColumn }}</span>
                                </div>
                            </div>
                        </div>
                    </div>

                    <!-- 错误信息 -->
                    <div class="card card-compact" v-if="task.errorMessage">
                        <div class="card-title" style="color: var(--danger);">
                            <svg class="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><line x1="15" y1="9" x2="9" y2="15"/><line x1="9" y1="9" x2="15" y2="15"/></svg>
                            错误信息
                        </div>
                        <div class="alert-box alert-danger">
                            <span>&#10005;</span>
                            <span style="font-family: var(--font-mono); font-size: var(--text-xs); word-break: break-all;">{{ task.errorMessage }}</span>
                        </div>
                    </div>
                </div>

                <!-- 右列：造数规则（单容器，按表展示多个 table） -->
                <div class="detail-col">
                    <div class="card card-compact">
                        <div class="card-title">
                            <svg class="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/></svg>
                            造数规则
                            <span class="tag tag-ghost" style="margin-left:auto;">{{ parsedRules.length }} 条</span>
                        </div>

                        <template v-if="Object.keys(rulesGroupedByTable).length > 0">
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
                                                <td style="font-size: var(--text-xs); color: var(--text-secondary); font-family: var(--font-mono); max-width: 200px; overflow:hidden; text-overflow:ellipsis; white-space:nowrap;">
                                                    {{ r.description || r.ruleConfig || '—' }}
                                                </td>
                                            </tr>
                                        </tbody>
                                    </table>
                                </div>
                            </div>
                        </template>

                        <!-- 无规则时的占位 -->
                        <div v-else class="empty-state" style="padding: var(--space-6);">
                            <div class="empty-state-title" style="font-size: var(--text-sm);">暂无造数规则</div>
                            <div class="empty-state-desc" style="font-size: var(--text-xs);">此任务使用默认生成策略</div>
                        </div>
                    </div>
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
    },
    watch: {
        taskId: {
            handler() { this.loadTask(); },
            immediate: true,
        },
    },
    methods: {
        async loadTask() {
            if (!this.taskId) return;
            this.loading = true;
            try {
                this.task = await API.getHistory(this.taskId);
            } catch (e) {
                Toast.error('加载任务详情失败: ' + e.message);
                this.task = null;
            }
            this.loading = false;
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
    },
};
