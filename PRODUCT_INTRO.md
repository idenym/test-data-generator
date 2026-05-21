# 智能测试数据生成平台 —— 产品介绍文档

---

## 一、我们做了什么

**基于 AI 大模型的智能测试数据生成平台**：通过解析业务 SQL，自动理解数据库表结构与表间外键依赖关系，将 AI 大模型的语义生成能力与规则引擎的精确约束能力深度融合，一键生成既符合业务语义又满足所有数据库约束的真实感测试数据，并以事务方式直接写入目标数据库，同时完整记录任务执行快照供追溯复现。

---

## 二、功能方案设计

### 2.1 整体工作流程

平台采用「五步向导」设计，用户从 SQL 粘贴到数据落库全程零编码：

```
┌─────────┐    ┌─────────┐    ┌─────────┐    ┌─────────┐    ┌─────────┐
│ ① 配置   │───▶│ ② 输入  │───▶│ ③ 配置  │───▶│ ④ 预览  │───▶│ ⑤ 写入  │
│   连接   │    │   SQL   │    │   规则  │    │   数据  │    │ 数据库  │
└─────────┘    └─────────┘    └─────────┘    └─────────┘    └─────────┘
 多环境连接      解析表结构        字段策略        实时调整        事务写入
 加密存储        关系分析        AI/正则/枚举     列级重生成       历史记录
```

---

### 2.2 各步骤功能详解

#### Step 1 — 数据库连接管理

- 管理多套目标数据库连接（开发/测试/预发各套环境）
- 一键连通性测试，配置项 AES 加密存储
- 连接信息（host、port、username、database_name）统一管理，按需切换

#### Step 2 — SQL 输入与智能分析

**输入支持**：
- 手动粘贴业务 SQL（`INSERT INTO ... SELECT ...`、多表 JOIN `SELECT`）
- 文件上传（`.sql` / `.txt`），自动命名并保存到脚本库
- 脚本库：支持命名保存、重命名、删除，随时复用历史 SQL

**自动分析内容（SqlParserService）**：

| 分析项 | 说明 |
|------|------|
| 涉及表清单 | 从 FROM、JOIN、子查询中提取所有表名 |
| 字段元信息 | 字段名、数据类型、最大长度、是否主键/自增/可空、注释 |
| 表间关系 | JOIN 关联、数据库元数据中的 FK 约束，精确到字段级 |
| 拓扑排序 | 依 FK 依赖关系排出生成顺序，保证子表写入时父表已存在 |
| WHERE 约束 | 自动提取 `WHERE`/`JOIN ON` 条件中的等值、IN、范围约束 |
| 循环依赖检测 | 出现循环 FK 时记录警告，原样追加以防死锁 |

**WHERE 约束提取示例**：

输入 SQL：
```sql
WHERE business_status IN ('ACTIVE', 'INACTIVE')
  AND establish_date >= '2020-01-01'
  AND establish_date <= '2024-12-31'
  AND age > 18
```

自动推导出的规则建议：
- `business_status` → **枚举规则** `['ACTIVE', 'INACTIVE']`
- `establish_date` → **范围规则** `[2020-01-01, 2024-12-31]`（同列的 `>=` 与 `<=` 自动合并取交集）
- `age` → **范围规则** `min=18`

**可视化展示**：
- 关联关系图：以 `users.id → orders.user_id (FK)` 的连线方式展示
- 生成顺序：`users → orders → order_items`（拓扑箭头）
- 表结构详情：字段名、类型、PK/AI/NN 标记、注释、外键引用

#### Step 3 — 字段规则配置

**四种生成策略**：

| 策略 | 标识 | 配置项 | 典型用途 |
|------|------|------|---------|
| **AI 语义生成** | `LLM_DESCRIPTION` | 语义描述文本 | 姓名、地址、公司名、订单备注等语义丰富字段 |
| **正则表达式** | `REGEX` | 正则 pattern | 身份证号、手机号、统一社会信用代码、编号 |
| **范围值** | `RANGE` | min、max、type | 金额、年龄、日期区间、整数范围 |
| **枚举值** | `ENUM` | 候选值列表（可加权重） | 状态码、性别、省份、业务类型 |

**自动跳过字段**：自增列（`AUTO_INCREMENT`）和外键引用列（由引擎自动处理，不需用户配置）。

**规则来源标记系统**：每条规则都带有来源标识，帮助用户理解建议来自哪里：

| 标记 | 颜色 | 来源 |
|------|------|------|
| `WHERE` | 黄色 | SQL WHERE/ON 条件自动推导 |
| `HISTORY` | 蓝色 | 历史任务中相同字段的历史规则 |
| `KNOWLEDGE_BASE` | 紫色 | 知识库匹配建议 |
| `AI` | 紫色 | 大模型分析建议 |
| `COMMENT` | 灰色 | 字段注释自动识别 |
| `MANUAL` | 灰色 | 用户手动修改 |

**规则配置辅助功能**：
- **自动回填**：页面加载时自动调用 API，将历史规则 + 知识库建议批量回填，大幅减少手工配置
- **一键 AI 建议**：针对整张表，调用大模型分析表结构（字段名、类型、注释、FK）后批量输出每列建议，直接回填到配置表单
- **字段历史规则**：点击任意字段可查看历史使用记录（规则类型、配置、使用次数、最近使用时间），一键应用

