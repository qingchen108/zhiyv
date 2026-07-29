# 03 — 科室/医生/药品管理

**What to build:** 管理员登录 B 端后台后，能看到科室、医生、药品的列表页，能新增、编辑、删除，数据实时生效。医生能编辑自己的部分信息。

**Blocked by:** 02 — 后端骨架与鉴权

**Status:** ready-for-agent

- [ ] 后端：科室 CRUD API（/api/b/departments）
- [ ] 后端：医生 CRUD API（/api/b/doctors），关联科室，DOCTOR 角色仅可编辑本人
- [ ] 后端：药品 CRUD API（/api/b/drugs）
- [ ] B 端：Umi + React 项目初始化，集成 Ant Design、Zustand、Less
- [ ] B 端：登录页 + JWT 存储 + 路由守卫
- [ ] B 端：科室管理页（表格 + 新增/编辑弹窗 + 删除确认）
- [ ] B 端：医生管理页（表格 + 表单含科室下拉、职称选择）
- [ ] B 端：药品管理页（表格 + 表单）
- [ ] 权限：科室/药品仅 ADMIN 可操作，医生管理 ADMIN 全部 + DOCTOR 编辑本人
