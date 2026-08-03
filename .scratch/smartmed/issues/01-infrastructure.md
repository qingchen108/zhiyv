# 01 — 基础设施搭建

**What to build:** 团队成员 clone 仓库后，一条命令拉起全部中间件，数据库表结构和种子数据就绪，Neo4j 知识图谱可查询，四端目录结构清晰可用。

**Blocked by:** None — can start immediately.

**Status:** done

- [x] Docker Compose 文件可一键启动 PostgreSQL（pgvector/pgvector:pg16 镜像，含 pgvector 扩展）、Redis、Neo4j（init.cypher 由 neo4j-init sidecar 容器用 cypher-shell 在 Neo4j 就绪后执行，`docker compose up` 后图谱即可查询）
- [x] PostgreSQL DDL 脚本创建全部 20 张业务表（含索引、外键，含 hospital 表 + drug_pharmacy_stock 桥表）
- [x] 种子数据：1 家三甲医院（hospital 表）、管理员账号、演示患者账号、基础科室/医生/药品
- [x] 账号密码约束：B 端 ADMIN/DOCTOR 种子账号写预生成 BCrypt 哈希（$2b$10$...），明文（如 admin123）仅放 .env.example 注释；patient 表无密码列，C 端 demo-login 按预设 patient.id 签发 JWT。**跨 ticket 耦合：02 必须配 BCryptPasswordEncoder Bean 才能匹配此哈希**
- [x] pgvector 扩展启用 + 预建 kb_embedding 表（vector(1536)，见 ADR-0001）；01 不导任何 embedding 数据，不碰向量化逻辑（留待 09/11）
- [x] Neo4j Cypher 初始化脚本：创建 5 类节点 + 6 种关系 + 常见病种数据（约 50 症状 + 30 疾病），挂载至 neo4j-init sidecar 容器由 cypher-shell 执行
- [x] Monorepo 顶层目录：backend/、web-admin/、miniapp/、agent/ 各含 README.md（标注归属 ticket）+ .gitkeep；01 不建任何构建配置（pom.xml/pyproject.toml 等留给各端 ticket）
- [x] 根目录 .env.example 列基础设施变量（POSTGRES_*、REDIS_*、NEO4J_*、JWT_SECRET、AGENT_SECRET）；LLM_* 不在此列，留待 09