#### Step 4 — 数据预览与精细调整

**预览数据表格**：

```
行号 | 字段1    | 字段2(FK🔗) | 字段3(AI✨) | 字段4(自增🔒) | ...
  1  | 张伟     | 10001       | 已发货      | 1             | ...
  2  | 李娜     | 10003       | 待付款      | 2             | ...
```

**表头功能**：
- 复选框选中列 → 批量重新生成
- 字段属性图标：🔒（自增，不可编辑）、🔗（外键，自动引用）
- 列菜单（⋯）：重新生成此列 / 修改规则后重新生成 / 恢复原始数据

**单元格编辑**：双击进入内联编辑，Enter/失焦保存，Esc 取消，编辑后高亮标记"已修改"。

**对比与恢复**：开启 Diff 视图后，单元格下方显示"原: 旧值"；支持按列或整表一键恢复到初始预览状态。

**列级重新生成**（详见"功能亮点"章节）。

#### Step 5 — 写入数据库与任务记录

- 将预览数据（含所有手动编辑内容）批量写入目标数据库
- 全部表同一事务提交：任何一张表写入失败立即全部回滚，保证一致性
- 写入完成后自动创建任务历史记录，保存三类快照（规则 / 分析 / 数据）

**任务详情页 Tab 结构**：

| Tab | 显示条件 | 内容 |
|-----|---------|------|
| 库表结构 | 始终 | 交互式 ER 关系图（可拖拽） |
| SQL 语句 | 有输入 SQL | 只读 SQL 查看 |
| 造数规则 | 始终 | 按表分组的字段规则快照 |
| 写入数据 | 成功任务 | 每表最多 200 行数据快照，横向滚动 |
| 错误日志 | 失败任务 | 保留格式的错误信息 |

---

### 2.3 功能亮点与创新点

#### 亮点一：简单高效的用户操作

传统测试数据工具往往需要用户逐字段配置生成规则，多表场景下配置工作量随字段数线性增长。本平台从三个层面系统性降低了用户操作成本：

**层面一：SQL 约束自动推导 — "SQL 即规则"**

用户只需粘贴一条业务 SQL，平台即自动从 `WHERE` 和 `JOIN ON` 条件中提取语义约束，反向转化为字段生成规则，无需手工配置：

```sql
-- 用户输入的 SQL
WHERE business_status IN ('ACTIVE', 'INACTIVE')
  AND establish_date >= '2020-01-01'
  AND establish_date <= '2024-12-31'
  AND age > 18
```

平台自动推导：
- `business_status` → 枚举规则 `['ACTIVE', 'INACTIVE']`（IN 列表提取）
- `establish_date` → 范围规则 `[2020-01-01, 2024-12-31]`（同列 `>=` 与 `<=` 自动合并取交集）
- `age` → 范围规则 `min=18`（比较运算符提取）

支持的表达式类型：等值 `=` → 枚举、`IN (...)` → 枚举、`BETWEEN` → 范围、`>/>=/</<=` → 范围。同一字段多个范围条件自动合并（取最大下界 + 最小上界）。

**层面二：规则自动回填 + AI 一键推荐**

进入规则配置页时，系统自动执行多层回填，用户大多数情况下只需确认即可：

```
回填优先级（由高到低）：
  ① 已保存规则   — 上次任务中用户手动确认过的规则
  ② WHERE 推导   — 从 SQL 约束自动推导（标记为"WHERE"来源，黄色标识）
  ③ 历史规则     — 其他任务中相同字段的规则（标记为"HISTORY"，蓝色标识）
  ④ 知识库匹配   — 企业知识库中的规则建议（标记为"KNOWLEDGE_BASE"，紫色标识）
  ⑤ 启发式识别   — 内置规则库自动匹配（如 email 列 → 邮箱正则）
```

若自动回填仍不满意，用户可一键触发"AI 建议规则"，由大模型分析整张表结构后批量输出每列建议，直接回填到配置表单。每种来源都有独立颜色标记（WHERE/HISTORY/AI/COMMENT/MANUAL），用户对规则的出处一目了然。

**层面三：预览即所得，一键写入**

配置完成后点击"预览"即可看到生成效果，不满意可列级重新生成或单元格双击编辑，确认后一键写入数据库。全表事务原子提交，任意表失败自动回滚，用户无需关心写入细节。

---

#### 亮点二：多元化造数规则支持

不同字段对数据的要求差异巨大：身份证号需要精确格式、订单备注需要语义丰富、金额需要数值范围、状态码只需枚举。单一策略无法覆盖所有场景，平台提供了"四种策略 + 五级匹配 + 列级重生成"的完整体系：

**四种核心策略**：

| 策略 | 精确度 | 典型用途 | 实现引擎 |
|------|--------|---------|---------|
| **AI 语义生成** | 语义级 | 姓名、地址、公司名、订单备注 | 大模型批量生成 |
| **正则表达式** | 格式级 | 身份证号、手机号、信用代码 | RgxGen 正则反向生成 |
| **范围值** | 数值级 | 金额、年龄、日期区间 | 区间均匀随机 |
| **枚举值** | 离散级 | 状态码、性别、业务类型 | 加权随机抽样 |

