# 01 — 基础设施搭建

**What to build:** 团队成员 clone 仓库后，一条命令拉起全部中间件，数据库表结构和种子数据就绪，Neo4j 知识图谱可查询，四端目录结构清晰可用。

**Blocked by:** None — can start immediately.

**Status:** ready-for-agent

- [ ] Docker Compose 文件可一键启动 PostgreSQL（含 pgvector 扩展）、Redis、Neo4j
- [ ] PostgreSQL DDL 脚本创建全部 16 张业务表（含索引、外键）
- [ ] 种子数据：1 家三甲医院、管理员账号、演示患者账号、基础科室/医生/药品
- [ ] Neo4j Cypher 初始化脚本：创建 5 类节点 + 6 种关系 + 常见病种数据（约 50 症状 + 30 疾病）
- [ ] Monorepo 顶层目录：backend/、web-admin/、miniapp/、agent/ 各有占位文件
- [ ] .env.example 列出所有环境变量（DB连接、Redis、Neo4j、JWT密钥、Agent密钥）
