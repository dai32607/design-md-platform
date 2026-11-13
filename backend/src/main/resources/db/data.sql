-- 初始化品牌数据
INSERT INTO brand (id, name, logo_url, source, website, description) VALUES
(1, 'Apple', 'https://developer.apple.com/favicon.ico', 'system', 'https://developer.apple.com/design/human-interface-guidelines/',
 'Apple Human Interface Guidelines — 苹果官方人机界面设计指南，定义了 iOS、macOS、watchOS、tvOS 和 visionOS 的设计原则与视觉规范。'),
(2, 'Google', 'https://m3.material.io/static/assets/m3-favicon.ico', 'system', 'https://m3.material.io/',
 'Material Design 3 — Google 最新一代设计语言，提供自适应配色、动态颜色系统、组件规范与设计 Token 体系。'),
(3, 'Ant Design', 'https://gw.alipayobjects.com/zos/rmsportal/KDpgvguMpGfqaHPjicRK.svg', 'system', 'https://ant.design/',
 'Ant Design — 蚂蚁集团开源的企业级 UI 设计语言和 React 组件库，提供完整的设计 Token 与企业级中后台解决方案。');

-- 默认管理员账号 admin：密码仅以 BCrypt 哈希形式存放，仓库不提供明文；非本地演示环境部署前必须重置密码与 JWT_SECRET
INSERT INTO `user` (id, username, password, nickname, role) VALUES
(1, 'admin', '$2b$10$jPRKPV58a9qjw8yHaCWn6uZ5h60S.4PMaeAHQRvwZpLvNmmL.QYQm', '管理员', 'ADMIN');

-- Apple 设计 Token (brand_id=1)
INSERT INTO design_token (brand_id, category, token_name, token_value, description) VALUES
(1, 'COLORS', 'System Blue', '#007AFF', 'iOS 系统主色调，用于按钮、链接等交互元素'),
(1, 'COLORS', 'System Red', '#FF3B30', '系统红色，用于删除、警告等破坏性操作'),
(1, 'COLORS', 'System Green', '#34C759', '系统绿色，用于成功状态、确认操作'),
(1, 'COLORS', 'System Background', '#FFFFFF', '浅色模式主背景色'),
(1, 'TYPOGRAPHY', 'Large Title', 'SF Pro Display, 34pt, Bold', '大标题字体样式，用于导航栏大标题'),
(1, 'TYPOGRAPHY', 'Title 1', 'SF Pro Display, 28pt, Regular', '一级标题，用于页面主标题'),
(1, 'TYPOGRAPHY', 'Body', 'SF Pro Text, 17pt, Regular', '正文文本，用于主要内容段落'),
(1, 'TYPOGRAPHY', 'Caption', 'SF Pro Text, 12pt, Regular', '辅助说明文字，用于图片说明等'),
(1, 'SPACING', 'Standard Margin', '16pt', '标准外边距，页面四周边距'),
(1, 'SPACING', 'Cell Padding', '12pt 16pt', '列表项内边距'),
(1, 'COMPONENTS', 'Navigation Bar Height', '44pt', '导航栏标准高度'),
(1, 'COMPONENTS', 'Tab Bar Height', '49pt (83pt with safe area)', '底部标签栏高度');

-- Google Material Design 3 Token (brand_id=2)
INSERT INTO design_token (brand_id, category, token_name, token_value, description) VALUES
(2, 'COLORS', 'Primary', '#6750A4', 'M3 主色调，用于关键组件、FAB、强调元素'),
(2, 'COLORS', 'On Primary', '#FFFFFF', '主色调上的内容颜色（文字/图标）'),
(2, 'COLORS', 'Surface', '#FFFBFE', '浅色模式 surface 容器背景色'),
(2, 'COLORS', 'Error', '#B3261E', '错误状态色，用于表单验证等'),
(2, 'TYPOGRAPHY', 'Display Large', 'Roboto, 57px, Regular', '超大展示文字'),
(2, 'TYPOGRAPHY', 'Headline Medium', 'Roboto, 28px, Regular', '中号标题'),
(2, 'TYPOGRAPHY', 'Body Large', 'Roboto, 16px, Regular, 24px line-height', '正文大号'),
(2, 'TYPOGRAPHY', 'Label Medium', 'Roboto, 12px, Medium, 16px line-height', '标签文字'),
(2, 'SPACING', 'Density Scale', '0dp (default) / -4dp (comfortable)', 'M3 密度等级'),
(2, 'COMPONENTS', 'FAB Size', '56dp (default) / 40dp (small)', 'Floating Action Button 尺寸');