**五级规则匹配优先级**：系统为每个字段自动绑定最合适的生成器，匹配逻辑从高到低依次为：

```
① 外键列（最高优先） → ForeignKeyGenerator，自动从父表 PK 值池引用
② 用户规则           → 本次会议中用户明确指定的规则，精确匹配表名+列名
③ 存储规则           → 数据库中保存的规则，支持通配符模式（* 匹配任意表/列）
④ 启发式规则         → 内置规则库自动识别：
                       · 列名含 email/mail → 邮箱正则
                       · 列名含 phone/mobile → 手机号正则
                       · 列名 == status → 枚举 ["0","1"]
                       · 列名 == gender/sex → 枚举 ["M","F"]
                       · MySQL enum() 类型 → 自动提取枚举值
                       · 列名含 create_time/update_time → 日期范围
⑤ 默认生成器（兜底） → 基于数据类型（DataFaker）生成基础值
```

这意味着即使一个字段没有配置任何规则，系统也能通过 FK 检测、通配符匹配、启发式识别自动给出合理的生成策略。用户只需关注需要自定义的字段，其余交给系统。

**列级无损重新生成**：预览后发现某列数据不满意时，无需重新生成整张表。用户选中目标列，修改规则后仅该列重新调用生成器，其他列数据完全保持不变。外键列自动从已有父表数据中提取引用值，保证引用完整性。这种"局部刷新"能力让调整效率从 O(全表) 降至 O(单列)。

---

#### 亮点三：高效可控利用大模型

> 核心理念：**不是将库表结构和造数需求一股脑丢给大模型，而是通过 SQL 解析等预处理手段，将大模型调用收敛到"按字段维度"的精确调用，同时利用大模型的语义理解能力辅助规则推荐。**

**问题背景**：一种直觉的 AI 造数方式是将"表结构 + 所有约束 + 生成行数"整体抛给大模型，让模型一次输出整张表的数据。这种方式存在明显问题：

- **成本高**：整表输出的 Token 消耗远大于单列输出，10 列 × 100 行 = 1000 个值一次性生成
- **质量低**：模型需同时满足所有列的约束，出错概率随列数指数增长（如某列长度超限、类型不符）
- **不可控**：某一列出错需要整表重新生成，无法局部修正
- **不可组合**：正则、范围、枚举等确定性策略无法与 AI 输出混合编排

平台采用了"**预处理 + 按字段精确调用 + 规则引擎兜底**"的分层架构：

```
┌─────────────────────────────────────────────────────────┐
│  第一层：SQL 解析预处理（SqlParserService）              │
│  ─────────────────────────────────────────────────      │
│  · 解析表结构、字段类型、长度约束                        │
│  · 提取 WHERE/ON 约束 → 自动推导为枚举/范围规则         │
│  · 发现外键关系 → 自动绑定 ForeignKeyGenerator          │
│  · 拓扑排序 → 确定多表生成顺序                          │
│                                                         │
│  产出：每个字段的"已知的确定性约束"已由规则引擎处理，    │
│  只剩"需要语义理解"的字段才走大模型。                    │
├─────────────────────────────────────────────────────────┤
│  第二层：规则引擎匹配（RuleMatchingEngine）              │
│  ─────────────────────────────────────────────────      │
│  · 5 级优先级自动匹配：FK → 用户规则 → 存储规则 →       │
│    启发式 → 默认生成器                                   │
│  · 正则/范围/枚举/序列等确定性策略由对应 Generator 处理   │
│  · 仅 LLM_DESCRIPTION 类型的字段才进入大模型调用流程     │
│                                                         │
│  产出：一张表中通常只有部分字段需要调用大模型，           │
│  其余字段由确定性生成器覆盖。                            │
├─────────────────────────────────────────────────────────┤
│  第三层：按字段维度精确调用大模型（LlmService）          │
│  ─────────────────────────────────────────────────      │
│  · 每次调用只针对单个字段，Prompt 精确包含：             │
│    表名、列名、数据类型、长度约束、可空性、语义描述       │
│  · 单次请求返回 50 条值（可配置），存入线程安全值池      │
│  · 多个 LLM 字段并发调用，互不阻塞                      │
│  · 多模型随机分配：不同字段可随机使用不同模型             │
│                                                         │
│  优势：Prompt 聚焦单字段，约束明确、输出格式简单         │
│  （JSON 数组），模型理解和遵守约束的成功率极高。          │
├─────────────────────────────────────────────────────────┤
│  第四层：容错降级（全链路兜底）                          │
│  ─────────────────────────────────────────────────      │
│  · API 调用失败 → 重试 3 次（指数退避）                  │
│  · 全部重试失败 → DefaultGenerator 按数据类型兜底生成    │
│  · 并发异常 → 回退串行模式                              │
│  · Markdown 响应 → 自动清理代码块标记                    │
│                                                         │
│  保证：任意场景下都能输出完整行数据，                    │
│  不会因 LLM 异常中断整个生成流程。                       │
└─────────────────────────────────────────────────────────┘
```

**具体对比**：

