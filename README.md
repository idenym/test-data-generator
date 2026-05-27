# Test Data Generator

基于 AI 大模型的智能测试数据生成平台，解析业务 SQL 自动理解表结构与关联关系，结合大模型语义理解能力生成符合业务语义的测试数据。

## 功能特性

- **SQL 智能解析** - 自动解析 DDL/DML，提取表结构、外键关系、JOIN 条件
- **多策略数据生成** - 支持 AI 语义生成、正则表达式、范围值、枚举值四种策略
- **约束感知推导** - 从 WHERE/JOIN ON 条件自动提取规则约束
- **多表依赖处理** - 外键依赖自动拓扑排序，按正确顺序生成数据
- **列级重新生成** - 支持对单列数据无损重新生成，不影响其他列
- **预览与编辑** - 数据写入前可预览、手动编辑、对比差异
- **历史规则复用** - 自动回填历史规则，支持多优先级策略
- **采纳率统计** - 跟踪用户编辑/重新生成行为，量化数据质量
- **多模型支持** - 可配置多个 LLM 模型，按需分配不同字段

## 技术栈

| 类别 | 技术 |
|------|------|
| 语言 | Java 11 |
| 框架 | Spring Boot 2.7.18 |
| SQL 解析 | JSQLParser 4.9 |
| 数据生成 | Datafaker 1.9.0, RgxGen 2.0 |
| 数据库 | H2 (开发), MySQL 8.0 (生产) |
| HTTP 客户端 | OkHttp 3.12.0 |
| JSON | Fastjson 1.2.83 |
| 前端 | Vue 3 (CDN), Axios |

## 快速开始

### 环境要求

- JDK 11+
- Maven 3.6+ (或使用内置 Maven Wrapper)

### 本地运行

```bash
# 克隆项目
git clone https://github.com/idenym/test-data-generator.git
cd test-data-generator

# 编译打包
mvnw.cmd clean package -DskipTests    # Windows
./mvnw clean package -DskipTests      # Linux/Mac

# 启动应用 (默认使用 H2 内嵌数据库)
java -jar target/test-data-generator-1.0.0.jar
```

启动后访问 http://localhost:8080

### 生产部署 (MySQL)

```bash
# 使用 prod 配置启动
java -jar target/test-data-generator-1.0.0.jar --spring.profiles.active=prod
```

需配置以下环境变量：

| 变量 | 说明 | 默认值 |
|------|------|--------|
| `MYSQL_HOST` | MySQL 地址 | localhost |
| `MYSQL_PORT` | MySQL 端口 | 3306 |
| `MYSQL_DATABASE` | 数据库名 | testdatagen |
| `MYSQL_USER` | 数据库用户 | testdatagen |
| `MYSQL_PASSWORD` | 数据库密码 | - |
| `ENCRYPTION_KEY` | 连接密码加密密钥 | - |
| `OPENAI_API_KEY` | 默认 LLM API Key | - |
| `OPENAI_BASE_URL` | 默认 LLM API 地址 | https://api.deepseek.com |
| `OPENAI_MODEL` | 默认模型 | deepseek-v4-pro |

## 使用流程

项目采用 4 步 Stepper 工作流：

1. **数据库连接** - 配置目标数据库连接信息
2. **SQL 解析** - 输入或选择 SQL 脚本，解析表结构与关联关系
3. **规则配置** - 为字段配置生成规则（支持 AI 自动推荐）
4. **数据生成** - 选择模型、设置行数，预览确认后写入数据库

### 自动回填优先级

规则自动回填按以下优先级从高到低执行：

1. WHERE 推导 (覆盖已有值)
2. 历史规则 (HISTORY)
3. 知识库 (KNOWLEDGE_BASE)
4. 字段注释默认 (COMMENT)
5. 启发式识别 (Heuristic)

## 项目结构

```
src/main/java/com/testdatagen/
├── controller/          # REST API 控制器
├── model/
│   ├── entity/          # JPA 实体
│   ├── dto/             # 请求/响应 DTO
│   └── enums/           # 枚举类型
├── repository/          # 数据访问层
├── service/             # 业务逻辑层
│   ├── llm/             # LLM 调用服务
│   ├── parser/          # SQL 解析服务
│   └── generator/       # 数据生成引擎
└── config/              # 配置类

src/main/resources/
├── static/              # 前端静态资源
│   ├── js/pages/        # Vue 页面组件
│   └── css/             # 样式文件
├── application.yml      # 开发配置 (H2)
└── application-prod.yml # 生产配置 (MySQL)
```

## API 接口

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/v1/connections` | 连接列表 |
| POST | `/api/v1/connections` | 创建连接 |
| POST | `/api/v1/connections/test` | 测试连接 |
| POST | `/api/v1/sql/analyze` | SQL 解析 |
| POST | `/api/v1/rules/auto-fill` | 规则自动回填 |
| GET | `/api/v1/generate/models` | 可用模型列表 |
| POST | `/api/v1/generate/preview` | 预览生成数据 |
| POST | `/api/v1/generate/write` | 写入数据库 |
| POST | `/api/v1/generate/regenerate-columns` | 重新生成指定列 |
| GET | `/api/v1/history` | 任务历史列表 |
| GET | `/api/v1/history/statistics` | 采纳率统计 |

## 多模型配置

在 `application.yml` 中配置多个 LLM 模型：

```yaml
app:
  openai:
    models:
      "[model-id]":
        base-url: https://api.example.com
        api-key: your-api-key
        name: Display Name
```

每个模型拥有独立的 base-url 和 api-key，生成时可选择多个模型随机分配字段。

## License

MIT
