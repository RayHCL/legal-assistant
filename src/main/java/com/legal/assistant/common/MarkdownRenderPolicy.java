package com.legal.assistant.common;

import com.deepoove.poi.policy.AbstractRenderPolicy;
import com.deepoove.poi.render.RenderContext;
import org.apache.poi.xwpf.usermodel.UnderlinePatterns;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.xmlbeans.XmlCursor;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTShd;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STShd;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Markdown渲染插件 for poi-tl 1.12.2
 * 支持标题、粗体、斜体、代码块、列表、链接等格式
 */
public class MarkdownRenderPolicy extends AbstractRenderPolicy<String> {

    @Override
    public void doRender(RenderContext<String> context) throws Exception {
        XWPFRun run = context.getRun();
        String markdown = context.getData();

        if (markdown == null || markdown.isEmpty()) {
            return;
        }

        // 获取父段落和文档
        XWPFParagraph paragraph = (XWPFParagraph) run.getParent();
        XWPFDocument document = paragraph.getDocument();

        // 删除原有的run
        int runIndex = paragraph.getRuns().indexOf(run);
        paragraph.removeRun(runIndex);

        // 解析并渲染Markdown
        renderMarkdown(document, paragraph, markdown);
    }

    private void renderMarkdown(XWPFDocument document, XWPFParagraph firstParagraph, String markdown) {
        String[] lines = markdown.split("\n");
        boolean inCodeBlock = false;
        StringBuilder codeBlockContent = new StringBuilder();

        // 先清空第一个段落的所有runs
        while (firstParagraph.getRuns().size() > 0) {
            firstParagraph.removeRun(0);
        }

        // 解析所有行，构建段落列表
        List<ParagraphData> paragraphDataList = new ArrayList<>();

        for (String line : lines) {
            // 处理代码块
            if (line.trim().startsWith("```")) {
                if (inCodeBlock) {
                    // 结束代码块
                    paragraphDataList.add(new ParagraphData(ParagraphType.CODE_BLOCK, codeBlockContent.toString()));
                    codeBlockContent = new StringBuilder();
                    inCodeBlock = false;
                } else {
                    inCodeBlock = true;
                }
                continue;
            }

            if (inCodeBlock) {
                codeBlockContent.append(line).append("\n");
                continue;
            }

            // 处理标题
            if (line.startsWith("#")) {
                paragraphDataList.add(new ParagraphData(ParagraphType.HEADING, line));
                continue;
            }

            // 处理无序列表
            if (line.trim().matches("^[*\\-+]\\s+.+")) {
                paragraphDataList.add(new ParagraphData(ParagraphType.UNORDERED_LIST, line));
                continue;
            }

            // 处理有序列表 - 更严格的匹配
            if (line.trim().matches("^\\d+\\.\\s+.+")) {
                paragraphDataList.add(new ParagraphData(ParagraphType.ORDERED_LIST, line));
                continue;
            }

            // 处理普通段落
            if (!line.trim().isEmpty()) {
                paragraphDataList.add(new ParagraphData(ParagraphType.NORMAL, line));
            }
        }

        if (paragraphDataList.isEmpty()) {
            return;
        }

        // 渲染第一个段落
        renderParagraphData(firstParagraph, paragraphDataList.get(0));

        // 从第二个段落开始，按顺序插入
        XWPFParagraph lastPara = firstParagraph;
        for (int i = 1; i < paragraphDataList.size(); i++) {
            ParagraphData data = paragraphDataList.get(i);

            // 在上一个段落后插入新段落
            XmlCursor cursor = lastPara.getCTP().newCursor();
            cursor.toEndToken();
            cursor.toNextToken();

            XWPFParagraph newPara = document.insertNewParagraph(cursor);
            cursor.dispose();

            if (newPara != null) {
                renderParagraphData(newPara, data);
                lastPara = newPara;
            }
        }
    }

    private void renderParagraphData(XWPFParagraph para, ParagraphData data) {
        switch (data.type) {
            case HEADING:
                renderHeading(para, data.content);
                break;
            case CODE_BLOCK:
                renderCodeBlock(para, data.content);
                break;
            case UNORDERED_LIST:
                renderUnorderedList(para, data.content);
                break;
            case ORDERED_LIST:
                renderOrderedList(para, data.content);
                break;
            case NORMAL:
                renderParagraph(para, data.content);
                break;
        }
    }

    private void renderHeading(XWPFParagraph para, String line) {
        int level = 0;
        while (level < line.length() && line.charAt(level) == '#') {
            level++;
        }

        String text = line.substring(level).trim();

        XWPFRun run = para.createRun();
        run.setText(text);
        run.setBold(true);

        // 根据级别设置字体大小
        int fontSize = Math.max(12, 24 - (level - 1) * 2);
        run.setFontSize(fontSize);

        para.setSpacingAfter(200);
    }