| 维度 | 整表丢给 AI | 平台的"预处理+字段级调用" |
|------|------------|--------------------------|
| 单次 Prompt 信息量 | 全表 10+ 列结构 + 所有约束 | 单字段定义 + 精确约束 |
| 输出格式 | 整表 N 行 × M 列 JSON | 单列 N 个值的 JSON 数组 |
| Token 消耗 | 高（输入+输出均大规模） | 低（按字段拆分，每次仅单列） |
| 约束遵守率 | 随列数增长而下降 | 单列约束简单，遵守率极高 |
| 出错影响面 | 整表需重新生成 | 仅影响单列，可列级重生成 |
| 确定性策略 | 无法与 AI 混合编排 | 正则/范围/枚举/AI 自由组合 |
| FK 处理 | 模型难以保证引用完整性 | 引擎自动追踪 PK，FK 精确引用 |
| 容错能力 | 模型失败则整表无数据 | LLM 失败回退默认生成器 |

**AI 规则推荐 — 另一种高效利用方式**：

除了数据生成，平台还利用大模型做"规则推荐"：将整张表的字段元信息（名称、类型、注释、PK/FK 标记）打包为结构化 Prompt，让模型分析每个字段最合适的生成策略，返回结构化 JSON 直接回填到配置表单。这种用法是"分析型"而非"生成型"——让 AI 做它擅长的事（理解语义、推荐策略），而不是让它做确定性程序更擅长的事（格式校验、范围约束、FK 引用）。

```
AI 规则推荐返回示例：
[
  {"columnName": "unified_code", "ruleType": "REGEX",
   "ruleConfig": {"pattern": "[0-9A-Z]{18}"}, "description": "18位统一社会信用代码"},
  {"columnName": "company_name", "ruleType": "LLM_DESCRIPTION",
   "ruleConfig": {"description": "中国境内真实企业名称，含有限公司、股份公司等后缀"},
   "description": "企业名称"},
  {"columnName": "business_status", "ruleType": "ENUM",
   "ruleConfig": {"values": ["正常", "注销", "吊销", "迁出"]}, "description": "企业经营状态"}
]
```

可以看到，AI 推荐的规则混合了 REGEX、LLM_DESCRIPTION、ENUM 三种类型——这恰好体现了"让 AI 推荐策略，让引擎执行策略"的分工理念。

---

## 三、技术架构设计

### 3.1 系统整体架构

```
┌────────────────────────────────────────────────────────────────────────┐
│                           Browser (Vue 3 SPA)                          │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────────┐ │
│  │Connection│ │SqlInput  │ │RuleConfig│ │Generate  │ │TaskDetail    │ │
│  │Page      │ │Page      │ │Page      │ │Page      │ │Page          │ │
│  └──────────┘ └──────────┘ └──────────┘ └──────────┘ └──────────────┘ │
│                         api.js (REST 封装)                              │
└────────────────────────────┬───────────────────────────────────────────┘
                             │ HTTP/JSON REST
┌────────────────────────────▼───────────────────────────────────────────┐
│                     Spring Boot Application                            │
│                                                                        │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │                      Controller 层 (7个)                         │  │
│  │  DataGen | History | SqlAnalysis | Metadata | Connection | Rule  │  │
│  └────────────────────────────┬─────────────────────────────────────┘  │
│                               │                                        │
│  ┌────────────────────────────▼─────────────────────────────────────┐  │
│  │                       Service 层                                 │  │
│  │  ┌──────────────────┐  ┌────────────┐  ┌─────────────────────┐  │  │
│  │  │DataGeneratorSvc  │  │ LlmService │  │  SqlParserService   │  │  │
│  │  │(流程编排)         │  │(大模型调用) │  │  (SQL解析+分析)     │  │  │
│  │  └────────┬─────────┘  └─────┬──────┘  └──────────┬──────────┘  │  │
│  └───────────┼─────────────────-┼──────────────────-─┼─────────────┘  │
│              │                  │                     │               │
│  ┌───────────▼──────────────────▼────────┐           │               │
│  │           Engine 层                   │           │               │
│  │  ┌──────────────────────────────────┐ │           │               │
│  │  │    DataGenerationPipeline        │ │           │               │
│  │  │  ┌────────────┐ ┌─────────────┐ │ │           │               │
│  │  │  │RuleMatching│ │FieldGenerator│ │ │           │               │
│  │  │  │Engine      │ │体系(7种)     │ │ │           │               │
│  │  │  └────────────┘ └─────────────┘ │ │           │               │
│  │  └──────────────────────────────────┘ │           │               │
│  │  DependencyResolver (Kahn拓扑排序)     │           │               │
│  └────────────────────┬──────────────────┘           │               │
│                       │                              │               │
│  ┌────────────────────▼──────────────────────────────▼─────────────┐  │
│  │                  Repository 层 (JPA / H2)                        │  │
│  │  GenerationTask | FieldRule | RuleSet | ConnectionConfig | ...   │  │
│  └──────────────────────────────────────────────────────────────────┘  │
└──────────────┬──────────────────────────────────────────┬──────────────┘
               │ JDBC                                     │ HTTP/REST
               ▼                                          ▼
       ┌──────────────┐                       ┌─────────────────────────┐
       │  目标数据库   │                       │     AI 大模型服务        │
       │  (MySQL等)   │                       │  DeepSeek V4 Pro         │
       └──────────────┘                       │  Mimo V2.5 Pro           │
                                              │  (OpenAI 兼容接口)       │
                                              └─────────────────────────┘
```

