# 0008 - 数据库迁移：Flyway 接管 schema 版本控制

引入 Flyway 管理数据库 schema 变更。迁移脚本放 `backend/src/main/resources/db/migration/`，命名 `V<ticket>_<序号>__<描述>.sql`（如 `V01_1__init_schema.sql`、`V01_2__seed_data.sql`、`V04_1__xxx.sql`），按版本号字符串升序执行。Spring Boot 启动时 Flyway 自动扫描 classpath，对比库内 `flyway_schema_history` 表，执行未应用的脚本、跳过已应用的，无需手动连库执行。移除 docker-compose 的 `db/init:/docker-entrypoint-initdb.d` 挂载，postgres 容器只建空库（`POSTGRES_DB`），schema/种子全由 Flyway 在应用端接管。

**Considered Options**: 外部目录 `filesystem:db/migration`（否决，工作目录依赖导致测试从 backend/ 启动时找不到 ../db/migration）、Flyway 只管增量+baseline 对齐旧库（否决，边界混乱）、保留 docker-entrypoint-initdb.d 不引入 Flyway（否决，手动执行迁移繁琐且易漏，03 的 VM 旧 schema 未更新即此痛点）、Liquibase（否决，XML/YAML 格式重，SQL 脚本对 DBA 更直观）。

**Consequences**: 以后每次改表只需新增一个 `V<ticket>_<序号>__<描述>.sql`，应用下次启动自动应用，不再手动连库。脚本一旦应用不可修改（Flyway 校验 checksum），改结构须新增脚本而非改旧脚本。新环境（VM/本机/CI）首次启动应用即自动全量建库到最新。测试启动时 Flyway 检查并跳过已应用脚本，零开销且保证 schema 最新。VM 库已 drop 重建，`flyway_schema_history` 记录 V01_1+V01_2 完整历史。脚本进 classpath（打进 jar），虽失去"改脚本不重打包"但换来无工作目录依赖的健壮性--迁移脚本改动本就需要重启应用触发 Flyway，重打包收益有限。`clean-disabled: false` 保留开发期 `flyway clean` 能力（生产应置 true）。
