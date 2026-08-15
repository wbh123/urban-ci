# AI 专业复核标注缺失回归

现象：AI 检测候选存在，但专业复核页面图片上没有检测框。

本分支先加入真实 PostgreSQL 集成断言：`AiInferenceRepository.findTaskDetail()` 返回的 `detections[*].boundingBox` 必须是可直接序列化给前端的 Map，且包含数值型 `x/y/width/height` 和 `NORMALIZED_XYWH`。

若当前仓储层直接返回 PostgreSQL JSONB 包装对象，该断言应失败，用于确认根因。