---

### 3.2 核心组件详解

#### 3.2.1 数据生成引擎（DataGenerationPipeline）

引擎是整个平台的核心枢纽，串联 SQL 分析结果、规则配置、LLM 服务和数据库写入。

**三大核心方法**：

**① preview() — 纯预览（不写库）**

```
preview(DataGenRequest, SqlAnalysisResult) → DataPreviewResponse

1. buildDependencyLevels()
   └─ 分析 FK 关系 → 返回分层列表 [[table_A, table_B], [table_C], ...]
      同层表之间无依赖关系，可安全并行

2. 按层级处理（层间串行，层内并行）：
   FOR each level in levels:
     IF level.size() == 1: generateTablePreviewData() 串行
     ELSE: CompletableFuture.allOf(...runAsync()) 并行，等所有完成

3. generateTablePreviewData(tableName, ...):
   ├─ ruleMatchingEngine.matchRules() → generators (5级优先级)
   ├─ setupForeignKeyGenerators()     → 注入父表已生成的 PK 值
   ├─ preFillLlmGenerators()          → 并发预填充所有 LLM 字段
   └─ generateRows(rowCount)          → 逐行调用 generator.generate(context)

4. 维护 generatedPkMap (ConcurrentHashMap)
   → 追踪生成的主键值，供后续层级子表 FK 引用

5. 按 generationOrder 排序结果
   → 返回 DataPreviewResponse {tableData, generationOrder}
```

**② execute() — 生成 + 事务写入**

```
execute(DataGenRequest, SqlAnalysisResult) → ExecutionResult

1. 同 preview 流程并行生成所有表数据

2. 按 generationOrder 排列写入顺序（严格顺序）

3. 事务写入：
   conn.setAutoCommit(false)
   TRY:
     FOR each table in allTableData (按 generationOrder 顺序):
       dataWriterService.writeRowsOnConnection(conn, tableName, rows)
       trackGeneratedPks() → 追踪自增列生成的实际 key
     conn.commit()
   CATCH Exception:
     conn.rollback()   ← 任何表失败，全部回滚

4. 收集数据快照（每表最多 200 行）
   → 返回 ExecutionResult {rowCounts, snapshotData}
```

**③ regenerateColumns() — 列级无损重生成**

```
regenerateColumns(tableName, columns[], rowCount, fieldRules, models,
                  analysisResult, existingData) → RegenerateColumnsResponse

1. 过滤自增列（加入 warnings，跳过）
2. ruleMatchingEngine.matchRules() → generators
3. setupForeignKeyGeneratorsFromExistingData()
   └─ 从 existingData 中提取父表 PK 值注入 ForeignKeyGenerator
4. preFillLlmGeneratorsForColumns() → 仅对目标列中的 LLM 列预填
5. FOR i = 0 to rowCount-1:
     context = {rowIndex: i, currentRow: existingData[tableName][i]}
     FOR each targetColumn:
       value = generators[col].generate(context)
       value = truncateIfNeeded(value, colMeta)
       columnData[col][i] = value
6. 返回 RegenerateColumnsResponse {columnData, warnings}
```

**LLM 并发预填充机制**：

```
preFillLlmGenerators(tableName, tableMeta, generators, totalRows, models)

收集所有 LlmBatchGenerator 类型的列
FOR each LLM列:
  CompletableFuture.runAsync(() -> {
    WHILE llmGen.needsMoreValues(needed):
      batchSize = min(llmBatchSize, needed)        // 默认批次 50 条
      description = 规则描述 || 字段注释 || 字段名
      values = llmService.generateBatchValues(...)
      IF values.isEmpty(): 用 DefaultGenerator 补充
      ELSE: llmGen.addValues(values)
  }, llmExecutor)   // 专用线程池：max(8, CPU×2)

CompletableFuture.allOf(...).join()   // 等待全部完成
CATCH: 回退到串行逐列预填
```

**FieldGenerator 体系（7种实现）**：

| Generator | 依赖 | 算法要点 |
|-----------|------|---------|
| `LlmBatchGenerator` | LlmService | 预填值池（`CopyOnWriteArrayList`）+ 循环取用（`AtomicInteger`），线程安全 |
| `RegexGenerator` | RgxGen 2.0 | 将正则表达式反向生成匹配字符串 |
| `RangeGenerator` | — | 按 type（int/long/double/date/datetime）在 [min, max] 内均匀随机 |
| `EnumGenerator` | — | 按权重数组进行加权随机抽样 |
| `ForeignKeyGenerator` | — | 从父表 PK 值池中 `ThreadLocalRandom` 随机引用 |
| `SequenceGenerator` | — | 从起始值按步长递增 |
| `DefaultGenerator` | DataFaker 1.9.0 | 基于数据类型兜底生成（varchar/int/date/decimal/bool 等） |

---

#### 3.2.2 规则匹配引擎（RuleMatchingEngine）

为每个字段绑定最合适的 `FieldGenerator`，采用 5 级优先级策略：

