const GeneratePage = {
    props: ['connectionId', 'analysisResult', 'fieldRules', 'currentSql', 'sqlScriptId'],
    emits: ['prev', 'done', 'view-detail'],
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
                        {{ previewing ? '' : '' }} {{ previewing ? '生成中...' : '预览数据' }}
                    </button>
                    <button class="btn btn-success btn-lg" @click="executeWrite" :disabled="!previewResult || executing">
                        {{ executing ? '写入中...' : '写入数据库' }}
                    </button>
                </div>
            </div>

            <!-- 历史统计面板 -->
            <div v-if="statistics && statistics.totalTasks > 0" class="stats-panel">
                <span class="stats-label">历史统计</span>
                <span class="stats-item">采纳率: <strong>{{ (statistics.adoptionRate * 100).toFixed(1) }}%</strong></span>
                <span class="stats-divider">|</span>
                <span class="stats-item">重新生成率: <strong>{{ (statistics.regenerationRate * 100).toFixed(1) }}%</strong></span>
                <span class="stats-divider">|</span>
                <span class="stats-item">共 {{ statistics.totalTasks }} 次任务</span>
            </div>

            <!-- 生成中 Loading -->
            <div v-if="previewing" class="card">
                <div class="loading-container">
                    <div class="loading-spinner"></div>
                    <div class="loading-text">
                        <template v-if="previewProgress && previewProgress.currentTable">
                            正在生成: {{ previewProgress.currentTable }}
                            ({{ previewProgress.completedTables }}/{{ previewProgress.totalTables }})
                        </template>
                        <template v-else>
                            正在提交预览任务...
                        </template>
                    </div>
                    <div class="loading-progress">
                        <div class="loading-progress-bar" :style="{ width: (previewProgress ? previewProgress.percentage : 0) + '%' }"></div>
                    </div>
                    <div style="margin-top: var(--space-3); text-align: center;">
                        <button class="btn btn-sm btn-ghost" @click="cancelPreview"
                            style="color: var(--text-muted);">
                            取消生成
                        </button>
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

            <!-- 数据预览（可编辑） -->
            <template v-if="previewResult && !previewing">
                <div v-for="(rows, tableName) in previewResult.tableData" :key="tableName" class="card" style="overflow:visible;">
                    <div class="preview-table-wrap">
                        <div class="preview-table-header">
                            <div class="preview-table-title">
                                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="18" height="18" rx="2"/><line x1="3" y1="9" x2="21" y2="9"/><line x1="9" y1="21" x2="9" y2="9"/></svg>
                                {{ tableName }}
                            </div>
                            <div class="preview-toolbar">
                                <span class="edit-count-badge" v-if="getEditCount(tableName) > 0">
                                    已修改 {{ getEditCount(tableName) }} 处
                                </span>
                                <button class="btn btn-sm btn-ghost" v-if="getSelectedCount(tableName) > 0"
                                    @click="regenerateSelected(tableName)" :disabled="regenerating">
                                    {{ regenerating ? '生成中...' : '重新生成选中列 (' + getSelectedCount(tableName) + ')' }}
                                </button>
                                <button class="btn btn-sm btn-ghost" v-if="getEditCount(tableName) > 0"
                                    @click="revertAll(tableName)">恢复全部</button>
                                <button class="btn btn-sm btn-ghost" :class="{selected: showDiff}"
                                    @click="showDiff = !showDiff">对比</button>
                                <span class="preview-table-count">{{ rows.length }} 行</span>
                            </div>
                        </div>
                        <div class="preview-scroll">
                            <table class="data-table" v-if="rows.length > 0">
                                <thead>
                                    <tr>
                                        <th style="width: 50px;">#</th>
                                        <th v-for="col in Object.keys(rows[0])" :key="col"
                                            :class="{'col-selected': selectedColumns[tableName+'.'+col]}">
                                            <div class="col-header-cell">
                                                <input type="checkbox"
                                                    :checked="selectedColumns[tableName+'.'+col]"
                                                    @change="toggleColumnSelect(tableName, col)"
                                                    class="col-checkbox">
                                                <span class="col-header-name">{{ col }}</span>
                                                <span class="col-meta-icon" v-if="isAutoIncrement(tableName, col)" title="自增列">&#128274;</span>
                                                <span class="col-meta-icon" v-if="isForeignKey(tableName, col)" title="外键列">&#128279;</span>
                                                <button class="col-action-btn" @click.stop="showColumnMenu($event, tableName, col)" title="列操作">&#8942;</button>
                                            </div>
                                        </th>
                                    </tr>
                                </thead>
                                <tbody>
                                    <tr v-for="(row, i) in rows" :key="i">
                                        <td style="color: var(--text-muted);">{{ i + 1 }}</td>
                                        <td v-for="col in Object.keys(row)" :key="col"
                                            :class="{
                                                'cell-edited': isCellEdited(tableName, i, col),
                                                'cell-locked': !isColumnEditable(tableName, col)
                                            }"
                                            @dblclick="startEdit(tableName, i, col)"
                                            style="font-family: var(--font-mono); font-size: var(--text-xs); position:relative;">
                                            <input v-if="isEditing(tableName, i, col)"
                                                :value="editingValue"
                                                @input="editingValue = $event.target.value"
                                                class="cell-inline-input"
                                                @blur="saveEdit"
                                                @keyup.enter="saveEdit"
                                                @keyup.escape="cancelEdit"
                                                ref="editInput">
                                            <template v-else>
                                                <span>{{ row[col] }}</span>
                                                <span v-if="showDiff && isCellEdited(tableName, i, col)" class="cell-original">
                                                    原: {{ getCellOriginalValue(tableName, i, col) }}
                                                </span>
                                            </template>
                                        </td>
                                    </tr>
                                </tbody>
                            </table>
                        </div>
                    </div>
                </div>
            </template>

            <!-- 列操作菜单 -->
            <div v-if="columnMenuVisible" class="col-menu"
                :style="{left: columnMenuVisible.x+'px', top: columnMenuVisible.y+'px'}"
                @click.stop>
                <div class="col-menu-item" @click="regenerateSingleColumn">&#128260; 重新生成此列</div>
                <div class="col-menu-item" @click="openRuleDialog">&#9998; 修改规则后重新生成</div>
                <div class="col-menu-item" @click="revertColumn" v-if="getColumnEditCount(columnMenuVisible.table, columnMenuVisible.col) > 0">&#8617; 恢复原始数据</div>
            </div>

            <!-- 规则修改弹窗 -->
            <div v-if="ruleDialogVisible" class="modal-overlay" @click.self="ruleDialogVisible=false">
                <div class="modal-content" style="max-width:480px;">
                    <div class="modal-header">
                        <span class="modal-title">修改列规则 — {{ ruleDialogTarget?.col }}</span>
                        <button class="modal-close" @click="ruleDialogVisible=false">&times;</button>
                    </div>
                    <div class="form-group">
                        <label class="form-label">规则类型</label>
                        <select v-model="ruleDialogForm.ruleType" class="inline-select" style="width:100%;">
                            <option value="REGEX">正则表达式</option>
                            <option value="RANGE">范围</option>
                            <option value="ENUM">枚举值</option>
                            <option value="LLM_DESCRIPTION">AI 描述生成</option>
                        </select>
                    </div>
                    <!-- REGEX -->
                    <div class="form-group" v-if="ruleDialogForm.ruleType === 'REGEX'">
                        <label class="form-label">正则表达式</label>
                        <input v-model="ruleDialogForm.pattern" class="inline-input inline-input-mono" placeholder="例: [a-z]{5,10}@gmail\\.com">
                    </div>
                    <!-- RANGE -->
                    <template v-if="ruleDialogForm.ruleType === 'RANGE'">
                        <div class="form-group">
                            <label class="form-label">范围类型</label>
                            <select v-model="ruleDialogForm.rangeType" class="inline-select" style="width:100%;">
                                <option value="integer">整数</option>
                                <option value="decimal">小数</option>
                                <option value="date">日期</option>
                                <option value="datetime">日期时间</option>
                            </select>
                        </div>
                        <div class="form-group">
                            <label class="form-label">范围</label>
                            <div class="range-group">
                                <input v-model="ruleDialogForm.rangeMin" class="inline-input" placeholder="最小值">
                                <span class="range-separator">~</span>
                                <input v-model="ruleDialogForm.rangeMax" class="inline-input" placeholder="最大值">
                            </div>
                        </div>
                    </template>
                    <!-- ENUM -->
                    <div class="form-group" v-if="ruleDialogForm.ruleType === 'ENUM'">
                        <label class="form-label">枚举值（逗号分隔）</label>
                        <input v-model="ruleDialogForm.enumValues" class="inline-input" placeholder="例: male,female">
                    </div>
                    <!-- LLM -->
                    <div class="form-group" v-if="ruleDialogForm.ruleType === 'LLM_DESCRIPTION'">
                        <label class="form-label">AI 生成描述</label>
                        <input v-model="ruleDialogForm.llmDesc" class="inline-input" placeholder="例: 中文姓名、手机号、邮箱地址...">
                    </div>
                    <div class="btn-group" style="margin-top: var(--space-6);">
                        <button class="btn btn-primary" @click="confirmRuleAndRegenerate" :disabled="regenerating">
                            {{ regenerating ? '生成中...' : '确认并重新生成' }}
                        </button>
                        <button class="btn btn-ghost" @click="ruleDialogVisible=false">取消</button>
                    </div>
                </div>
            </div>

            <!-- 执行结果 -->
            <div v-if="executeResult" class="card">
                <div class="alert-box" :class="executeResult.status === 'SUCCESS' ? 'alert-success' : 'alert-danger'">
                    <span style="font-size: 18px;">{{ executeResult.status === 'SUCCESS' ? '&#10003;' : '&#10005;' }}</span>
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
                <div style="display: flex; gap: var(--space-3); align-items: center;">
                    <button v-if="previewDbTaskId" class="btn btn-ghost" @click="$emit('view-detail', previewDbTaskId)" style="font-size: var(--text-sm);">
                        查看任务详情 →
                    </button>
                    <button v-if="executeResult && executeResult.status === 'SUCCESS'" class="btn btn-primary btn-lg" @click="$emit('done')">
                        完成，返回首页
                    </button>
                    <button v-else-if="previewResult && !previewing" class="btn btn-primary btn-lg" @click="$emit('done')">
                        完成，返回首页
                    </button>
                </div>
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
            // 异步预览状态
            previewTaskId: null,
            previewDbTaskId: null,
            previewProgress: null,
            previewPollingTimer: null,
            availableModels: [],
            selectedModels: [],
            loadingModels: false,
            // 编辑相关
            originalData: null,
            editedCells: {},
            editingCell: null,
            editingValue: '',
            // 重新生成追踪（与手动编辑分离）
            regeneratedColumnsMap: {},
            regeneratedCellKeys: {},
            // 统计面板
            statistics: null,
            // 列操作
            columnMenuVisible: null,
            selectedColumns: {},
            regenerating: false,
            showDiff: false,
            // 规则修改弹窗
            ruleDialogVisible: false,
            ruleDialogTarget: null,
            ruleDialogForm: {
                ruleType: 'LLM_DESCRIPTION',
                pattern: '',
                rangeMin: '',
                rangeMax: '',
                rangeType: 'integer',
                enumValues: '',
                llmDesc: ''
            },
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
            this.resetEditState();
        },
    },
    mounted() {
        this.loadModels();
        this.loadStatistics();
        document.addEventListener('click', this.handleGlobalClick);
    },
    beforeUnmount() {
        document.removeEventListener('click', this.handleGlobalClick);
        if (this.previewPollingTimer) {
            clearTimeout(this.previewPollingTimer);
            this.previewPollingTimer = null;
        }
    },
    methods: {
        handleGlobalClick() {
            this.columnMenuVisible = null;
        },

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
            this.previewProgress = null;
            this.previewTaskId = null;
            this.previewDbTaskId = null;
            this.resetEditState();
            try {
                const request = this.buildRequest();
                if (!request.sql) {
                    Toast.warning('SQL 语句不能为空');
                    this.previewing = false;
                    return;
                }
                // 提交异步任务
                const submitResp = await API.submitPreview(request);
                this.previewTaskId = submitResp.taskId;
                this.previewDbTaskId = submitResp.dbTaskId;
                // 开始轮询
                this.pollPreviewStatus();
            } catch (e) {
                Toast.error('提交预览任务失败: ' + e.message);
                this.previewing = false;
            }
        },
        pollPreviewStatus() {
            if (this.previewPollingTimer) {
                clearTimeout(this.previewPollingTimer);
            }
            this.previewPollingTimer = setTimeout(async () => {
                if (!this.previewTaskId || !this.previewing) return;
                try {
                    const status = await API.getPreviewStatus(this.previewTaskId);
                    this.previewProgress = status.progress;
                    if (status.status === 'SUCCESS') {
                        this.previewResult = status.result;
                        this.originalData = JSON.parse(JSON.stringify(status.result.tableData));
                        this.previewing = false;
                        this.previewTaskId = null;
                        this.previewProgress = null;
                        Toast.success('数据生成完成，请预览确认');
                    } else if (status.status === 'FAILED') {
                        this.previewing = false;
                        this.previewTaskId = null;
                        this.previewProgress = null;
                        Toast.error('生成失败: ' + (status.errorMessage || '未知错误'));
                    } else if (status.status === 'CANCELLED') {
                        this.previewing = false;
                        this.previewTaskId = null;
                        this.previewProgress = null;
                        Toast.warning('预览任务已取消');
                    } else {
                        // PENDING 或 RUNNING，继续轮询
                        this.pollPreviewStatus();
                    }
                } catch (e) {
                    this.previewing = false;
                    this.previewTaskId = null;
                    this.previewProgress = null;
                    Toast.error('查询任务状态失败: ' + e.message);
                }
            }, 1500);
        },
        async cancelPreview() {
            if (!this.previewTaskId) return;
            try {
                await API.cancelPreview(this.previewTaskId);
            } catch (e) {
                Toast.error('取消失败: ' + e.message);
            }
        },
        async executeWrite() {
            if (!this.previewResult) {
                Toast.warning('请先预览数据');
                return;
            }
            this.executing = true;
            this.executeResult = null;
            try {
                // 计算编辑/重生成元数据
                const regenKeys = Object.keys(this.regeneratedCellKeys);
                const pureEditKeys = Object.keys(this.editedCells).filter(k => !this.regeneratedCellKeys[k]);
                const totalCellCount = this.computeTotalCellCount();

                const writeRequest = {
                    connectionId: this.connectionId,
                    sql: this.sql || this.currentSql || '',
                    tableData: this.previewResult.tableData,
                    generationOrder: this.previewResult.generationOrder,
                    fieldRules: this.fieldRules || [],
                    sqlScriptId: this.sqlScriptId,
                    hasManualEdits: pureEditKeys.length > 0,
                    hasRegeneration: Object.keys(this.regeneratedColumnsMap).length > 0,
                    regeneratedColumns: this.regeneratedColumnsMap,
                    editedCellCount: pureEditKeys.length,
                    regeneratedCellCount: regenKeys.length,
                    totalCellCount: totalCellCount,
                };
                this.executeResult = await API.writePreviewData(writeRequest);
                if (this.executeResult.status === 'SUCCESS') {
                    Toast.success('数据写入成功!');
                    this.loadStatistics();
                } else {
                    Toast.error('写入失败: ' + (this.executeResult.errorMessage || '未知错误'));
                }
            } catch (e) {
                Toast.error('执行失败: ' + e.message);
            }
            this.executing = false;
        },

        // ==================== 编辑功能 ====================
        resetEditState() {
            this.originalData = null;
            this.editedCells = {};
            this.editingCell = null;
            this.editingValue = '';
            this.selectedColumns = {};
            this.showDiff = false;
            this.regeneratedColumnsMap = {};
            this.regeneratedCellKeys = {};
        },

        isColumnEditable(table, col) {
            const meta = this.analysisResult?.tableMetadataMap?.[table];
            if (!meta?.columns) return true;
            const colMeta = meta.columns.find(c => c.columnName === col);
            return !(colMeta && colMeta.autoIncrement);
        },

        isAutoIncrement(table, col) {
            const meta = this.analysisResult?.tableMetadataMap?.[table];
            if (!meta?.columns) return false;
            const colMeta = meta.columns.find(c => c.columnName === col);
            return !!(colMeta && colMeta.autoIncrement);
        },

        isForeignKey(table, col) {
            const meta = this.analysisResult?.tableMetadataMap?.[table];
            if (!meta?.columns) return false;
            const colMeta = meta.columns.find(c => c.columnName === col);
            return !!(colMeta && colMeta.referencedTable);
        },

        startEdit(table, rowIdx, col) {
            if (!this.isColumnEditable(table, col)) {
                Toast.warning('自增列不可编辑');
                return;
            }
            this.editingCell = { table, row: rowIdx, col };
            this.editingValue = String(this.previewResult.tableData[table][rowIdx][col] ?? '');
            this.$nextTick(() => {
                const inputs = this.$refs.editInput;
                if (inputs && inputs.length > 0) {
                    inputs[0].focus();
                    inputs[0].select();
                }
            });
        },

        isEditing(table, rowIdx, col) {
            if (!this.editingCell) return false;
            return this.editingCell.table === table
                && this.editingCell.row === rowIdx
                && this.editingCell.col === col;
        },

        saveEdit() {
            if (!this.editingCell) return;
            const { table, row, col } = this.editingCell;
            const oldVal = this.previewResult.tableData[table][row][col];
            const newVal = this.editingValue;
            if (String(oldVal ?? '') !== String(newVal)) {
                this.previewResult.tableData[table][row][col] = newVal;
                const key = `${table}.${row}.${col}`;
                this.editedCells[key] = true;
                // 手动编辑优先：从重生成追踪中移除
                delete this.regeneratedCellKeys[key];
            }
            this.editingCell = null;
            this.editingValue = '';
        },

        cancelEdit() {
            this.editingCell = null;
            this.editingValue = '';
        },

        isCellEdited(table, rowIdx, col) {
            return !!this.editedCells[`${table}.${rowIdx}.${col}`];
        },

        getCellOriginalValue(table, rowIdx, col) {
            if (!this.originalData || !this.originalData[table]) return '';
            const row = this.originalData[table][rowIdx];
            return row ? (row[col] ?? '') : '';
        },

        getEditCount(tableName) {
            let count = 0;
            for (const key of Object.keys(this.editedCells)) {
                if (key.startsWith(tableName + '.')) count++;
            }
            return count;
        },

        getColumnEditCount(table, col) {
            let count = 0;
            for (const key of Object.keys(this.editedCells)) {
                if (key.startsWith(`${table}.`) && key.endsWith(`.${col}`)) count++;
            }
            return count;
        },

        // ==================== 列操作 ====================
        toggleColumnSelect(table, col) {
            const key = `${table}.${col}`;
            if (this.selectedColumns[key]) {
                delete this.selectedColumns[key];
            } else {
                this.selectedColumns[key] = true;
            }
            // 触发响应式更新
            this.selectedColumns = { ...this.selectedColumns };
        },

        getSelectedCount(tableName) {
            let count = 0;
            for (const key of Object.keys(this.selectedColumns)) {
                if (key.startsWith(tableName + '.') && this.selectedColumns[key]) count++;
            }
            return count;
        },

        showColumnMenu(event, table, col) {
            event.stopPropagation();
            this.columnMenuVisible = {
                table, col,
                x: event.clientX,
                y: event.clientY
            };
        },

        // ==================== 重新生成 ====================
        async regenerateColumns(table, columns) {
            this.regenerating = true;
            this.columnMenuVisible = null;
            try {
                const resp = await API.regenerateColumns({
                    connectionId: this.connectionId,
                    sql: this.sql || this.currentSql || '',
                    tableName: table,
                    columns: columns,
                    rowCount: this.previewResult.tableData[table].length,
                    fieldRules: this.fieldRules || [],
                    models: this.selectedModels,
                    existingData: this.previewResult.tableData
                });
                // 合并新列数据到预览结果
                if (resp.columnData) {
                    // 追踪重新生成的列
                    if (!this.regeneratedColumnsMap[table]) {
                        this.regeneratedColumnsMap[table] = [];
                    }
                    for (const [col, values] of Object.entries(resp.columnData)) {
                        if (!this.regeneratedColumnsMap[table].includes(col)) {
                            this.regeneratedColumnsMap[table].push(col);
                        }
                        values.forEach((val, idx) => {
                            if (idx < this.previewResult.tableData[table].length) {
                                this.previewResult.tableData[table][idx][col] = val;
                                const key = `${table}.${idx}.${col}`;
                                this.editedCells[key] = true;
                                this.regeneratedCellKeys[key] = true;
                            }
                        });
                    }
                    // 强制触发响应式更新
                    this.previewResult = { ...this.previewResult };
                    this.editedCells = { ...this.editedCells };
                    this.regeneratedCellKeys = { ...this.regeneratedCellKeys };
                    this.regeneratedColumnsMap = { ...this.regeneratedColumnsMap };
                }
                if (resp.warnings && resp.warnings.length > 0) {
                    resp.warnings.forEach(w => Toast.warning(w));
                } else {
                    Toast.success('已重新生成 ' + columns.length + ' 列数据');
                }
            } catch (e) {
                Toast.error('重新生成失败: ' + e.message);
            }
            this.regenerating = false;
        },

        regenerateSingleColumn() {
            if (!this.columnMenuVisible) return;
            const { table, col } = this.columnMenuVisible;
            this.regenerateColumns(table, [col]);
        },

        regenerateSelected(tableName) {
            const cols = [];
            for (const key of Object.keys(this.selectedColumns)) {
                if (key.startsWith(tableName + '.') && this.selectedColumns[key]) {
                    cols.push(key.substring(tableName.length + 1));
                }
            }
            if (cols.length === 0) {
                Toast.warning('请先选择要重新生成的列');
                return;
            }
            this.regenerateColumns(tableName, cols);
        },

        // ==================== 恢复 ====================
        revertColumn() {
            if (!this.columnMenuVisible || !this.originalData) return;
            const { table, col } = this.columnMenuVisible;
            const originalRows = this.originalData[table];
            if (!originalRows) return;

            const rows = this.previewResult.tableData[table];
            for (let i = 0; i < rows.length && i < originalRows.length; i++) {
                rows[i][col] = originalRows[i][col];
                delete this.editedCells[`${table}.${i}.${col}`];
                delete this.regeneratedCellKeys[`${table}.${i}.${col}`];
            }
            // 从 regeneratedColumnsMap 中移除该列
            if (this.regeneratedColumnsMap[table]) {
                this.regeneratedColumnsMap[table] = this.regeneratedColumnsMap[table].filter(c => c !== col);
                if (this.regeneratedColumnsMap[table].length === 0) {
                    delete this.regeneratedColumnsMap[table];
                }
                this.regeneratedColumnsMap = { ...this.regeneratedColumnsMap };
            }
            this.previewResult = { ...this.previewResult };
            this.editedCells = { ...this.editedCells };
            this.regeneratedCellKeys = { ...this.regeneratedCellKeys };
            this.columnMenuVisible = null;
            Toast.success('已恢复列 ' + col + ' 的原始数据');
        },

        revertAll(tableName) {
            if (!this.originalData || !this.originalData[tableName]) return;
            this.previewResult.tableData[tableName] = JSON.parse(JSON.stringify(this.originalData[tableName]));
            // 清除该表的所有编辑标记和重生成追踪
            const newEdited = {};
            for (const key of Object.keys(this.editedCells)) {
                if (!key.startsWith(tableName + '.')) {
                    newEdited[key] = true;
                }
            }
            this.editedCells = newEdited;
            const newRegenKeys = {};
            for (const key of Object.keys(this.regeneratedCellKeys)) {
                if (!key.startsWith(tableName + '.')) {
                    newRegenKeys[key] = true;
                }
            }
            this.regeneratedCellKeys = newRegenKeys;
            delete this.regeneratedColumnsMap[tableName];
            this.regeneratedColumnsMap = { ...this.regeneratedColumnsMap };
            this.previewResult = { ...this.previewResult };
            Toast.success('已恢复全部原始数据');
        },

        // ==================== 规则弹窗 ====================
        openRuleDialog() {
            if (!this.columnMenuVisible) return;
            const { table, col } = this.columnMenuVisible;
            const existingRule = (this.fieldRules || []).find(
                r => r.tableName === table && r.columnName === col
            );
            this.ruleDialogTarget = { table, col };
            this.ruleDialogForm = {
                ruleType: existingRule?.ruleType || 'LLM_DESCRIPTION',
                pattern: '',
                rangeMin: '',
                rangeMax: '',
                rangeType: 'integer',
                enumValues: '',
                llmDesc: ''
            };
            // 从已有规则恢复表单值
            if (existingRule && existingRule.ruleConfig) {
                try {
                    const config = JSON.parse(existingRule.ruleConfig);
                    switch (existingRule.ruleType) {
                        case 'REGEX':
                            this.ruleDialogForm.pattern = config.pattern || '';
                            break;
                        case 'RANGE':
                            this.ruleDialogForm.rangeMin = String(config.min || '');
                            this.ruleDialogForm.rangeMax = String(config.max || '');
                            this.ruleDialogForm.rangeType = config.type || 'integer';
                            break;
                        case 'ENUM':
                            this.ruleDialogForm.enumValues = (config.values || []).join(',');
                            break;
                        case 'LLM_DESCRIPTION':
                            this.ruleDialogForm.llmDesc = config.description || '';
                            break;
                    }
                } catch (e) { /* ignore */ }
            }
            this.ruleDialogVisible = true;
            this.columnMenuVisible = null;
        },

        confirmRuleAndRegenerate() {
            const { table, col } = this.ruleDialogTarget;
            const newRule = this.buildRuleFromForm(table, col);
            if (!newRule) {
                Toast.warning('请填写规则配置');
                return;
            }
            // 更新 fieldRules
            const rules = this.fieldRules || [];
            const idx = rules.findIndex(r => r.tableName === table && r.columnName === col);
            if (idx >= 0) {
                rules[idx] = newRule;
            } else {
                rules.push(newRule);
            }
            this.ruleDialogVisible = false;
            // 使用新规则调用重新生成
            this.regenerateColumns(table, [col]);
        },

        buildRuleFromForm(table, col) {
            const form = this.ruleDialogForm;
            let ruleConfig = '';
            switch (form.ruleType) {
                case 'REGEX':
                    if (!form.pattern) return null;
                    ruleConfig = JSON.stringify({ pattern: form.pattern });
                    break;
                case 'RANGE':
                    if (!form.rangeMin && !form.rangeMax) return null;
                    ruleConfig = JSON.stringify({ type: form.rangeType, min: form.rangeMin, max: form.rangeMax });
                    break;
                case 'ENUM':
                    if (!form.enumValues) return null;
                    ruleConfig = JSON.stringify({ values: form.enumValues.split(',').map(v => v.trim()).filter(Boolean) });
                    break;
                case 'LLM_DESCRIPTION':
                    if (!form.llmDesc) return null;
                    ruleConfig = JSON.stringify({ description: form.llmDesc });
                    break;
                default:
                    return null;
            }
            return {
                tableName: table,
                columnName: col,
                ruleType: form.ruleType,
                ruleConfig: ruleConfig,
                description: form.ruleType === 'LLM_DESCRIPTION' ? form.llmDesc : ''
            };
        },

        computeTotalCellCount() {
            if (!this.previewResult || !this.previewResult.tableData) return 0;
            let total = 0;
            for (const tableName of Object.keys(this.previewResult.tableData)) {
                const rows = this.previewResult.tableData[tableName];
                if (!rows || rows.length === 0) continue;
                const cols = Object.keys(rows[0]);
                const editableCols = cols.filter(col => this.isColumnEditable(tableName, col));
                total += rows.length * editableCols.length;
            }
            return total;
        },

        async loadStatistics() {
            try {
                this.statistics = await API.getAdoptionStatistics();
            } catch (e) {
                console.error('加载统计数据失败:', e);
            }
        },
    },
};
