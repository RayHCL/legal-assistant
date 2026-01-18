package com.legal.assistant.controller;

import com.legal.assistant.common.Result;
import com.legal.assistant.entity.KnowledgeBase;
import com.legal.assistant.service.KnowledgeBaseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/knowledge-base")
@Tag(name = "知识库管理", description = "知识库管理相关接口，包括创建、查询、删除知识库")
public class KnowledgeBaseController {
    
    @Autowired
    private KnowledgeBaseService knowledgeBaseService;
    
    @PostMapping("/create")
    @Operation(summary = "创建知识库", description = "创建一个新的知识库，用于组织和管理文件。需要Token认证。")
    public Result<KnowledgeBase> createKnowledgeBase(
            @Parameter(description = "知识库名称", required = true, example = "我的法律文档库")
            @RequestParam String name,
            @Parameter(description = "知识库描述（可选）", example = "存储合同相关文档")
            @RequestParam(required = false) String description,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        KnowledgeBase knowledgeBase = knowledgeBaseService.createKnowledgeBase(userId, name, description);
        return Result.success(knowledgeBase);
    }
    
    @GetMapping("/list")
    @Operation(summary = "获取知识库列表", description = "获取当前用户的所有知识库列表。需要Token认证。")
    public Result<List<KnowledgeBase>> getKnowledgeBaseList(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        List<KnowledgeBase> list = knowledgeBaseService.getKnowledgeBaseList(userId);
        return Result.success(list);
    }
    
    @DeleteMapping("/{id}")
    @Operation(summary = "删除知识库", description = "删除指定的知识库。需要Token认证。")
    public Result<Void> deleteKnowledgeBase(
            @Parameter(description = "知识库ID", required = true, example = "1")
            @PathVariable Long id,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        knowledgeBaseService.deleteKnowledgeBase(userId, id);
        return Result.success();
    }
}
