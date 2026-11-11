# Markdown 编辑器兼容性裁定

采用 Milkdown Kit 7.23.0，使用 Markdown 原生 parser/serializer，保留 `guides.content`、GuideInput、GuideDetail 和后端全部校验。Tiptap 的 Beta Markdown 适配层未采用；Crepe 的额外图片 URL/上传入口未引入，使用现有上传接口和轻量 Vue 工具栏。

PoC 使用真实攻略4、5、6以及包含多级标题、粗斜体、无序/有序列表、链接、引用、五图、表格、代码块的综合夹具。当前数据库三篇内容与 `docs/m8/content/guide-4/5/6.md` 逐字相同。前端测试直接读取这些历史文件并执行实际 Milkdown 导入/导出；保留重要文本、结构和图片地址。

兼容性保护：

- 无修改导入不触发v-model写回，保存原 Markdown 逐字保持。
- 源码切换保留未保存内容；视觉编辑器保持挂载，首次导入不进入撤销历史，后续源码修改进入原编辑历史。
- 原始HTML、特殊扩展、引用定义、元数据、非白名单链接/图片回退完整源码；解析器生成的实际节点也执行来源校验。
- 导入前后使用原 SafeMarkdown 的安全渲染比较语义；保留行内词间空格和代码块空白，标题插件内部id不作为业务差异。
- 编辑时的非法图片/链接事务直接拒绝；HTML粘贴沿用原DOMPurify白名单，不放开原始HTML。上传仍走原 ImageUpload/API，光标处插图，正文alt和图库说明分别维护。
- 禁用编辑库将多余空段落写为 `<br />` 的插件；连续空段落按标准Markdown空行归一，不持久化HTML。
- 从图库移除需确认，只删除关联；正文图单独编辑/删除，上传文件不自动删除。失败上传不添加图库记录。

真实浏览器验收、测试计数与截图索引以 [ACCEPTANCE.md](ACCEPTANCE.md) 为准。