```
matchRules(tableName, columns, userRules, ruleSetId)

优先级 1（最高）：外键列
  IF col.referencedTable != null → ForeignKeyGenerator（FK 父键稍后由 Pipeline 注入）

优先级 2：用户规则（本次请求中传入的 fieldRules）
  精确匹配：tableName.equalsIgnoreCase() && columnName.equalsIgnoreCase()
  → GeneratorFactory.create(ruleType, ruleConfig, dataType, maxLength, nullable)

优先级 3：存储规则（数据库中保存的规则，支持通配符模式）
  glob 模式匹配：tablePattern && columnPattern && dataTypePattern
  * → .* ; ? → .  （转换为正则，CASE_INSENSITIVE）

优先级 4：启发式规则（内置规则库，按列名/数据类型识别）
  ├─ 列名含 "email"/"mail"    → REGEX: [a-z]{5,8}@(gmail|qq|163|outlook)\.com
  ├─ 列名含 "phone"/"mobile"  → REGEX: 1[3-9][0-9]{9}
  ├─ 列名 == "status"         → ENUM: ["0","1"] 权重 [0.3, 0.7]
  ├─ 列名 == "gender"/"sex"   → ENUM: ["M","F"] 权重 [0.5, 0.5]
  ├─ 数据类型 starts_with enum(...) → 提取枚举值列表
  └─ 列名含 "create_time"/"update_time" → RANGE: datetime [2024-01-01, 2025-12-31]

优先级 5（最低）：默认生成器
  → DefaultGenerator(dataType, maxLength, nullable)
```

---

#### 3.2.3 SQL 解析服务（SqlParserService）

基于 **JSQLParser 4.9** 对业务 SQL 进行深度语义解析：

**解析层次**：

```
输入 SQL
   ↓
CCJSqlParserUtil.parse(sql)
   ├─ Insert 语句: 提取 target table + 内嵌 SELECT
   ├─ Select 语句: PlainSelect / SetOperationList（UNION 等）/ 子查询
   └─ 解析失败: 正则回退提取表名

PlainSelect 解析流程:
   ├─ FROM 子句: extractFromItem() → 表名 + 别名映射
   ├─ JOIN 子句:
   │   ├─ getRightItem() → 右表 + 别名
   │   ├─ getOnExpressions() → 关联关系 + ON 条件提示
   │   └─ JOIN 类型: LEFT/RIGHT/INNER
   └─ WHERE 子句: extractJoinRelation() + extractWhereHints()
```

**WHERE 提示提取支持的表达式类型**：

```
AndExpression / OrExpression → 递归处理左右子树
EqualsTo {col = val}         → ENUM: [val]（过滤纯列间关联条件）
InExpression {col IN (...)}  → ENUM: 提取 ExpressionList（过滤子查询 IN）
Between {col BETWEEN a AND b}→ RANGE: {min: a, max: b}
GreaterThan/GreaterThanEquals → RANGE: min
MinorThan/MinorThanEquals     → RANGE: max
左右翻转处理: "5 > age" → effectiveOp 翻转为 "age < 5"
```

**同列 RANGE 条件合并**（mergeRangeHints）：
- `age >= 18 AND age >= 21` → 取最大下界 min = 21
- `age <= 60 AND age <= 50` → 取最小上界 max = 50

**拓扑排序**（DependencyResolver，Kahn 算法）：

```
构建有向图:
  parent (被 FK 引用的表) → children (有 FK 的表)
  inDegree[table] = 入度（被依赖数量）

Kahn 排序:
  queue = {inDegree == 0 的表}
  WHILE queue not empty:
    node = queue.pop() → result.add(node)
    FOR each child of node:
      inDegree[child]--
      IF inDegree[child] == 0: queue.add(child)

循环依赖检测:
  IF result.size() < tables.size():
    剩余表追加到 result + warnings 中记录
```

---

#### 3.2.4 可视化 ER 关系图（SchemaGraph）

- 基于 HTML5 Canvas + 纯 JS 实现，无外部图形库依赖
- **贪心层次分配算法**：MAX_LAYERS=5，MAX_PER_LAYER=4，避免所有表横向铺满
  - 长 FK 链（A→B→C→...→H）自动合并相邻层，保证不超出最大层数
  - 单层超过 4 个表时水平拆分为子列（MAX_SUB_COLS=3）
- **节点拖拽**：onCardHeaderMouseDown 鼠标事件驱动，鼠标悬停提示"拖拽可移动表位置"
- 外键连线动态渲染，颜色按层区分

---

### 3.3 AI 大模型交互设计

#### 3.3.1 两种 LLM 调用场景

**场景 A：批量字段值生成**（核心高频调用）

```
触发时机：用户在规则配置中为字段选择了 LLM_DESCRIPTION 策略，点击"预览"
调用时机：DataGenerationPipeline.preFillLlmGenerators()，预览开始前并发调用
每次调用：一次 API 请求返回 50 条值（llm-batch-size=50，可配置）
```

Prompt 工程细节：

