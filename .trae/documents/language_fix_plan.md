# 语言问题修复计划

## 问题分析

1. **硬编码字符串问题**：
   - 在RadioStationRepository.java中存在硬编码的中文和英文字符串
   - 特别是在数据库更新过程中的进度提示和错误信息
   - 这些硬编码字符串会导致语言切换不正常

2. **俄文语言支持**：
   - strings.xml中已添加language_russian字符串
   - 但arrays.xml中的语言选项数组未包含俄文
   - FragmentSettings.java中的updateAppLanguage方法未处理俄文

3. **其他潜在问题**：
   - 可能存在其他文件中的硬编码字符串
   - 需要检查整个代码库的国际化实现

## 修复步骤

### 1. 修复RadioStationRepository.java中的硬编码字符串

- 将硬编码的中文和英文字符串替换为字符串资源
- 确保所有进度提示和错误信息都使用字符串资源

### 2. 更新arrays.xml添加俄文语言选项

- 在language_entries数组中添加俄文选项
- 在language_values数组中添加对应的俄文值

### 3. 更新FragmentSettings.java支持俄文

- 修改updateAppLanguage方法，添加对俄文的处理

### 4. 检查其他文件中的硬编码字符串

- 使用grep工具搜索整个代码库中的硬编码字符串
- 修复发现的硬编码字符串问题

### 5. 验证俄文语言支持

- 确保俄文语言选项在设置中显示
- 测试俄文界面的显示效果

## 技术实现

### 1. 修复RadioStationRepository.java

- 为硬编码字符串添加对应的字符串资源
- 使用context.getString()方法获取字符串资源

### 2. 更新arrays.xml

- 在language_entries中添加@string/language_russian
- 在language_values中添加"ru"

### 3. 更新FragmentSettings.java

- 在updateAppLanguage方法中添加对"ru"语言代码的处理

### 4. 全局搜索硬编码字符串

- 使用grep搜索"[^"]*[\u4e00-\u9fa5][^"]*"查找中文硬编码
- 使用grep搜索""[^"]*[a-zA-Z][^"]*""查找英文硬编码

## 预期结果

- 所有界面文本都通过字符串资源加载
- 中文用户看到完整的中文界面
- 俄文用户看到完整的俄文界面
- 语言切换功能正常工作
- 数据库更新进度提示正确显示对应语言

## 风险评估

- 低风险：修改仅涉及字符串资源和语言处理逻辑
- 需确保所有新添加的字符串资源都有对应的翻译
- 需测试语言切换功能确保正常工作
