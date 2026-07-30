# SmartMed Admin 设计系统（MASTER）

> 由 ui-ux-pro-max `--design-system` 生成，针对 B 端医疗管理后台（科室/医生/药品 CRUD）。
> 所有页面以此为准；页面级特殊需求在 `pages/<name>.md` 覆盖。

## 风格：Accessible & Ethical

- 无障碍优先，目标 WCAG AAA
- 高对比、大字号（正文 ≥16px）、键盘可导航、屏幕阅读器友好
- 适用：医疗、政府、教育、包容性产品
- 性能：⚡ 优秀 ｜ 无障碍：✓ WCAG AAA

## 配色（青色系 + 健康绿）

| 角色 | Hex | 用途 |
|------|-----|------|
| Primary | `#0891B2` | 主色：主按钮、激活态、链接、焦点环 |
| On Primary | `#FFFFFF` | 主色上的文字/图标 |
| Secondary | `#22D3EE` | 次要强调（慎用，面积小） |
| Accent/CTA | `#059669` | 成功/确认操作（如"保存"成功提示） |
| Background | `#ECFEFF` | 页面底色（极浅青） |
| Foreground | `#164E63` | 正文主色（深青灰） |
| Muted | `#E8F1F6` | 卡片次级背景、禁用底 |
| Border | `#A5F3FC` | 分割线、卡片描边 |
| Destructive | `#DC2626` | 删除、错误、危险操作 |
| Ring | `#0891B2` | 焦点环（3-4px） |

落地方式：AntD `ConfigProvider` theme token + Less 变量。

## 字体

- 标题：Figtree（300-700）
- 正文：Noto Sans（300-700）
- 情绪：医疗、洁净、无障碍、专业、可信
- Google Fonts：`Figtree:wght@300;400;500;600;700` + `Noto+Sans:wght@300;400;500;700`

## 关键效果

- 清晰焦点环（3-4px，`--color-ring`）
- ARIA 标签、skip links
- 响应式：375 / 768 / 1024 / 1440px
- 尊重 `prefers-reduced-motion`
- 触摸目标 ≥44px

## UX 准则（表格/表单 CRUD 重点）

| 准则 | Do | Don't |
|------|----|----|
| 错误提示 | `role=alert`/`aria-live` 播报 | 仅红色边框 |
| 表单标签 | `<label for>` 关联 | 仅 placeholder |
| 表格响应 | `overflow-x-auto` 横向滚动/卡片布局 | 宽表撑破视口 |
| 提交反馈 | loading -> success/error | 点击无响应 |
| 错误恢复 | 给出下一步（重试/帮助链接） | 只报错不引导 |

## 反模式（禁止）

- 霓虹亮色
- 重度动画
- AI 紫/粉渐变
- emoji 当结构图标（用 SVG：AntD Icons）
- 硬编码颜色（走 token）

## 交付前检查

- [ ] 无 emoji 当图标（用 AntD Icons）
- [ ] 可点击元素 `cursor: pointer`
- [ ] 悬停态有 150-300ms 过渡
- [ ] 浅色正文对比度 ≥4.5:1
- [ ] 焦点态键盘可见
- [ ] `prefers-reduced-motion` 受尊重
- [ ] 响应式：375/768/1024/1440px