```
System Prompt（每次调用都携带）:
  "你是一个严谨的数据库测试数据生成专家，必须严格遵守字段定义的所有约束
  （类型、长度、可空性、边界值等），绝不生成任何违反约束的值。"

User Prompt（动态构造，示例）:
  "请根据以下字段定义生成50条realistic且diverse的测试数据，
  仅输出JSON数组，不包含任何其他说明。

  Table: t_enterprise
  Column: company_name
  Data type: varchar(200)
  Max length: 200（若varchar类型则每个值字符数≤该值；若数值类型则忽略）
  Nullable: false
  Description: 中国境内真实企业名称，含有限公司、股份公司等后缀

  额外要求:
  - 若varchar(n)且n<36，严禁生成带连字符的UUID（应改用无连字符的32位十六进制、
    字母数字组合、雪花ID或符合长度的业务编码）
  - 若bigint/bigint unsigned，注意非负范围
  - 数据可以不唯一，但尽量多样化，且贴近description的语义，
    不要刻意使用边界值，尽量不要构造接近边界值的数据
  - 输出格式：[\"北京华夏科技有限公司\", \"上海腾讯信息技术股份有限公司\", ...]"
```

**场景 B：表级规则推荐**（低频辅助调用）

```
触发时机：用户在规则配置页点击"AI建议规则"按钮
调用时机：RuleConfigPage → API.suggestRules()
每次调用：一次 API 请求分析整张表的所有字段，返回每字段建议
```

Prompt 工程细节：

```
User Prompt（动态构造）:
  "Analyze this database table and suggest data generation rules for each column.

  Table: t_enterprise
  Comment: 企业信息表

  Columns:
  - id (bigint) [AUTO_INCREMENT] [PK]
  - unified_code (varchar(18)) -- 统一社会信用代码
  - company_name (varchar(200)) -- 企业名称
  - business_status (varchar(20)) -- 经营状态
  - establish_date (date) -- 成立日期
  - user_id (bigint) [FK->t_user]

  对于每个非自增字段，根据字段的数据类型和数据长度上限，给出最佳建议
  Return JSON array: [{columnName, ruleType, ruleConfig, description}]
  ruleType: REGEX (with pattern) | RANGE (with min/max/type) | ENUM (with values/weights)
           | LLM_DESCRIPTION (with description)

  请用中文回答，description字段请使用中文描述。"
```

#### 3.3.2 多模型支持与随机分配

**模型配置**（application.yml）：

```yaml
app:
  openai:
    # 全局默认配置
    model: deepseek-v4-pro
    max-tokens: 10000
    temperature: 0.8
    timeout-seconds: 60
    enable-thinking: true     # 开启 Extended Thinking

    # 各模型独立配置（可覆盖全局值）
    models:
      "[mimo-v2.5-pro]":
        base-url: https://token-plan-cn.xiaomimimo.com/
        api-key: ${MIMO_API_KEY}
        name: Mimo V2.5 Pro
      "[deepseek-v4-pro]":
        base-url: https://api.deepseek.com
        api-key: ${DEEPSEEK_API_KEY}
        name: DeepSeek V4 Pro
```

新增模型只需在 `models` 下添加条目，无需修改任何代码。

**模型随机分配**（LlmService.pickModel）：

```java
// 用户可为同一任务选择多个模型
// 不同字段随机使用不同模型，提升生成结果多样性
private String pickModel(List<String> models) {
    if (models == null || models.isEmpty()) return openAiProperties.getModel();
    if (models.size() == 1) return models.get(0);
    return models.get(ThreadLocalRandom.current().nextInt(models.size()));
}
```

#### 3.3.3 Extended Thinking（深度推理）支持

针对 DeepSeek 和 Mimo 两大模型系列的推理能力分别适配：

```java
// DeepSeek 系列
if (actualModel.startsWith("deepseek") && enableThinking) {
    body.put("thinking", new JSONObject().put("type", "enabled"));
}

// Mimo 系列
if (actualModel.startsWith("mimo")) {
    body.put("thinking", enableThinking);
}
```

开启后模型先进行内部推理链再给出答案，对复杂约束场景（如字段间联动约束、多条件组合）生成质量更高。

#### 3.3.4 容错与降级机制

| 异常场景 | 处理策略 |
|---------|---------|
| API 调用失败 | 自动重试，最多 3 次，指数退避（1s → 2s → 4s） |
| 全部重试失败 | 返回 `"[]"`，LlmBatchGenerator 值池为空 |
| 值池为空时生成 | 回退到 `DefaultGenerator` 按数据类型兜底生成 |
| 并发预填异常 | 回退到串行逐列预填模式 |
| 响应含 Markdown | 自动去除 ` ```json ` 和 ` ``` ` 代码块标记 |

**任意场景下都能输出完整行数据，不会因 LLM 异常中断整个生成流程。**

#### 3.3.5 LLM 调用完整链路图

