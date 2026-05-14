const GeneratePage = {
    props: ['connectionId', 'analysisResult', 'fieldRules', 'currentSql', 'sqlScriptId'],
    emits: ['prev', 'done'],
    template: `
    <div>
        <div class="page-header">
            <h1 class="page-title">数据生成</h1>
            <p class="page-desc">选择 AI 模型，设置生成行数，预览数据后写入数据库</p>
        </div>

        <div v-if="!connectionId || !analysisResult" class="card">
            <div class="empty-state">
                <div class="empty-state-icon">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg>
                </div>
                <div class="empty-state-title">请先完成前置步骤</div>
                <div class="empty-state-desc">需要先选择数据库连接并完成 SQL 解析</div>
            </div>
        </div>

        <template v-else>
            <!-- 生成配置 -->
            <div class="card">
                <div class="card-title">
                    <svg class="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1 0 2.83 2 2 0 0 1-2.83 0l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-2 2 2 2 0 0 1-2-2v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83 0 2 2 0 0 1 0-2.83l.06-.06A1.65 1.65 0 0 0 4.68 15a1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1-2-2 2 2 0 0 1 2-2h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 0-2.83 2 2 0 0 1 2.83 0l.06.06A1.65 1.65 0 0 0 9 4.68a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 2-2 2 2 0 0 1 2 2v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 0 2 2 0 0 1 0 2.83l-.06.06A1.65 1.65 0 0 0 19.4 9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 2 2 2 2 0 0 1-2 2h-.09a1.65 1.65 0 0 0-1.51 1z"/></svg>
                    生成配置
                </div>

                <div class="config-section">
                    <div>
                        <div class="form-group">
                            <label class="form-label">选择 AI 模型</label>
                            <div class="chip-select">
                                <div v-if="loadingModels" style="color: var(--text-muted); font-size: var(--text-sm);">
                                    加载模型列表...
                                </div>
                                <template v-else>
                                    <button v-for="m in availableModels" :key="m.id"
                                        class="chip" :class="{ selected: selectedModels.includes(m.id) }"
                                        @click="toggleModel(m.id)">
                                        {{ m.name }}
                                    </button>
                                </template>
                            </div>
                            <p style="font-size: var(--text-xs); color: var(--text-muted); margin-top: var(--space-2);">
                                可多选，系统将随机分配不同字段使用不同模型
                            </p>
                        </div>

                        <div class="form-group">
                            <label class="form-label">生成行数</label>
                            <div class="number-input-wrap">
                                <button @click="rowCount = Math.max(1, rowCount - 10)">−</button>
                                <input type="number" v-model.number="rowCount" min="1" max="10000">
                                <button @click="rowCount = Math.min(10000, rowCount + 10)">+</button>
                            </div>
                        </div>
                    </div>

                    <div>
                        <div class="form-group">
                            <label class="form-label">涉及的表</label>
                            <div style="display: flex; flex-wrap: wrap; gap: var(--space-2);">
                                <span v-for="t in analysisResult.generationOrder" :key="t" class="tag tag-success">
                                    {{ t }}
                                </span>
                            </div>
                        </div>

                        <div class="form-group">
                            <label class="form-label">SQL 语句</label>
                            <div class="sql-editor-wrap" style="max-height: 100px; overflow: hidden;">
                                <div class="sql-editor-header">
                                    <span class="sql-editor-dot"></span>
                                    <span class="sql-editor-dot"></span>
                                    <span class="sql-editor-dot"></span>
                                    <span class="sql-editor-label">readonly</span>
                                </div>
                                <div style="padding: var(--space-3) var(--space-4); font-family: var(--font-mono); font-size: var(--text-xs); color: var(--primary-lighter); background: var(--bg-code); line-height: 1.5; overflow: hidden; text-overflow: ellipsis; white-space: pre-wrap; max-height: 60px;">{{ sql }}</div>
                            </div>
                        </div>
                    </div>
                </div>

                <div class="btn-group">
                    <button class="btn btn-primary btn-lg" @click="previewData" :disabled="previewing">
                        {{ previewing ? '' : '⚡' }} {{ previewing ? '生成中...' : '预览数据' }}
                    </button>
                    <button class="btn btn-success btn-lg" @click="executeWrite" :disabled="!previewResult || executing">
                        {{ executing ? '写入中...' : '💾 写入数据库' }}
                    </button>
                </div>
            </div>

            <!-- 生成中 Loading -->
            <div v-if="previewing" class="card">
                <div class="loading-container">
                    <div class="loading-spinner"></div>
                    <div class="loading-text">正在生成测试数据，请稍候...</div>
                    <div class="loading-progress">
                        <div class="loading-progress-bar"></div>
                    </div>
                    <div style="margin-top: var(--space-4);">
                        <div class="skeleton-row">
                            <div class="skeleton skeleton-cell"></div>
                            <div class="skeleton skeleton-cell"></div>
                            <div class="skeleton skeleton-cell"></div>
                            <div class="skeleton skeleton-cell"></div>
                        </div>
                        <div class="skeleton-row">
                            <div class="skeleton skeleton-cell"></div>
                            <div class="skeleton skeleton-cell"></div>
                            <div class="skeleton skeleton-cell"></div>
                            <div class="skeleton skeleton-cell"></div>
                        </div>
                        <div class="skeleton-row">
                            <div class="skeleton skeleton-cell"></div>
                            <div class="skeleton skeleton-cell"></div>
                            <div class="skeleton skeleton-cell"></div>
                            <div class="skeleton skeleton-cell"></div>
                        </div>
                    </div>
                </div>
            </div>

            <!-- 数据预览 -->
            <template v-if="previewResult && !previewing">
                <div v-for="(rows, tableName) in previewResult.tableData" :key="tableName" class="card">
                    <div class="preview-table-wrap">
                        <div class="preview-table-header">
                            <div class="preview-table-title">
                                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="18" height="18" rx="2"/><line x1="3" y1="9" x2="21" y2="9"/><line x1="9" y1="21" x2="9" y2="9"/></svg>
                                {{ tableName }}
                            </div>
                            <span class="preview-table-count">{{ rows.length }} 行</span>
                        </div>
                        <div class="preview-scroll">
                            <table class="data-table" v-if="rows.length > 0">
                                <thead>
                                    <tr>
                                        <th style="width: 50px;">#</th>
                                        <th v-for="col in Object.keys(rows[0])" :key="col">{{ col }}</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    <tr v-for="(row, i) in rows" :key="i">
                                        <td style="color: var(--text-muted);">{{ i + 1 }}</td>
                                        <td v-for="col in Object.keys(row)" :key="col"
                                            style="font-family: var(--font-mono); font-size: var(--text-xs);">
                                            {{ row[col] }}
                                        </td>
                                    </tr>
                                </tbody>
                            </table>
                        </div>
                    </div>
                </div>
            </template>

            <!-- 执行结果 -->
            <div v-if="executeResult" class="card">
                <div class="alert-box" :class="executeResult.status === 'SUCCESS' ? 'alert-success' : 'alert-danger'">
                    <span style="font-size: 18px;">{{ executeResult.status === 'SUCCESS' ? '✓' : '✕' }}</span>
                    <div>
                        <div style="font-weight: 600;">
                            {{ executeResult.status === 'SUCCESS' ? '数据写入成功!' : '执行失败' }}
                        </div>
                        <div style="font-size: var(--text-xs); margin-top: 2px; opacity: 0.8;">
                            {{ executeResult.status === 'SUCCESS'
                                ? '共生成 ' + executeResult.rowsGenerated + ' 行数据'
                                : executeResult.errorMessage || '未知错误' }}
                        </div>
                    </div>
                </div>
            </div>

            <!-- Step Navigation -->
            <div class="step-navigation">
                <button class="btn btn-ghost" @click="$emit('prev')">← 上一步</button>
                <button v-if="executeResult && executeResult.status === 'SUCCESS'" class="btn btn-primary btn-lg" @click="$emit('done')">
                    ✓ 完成，返回首页
                </button>
                <div v-else></div>
            </div>
        </template>
    </div>
    `,
    data() {
        return {
            sql: '',
            rowCount: 100,
            previewing: false,
            executing: false,
            previewResult: null,
            executeResult: null,
            availableModels: [],
            selectedModels: [],
            loadingModels: false,
        };
    },
    watch: {
        currentSql: {
            handler(val) { if (val) this.sql = val; },
            immediate: true,
        },
        rowCount() {
            this.previewResult = null;
            this.executeResult = null;
        },
    },
    mounted() {
        this.loadModels();
    },
    methods: {
        async loadModels() {
            this.loadingModels = true;
            try {
                this.availableModels = await API.getAvailableModels();
                if (this.availableModels.length > 0 && this.selectedModels.length === 0) {
                    this.selectedModels = [this.availableModels[0].id];
                }
            } catch (e) {
                console.error('加载模型列表失败:', e);
            }
            this.loadingModels = false;
        },
        toggleModel(modelId) {
            const idx = this.selectedModels.indexOf(modelId);
            if (idx >= 0) {
                if (this.selectedModels.length > 1) {
                    this.selectedModels.splice(idx, 1);
                }
            } else {
                this.selectedModels.push(modelId);
            }
        },
        buildRequest() {
            return {
                connectionId: this.connectionId,
                sql: this.sql || this.currentSql || '',
                rowCount: this.rowCount,
                fieldRules: this.fieldRules || [],
                models: this.selectedModels,
                sqlScriptId: this.sqlScriptId,
            };
        },
        async previewData() {
            this.previewing = true;
            this.previewResult = null;
            this.executeResult = null;
            try {
                const request = this.buildRequest();
                if (!request.sql) {
                    Toast.warning('SQL 语句不能为空');
                    this.previewing = false;
                    return;
                }
                this.previewResult = await API.previewData(request);
                Toast.success('数据生成完成，请预览确认');
            } catch (e) {
                Toast.error('生成失败: ' + e.message);
            }
            this.previewing = false;
        },
        async executeWrite() {
            if (!this.previewResult) {
                Toast.warning('请先预览数据');
                return;
            }
            this.executing = true;
            this.executeResult = null;
            try {
                const writeRequest = {
                    connectionId: this.connectionId,
                    sql: this.sql || this.currentSql || '',
                    tableData: this.previewResult.tableData,
                    generationOrder: this.previewResult.generationOrder,
                    fieldRules: this.fieldRules || [],
                    sqlScriptId: this.sqlScriptId,
                };
                this.executeResult = await API.writePreviewData(writeRequest);
                if (this.executeResult.status === 'SUCCESS') {
                    Toast.success('数据写入成功!');
                } else {
                    Toast.error('写入失败: ' + (this.executeResult.errorMessage || '未知错误'));
                }
            } catch (e) {
                Toast.error('执行失败: ' + e.message);
            }
            this.executing = false;
        },
    },
};