    private void renderParagraph(XWPFParagraph para, String line) {
        parseInlineFormats(para, line);
        para.setSpacingAfter(100);
    }

    private void parseInlineFormats(XWPFParagraph para, String text) {
        // 匹配粗体、斜体、行内代码、链接等
        Pattern pattern = Pattern.compile(
                "(\\*\\*|__)(.*?)\\1|" +  // 粗体
                        "(\\*|_)(.*?)\\3|" +       // 斜体
                        "`([^`]+)`|" +             // 行内代码
                        "\\[([^\\]]+)\\]\\(([^)]+)\\)" // 链接
        );

        Matcher matcher = pattern.matcher(text);
        int lastEnd = 0;

        while (matcher.find()) {
            // 添加匹配前的普通文本
            if (matcher.start() > lastEnd) {
                XWPFRun run = para.createRun();
                run.setText(text.substring(lastEnd, matcher.start()));
            }

            XWPFRun run = para.createRun();

            if (matcher.group(2) != null) {
                // 粗体
                run.setText(matcher.group(2));
                run.setBold(true);
                run.setColor("F6E6DF");
            } else if (matcher.group(4) != null) {
                // 斜体
                run.setText(matcher.group(4));
                run.setItalic(true);
            } else if (matcher.group(5) != null) {
                // 行内代码
                run.setText(matcher.group(5));
                run.setFontFamily("Consolas");
                run.setColor("D73A49");
                CTRPr rPr = run.getCTR().getRPr();
                if (rPr == null) {
                    rPr = run.getCTR().addNewRPr();
                }
                CTShd shd = rPr.getShdArray().length > 0 ? rPr.getShdArray(0) : rPr.addNewShd();
                shd.setVal(STShd.CLEAR);
                shd.setFill("F6F8FA");
            } else if (matcher.group(6) != null && matcher.group(7) != null) {
                // 链接
                run.setText(matcher.group(6));
                run.setColor("0366D6");
                run.setUnderline(UnderlinePatterns.SINGLE);
            }

            lastEnd = matcher.end();
        }

        // 添加剩余的文本
        if (lastEnd < text.length()) {
            XWPFRun run = para.createRun();
            run.setText(text.substring(lastEnd));
        }
    }

    private void renderCodeBlock(XWPFParagraph para, String code) {
        XWPFRun run = para.createRun();
        run.setText(code.trim());
        run.setFontFamily("Consolas");
        run.setFontSize(10);

        // 设置run背景色
        CTRPr rPr = run.getCTR().getRPr();
        if (rPr == null) {
            rPr = run.getCTR().addNewRPr();
        }
        CTShd shd = rPr.getShdArray().length > 0 ? rPr.getShdArray(0) : rPr.addNewShd();
        shd.setVal(STShd.CLEAR);
        shd.setFill("F6F8FA");

        // 设置段落背景
        CTPPr pPr = para.getCTP().getPPr();
        if (pPr == null) {
            pPr = para.getCTP().addNewPPr();
        }
        CTShd pShd = pPr.getShd() != null ? pPr.getShd() : pPr.addNewShd();
        pShd.setVal(STShd.CLEAR);
        pShd.setFill("F6F8FA");

        para.setSpacingAfter(200);
    }

    private void renderUnorderedList(XWPFParagraph para, String line) {
        String text = line.trim().replaceFirst("^[*\\-+]\\s+", "");

        // 使用特殊字符模拟项目符号，不使用编号系统
        XWPFRun bullet = para.createRun();
        bullet.setText("• ");

        parseInlineFormats(para, text);
        para.setIndentationLeft(400);
        para.setIndentationHanging(200);
    }

    private void renderOrderedList(XWPFParagraph para, String line) {
        String text = line.trim();

        // 使用正则表达式精确匹配序号
        Pattern pattern = Pattern.compile("^(\\d+)\\.");
        Matcher matcher = pattern.matcher(text);

        if (matcher.find()) {
            String number = matcher.group(1) + ". ";
            String content = text.substring(matcher.end()).trim();

            XWPFRun numRun = para.createRun();
            numRun.setText(number);

            parseInlineFormats(para, content);
        } else {
            // 如果没有匹配到序号，直接渲染整行
            parseInlineFormats(para, text);
        }

        para.setIndentationLeft(400);
        para.setIndentationHanging(200);
    }

    // 内部类：段落数据
    private static class ParagraphData {
        ParagraphType type;
        String content;

        ParagraphData(ParagraphType type, String content) {
            this.type = type;
            this.content = content;
        }
    }

    // 段落类型枚举
    private enum ParagraphType {
        HEADING,
        CODE_BLOCK,
        UNORDERED_LIST,
        ORDERED_LIST,
        NORMAL
    }
}