```
前端 RuleConfigPage / GeneratePage
     │
     │  ① 用户操作触发（AI建议 / 预览按钮）
     ▼
DataGeneratorService.generatePreview()
     │
     ├─ SqlParserService.analyze() → SqlAnalysisResult
     │
     └─ DataGenerationPipeline.preview()
            │
            └─ preFillLlmGenerators()
                     │
                     │ CompletableFuture × N 个 LLM 字段（并发）
                     ▼
               LlmService.generateBatchValues()
                     │
                     ├─ buildGenerationPrompt()   ← 构造含约束的结构化 Prompt
                     ├─ pickModel(models)          ← 随机选择模型
                     │
                     ▼
               POST /v1/chat/completions  (OpenAI-Compatible API)
               ┌────────────────────────────────────────┐
               │ System: 严谨测试数据生成专家             │
               │ User:   字段定义 + 约束 + 格式要求       │
               │ model:  deepseek-v4-pro / mimo-v2.5-pro │
               │ thinking: enabled (推理增强)             │
               └────────────────────────────────────────┘
                     │
                     ▼
               parseValuesFromResponse()
               去除 Markdown → JSON.parseArray()
                     │
                     ▼
               LlmBatchGenerator.addValues(values)
               → valuePool (CopyOnWriteArrayList)
                     │
                     ▼
               generateRows() 时循环取用
               generator.generate(context)
               → 最终行数据
```

---

### 3.4 技术选型

| 层次 | 技术 / 库 | 版本 | 选型理由 |
|------|----------|------|---------|
| **后端框架** | Spring Boot | 2.7.18 | 生产成熟，零配置快速启动 |
| **数据持久化** | Spring Data JPA + H2 | — | 内嵌零配置数据库，DDL 自动维护（`ddl-auto: update`） |
| **SQL 解析** | JSQLParser | 4.9 | 工业级 Java SQL 语法解析器，支持复杂多层嵌套 SQL |
| **AI 调用** | RestTemplate (OkHttp) | — | 兼容 OpenAI Chat Completions 接口，无厂商锁定，按模型独立配置 |
| **基础数据生成** | DataFaker | 1.9.0 | 多语言本地化假数据兜底（姓名/地址/日期等） |
| **正则反向生成** | RgxGen | 2.0 | 将正则表达式反向生成符合模式的字符串 |
| **JSON 处理** | FastJSON | 1.2.83 | 高性能 JSON 序列化，支持泛型 TypeReference |
| **前端框架** | Vue 3 | — | Options API，无构建步骤，SPA 嵌入静态资源直接部署 |
| **并发** | CompletableFuture + 自定义线程池 | — | LLM 并发调用，专用线程池 max(8, CPU×2) |

---

### 3.5 数据库设计要点

**核心表：`generation_task`（任务记录 + 三类快照）**

```sql
CREATE TABLE generation_task (
    id                       BIGINT PRIMARY KEY AUTO_INCREMENT,
    connection_id            BIGINT,
    input_sql                TEXT,            -- 原始业务 SQL
    row_count                INT,             -- 目标行数
    status                   VARCHAR(20),     -- PENDING/RUNNING/SUCCESS/FAILED
    error_message            TEXT,            -- 失败原因
    rows_generated           INT DEFAULT 0,   -- 实际生成行数
    started_at               DATETIME,
    completed_at             DATETIME,
    rules_snapshot           TEXT,            -- 规则配置 JSON 快照
    analysis_snapshot        TEXT,            -- SQL 分析结果 JSON 快照
    generated_data_snapshot  MEDIUMTEXT       -- 生成数据快照，每表 ≤200 行
);                                            -- MEDIUMTEXT 最大支持 16MB
```

**`generated_data_snapshot` 格式（示例）**：

```json
{
  "t_user": [
    {"id": 1, "name": "张伟", "email": "zhangwei@qq.com", "status": "1"},
    {"id": 2, "name": "李娜", "email": "lina@163.com", "status": "0"}
  ],
  "t_order": [
    {"id": 1, "user_id": 1, "amount": 299.00, "status": "已发货"},
    {"id": 2, "user_id": 2, "amount": 1580.00, "status": "待付款"}
  ]
}
```

---

### 3.6 并发与性能设计

| 优化点 | 实现方式 | 效果 |
|--------|---------|------|
| **表间并行生成** | 同依赖层级表使用 `CompletableFuture.allOf` 并行 | N 张无依赖表并行，时间从 O(N) 降至 O(1) |
| **LLM 字段并发** | 每个 LLM 字段独立异步任务，专用线程池 | M 个 LLM 字段并发请求，不互相阻塞 |
| **批量 LLM 调用** | 每次请求 50 条值（可配置），循环取用 | 减少 API 调用次数，单次请求摊薄延迟 |
| **数据库批量写入** | `insert-batch-size: 500`，批量 INSERT | 单次 500 行，减少数据库往返 |
| **线程安全数据结构** | `ConcurrentHashMap`, `CopyOnWriteArrayList`, `AtomicInteger` | 并发写入时无竞争冲突 |

---

## 四、总结

本平台将 **AI 大模型的语义理解能力** 与 **规则引擎的精确约束能力** 深度融合，解决了测试数据生成领域长期面临的核心矛盾：

- **真实感 vs. 约束合规**：大模型生成高质量语义数据 + 系统强制约束校验 + 长度截断兜底，两者互补而非对立
- **灵活性 vs. 一致性**：列级重新生成支持局部调整，外键依赖自动处理保证全局一致性
- **效率 vs. 易用性**：SQL 约束自动推导 + 历史规则回填 + AI 建议，将规则配置时间压缩至分钟级

**适用场景**：涉及多表联查的复杂业务 SQL、需要高真实感数据的接口测试、数据库外键约束密集的核心交易系统测试数据准备。
