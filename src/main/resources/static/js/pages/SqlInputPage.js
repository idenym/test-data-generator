const SqlInputPage = {
    props: ['connectionId'],
    emits: ['analysis-done', 'script-selected', 'prev', 'next'],
    template: `
    <div>
        <div class="page-header">
            <h1 class="page-title">SQL 解析</h1>
            <p class="page-desc">输入 INSERT 或 SELECT 语句，系统将自动解析表结构、字段信息和关联关系</p>
        </div>

        <div v-if="!connectionId" class="card">
            <div class="empty-state">
                <div class="empty-state-icon">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg>
                </div>
                <div class="empty-state-title">未选择数据库连接</div>
                <div class="empty-state-desc">请返回上一步选择数据库连接</div>
            </div>
        </div>

        <template v-else>
            <div class="sql-page-layout">
                <!-- 侧边栏脚本库 -->
                <div class="script-sidebar" :class="{ collapsed: sidebarCollapsed }">
                    <div class="script-sidebar-header">
                        <span class="script-sidebar-title" v-show="!sidebarCollapsed">脚本库</span>
                        <button class="btn-icon sidebar-toggle-btn" @click="sidebarCollapsed = !sidebarCollapsed" :title="sidebarCollapsed ? '展开脚本库' : '收起脚本库'">
                            <svg v-if="!sidebarCollapsed" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="15 18 9 12 15 6"/></svg>
                            <svg v-else width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="9 18 15 12 9 6"/></svg>
                        </button>
                    </div>
                    <div class="script-sidebar-content" v-show="!sidebarCollapsed">
                        <button class="btn btn-sm btn-outline script-add-btn" @click="showSaveDialog = true">+ 保存当前SQL</button>
                        <div class="script-list" v-if="scripts.length > 0">
                            <div v-for="script in scripts" :key="script.id"
                                 class="script-list-item"
                                 :class="{ active: currentScriptId === script.id }"
                                 @click="selectScript(script)">
                                <div class="script-item-name" :title="script.name">{{ script.name }}</div>
                                <div class="script-item-actions">
                                    <button class="btn-icon btn-tiny" @click.stop="startRename(script)" title="重命名">
                                        <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/><path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/></svg>
                                    </button>
                                    <button class="btn-icon btn-tiny" @click.stop="deleteScript(script.id)" title="删除">
                                        <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/></svg>
                                    </button>
                                </div>
                            </div>
                        </div>
                        <div v-else class="script-list-empty">暂无保存的脚本</div>
                    </div>
                </div>

                <!-- 主内容区 -->
                <div class="sql-main-content">
                    <!-- SQL 输入 -->
                    <div class="card">
                        <div class="card-title">
                            <svg class="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="16 18 22 12 16 6"/><polyline points="8 6 2 12 8 18"/></svg>
                            输入 SQL 语句
                            <span v-if="currentScriptName" class="script-indicator">[ {{ currentScriptName }} ]</span>
                        </div>
                        <div class="sql-editor-wrap">
                            <div class="sql-editor-header">
                                <span class="sql-editor-dot"></span>
                                <span class="sql-editor-dot"></span>
                                <span class="sql-editor-dot"></span>
                                <span class="sql-editor-label">SQL Query</span>
                            </div>
                            <textarea class="sql-editor" v-model="sql" placeholder="-- 输入 SQL 语句，例如:

SELECT u.id, u.name, u.email, o.order_no, o.amount
FROM users u
JOIN orders o ON u.id = o.user_id
WHERE u.status = 1

-- 或者:
INSERT INTO users (name, age, phone, email) VALUES (...)"></textarea>
                        </div>
                        <div class="btn-group">
                            <button class="btn btn-primary" @click="analyzeSql" :disabled="analyzing || !sql.trim()">
                                {{ analyzing ? '解析中...' : '解析 SQL' }}
                            </button>
                            <button class="btn btn-outline" @click="saveCurrentScript" v-if="currentScriptId" :disabled="!sql.trim()">
                                保存
                            </button>
                            <button class="btn btn-outline" @click="showSaveDialog = true" :disabled="!sql.trim()">
                                另存为...
                            </button>
                            <button class="btn btn-ghost" @click="$refs.fileInput.click()">
                                上传 SQL 文件
                            </button>
                            <button class="btn btn-ghost" @click="clearSql">清空</button>
                            <input type="file" ref="fileInput" accept=".sql,.txt" style="display:none" @change="handleFileUpload">
                        </div>
                    </div>

                    <!-- 解析结果 -->
                    <template v-if="result">
                        <!-- 关联关系图 -->
                        <div class="card" v-if="result.relations && result.relations.length > 0">
                            <div class="card-title">
                                <svg class="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="18" cy="5" r="3"/><circle cx="6" cy="12" r="3"/><circle cx="18" cy="19" r="3"/><line x1="8.59" y1="13.51" x2="15.42" y2="17.49"/><line x1="15.41" y1="6.51" x2="8.59" y2="10.49"/></svg>
                                表关联关系
                            </div>
                            <div class="relation-flow">
                                <div v-for="rel in result.relations" :key="rel.fromTable + rel.fromColumn" class="relation-item">
                                    <span class="relation-table-name">{{ rel.fromTable }}</span>
                                    <span class="relation-col-name">{{ rel.fromColumn }}</span>
                                    <span class="relation-arrow-icon">-></span>
                                    <span class="relation-table-name">{{ rel.toTable }}</span>
                                    <span class="relation-col-name">{{ rel.toColumn }}</span>
                                    <span class="relation-join-type">{{ rel.joinType || 'FK' }}</span>
                                </div>
                            </div>
                        </div>

                        <!-- 生成顺序 -->
                        <div class="card">
                            <div class="card-title">
                                <svg class="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="8" y1="6" x2="21" y2="6"/><line x1="8" y1="12" x2="21" y2="12"/><line x1="8" y1="18" x2="21" y2="18"/><line x1="3" y1="6" x2="3.01" y2="6"/><line x1="3" y1="12" x2="3.01" y2="12"/><line x1="3" y1="18" x2="3.01" y2="18"/></svg>
                                数据生成顺序
                            </div>
                            <div class="gen-order">
                                <template v-for="(t, i) in result.generationOrder" :key="'o'+t">
                                    <div class="gen-order-item">
                                        <span class="gen-order-tag">{{ t }}</span>
                                        <span v-if="i < result.generationOrder.length - 1" class="gen-order-arrow">-></span>
                                    </div>
                                </template>
                            </div>

                            <!-- 警告信息 -->
                            <div v-if="result.warnings && result.warnings.length > 0" style="margin-top: var(--space-5)">
                                <div v-for="w in result.warnings" :key="w" class="alert-box alert-warning">
                                    <span>!</span>
                                    <span>{{ w }}</span>
                                </div>
                            </div>
                        </div>

                        <!-- 表结构详情 -->
                        <div v-for="(meta, tableName) in result.tableMetadataMap" :key="tableName" class="card">
                            <div class="table-section-header">
                                <div class="table-section-name">
                                    <svg class="table-icon" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="18" height="18" rx="2"/><line x1="3" y1="9" x2="21" y2="9"/><line x1="9" y1="21" x2="9" y2="9"/></svg>
                                    {{ tableName }}
                                    <span v-if="meta.tableComment" class="table-section-comment">{{ meta.tableComment }}</span>
                                </div>
                                <span class="tag tag-ghost">{{ meta.columns ? meta.columns.length : 0 }} 字段</span>
                            </div>
                            <div class="data-table-wrap">
                                <table class="data-table">
                                    <thead>
                                        <tr>
                                            <th>列名</th>
                                            <th>类型</th>
                                            <th style="text-align:center">属性</th>
                                            <th>注释</th>
                                            <th>外键引用</th>
                                        </tr>
                                    </thead>
                                    <tbody>
                                        <tr v-for="col in meta.columns" :key="col.columnName">
                                            <td style="font-family: var(--font-mono); font-weight: 500;">{{ col.columnName }}</td>
                                            <td><span class="tag tag-ghost">{{ col.columnType }}</span></td>
                                            <td style="text-align:center">
                                                <span v-if="col.primaryKey" class="table-tag tag-pk" style="margin-right: 4px;">PK</span>
                                                <span v-if="col.autoIncrement" class="table-tag tag-ai" style="margin-right: 4px;">AI</span>
                                                <span v-if="!col.nullable" class="tag tag-ghost">NOT NULL</span>
                                            </td>
                                            <td style="color: var(--text-secondary); font-size: var(--text-xs);">{{ col.comment || '\u2014' }}</td>
                                            <td>
                                                <span v-if="col.referencedTable" class="tag tag-fk">
                                                    -> {{ col.referencedTable }}.{{ col.referencedColumn }}
                                                </span>
                                            </td>
                                        </tr>
                                    </tbody>
                                </table>
                            </div>
                        </div>
                    </template>
                </div>
            </div>

            <!-- Step Navigation -->
            <div class="step-navigation">
                <button class="btn btn-ghost" @click="$emit('prev')">< 上一步</button>
                <button class="btn btn-primary btn-lg" @click="proceedToRules" :disabled="!result">
                    下一步：配置规则 >
                </button>
            </div>
        </template>

        <!-- 保存脚本弹窗 -->
        <div v-if="showSaveDialog" class="save-dialog-overlay" @click.self="showSaveDialog = false">
            <div class="save-dialog">
                <div class="save-dialog-title">{{ renaming ? '重命名脚本' : '保存 SQL 脚本' }}</div>
                <input class="save-dialog-input" v-model="saveName" :placeholder="renaming ? '输入新名称' : '输入脚本名称'" @keyup.enter="confirmSave" ref="saveInput">
                <div class="save-dialog-btns">
                    <button class="btn btn-ghost btn-sm" @click="cancelSave">取消</button>
                    <button class="btn btn-primary btn-sm" @click="confirmSave" :disabled="!saveName.trim()">确定</button>
                </div>
            </div>
        </div>
    </div>
    `,
    data() {
        return {
            sql: '',
            result: null,
            analyzing: false,
            // 脚本库
            scripts: [],
            currentScriptId: null,
            currentScriptName: '',
            sidebarCollapsed: false,
            showSaveDialog: false,
            saveName: '',
            renaming: false,
            renamingId: null,
            pendingProceed: false,
        };
    },
    mounted() {
        this.loadScripts();
    },
    methods: {
        async loadScripts() {
            try {
                this.scripts = await API.listScripts();
            } catch (e) {
                console.warn('加载脚本列表失败:', e.message);
            }
        },
        selectScript(script) {
            this.sql = script.sqlContent || '';
            this.currentScriptId = script.id;
            this.currentScriptName = script.name;
            this.result = null;
            this.$emit('script-selected', script.id);
        },
        async saveCurrentScript() {
            if (!this.currentScriptId || !this.sql.trim()) return;
            try {
                await API.updateScript(this.currentScriptId, {
                    name: this.currentScriptName,
                    sqlContent: this.sql
                });
                Toast.success('脚本已保存');
                this.loadScripts();
            } catch (e) {
                Toast.error('保存失败: ' + e.message);
            }
        },
        async confirmSave() {
            if (!this.saveName.trim()) return;

            if (this.renaming && this.renamingId) {
                // 重命名
                try {
                    await API.updateScript(this.renamingId, { name: this.saveName });
                    Toast.success('重命名成功');
                    if (this.renamingId === this.currentScriptId) {
                        this.currentScriptName = this.saveName;
                    }
                    this.loadScripts();
                } catch (e) {
                    Toast.error('重命名失败: ' + e.message);
                }
            } else {
                // 另存为新脚本
                try {
                    const script = await API.createScript({
                        name: this.saveName,
                        sqlContent: this.sql,
                        connectionId: this.connectionId
                    });
                    this.currentScriptId = script.id;
                    this.currentScriptName = script.name;
                    this.$emit('script-selected', script.id);
                    Toast.success('脚本已保存');
                    this.loadScripts();
                } catch (e) {
                    Toast.error('保存失败: ' + e.message);
                }
            }
            const shouldProceed = this.pendingProceed && this.currentScriptId;
            this.cancelSave();
            // 保存后如果是从"下一步"触发的，自动跳转
            if (shouldProceed && this.result) {
                this.$emit('analysis-done', this.result, this.sql);
                this.$emit('next');
            }
        },
        cancelSave() {
            this.showSaveDialog = false;
            this.saveName = '';
            this.renaming = false;
            this.renamingId = null;
            this.pendingProceed = false;
        },
        startRename(script) {
            this.renaming = true;
            this.renamingId = script.id;
            this.saveName = script.name;
            this.showSaveDialog = true;
        },
        async deleteScript(id) {
            if (!confirm('确定删除此脚本？')) return;
            try {
                await API.deleteScript(id);
                if (this.currentScriptId === id) {
                    this.currentScriptId = null;
                    this.currentScriptName = '';
                    this.$emit('script-selected', null);
                }
                Toast.success('脚本已删除');
                this.loadScripts();
            } catch (e) {
                Toast.error('删除失败: ' + e.message);
            }
        },
        clearSql() {
            this.sql = '';
            this.currentScriptId = null;
            this.currentScriptName = '';
            this.result = null;
            this.$emit('script-selected', null);
        },
        async analyzeSql() {
            if (!this.sql.trim()) {
                Toast.warning('请输入 SQL 语句');
                return;
            }
            this.analyzing = true;
            try {
                this.result = await API.analyzeSql({ connectionId: this.connectionId, sql: this.sql });
                this.$emit('analysis-done', this.result, this.sql);
                Toast.success('SQL 解析成功');
            } catch (e) {
                Toast.error('SQL 解析失败: ' + e.message);
            }
            this.analyzing = false;
        },
        proceedToRules() {
            if (!this.result) {
                Toast.warning('请先解析 SQL');
                return;
            }
            if (!this.currentScriptId) {
                Toast.warning('请先保存脚本（输入名称后保存）');
                this.showSaveDialog = true;
                this.pendingProceed = true;
                return;
            }
            this.$emit('analysis-done', this.result, this.sql);
            this.$emit('next');
        },
        handleFileUpload(event) {
            const file = event.target.files[0];
            if (!file) return;

            const reader = new FileReader();
            reader.onload = async (e) => {
                const content = e.target.result;
                this.sql = content;
                this.result = null;

                // 从文件名获取脚本名（去掉扩展名）
                const scriptName = file.name.replace(/\.(sql|txt)$/i, '');

                // 自动保存到脚本库
                try {
                    const script = await API.createScript({
                        name: scriptName,
                        sqlContent: content,
                        connectionId: this.connectionId || null
                    });
                    this.currentScriptId = script.id;
                    this.currentScriptName = scriptName;
                    this.$emit('script-selected', script.id);
                    await this.loadScripts();
                    Toast.success('文件 "' + file.name + '" 已导入并保存到脚本库');
                } catch (err) {
                    Toast.error('保存脚本失败: ' + err.message);
                }
            };
            reader.readAsText(file, 'UTF-8');

            // 重置 input，允许重复上传相同文件
            event.target.value = '';
        },
    },
};
