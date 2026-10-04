# DesignMD · AI 设计规范生成与管理平台

> 一个以 DESIGN.md 为唯一规范源的设计系统管理平台：AI一句话生成品牌设计规范，自动解析为结构化设计 Token，供页面与组件直接消费。

[![CI](https://github.com/OWNER/REPO/actions/workflows/ci.yml/badge.svg)](https://github.com/OWNER/REPO/actions/workflows/ci.yml)

> 徽章为占位符：推送到 GitHub 后请将 `OWNER/REPO` 替换为实际仓库地址（如 `yourname/design-md-platform`）。

## 功能特性

- **AI 生成设计规范闭环**：输入一句品牌描述（可指定色板），Agent 自动完成「LLM 生成 DESIGN.md → 创建品牌 → 解析 Token → 入库」全流程
- **LLM 双模式客户端**：基于 OpenAI 兼容协议封装，配置 API Key 走真实大模型，未配置自动降级为 Mock 演示模式，开发与演示开箱即用
- **用户色板约束生成**：前端取色器采集 → 后端校验归一化 → 主色必用用户色值、不足自动以同色系明暗变体补齐，解决 LLM 颜色输出不可控问题
- **设计规范结构化解析**：解析 DESIGN.md 中 Colors / Typography / Spacing / Components 四大板块，生成标准化设计 Token
- **Token 驱动的 AI 预览**：品牌详情页基于真实 Token 动态渲染导航、Hero、卡片、表单等组件预览，所见即所得
- **三级权限模型**：预置品牌（system）只读 / 用户自建品牌（custom）创建者可查改删 / ADMIN 全量管理，前后端双层鉴权
- **文件与 Logo 管理**：DESIGN.md 上传解析、品牌 Logo 上传（安全校验扩展名与大小）

## 技术栈

| 层 | 技术 |
|---|---|
| 后端 | Java 21 · Spring Boot 3.2 · Spring Security + JWT · MyBatis-Plus · MySQL · springdoc-openapi（OpenAPI 3 / Swagger UI） |
| 前端 | Vue 3 · Vite 5 · Element Plus · Vue Router · ECharts |
| AI | OpenAI 兼容 Chat Completions（DeepSeek 等可无缝切换）+ Mock 降级 |

## API 文档（springdoc-openapi）

后端集成 springdoc-openapi（Spring Boot 3.2 兼容版 `springdoc-openapi-starter-webmvc-ui`），仅扫描 `/api/**` 接口路径：

| 入口 | 地址 |
|---|---|
| OpenAPI JSON | `http://localhost:8080/v3/api-docs` |
| Swagger UI | `http://localhost:8080/swagger-ui.html` |

演示包 / 本地开发已放行 `/v3/api-docs/**`、`/swagger-ui/**`、`/swagger-ui.html`（免登录即可访问，便于联调）；**生产环境部署时应改为鉴权后访问**，避免对外暴露接口结构。

## 快速启动

### 1. 初始化数据库

```sql
CREATE DATABASE design_db DEFAULT CHARACTER SET utf8mb4;
```

首次启动自动执行 schema.sql 建表；或手动导入 `backend/schema.sql`。

### 2. 启动后端（端口 8080）

```bash
cd backend
mvn spring-boot:run          # 要求 JDK 17+；建议 JDK 21
```

数据库连接在 `backend/src/main/resources/application.yml`：
`jdbc:mysql://localhost:3306/design_db`，用户名与密码通过环境变量注入。

### 3. 启动前端（端口 3000）

```bash
cd frontend
npm install
npm run dev                  # 访问 http://localhost:3000
```

> 前端已配置 Vite 代理，`/api` 与 `/uploads` 请求自动转发至 `http://localhost:8080`。

### 4. 演示账号

| 账号 | 密码 | 角色 |
|---|---|---|
| admin | see-local-config | ADMIN（管理后台 / 全量权限） |
| test | see-local-config | USER（可自建品牌并上传规范） |

## 核心模块

```
backend/src/main/java/com/designmd/platform
├── controller        # REST 接口层（品牌 / 用户 / Token / AI Agent / 统计）
├── service           # 业务逻辑（品牌创建/修改/删除、解析入库、Agent 编排）
├── config            # Security 鉴权 / LlmClient / Cors / 上传路径解析
├── entity + mapper   # 数据实体与 MyBatis-Plus 映射
└── common            # 统一响应 Result / 全局异常 / 分页

frontend/src
├── views             # Home / BrandDetail / AgentGenerate / Upload / Admin / Dashboard 等
├── api/index.js      # Axios 接口封装（含 JWT 拦截器）
└── router            # 路由与登录守卫
```

## 权限设计

| 操作 | system 品牌（预置） | custom 品牌（自建） | ADMIN |
|---|---|---|---|
| 查看 | 公开 | 公开 | 可看全部 |
| 修改 / 删除 | 禁止 | 仅创建者本人 | 可操作 |
| 管理后台 / 用户管理 / 统计 | - | - | 仅 ADMIN |

接口层由 Spring Security 统一校验（`/api/admin/**` 要求 ROLE_ADMIN），页面按钮按用户角色/品牌归属动态显隐。

## AI 生成闭环（项目亮点）

```
一句话描述 + 可选色板
        │
        ▼
AgentController ──► AgentService
                        ├─ ① 组装 Prompt（OpenAI 兼容）
                        ├─ ② LlmClient 生成 DESIGN.md（Mock / 真实大模型）
                        ├─ ③ 复用 BrandService 创建自定义品牌
                        ├─ ④ DesignFileService 保存规范文件
                        └─ ⑤ DesignTokenService 解析 Token 入库
```

LLM 配置（`application.yml`）：

```yaml
llm:
  base-url: https://api.deepseek.com   # OpenAI 兼容地址
  api-key: ""                          # 留空 = Mock 演示模式
  model: deepseek-chat
```

## 数据库表

- `user`：账号体系（角色 USER / ADMIN）
- `brand`：品牌（source=system/custom，creator_id 记录创建者）
- `design_file`：DESIGN.md 规范文件
- `design_token`：结构化设计 Token（category: COLORS / TYPOGRAPHY / SPACING / COMPONENTS）

## 界面一览

![首页品牌列表](docs/screenshots/home.png)

![AI 生成设计规范](docs/screenshots/agent.png)

![品牌详情与 AI 预览](docs/screenshots/brand-detail.png)

![管理后台 Dashboard](docs/screenshots/dashboard.png)