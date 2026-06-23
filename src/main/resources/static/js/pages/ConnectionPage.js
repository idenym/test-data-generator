const ConnectionPage = {
    emits: ['connection-selected', 'next'],
    template: `
    <div>
        <div class="page-header">
            <h1 class="page-title">数据库连接</h1>
            <p class="page-desc">配置并选择目标数据库，支持 MySQL / GaussDB / TDSQL / Hive</p>
        </div>

        <!-- 新建/编辑 连接表单 -->
        <div class="card">
            <div class="card-title">
                <svg class="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 2L2 7l10 5 10-5-10-5z"/><path d="M2 17l10 5 10-5"/><path d="M2 12l10 5 10-5"/></svg>
                {{ editingId ? '编辑连接' : '新建连接' }}
            </div>
            <div class="form-row form-row-4">
                <div class="form-group">
                    <label class="form-label">数据库类型</label>
                    <select class="inline-input" v-model="form.dbType" @change="onDbTypeChange">
                        <option value="MYSQL">MySQL</option>
                        <option value="GAUSSDB">GaussDB</option>
                        <option value="TDSQL">TDSQL</option>
                        <option value="HIVE">Hive</option>
                    </select>
                </div>
                <div class="form-group">
                    <label class="form-label">连接名称</label>
                    <input class="inline-input" v-model="form.name" placeholder="例如: 测试库">
                </div>
                <div class="form-group">
                    <label class="form-label">主机地址</label>
                    <input class="inline-input inline-input-mono" v-model="form.host" placeholder="127.0.0.1">
                </div>
                <div class="form-group">
                    <label class="form-label">端口</label>
                    <input class="inline-input inline-input-mono" v-model.number="form.port" type="number" min="1" max="65535" :placeholder="defaultPort">
                </div>
            </div>
            <div class="form-row form-row-3">
                <div class="form-group">
                    <label class="form-label">用户名</label>
                    <input class="inline-input inline-input-mono" v-model="form.username" placeholder="root">
                </div>
                <div class="form-group">
                    <label class="form-label">密码</label>
                    <input class="inline-input" v-model="form.password" type="password" placeholder="••••••••">
                </div>
                <div class="form-group">
                    <label class="form-label">数据库名</label>
                    <input class="inline-input inline-input-mono" v-model="form.databaseName" placeholder="test_db">
                </div>
            </div>
            <div class="btn-group">
                <button class="btn btn-ghost" @click="testConnection" :disabled="testing">
                    {{ testing ? '测试中...' : '🔌 测试连接' }}
                </button>
                <button class="btn btn-primary" @click="saveConnection" :disabled="saving">
                    {{ saving ? '保存中...' : (editingId ? '更新连接' : '💾 保存连接') }}
                </button>
                <button v-if="editingId" class="btn btn-ghost" @click="resetForm">取消编辑</button>
            </div>

            <!-- 测试结果 -->
            <div v-if="testResult" style="margin-top: var(--space-4)">
                <div class="alert-box" :class="testResult.success ? 'alert-success' : 'alert-danger'">
                    <span>{{ testResult.success ? '✓' : '✕' }}</span>
                    <span>{{ testResult.message }}{{ testResult.version ? ' (' + testResult.version + ')' : '' }}</span>
                </div>
            </div>
        </div>

        <!-- 已保存连接列表 -->
        <div class="card">
            <div class="card-title">
                <svg class="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="2" y="2" width="20" height="8" rx="2" ry="2"/><rect x="2" y="14" width="20" height="8" rx="2" ry="2"/><line x1="6" y1="6" x2="6.01" y2="6"/><line x1="6" y1="18" x2="6.01" y2="18"/></svg>
                已保存的连接
                <span style="font-size: var(--text-xs); color: var(--text-tertiary); font-weight: 400; margin-left: auto;">
                    {{ connections.length }} 个连接
                </span>
            </div>

            <div v-if="connections.length === 0" class="empty-state">
                <div class="empty-state-icon">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><path d="M4 7v10c0 2.21 3.582 4 8 4s8-1.79 8-4V7"/><path d="M20 7c0 2.21-3.582 4-8 4S4 9.21 4 7c0-2.21 3.582-4 8-4s8 1.79 8 4z"/><path d="M4 12c0 2.21 3.582 4 8 4s8-1.79 8-4"/></svg>
                </div>
                <div class="empty-state-title">暂无已保存的连接</div>
                <div class="empty-state-desc">在上方填写连接信息并保存</div>
            </div>

            <div v-else class="conn-grid">
                <div v-for="conn in connections" :key="conn.id"
                     class="conn-card" :class="{ active: selectedId === conn.id }"
                     @click="selectConnection(conn)">
                    <div class="conn-card-name">
                        <span class="status-dot" :class="selectedId === conn.id ? 'status-success' : 'status-info'"></span>
                        {{ conn.name }}
                        <span class="db-type-badge" :class="'db-' + (conn.dbType || 'MYSQL')">{{ dbTypeLabel(conn.dbType) }}</span>
                    </div>
                    <div class="conn-card-info">{{ conn.host }}:{{ conn.port }} / {{ conn.databaseName }}</div>
                    <div class="conn-card-actions">
                        <button class="btn btn-ghost btn-sm" @click.stop="editConnection(conn)">编辑</button>
                        <button class="btn btn-danger btn-sm" @click.stop="deleteConnection(conn.id)">删除</button>
                        <span v-if="selectedId === conn.id" class="tag tag-success" style="margin-left: auto;">已选中</span>
                    </div>
                </div>
            </div>
        </div>

        <!-- Step Navigation -->
        <div class="step-navigation">
            <div></div>
            <button class="btn btn-primary btn-lg" @click="goNext" :disabled="!selectedId">
                下一步：SQL 解析 →
            </button>
        </div>
    </div>
    `,
    data() {
        return {
            form: { dbType: 'MYSQL', name: '', host: '127.0.0.1', port: 3306, username: 'root', password: '', databaseName: '', extraParams: '' },
            connections: [],
            selectedId: null,
            editingId: null,
            testing: false,
            saving: false,
            testResult: null,
        };
    },
    computed: {
        defaultPort() {
            const ports = { MYSQL: 3306, GAUSSDB: 5432, TDSQL: 3306, HIVE: 10000 };
            return ports[this.form.dbType] || 3306;
        }
    },
    async mounted() {
        await this.loadConnections();
    },
    methods: {
        async loadConnections() {
            try {
                this.connections = await API.listConnections();
            } catch (e) {
                Toast.error('加载连接列表失败: ' + e.message);
            }
        },
        async testConnection() {
            if (!this.form.host || !this.form.databaseName) {
                Toast.warning('请填写主机地址和数据库名');
                return;
            }
            this.testing = true;
            this.testResult = null;
            try {
                this.testResult = await API.testConnection(this.form);
            } catch (e) {
                this.testResult = { success: false, message: e.message };
            }
            this.testing = false;
        },
        async saveConnection() {
            if (!this.form.name || !this.form.host || !this.form.databaseName) {
                Toast.warning('请填写连接名称、主机地址和数据库名');
                return;
            }
            this.saving = true;
            try {
                if (this.editingId) {
                    await API.updateConnection(this.editingId, this.form);
                    Toast.success('连接更新成功');
                } else {
                    await API.createConnection(this.form);
                    Toast.success('连接保存成功');
                }
                this.resetForm();
                await this.loadConnections();
            } catch (e) {
                Toast.error('保存失败: ' + e.message);
            }
            this.saving = false;
        },
        selectConnection(conn) {
            this.selectedId = conn.id;
            this.$emit('connection-selected', conn.id);
            Toast.success('已选择连接: ' + conn.name);
        },
        editConnection(conn) {
            this.editingId = conn.id;
            this.form = {
                dbType: conn.dbType || 'MYSQL',
                name: conn.name,
                host: conn.host,
                port: conn.port,
                username: conn.username,
                password: '',
                databaseName: conn.databaseName,
                extraParams: conn.extraParams || '',
            };
        },
        async deleteConnection(id) {
            if (!confirm('确定要删除这个连接吗？')) return;
            try {
                await API.deleteConnection(id);
                Toast.success('删除成功');
                if (this.selectedId === id) {
                    this.selectedId = null;
                    this.$emit('connection-selected', null);
                }
                await this.loadConnections();
            } catch (e) {
                Toast.error('删除失败: ' + e.message);
            }
        },
        resetForm() {
            this.form = { dbType: 'MYSQL', name: '', host: '127.0.0.1', port: 3306, username: 'root', password: '', databaseName: '', extraParams: '' };
            this.editingId = null;
            this.testResult = null;
        },
        goNext() {
            if (!this.selectedId) {
                Toast.warning('请先选择一个数据库连接');
                return;
            }
            this.$emit('next');
        },
        onDbTypeChange() {
            // 自动切换默认端口
            const ports = { MYSQL: 3306, GAUSSDB: 5432, TDSQL: 3306, HIVE: 10000 };
            this.form.port = ports[this.form.dbType] || 3306;
        },
        dbTypeLabel(dbType) {
            const labels = { MYSQL: 'MySQL', GAUSSDB: 'GaussDB', TDSQL: 'TDSQL', HIVE: 'Hive' };
            return labels[dbType] || dbType || 'MySQL';
        },
    },
};
