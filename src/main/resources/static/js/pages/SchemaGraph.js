/**
 * SchemaGraph - ER Diagram Component
 * 以可视化的方式展示表结构和外键关联关系
 */
const SchemaGraph = {
    props: {
        tableMetadataMap: { type: Object, required: true },
        relations: { type: Array, default: () => [] },
        generationOrder: { type: Array, default: () => [] },
    },
    template: `
    <div class="er-graph-container" ref="container">
        <svg class="er-graph-svg" ref="svg">
            <path v-for="(line, i) in connLines" :key="'line-' + i"
                :d="line.path"
                class="er-conn-line"
                :class="{ highlighted: line.highlighted }"
            />
        </svg>
        <div v-for="(table, idx) in tableNodes" :key="table.name"
             class="er-table-card"
             :class="'er-table-color-' + (idx % 4)"
             :style="{ left: table.x + 'px', top: table.y + 'px' }"
             :ref="'table-' + table.name">
            <div class="er-table-header" :class="'er-header-' + (idx % 4)">
                <span class="er-table-name">{{ table.name }}</span>
                <span class="er-table-badge">{{ table.columns.length }}</span>
            </div>
            <div class="er-table-body">
                <div v-for="col in table.columns" :key="col.columnName"
                     class="er-col-row"
                     :class="{ 'er-col-pk': col.primaryKey, 'er-col-fk': !!col.referencedTable }"
                     :ref="'col-' + table.name + '-' + col.columnName">
                    <span class="er-col-icon">
                        <template v-if="col.primaryKey">&#128273;</template>
                        <template v-else-if="col.referencedTable">&#128279;</template>
                        <template v-else>&#9702;</template>
                    </span>
                    <span class="er-col-name">{{ col.columnName }}</span>
                    <span class="er-col-type">{{ formatType(col) }}</span>
                </div>
            </div>
        </div>
    </div>
    `,
    data() {
        return {
            connLines: [],
            tableNodes: [],
            containerWidth: 0,
        };
    },
    mounted() {
        this.layoutTables();
        this.$nextTick(() => {
            setTimeout(() => this.calcConnections(), 80);
        });
        window.addEventListener('resize', this.handleResize);
    },
    beforeUnmount() {
        window.removeEventListener('resize', this.handleResize);
    },
    methods: {
        handleResize() {
            this.layoutTables();
            this.$nextTick(() => {
                setTimeout(() => this.calcConnections(), 50);
            });
        },
        layoutTables() {
            const tables = Object.keys(this.tableMetadataMap);
            const order = this.generationOrder.length > 0 ? this.generationOrder : tables;
            const containerEl = this.$refs.container;
            this.containerWidth = containerEl ? containerEl.offsetWidth : 900;

            const cardWidth = 220;
            const cardBaseHeight = 60;
            const rowHeight = 26;
            const gapX = 80;
            const gapY = 40;

            // 分层布局：按依赖关系分层
            const layers = this.buildLayers(order, tables);
            const nodes = [];

            let globalY = 50;
            layers.forEach((layer, layerIdx) => {
                const totalWidth = layer.length * cardWidth + (layer.length - 1) * gapX;
                let startX = Math.max(30, (this.containerWidth - totalWidth) / 2);
                let maxHeight = 0;

                layer.forEach((tableName, colIdx) => {
                    const meta = this.tableMetadataMap[tableName];
                    const cols = meta && meta.columns ? meta.columns : [];
                    const cardH = cardBaseHeight + cols.length * rowHeight;
                    maxHeight = Math.max(maxHeight, cardH);

                    nodes.push({
                        name: tableName,
                        columns: cols,
                        x: startX + colIdx * (cardWidth + gapX),
                        y: globalY,
                        width: cardWidth,
                        height: cardH,
                    });
                });

                globalY += maxHeight + gapY;
            });

            this.tableNodes = nodes;

            // 设置容器最小高度
            if (containerEl) {
                containerEl.style.minHeight = (globalY + 50) + 'px';
            }
        },
        buildLayers(order, allTables) {
            // 基于关联关系构建层次
            if (this.relations.length === 0) {
                // 无关联，按每行 3 个排列
                const layers = [];
                for (let i = 0; i < allTables.length; i += 3) {
                    layers.push(allTables.slice(i, i + 3));
                }
                return layers;
            }

            // 有关联关系时，利用 generationOrder 做层级
            const placed = new Set();
            const layers = [];
            const dependsOn = {};

            // 构建依赖图
            this.relations.forEach(r => {
                if (!dependsOn[r.fromTable]) dependsOn[r.fromTable] = new Set();
                dependsOn[r.fromTable].add(r.toTable);
            });

            // 按 generationOrder 分层
            order.forEach(t => {
                if (placed.has(t)) return;
                // 找到这个表应该在的层
                let layerIdx = 0;
                if (dependsOn[t]) {
                    dependsOn[t].forEach(dep => {
                        for (let i = 0; i < layers.length; i++) {
                            if (layers[i].includes(dep)) {
                                layerIdx = Math.max(layerIdx, i + 1);
                            }
                        }
                    });
                }
                while (layers.length <= layerIdx) layers.push([]);
                layers[layerIdx].push(t);
                placed.add(t);
            });

            // 放入未排列的表
            allTables.forEach(t => {
                if (!placed.has(t)) {
                    if (layers.length === 0) layers.push([]);
                    layers[layers.length - 1].push(t);
                }
            });

            return layers;
        },
        calcConnections() {
            if (!this.relations || this.relations.length === 0) {
                this.connLines = [];
                return;
            }

            const container = this.$refs.container;
            if (!container) return;

            const lines = [];
            const headerH = 36;
            const rowH = 26;
            const basePadding = 40;

            this.relations.forEach((rel, relIdx) => {
                const fromNode = this.tableNodes.find(n => n.name === rel.fromTable);
                const toNode = this.tableNodes.find(n => n.name === rel.toTable);
                if (!fromNode || !toNode) return;

                // 起点：fromColumn 行的右边缘中心
                const fromColIdx = fromNode.columns.findIndex(c => c.columnName === rel.fromColumn);
                const fromX = fromNode.x + fromNode.width;
                const fromY = fromNode.y + headerH + (fromColIdx >= 0 ? fromColIdx : 0) * rowH + rowH / 2;

                // 终点：toColumn 行的右边缘中心
                const toColIdx = toNode.columns.findIndex(c => c.columnName === rel.toColumn);
                const toX = toNode.x + toNode.width;
                const toY = toNode.y + headerH + (toColIdx >= 0 ? toColIdx : 0) * rowH + rowH / 2;

                // 找到全图所有卡片的最大右边界
                const maxRight = Math.max(...this.tableNodes.map(n => n.x + n.width));

                // 曲线控制参数
                const dist = Math.abs(toY - fromY);
                const curveOut = dist * 0.4 + 40;
                const dirFrom = fromY > toY ? -1 : 1; // from在下方→向上出发, from在上方→向下出发
                const dirTo = fromY > toY ? 1 : -1;   // from在下方→从下方接近, from在上方→从上方接近

                // cp1: 从起点垂直方向出发（朝向终点方向），X 偏向右侧避开卡片
                const cp1X = Math.max(fromX, toX) + basePadding + relIdx * 16;
                const cp1Y = fromY + dirFrom * curveOut;

                // cp2: 从垂直方向接近终点，X 偏向右侧避开卡片
                const cp2X = Math.max(fromX, toX) + basePadding + relIdx * 16;
                const cp2Y = toY + dirTo * curveOut;

                // 三次贝塞尔：起点垂直出发，终点垂直进入，中间弓向右侧
                const path = `M ${fromX} ${fromY} C ${cp1X} ${cp1Y}, ${cp2X} ${cp2Y}, ${toX} ${toY}`;

                lines.push({ path, highlighted: false });
            });

            this.connLines = lines;
        },

        /**
         * 将折线路径点生成带圆角的 SVG path
         */
        smoothOrthogonal(points) {
            if (points.length < 2) return '';
            const radius = 8; // 圆角半径
            let d = `M ${points[0].x} ${points[0].y}`;

            for (let i = 1; i < points.length - 1; i++) {
                const prev = points[i - 1];
                const curr = points[i];
                const next = points[i + 1];

                // 计算到拐角的距离，限制圆角半径不超过线段长度的一半
                const d1 = Math.sqrt((curr.x - prev.x) ** 2 + (curr.y - prev.y) ** 2);
                const d2 = Math.sqrt((next.x - curr.x) ** 2 + (next.y - curr.y) ** 2);
                const r = Math.min(radius, d1 / 2, d2 / 2);

                // 拐角前的点
                const dx1 = curr.x - prev.x;
                const dy1 = curr.y - prev.y;
                const len1 = Math.sqrt(dx1 * dx1 + dy1 * dy1);
                const bx = curr.x - (dx1 / len1) * r;
                const by = curr.y - (dy1 / len1) * r;

                // 拐角后的点
                const dx2 = next.x - curr.x;
                const dy2 = next.y - curr.y;
                const len2 = Math.sqrt(dx2 * dx2 + dy2 * dy2);
                const ax = curr.x + (dx2 / len2) * r;
                const ay = curr.y + (dy2 / len2) * r;

                d += ` L ${bx} ${by}`;
                d += ` Q ${curr.x} ${curr.y} ${ax} ${ay}`;
            }

            // 最后一个点
            const last = points[points.length - 1];
            d += ` L ${last.x} ${last.y}`;

            return d;
        },
        formatType(col) {
            if (col.columnType) {
                return col.columnType.toLowerCase().replace(/\(\d+\)/, m => m);
            }
            let t = (col.dataType || '').toLowerCase();
            if (col.maxLength) t += '(' + col.maxLength + ')';
            return t;
        },
    },
};
