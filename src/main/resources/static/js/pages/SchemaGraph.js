/**
 * SchemaGraph V2 — Interactive ER Diagram
 *
 * 特性:
 *  - 横向分层布局：依赖从左 → 右流动，每层垂直堆叠
 *  - 鼠标滚轮缩放（以光标为锚点，0.3 ~ 2.5 倍）
 *  - 空白处拖动平移整个画布
 *  - 表头按住拖拽单卡片
 *  - 字段折叠 / 展开（折叠后只显示表名）
 *  - 全屏模式
 *  - 右下角迷你地图（鸟瞰）
 *  - 卡片位置 / 折叠状态写入 sessionStorage（按 taskId 区分）
 */
const SchemaGraph = {
    props: {
        taskId: { type: [String, Number], default: null },
        tableMetadataMap: { type: Object, required: true },
        relations: { type: Array, default: () => [] },
        generationOrder: { type: Array, default: () => [] },
    },
    template: `
    <div class="er-v2-wrapper" :class="{ 'is-fullscreen': fullscreen }" ref="wrapper" @wheel.prevent="onWheel">
        <!-- 工具栏 -->
        <div class="er-v2-toolbar">
            <button class="er-v2-toolbar-btn" @click="zoomIn" title="放大 (Ctrl + 滚轮)">+</button>
            <button class="er-v2-toolbar-btn" @click="zoomOut" title="缩小">−</button>
            <span class="er-v2-zoom-label">{{ Math.round(scale * 100) }}%</span>
            <span class="er-v2-toolbar-divider"></span>
            <button class="er-v2-toolbar-btn" @click="fitToView" title="适配视图">⤧</button>
            <button class="er-v2-toolbar-btn" @click="resetLayout" title="重置布局">⟲</button>
            <span class="er-v2-toolbar-divider"></span>
            <button class="er-v2-toolbar-btn" @click="collapseAll" title="全部折叠">⊟</button>
            <button class="er-v2-toolbar-btn" @click="expandAll" title="全部展开">⊞</button>
            <span class="er-v2-toolbar-divider"></span>
            <button class="er-v2-toolbar-btn" :class="{ 'is-active': showLegend }" @click="showLegend = !showLegend"
                    title="切换图注" v-if="relations && relations.length > 0">
                关联 {{ relations.length }}
            </button>
            <button class="er-v2-toolbar-btn" @click="toggleFullscreen" :title="fullscreen ? '退出全屏' : '进入全屏'">
                {{ fullscreen ? '◱' : '⤢' }}
            </button>
        </div>

        <!-- 视口（裁剪 + 平移监听） -->
        <div class="er-v2-viewport" :class="{ 'is-panning': panning }" ref="viewport"
             @mousedown="onViewportMouseDown">
            <!-- 画布（缩放 + 平移变换） -->
            <div class="er-v2-canvas" ref="canvas"
                 :style="{
                    transform: 'translate(' + tx + 'px, ' + ty + 'px) scale(' + scale + ')',
                    width: canvasWidth + 'px',
                    height: canvasHeight + 'px'
                 }">
                <!-- 连接线 -->
                <svg class="er-v2-svg"
                     :width="canvasWidth"
                     :height="canvasHeight"
                     :viewBox="'0 0 ' + canvasWidth + ' ' + canvasHeight">
                    <path v-for="(line, i) in connLines" :key="'line-' + i"
                          :d="line.path"
                          class="er-v2-conn"
                          :class="{ highlighted: line.highlighted }"
                    />
                </svg>

                <!-- 表卡片 -->
                <div v-for="(node, idx) in tableNodes" :key="node.name"
                     class="er-v2-card"
                     :class="[
                        'er-v2-card-color-' + (idx % 4),
                        { 'is-collapsed': isCollapsed(node.name), 'is-dragging': draggingNode === node.name }
                     ]"
                     :style="{ left: node.x + 'px', top: node.y + 'px', width: cardWidth + 'px' }">
                    <div class="er-v2-header" :class="'color-' + (idx % 4)"
                         title="拖拽可移动表位置"
                         @mousedown.stop="onCardHeaderMouseDown($event, node)">
                        <span class="er-v2-header-name">{{ node.name }}</span>
                        <span class="er-v2-header-badge">{{ node.columns.length }}</span>
                        <button class="er-v2-collapse-btn"
                                @mousedown.stop
                                @click.stop="toggleCollapse(node.name)"
                                :title="isCollapsed(node.name) ? '展开' : '折叠'">
                            {{ isCollapsed(node.name) ? '▶' : '▼' }}
                        </button>
                    </div>
                    <div class="er-v2-body">
                        <div v-for="col in node.columns" :key="col.columnName"
                             class="er-v2-row"
                             :class="{ 'is-pk': col.primaryKey, 'is-fk': !!col.referencedTable }">
                            <span class="er-v2-row-icon">
                                <template v-if="col.primaryKey">&#128273;</template>
                                <template v-else-if="col.referencedTable">&#128279;</template>
                                <template v-else>&#9702;</template>
                            </span>
                            <span class="er-v2-row-name">{{ col.columnName }}</span>
                            <span class="er-v2-row-type">{{ formatType(col) }}</span>
                        </div>
                    </div>
                </div>
            </div>

            <!-- 空状态 -->
            <div v-if="tableNodes.length === 0" class="er-v2-empty">
                <div>暂无库表数据</div>
            </div>
        </div>

        <!-- 浮动图注 -->
        <div v-if="showLegend && relations && relations.length > 0" class="er-v2-floating-legend">
            <div class="er-v2-floating-legend-header">
                <span>关联关系 · {{ relations.length }}</span>
                <button class="er-v2-floating-legend-close" @click="showLegend = false">×</button>
            </div>
            <div class="er-v2-floating-legend-list">
                <div v-for="(rel, i) in relations" :key="i" class="er-v2-floating-legend-item">
                    <span class="er-v2-floating-legend-from">{{ rel.fromTable }}.{{ rel.fromColumn }}</span>
                    <span class="er-v2-floating-legend-arrow">→</span>
                    <span class="er-v2-floating-legend-to">{{ rel.toTable }}.{{ rel.toColumn }}</span>
                </div>
            </div>
        </div>

        <!-- 迷你地图 -->
        <div v-if="tableNodes.length > 1" class="er-v2-minimap" ref="minimap"
             @mousedown.stop="onMinimapMouseDown">
            <svg class="er-v2-minimap-svg" :viewBox="'0 0 ' + canvasWidth + ' ' + canvasHeight"
                 preserveAspectRatio="xMidYMid meet">
                <rect v-for="node in tableNodes" :key="'mm-' + node.name"
                      :x="node.x" :y="node.y"
                      :width="cardWidth" :height="getNodeRenderHeight(node)"
                      class="er-v2-minimap-table" rx="4" ry="4" />
                <rect class="er-v2-minimap-viewport"
                      :x="minimapVp.x" :y="minimapVp.y"
                      :width="minimapVp.w" :height="minimapVp.h" />
            </svg>
        </div>

        <!-- 全屏关闭按钮 -->
        <button v-if="fullscreen" class="er-v2-close-fullscreen" @click="toggleFullscreen" title="退出全屏 (ESC)">×</button>
    </div>
    `,
    data() {
        return {
            // 布局参数
            cardWidth: 260,
            cardHeaderHeight: 36,
            cardRowHeight: 26,
            cardBodyPadding: 8,
            colGap: 120,
            rowGap: 30,
            // 节点
            tableNodes: [],
            connLines: [],
            // 视口尺寸
            canvasWidth: 1200,
            canvasHeight: 800,
            viewportWidth: 0,
            viewportHeight: 0,
            // 变换
            scale: 1,
            tx: 0,
            ty: 0,
            // 折叠状态
            collapsedSet: new Set(),
            // 拖拽 / 平移状态
            draggingNode: null,
            dragOffsetX: 0,
            dragOffsetY: 0,
            panning: false,
            panStartX: 0,
            panStartY: 0,
            panOriginTx: 0,
            panOriginTy: 0,
            // 迷你地图拖拽
            mmDragging: false,
            // UI 状态
            fullscreen: false,
            showLegend: true,
        };
    },
    computed: {
        sessionPosKey() {
            return 'er-v2-pos-' + (this.taskId || 'na');
        },
        sessionCollapsedKey() {
            return 'er-v2-collapsed-' + (this.taskId || 'na');
        },
        minimapVp() {
            // 当前视口在 canvas 坐标系中的位置（用于迷你地图蓝框）
            const x = -this.tx / this.scale;
            const y = -this.ty / this.scale;
            const w = this.viewportWidth / this.scale;
            const h = this.viewportHeight / this.scale;
            return { x, y, w, h };
        },
    },
    mounted() {
        this.restoreCollapsed();
        this.layout();
        this.$nextTick(() => {
            this.measureViewport();
            this.fitToView();
            this.calcConnections();
        });
        window.addEventListener('mousemove', this.onWindowMouseMove);
        window.addEventListener('mouseup', this.onWindowMouseUp);
        window.addEventListener('resize', this.onResize);
        window.addEventListener('keydown', this.onKeyDown);
    },
    beforeUnmount() {
        window.removeEventListener('mousemove', this.onWindowMouseMove);
        window.removeEventListener('mouseup', this.onWindowMouseUp);
        window.removeEventListener('resize', this.onResize);
        window.removeEventListener('keydown', this.onKeyDown);
    },
    watch: {
        tableMetadataMap: { handler() { this.layout(); this.$nextTick(this.calcConnections); }, deep: true },
        relations: { handler() { this.layout(); this.$nextTick(this.calcConnections); }, deep: true },
    },
    methods: {
        // ── Persistence ──
        restoreCollapsed() {
            try {
                const raw = sessionStorage.getItem(this.sessionCollapsedKey);
                if (raw) {
                    const arr = JSON.parse(raw);
                    if (Array.isArray(arr)) this.collapsedSet = new Set(arr);
                }
            } catch (e) {}
        },
        saveCollapsed() {
            try {
                sessionStorage.setItem(this.sessionCollapsedKey, JSON.stringify([...this.collapsedSet]));
            } catch (e) {}
        },
        loadSavedPositions() {
            try {
                const raw = sessionStorage.getItem(this.sessionPosKey);
                if (!raw) return null;
                return JSON.parse(raw);
            } catch (e) { return null; }
        },
        savePositions() {
            try {
                const map = {};
                this.tableNodes.forEach(n => { map[n.name] = { x: n.x, y: n.y }; });
                sessionStorage.setItem(this.sessionPosKey, JSON.stringify(map));
            } catch (e) {}
        },
        clearSavedPositions() {
            try { sessionStorage.removeItem(this.sessionPosKey); } catch (e) {}
        },

        // ── Helpers ──
        isCollapsed(name) {
            return this.collapsedSet.has(name);
        },
        getNodeFullHeight(colsLen) {
            return this.cardHeaderHeight + this.cardBodyPadding + colsLen * this.cardRowHeight;
        },
        getNodeRenderHeight(node) {
            if (this.isCollapsed(node.name)) return this.cardHeaderHeight;
            return this.getNodeFullHeight(node.columns.length);
        },
        formatType(col) {
            if (col.columnType) {
                return col.columnType.toLowerCase();
            }
            let t = (col.dataType || '').toLowerCase();
            if (col.maxLength) t += '(' + col.maxLength + ')';
            return t;
        },

        // ── Layout ──
        layout() {
            const tables = Object.keys(this.tableMetadataMap);
            if (tables.length === 0) {
                this.tableNodes = [];
                this.canvasWidth = 800;
                this.canvasHeight = 600;
                return;
            }
            const order = (this.generationOrder && this.generationOrder.length > 0) ? this.generationOrder : tables;
            const layers = this.buildLayers(order, tables);

            const savedPos = this.loadSavedPositions();
            const nodes = [];
            let maxX = 0, maxY = 0;

            // 估算视口高度（首次 mount 还没测量时给个默认值）
            this.measureViewport();
            const availH = Math.max(520, (this.viewportHeight || 700) - 80);

            // 子列内部紧凑间距，层与层之间用更宽的 colGap
            const subColGap = 60;
            const startLeft = 60;
            const startTop = 60;
            let curX = startLeft;

            layers.forEach((layer) => {
                // 1. 每张表先算高度
                const items = layer.map(name => {
                    const meta = this.tableMetadataMap[name];
                    const cols = (meta && meta.columns) ? meta.columns : [];
                    return {
                        name,
                        columns: cols,
                        h: this.getNodeFullHeight(cols.length),
                    };
                });

                // 2. 该层总高度（按行间距累加），决定需要拆分多少列（最多 3 列）
                const totalH = items.reduce((s, it) => s + it.h, 0) + Math.max(0, items.length - 1) * this.rowGap;
                const MAX_SUB_COLS = 3;
                const subCols = Math.min(Math.max(1, Math.ceil(totalH / availH)), MAX_SUB_COLS);

                // 3. 贪心装箱：按原顺序遍历，依次放入当前最矮的子列
                const buckets = [];
                for (let i = 0; i < subCols; i++) buckets.push({ items: [], h: 0 });
                items.forEach(it => {
                    let target = buckets[0];
                    for (let i = 1; i < buckets.length; i++) {
                        if (buckets[i].h < target.h) target = buckets[i];
                    }
                    if (target.items.length > 0) target.h += this.rowGap;
                    target.items.push(it);
                    target.h += it.h;
                });

                // 4. 把每个子列写入 nodes
                buckets.forEach((bucket, subIdx) => {
                    let curY = startTop;
                    const subX = curX + subIdx * (this.cardWidth + subColGap);
                    bucket.items.forEach(it => {
                        let nx = subX, ny = curY;
                        if (savedPos && savedPos[it.name]) {
                            nx = savedPos[it.name].x;
                            ny = savedPos[it.name].y;
                        }
                        nodes.push({
                            name: it.name,
                            columns: it.columns,
                            x: nx, y: ny,
                            fullHeight: it.h,
                        });
                        maxX = Math.max(maxX, nx + this.cardWidth);
                        maxY = Math.max(maxY, ny + it.h);
                        curY += it.h + this.rowGap;
                    });
                });

                // 5. 推进到下一层
                curX += subCols * this.cardWidth + (subCols - 1) * subColGap + this.colGap;
            });

            this.tableNodes = nodes;
            this.canvasWidth = Math.max(maxX + 60, 800);
            this.canvasHeight = Math.max(maxY + 60, 600);
        },
        buildLayers(order, allTables) {
            // 无关联：把所有表均分到若干层
            if (!this.relations || this.relations.length === 0) {
                const n = allTables.length;
                if (n <= 1) return [allTables];
                const perLayer = 4; // 每层最多 4 个表，避免过长或过宽
                const layers = [];
                for (let i = 0; i < n; i += perLayer) layers.push(allTables.slice(i, i + perLayer));
                return layers;
            }

            // 有关联：按 fromTable -> toTable 的依赖关系，被依赖的放在更左边
            const dependsOn = {};
            this.relations.forEach(r => {
                if (!dependsOn[r.fromTable]) dependsOn[r.fromTable] = new Set();
                dependsOn[r.fromTable].add(r.toTable);
            });

            const layerIdxMap = {};
            const placed = new Set();
            const rawLayers = [];
            const computeLayer = (t, visiting) => {
                if (layerIdxMap[t] !== undefined) return layerIdxMap[t];
                if (visiting.has(t)) return 0;
                visiting.add(t);
                let maxDep = -1;
                if (dependsOn[t]) {
                    dependsOn[t].forEach(dep => {
                        if (allTables.indexOf(dep) >= 0) {
                            maxDep = Math.max(maxDep, computeLayer(dep, visiting));
                        }
                    });
                }
                visiting.delete(t);
                layerIdxMap[t] = maxDep + 1;
                return layerIdxMap[t];
            };
            order.forEach(t => computeLayer(t, new Set()));
            allTables.forEach(t => { if (layerIdxMap[t] === undefined) computeLayer(t, new Set()); });
            Object.keys(layerIdxMap).forEach(t => {
                const li = layerIdxMap[t];
                while (rawLayers.length <= li) rawLayers.push([]);
                if (!placed.has(t)) { rawLayers[li].push(t); placed.add(t); }
            });
            const nonEmpty = rawLayers.filter(l => l.length > 0);

            // 后处理：层数太多时合并相邻层；单层太宽时拆分子层
            const MAX_LAYERS = 5;
            const MAX_PER_LAYER = 4;
            if (nonEmpty.length > MAX_LAYERS) {
                // 合并相邻层，贪心保持每层不超过 MAX_PER_LAYER 个表
                const merged = [];
                let buf = [];
                for (const layer of nonEmpty) {
                    if (buf.length + layer.length <= MAX_PER_LAYER) {
                        buf.push(...layer);
                    } else {
                        if (buf.length > 0) merged.push(buf);
                        buf = [...layer];
                    }
                }
                if (buf.length > 0) merged.push(buf);
                // 如果合并后仍然太多层，进一步合并
                if (merged.length > MAX_LAYERS) {
                    const result = [];
                    let acc = [];
                    for (const layer of merged) {
                        acc.push(...layer);
                        if (acc.length >= MAX_PER_LAYER) {
                            result.push(acc);
                            acc = [];
                        }
                    }
                    if (acc.length > 0) result.push(acc);
                    return result;
                }
                return merged;
            }

            // 拆分过大的单层
            const result = [];
            for (const layer of nonEmpty) {
                if (layer.length <= MAX_PER_LAYER) {
                    result.push(layer);
                } else {
                    for (let i = 0; i < layer.length; i += MAX_PER_LAYER) {
                        result.push(layer.slice(i, i + MAX_PER_LAYER));
                    }
                }
            }
            return result;
        },

        // ── Connections ──
        calcConnections() {
            if (!this.relations || this.relations.length === 0) {
                this.connLines = [];
                return;
            }
            const lines = [];
            const headerH = this.cardHeaderHeight;
            const rowH = this.cardRowHeight;
            const padTop = this.cardBodyPadding;

            this.relations.forEach((rel) => {
                const fromNode = this.tableNodes.find(n => n.name === rel.fromTable);
                const toNode = this.tableNodes.find(n => n.name === rel.toTable);
                if (!fromNode || !toNode) return;

                const fromCollapsed = this.isCollapsed(fromNode.name);
                const toCollapsed = this.isCollapsed(toNode.name);

                const fromColIdx = fromNode.columns.findIndex(c => c.columnName === rel.fromColumn);
                const toColIdx = toNode.columns.findIndex(c => c.columnName === rel.toColumn);

                const fromY = fromCollapsed
                    ? fromNode.y + headerH / 2
                    : fromNode.y + headerH + padTop + (fromColIdx >= 0 ? fromColIdx : 0) * rowH + rowH / 2;
                const toY = toCollapsed
                    ? toNode.y + headerH / 2
                    : toNode.y + headerH + padTop + (toColIdx >= 0 ? toColIdx : 0) * rowH + rowH / 2;

                // 起点取 fromNode 的左边或右边，看 toNode 在哪边
                const fromCenterX = fromNode.x + this.cardWidth / 2;
                const toCenterX = toNode.x + this.cardWidth / 2;
                const fromOnRight = toCenterX >= fromCenterX;
                const fromX = fromOnRight ? fromNode.x : fromNode.x + this.cardWidth;
                const toX = fromOnRight ? toNode.x + this.cardWidth : toNode.x;
                // 修正：from 的端口应朝向 to → 简化为：from 取靠近 to 的一侧，to 取靠近 from 的一侧
                const fromXFixed = fromOnRight ? fromNode.x + this.cardWidth : fromNode.x;
                const toXFixed = fromOnRight ? toNode.x : toNode.x + this.cardWidth;

                const dx = toXFixed - fromXFixed;
                const ctrl = Math.max(60, Math.abs(dx) * 0.45);
                const cp1X = fromXFixed + (fromOnRight ? ctrl : -ctrl);
                const cp1Y = fromY;
                const cp2X = toXFixed + (fromOnRight ? -ctrl : ctrl);
                const cp2Y = toY;

                const path = `M ${fromXFixed} ${fromY} C ${cp1X} ${cp1Y}, ${cp2X} ${cp2Y}, ${toXFixed} ${toY}`;
                lines.push({ path, highlighted: false });
            });

            this.connLines = lines;
        },

        // ── Viewport / Transform ──
        measureViewport() {
            const vp = this.$refs.viewport;
            if (!vp) return;
            this.viewportWidth = vp.clientWidth;
            this.viewportHeight = vp.clientHeight;
        },
        fitToView() {
            this.measureViewport();
            if (this.viewportWidth === 0 || this.viewportHeight === 0) return;
            const padding = 40;
            const sx = (this.viewportWidth - padding * 2) / this.canvasWidth;
            const sy = (this.viewportHeight - padding * 2) / this.canvasHeight;
            const s = Math.min(sx, sy, 1);
            this.scale = Math.max(0.3, s);
            this.tx = (this.viewportWidth - this.canvasWidth * this.scale) / 2;
            this.ty = (this.viewportHeight - this.canvasHeight * this.scale) / 2;
        },
        clampScale(s) {
            return Math.max(0.3, Math.min(2.5, s));
        },
        zoomIn() { this.zoomAt(1.2); },
        zoomOut() { this.zoomAt(1 / 1.2); },
        zoomAt(factor, anchorX, anchorY) {
            const newScale = this.clampScale(this.scale * factor);
            if (newScale === this.scale) return;
            // 默认以视口中心为锚点
            if (anchorX == null || anchorY == null) {
                anchorX = this.viewportWidth / 2;
                anchorY = this.viewportHeight / 2;
            }
            // 锚点在 canvas 坐标系中的位置应保持不变
            const canvasX = (anchorX - this.tx) / this.scale;
            const canvasY = (anchorY - this.ty) / this.scale;
            this.scale = newScale;
            this.tx = anchorX - canvasX * this.scale;
            this.ty = anchorY - canvasY * this.scale;
        },
        onWheel(e) {
            const factor = e.deltaY < 0 ? 1.1 : 1 / 1.1;
            const rect = this.$refs.viewport.getBoundingClientRect();
            const ax = e.clientX - rect.left;
            const ay = e.clientY - rect.top;
            this.zoomAt(factor, ax, ay);
        },

        // ── Pan ──
        onViewportMouseDown(e) {
            // 排除：表头按下（卡片拖拽通过 onCardHeaderMouseDown 处理并 .stop）
            // 这里只处理空白区域平移
            if (e.button !== 0) return;
            this.panning = true;
            this.panStartX = e.clientX;
            this.panStartY = e.clientY;
            this.panOriginTx = this.tx;
            this.panOriginTy = this.ty;
        },
        onWindowMouseMove(e) {
            if (this.panning) {
                this.tx = this.panOriginTx + (e.clientX - this.panStartX);
                this.ty = this.panOriginTy + (e.clientY - this.panStartY);
                return;
            }
            if (this.draggingNode) {
                const node = this.tableNodes.find(n => n.name === this.draggingNode);
                if (!node) return;
                const rect = this.$refs.viewport.getBoundingClientRect();
                const cx = (e.clientX - rect.left - this.tx) / this.scale;
                const cy = (e.clientY - rect.top - this.ty) / this.scale;
                node.x = cx - this.dragOffsetX;
                node.y = cy - this.dragOffsetY;
                this.calcConnections();
                return;
            }
            if (this.mmDragging) {
                this.applyMinimapPosition(e);
                return;
            }
        },
        onWindowMouseUp() {
            if (this.draggingNode) {
                this.savePositions();
            }
            this.panning = false;
            this.draggingNode = null;
            this.mmDragging = false;
        },

        // ── Card drag ──
        onCardHeaderMouseDown(e, node) {
            if (e.button !== 0) return;
            const rect = this.$refs.viewport.getBoundingClientRect();
            const cx = (e.clientX - rect.left - this.tx) / this.scale;
            const cy = (e.clientY - rect.top - this.ty) / this.scale;
            this.draggingNode = node.name;
            this.dragOffsetX = cx - node.x;
            this.dragOffsetY = cy - node.y;
        },

        // ── Collapse ──
        toggleCollapse(name) {
            if (this.collapsedSet.has(name)) this.collapsedSet.delete(name);
            else this.collapsedSet.add(name);
            this.collapsedSet = new Set(this.collapsedSet); // trigger reactivity
            this.saveCollapsed();
            this.$nextTick(this.calcConnections);
        },
        collapseAll() {
            this.tableNodes.forEach(n => this.collapsedSet.add(n.name));
            this.collapsedSet = new Set(this.collapsedSet);
            this.saveCollapsed();
            this.$nextTick(this.calcConnections);
        },
        expandAll() {
            this.collapsedSet.clear();
            this.collapsedSet = new Set();
            this.saveCollapsed();
            this.$nextTick(this.calcConnections);
        },

        // ── Reset / Fullscreen ──
        resetLayout() {
            this.clearSavedPositions();
            this.layout();
            this.$nextTick(() => {
                this.fitToView();
                this.calcConnections();
            });
        },
        toggleFullscreen() {
            this.fullscreen = !this.fullscreen;
            this.$nextTick(() => {
                this.measureViewport();
                this.fitToView();
            });
        },
        onKeyDown(e) {
            if (e.key === 'Escape' && this.fullscreen) {
                this.fullscreen = false;
                this.$nextTick(() => {
                    this.measureViewport();
                    this.fitToView();
                });
            }
        },
        onResize() {
            this.measureViewport();
        },

        // ── Minimap ──
        onMinimapMouseDown(e) {
            this.mmDragging = true;
            this.applyMinimapPosition(e);
        },
        applyMinimapPosition(e) {
            const mm = this.$refs.minimap;
            if (!mm) return;
            const rect = mm.getBoundingClientRect();
            const px = (e.clientX - rect.left) / rect.width;   // 0..1
            const py = (e.clientY - rect.top) / rect.height;   // 0..1
            // 计算 minimap 内部 viewBox 实际渲染区域（preserveAspectRatio=meet）
            const vbAspect = this.canvasWidth / this.canvasHeight;
            const mmAspect = rect.width / rect.height;
            let usedW, usedH, offX, offY;
            if (vbAspect > mmAspect) {
                usedW = rect.width;
                usedH = rect.width / vbAspect;
                offX = 0;
                offY = (rect.height - usedH) / 2;
            } else {
                usedH = rect.height;
                usedW = rect.height * vbAspect;
                offX = (rect.width - usedW) / 2;
                offY = 0;
            }
            const localX = e.clientX - rect.left - offX;
            const localY = e.clientY - rect.top - offY;
            const ratioX = localX / usedW;
            const ratioY = localY / usedH;
            // 对应 canvas 坐标
            const canvasCenterX = ratioX * this.canvasWidth;
            const canvasCenterY = ratioY * this.canvasHeight;
            // 让该点位于视口中心
            this.tx = this.viewportWidth / 2 - canvasCenterX * this.scale;
            this.ty = this.viewportHeight / 2 - canvasCenterY * this.scale;
        },
    },
};
