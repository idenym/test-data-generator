const HomePage = {
    emits: ['new-task', 'view-detail'],
    template: `
    <div>
        <!-- Hero Section - 紧凑横向布局 -->
        <div class="home-hero home-hero-compact">
            <div class="home-hero-content">
                <h1 class="home-hero-title">测试数据构造工具</h1>
                <p class="home-hero-desc">解析 SQL 语句，智能生成符合 Schema 约束的测试数据</p>
            </div>
            <div class="home-hero-right">
                <div class="home-stats">
                    <div class="home-stat-item">
                        <div class="home-stat-value">{{ tasks.length }}</div>
                        <div class="home-stat-label">总任务</div>
                    </div>
                    <div class="home-stat-item">
                        <div class="home-stat-value">{{ successCount }}</div>
                        <div class="home-stat-label">成功</div>
                    </div>
                    <div class="home-stat-item">
                        <div class="home-stat-value">{{ totalRows }}</div>
                        <div class="home-stat-label">生成行数</div>
                    </div>
                </div>
                <button class="btn btn-primary btn-lg home-hero-btn" @click="$emit('new-task')">
                    + 新建任务
                </button>
            </div>
        </div>

        <!-- 任务列表 -->
        <div class="card card-compact">
            <div style="display: flex; align-items: center; justify-content: space-between; margin-bottom: var(--space-4);">
                <div class="card-title" style="margin-bottom: 0;">
                    <svg class="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg>
                    执行历史
                    <span class="tag tag-ghost" style="margin-left: var(--space-2);">{{ tasks.length }}</span>
                </div>
                <button class="btn btn-ghost btn-sm" @click="loadTasks" :disabled="loading">
                    {{ loading ? '刷新中...' : '&#8635; 刷新' }}
                </button>
            </div>

            <!-- Loading skeleton -->
            <div v-if="loading && tasks.length === 0">
                <div class="skeleton-row" v-for="i in 4" :key="i">
                    <div class="skeleton skeleton-cell" style="width: 60px;"></div>
                    <div class="skeleton skeleton-cell" style="width: 80px;"></div>
                    <div class="skeleton skeleton-cell"></div>
                    <div class="skeleton skeleton-cell" style="width: 120px;"></div>
                </div>
            </div>

            <!-- 空状态 -->
            <div v-else-if="tasks.length === 0" class="empty-state">
                <div class="empty-state-icon">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><rect x="2" y="3" width="20" height="14" rx="2"/><line x1="8" y1="21" x2="16" y2="21"/><line x1="12" y1="17" x2="12" y2="21"/></svg>
                </div>
                <div class="empty-state-title">还没有执行记录</div>
                <div class="empty-state-desc">点击「新建任务」开始使用</div>
            </div>

            <!-- 单列任务列表 -->
            <div v-else class="task-list-single">
                <div v-for="task in paginatedTasks" :key="task.id"
                     class="task-card task-card-row" @click="$emit('view-detail', task.id)">
                    <div class="task-card-header">
                        <div class="task-card-id">#{{ task.id }}</div>
                        <div class="task-card-status">
                            <span class="status-dot" :class="statusDotClass(task.status)"></span>
                            <span class="tag" :class="'tag-' + statusTagType(task.status)">{{ statusLabel(task.status) }}</span>
                        </div>
                    </div>
                    <div class="task-card-sql">{{ task.inputSql || '—' }}</div>

                    <!-- 表结构摘要 -->
                    <div v-if="getAnalysis(task)" class="task-card-analysis">
                        <div class="task-card-tables">
                            <span v-for="t in getAnalysis(task).generationOrder" :key="t" class="task-card-table-tag">{{ t }}</span>
                        </div>
                        <span v-if="getAnalysis(task).relations && getAnalysis(task).relations.length > 0" class="task-card-relation-badge">
                            <svg width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="5" cy="12" r="3"/><circle cx="19" cy="12" r="3"/><line x1="8" y1="12" x2="16" y2="12"/></svg>
                            {{ getAnalysis(task).relations.length }} 关联
                        </span>
                    </div>

                    <div class="task-card-meta">
                        <span class="task-meta-item">
                            <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="18" height="18" rx="2"/><line x1="3" y1="9" x2="21" y2="9"/></svg>
                            {{ task.rowsGenerated || 0 }}/{{ task.rowCount || 0 }} 行
                        </span>
                        <span class="task-meta-item">
                            {{ formatTime(task.startedAt) }}
                        </span>
                    </div>
                </div>
            </div>

            <!-- 分页 -->
            <div v-if="totalPages > 1" class="pagination">
                <button class="btn btn-ghost btn-sm" :disabled="currentPage <= 1" @click="currentPage--">
                    ← 上一页
                </button>
                <div class="pagination-info">
                    <span class="pagination-current">{{ currentPage }}</span> / {{ totalPages }}
                </div>
                <button class="btn btn-ghost btn-sm" :disabled="currentPage >= totalPages" @click="currentPage++">
                    下一页 →
                </button>
            </div>
        </div>
    </div>
    `,
    data() {
        return {
            tasks: [],
            loading: false,
            analysisCache: {},
            currentPage: 1,
            pageSize: 10,
        };
    },
    computed: {
        successCount() {
            return this.tasks.filter(t => t.status === 'SUCCESS').length;
        },
        totalRows() {
            return this.tasks.reduce((sum, t) => sum + (t.rowsGenerated || 0), 0);
        },
        totalPages() {
            return Math.ceil(this.tasks.length / this.pageSize);
        },
        paginatedTasks() {
            const start = (this.currentPage - 1) * this.pageSize;
            return this.tasks.slice(start, start + this.pageSize);
        },
    },
    mounted() {
        this.loadTasks();
    },
    methods: {
        async loadTasks() {
            this.loading = true;
            try {
                this.tasks = await API.listHistory();
                // 预解析 analysisSnapshot
                this.analysisCache = {};
                this.tasks.forEach(t => {
                    if (t.analysisSnapshot) {
                        try { this.analysisCache[t.id] = JSON.parse(t.analysisSnapshot); } catch(e) {}
                    }
                });
                // 重置到第一页
                if (this.currentPage > this.totalPages && this.totalPages > 0) {
                    this.currentPage = 1;
                }
            } catch (e) {
                Toast.error('加载任务列表失败: ' + e.message);
            }
            this.loading = false;
        },
        getAnalysis(task) {
            return this.analysisCache[task.id] || null;
        },
        statusDotClass(status) {
            switch (status) {
                case 'SUCCESS': return 'status-success';
                case 'FAILED': return 'status-danger';
                case 'RUNNING': return 'status-warning';
                default: return 'status-info';
            }
        },
        statusTagType(status) {
            switch (status) {
                case 'SUCCESS': return 'success';
                case 'FAILED': return 'danger';
                case 'RUNNING': return 'warning';
                default: return 'ghost';
            }
        },
        statusLabel(status) {
            switch (status) {
                case 'SUCCESS': return '成功';
                case 'FAILED': return '失败';
                case 'RUNNING': return '运行中';
                case 'PENDING': return '等待中';
                default: return status;
            }
        },
        formatTime(timeStr) {
            if (!timeStr) return '—';
            if (Array.isArray(timeStr)) {
                const [y, m, d, h, mi, s] = timeStr;
                return y + '-' + String(m).padStart(2, '0') + '-' + String(d).padStart(2, '0')
                    + ' ' + String(h).padStart(2, '0') + ':' + String(mi).padStart(2, '0');
            }
            if (typeof timeStr === 'string') {
                return timeStr.replace('T', ' ').substring(0, 16);
            }
            return String(timeStr);
        },
    },
};