-- Ant Design Token (brand_id=3)
INSERT INTO design_token (brand_id, category, token_name, token_value, description) VALUES
(3, 'COLORS', 'Primary Color', '#1677FF', 'Ant Design 主色，用于按钮、链接等'),
(3, 'COLORS', 'Success Color', '#52C41A', '成功色，用于成功状态和提示'),
(3, 'COLORS', 'Warning Color', '#FAAD14', '警告色，用于警告状态'),
(3, 'COLORS', 'Border Color', '#D9D9D9', '默认边框颜色'),
(3, 'TYPOGRAPHY', 'Font Family', '-apple-system, BlinkMacSystemFont, ''Segoe UI'', Roboto', '全局字体栈'),
(3, 'TYPOGRAPHY', 'Font Size Base', '14px', '基准字号'),
(3, 'TYPOGRAPHY', 'Heading 1', '38px, 1.23 line-height, 600 weight', '一级标题样式'),
(3, 'TYPOGRAPHY', 'Line Height Base', '1.5715', '基准行高'),
(3, 'SPACING', 'Margin SM', '12px', '小号外边距'),
(3, 'SPACING', 'Margin MD', '20px', '中号外边距'),
(3, 'COMPONENTS', 'Button Height', '32px (default) / 40px (large)', '按钮高度规范');

-- 预置 DESIGN.md 文件
INSERT INTO design_file (brand_id, title, content, source_url) VALUES
(1, 'Apple Human Interface Guidelines', '# Apple Human Interface Guidelines\n\n## Overview\n\nApple 的 Human Interface Guidelines (HIG) 是构建 Apple 平台应用的权威设计参考。\n\n## 核心原则\n\n### 清晰 (Clarity)\n- 文字清晰易读\n- 图标精确、清晰\n- 装饰元素微妙适当\n- 功能驱动设计\n\n### 尊重 (Deference)\n- UI 帮助用户理解内容，而非与之竞争\n- 充分利用整个屏幕\n- 减少视觉修饰\n\n### 深度 (Depth)\n- 通过视觉层次和真实的动效表达层次关系\n- 提供触摸感知和可发现性\n- 使用半透明和模糊效果传达前后关系\n\n## 色彩系统\n\nApple 提供完整的系统色彩体系：\n- System Blue: #007AFF\n- System Red: #FF3B30\n- System Green: #34C759\n- System Orange: #FF9500\n- System Yellow: #FFCC00\n\n## 字体排版\n\n- SF Pro Display (大标题)\n- SF Pro Text (正文)\n- 支持动态类型 (Dynamic Type)\n\n## 间距与布局\n\n- 自动布局和 Safe Area\n- 标准 16pt 外边距\n- 弹性间距系统', 'https://developer.apple.com/design/human-interface-guidelines/'),
(2, 'Material Design 3 Guidelines', '# Material Design 3\n\n## Overview\n\nMaterial Design 3 (M3) 是 Google 最新一代开源设计系统。\n\n## 核心特性\n\n### Material You\n- 动态配色：从用户壁纸提取色彩方案\n- 自适应界面\n- 个性化主题\n\n### Design Token\nM3 使用 Design Token 体系管理设计变量：\n\n#### 颜色 Token\n- primary\n- on-primary\n- primary-container\n- secondary\n- surface\n- surface-variant\n- error\n\n#### 形状 Token\n- corner-small: 4dp\n- corner-medium: 8dp\n- corner-large: 16dp\n- corner-extra-large: 28dp\n\n#### 字体 Token\n- Display: 显示文字\n- Headline: 标题\n- Title: 标题\n- Body: 正文\n- Label: 标签\n\n### 组件规范\n\n- Material 3 Compose 组件库\n- 按钮、卡片、导航栏等完整组件\n- 支持暗色模式\n\n## 自适应\n\n- 支持手机、平板、桌面、Web\n- 响应式布局网格', 'https://m3.material.io/'),
(3, 'Ant Design Design System', '# Ant Design 设计体系\n\n## Overview\n\nAnt Design 是蚂蚁集团开源的企业级 UI 设计语言，广泛应用于中后台产品。\n\n## 设计价值观\n\n### 自然 (Natural)\n- 符合用户心智模型\n- 顺滑的交互过渡\n- 符合物理规律的动效\n\n### 确定性 (Certain)\n- 保持交互行为一致\n- 操作结果可预期\n- 状态变化清晰\n\n### 意义感 (Meaningful)\n- 设计服务于目标\n- 减少不必要的装饰\n- 聚焦核心任务\n\n### 生长性 (Growing)\n- 组件可组合复用\n- 支持多端适配\n- 生态丰富\n\n## 设计 Token\n\n### 颜色\n- Primary: #1677FF\n- Success: #52C41A\n- Warning: #FAAD14\n- Error: #FF4D4F\n\n### 字体\n- 字号: 12/14/16/20/24/30/38\n- 行高基准: 1.5715\n- 字重: 400/500/600\n\n### 间距\n- 基准 4px\n- xs: 4px, sm: 8px, md: 12px, lg: 16px, xl: 24px\n\n### 圆角\n- sm: 4px\n- base: 6px\n- lg: 8px\n\n### 阴影\n- 三级阴影层级\n- 用于表示元素层级关系', 'https://ant.design/docs/spec/introduce');
