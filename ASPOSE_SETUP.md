# Aspose.Words 配置说明

## 1. Aspose.Words 依赖安装

由于 Aspose.Words 不在公共 Maven 仓库中，需要手动安装。

### 方法一：从 Aspose Maven 仓库安装（推荐）

在 `pom.xml` 中添加 Aspose Maven 仓库：

```xml
<repositories>
    <repository>
        <id>AsposeJavaAPI</id>
        <name>Aspose Java API</name>
        <url>https://releases.aspose.com/java/repo/</url>
    </repository>
</repositories>
```

然后使用官方依赖：

```xml
<dependency>
    <groupId>com.aspose</groupId>
    <artifactId>aspose-words</artifactId>
    <version>23.1</version>
    <classifier>jdk17</classifier>
</dependency>
```

### 方法二：手动安装 JAR 包

1. 从 Aspose 官网下载 aspose-words-23.1-jdk17.jar
2. 执行以下命令安装到本地 Maven 仓库：

```bash
mvn install:install-file \
  -Dfile=aspose-words-23.1-jdk17.jar \
  -DgroupId=com.aspose \
  -DartifactId=aspose-words \
  -Dversion=23.1 \
  -Dclassifier=jdk17 \
  -Dpackaging=jar
```

### 方法三：使用现有配置

如果你已经有 `com.bmsoft.aspose:aspose-words:18.5`，确保：

1. JAR 包已安装在本地 Maven 仓库
2. 或者更新为官方 Aspose 依赖

## 2. 许可证配置

### 创建 license.xml

在 `src/main/resources/` 目录下创建 `license.xml`：

```xml
<License>
  <Data>
    <ProductNames>
      <ProductName>Aspose.Words for Java</ProductName>
    </ProductNames>
    <EditionType>Enterprise</EditionType>
    <SubscriptionExpiry>20991231</SubscriptionExpiry>
    <LicenseExpiry>20991231</LicenseExpiry>
    <OemKey>YOUR_OEM_KEY_HERE</OemKey>
  </Data>
  <Signature>
    YOUR_SIGNATURE_HERE
  </Signature>
</License>
```

**注意**：
- 如果没有许可证，Aspose.Words 会以评估模式运行
- 评估模式会在生成的 PDF 中添加水印
- 生产环境需要购买有效许可证

## 3. 中文字体配置

### 方法一：使用系统字体（推荐）

PdfUtil 中已配置从 `/app/fonts` 目录加载字体。

创建字体目录并添加中文字体：

```bash
mkdir -p /app/fonts
# 复制中文字体文件到此目录
# 例如：SimSun.ttf（宋体）、SimHei.ttf（黑体）等
```

### 方法二：修改字体路径

在 `PdfUtil.java` 中修改字体路径：

```java
String folders = "/path/to/your/fonts"; // 改为你的字体目录路径
```

### 方法三：使用 Docker 挂载字体

如果使用 Docker，在 docker-compose.yml 中添加：

```yaml
volumes:
  - ./fonts:/app/fonts
```

## 4. Word 模板准备

### 模板文件位置

将 Word 模板放在：
```
src/main/resources/template/风险评估报告-参考模板.docx
```

### 模板变量说明

在 Word 模板中使用以下变量（poi-tl 语法）：

| 变量名 | 说明 | 示例 |
|--------|------|------|
| `{{reportDate}}` | 报告日期 | 2026年01月23日 |
| `{{ourSide}}` | 我方当事人 | 张三 |
| `{{ourIdentity}}` | 我方身份 | 原告 |
| `{{otherParty}}` | 对方当事人 | XX公司 |
| `{{otherIdentity}}` | 对方身份 | 被告 |
| `{{caseReason}}` | 案由 | 合同纠纷 |
| `{{coreDemand}}` | 核心诉求 | 支付欠款50万元 |
| `{{basicFacts}}` | 基本事实 | *Markdown 格式* |
| `{{availableCoreEvidence}}` | 现有证据 | *Markdown 格式* |
| `{{overallRiskLevel}}` | 风险等级 | 中风险 |
| `{{overallRiskScore}}` | 风险评分 | 50 |
| `{{overallRiskScoreReason}}` | 评分原因 | *Markdown 格式* |
| `{{advantagesOpportunityAnalysis}}` | 优势分析 | *Markdown 格式* |
| `{{riskChallengeAlert}}` | 风险提示 | *Markdown 格式* |
| `{{riskPoint}}` | 风险点简述 | 证据不足 |
| `{{actionSuggestionsSubsequentStrategies}}` | 行动建议 | *Markdown 格式* |

### 模板示例内容

```
风险评估报告

报告日期：{{reportDate}}

一、案件基本信息

我方当事人：{{ourSide}}
我方身份：{{ourIdentity}}
对方当事人：{{otherParty}}
对方身份：{{otherIdentity}}
案由：{{caseReason}}

核心诉求：{{coreDemand}}

二、案件事实

基本事实：
{{basicFacts}}

现有证据：
{{availableCoreEvidence}}

三、风险评估

风险等级：{{overallRiskLevel}}
风险评分：{{overallRiskScore}}

评分说明：
{{overallRiskScoreReason}}

四、分析与建议

优势分析：
{{advantagesOpportunityAnalysis}}

风险提示：
{{riskChallengeAlert}}

风险点：{{riskPoint}}

行动建议：
{{actionSuggestionsSubsequentStrategies}}
```

## 5. 测试步骤

1. **安装 Aspose 依赖**
   ```bash
   # 清除之前的缓存
   rm -rf ~/.m2/repository/com/aspose
   rm -rf ~/.m2/repository/com/bmsoft

   # 重新编译
   mvn clean compile
   ```

2. **准备 Word 模板**
   - 复制你的 Word 模板到 `src/main/resources/template/`
   - 命名为 `风险评估报告-参考模板.docx`

3. **准备许可证（可选）**
   - 将 `license.xml` 放到 `src/main/resources/`

4. **准备中文字体**
   ```bash
   mkdir -p /app/fonts
   # 复制中文字体文件
   cp /System/Library/Fonts/STHeiti\ Light.ttc /app/fonts/
   ```

5. **测试生成**
   - 启动应用
   - 使用交互式风险评估 Agent 生成报告
   - 下载 PDF 查看

## 6. 常见问题

### Q: 编译失败，找不到 Aspose 依赖
**A**: 添加 Aspose Maven 仓库，或手动安装 JAR 包

### Q: 生成的 PDF 有水印
**A**: 这是评估模式限制，需要购买有效许可证

### Q: 中文显示为方块或乱码
**A**: 检查字体目录 `/app/fonts` 是否包含中文字体

### Q: 找不到 Word 模板
**A**: 确保模板文件在 `src/main/resources/template/风险评估报告-参考模板.docx`

### Q: Markdown 格式没有正确渲染
**A**: 检查 `MarkdownRenderPolicy` 是否正确配置
