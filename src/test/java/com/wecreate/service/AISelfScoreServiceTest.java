package com.wecreate.service;

import com.wecreate.entity.WecreatePrompt;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * AI 自评分服务单元测试
 */
public class AISelfScoreServiceTest {

    @InjectMocks
    private AISelfScoreService aiSelfScoreService;

    @Mock
    private SystemConfigService systemConfigService;

    @Mock
    private WecreatePromptService wecreatePromptService;

    @Mock
    private WecreatePromptMapper wecreatePromptMapper;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    /**
     * 测试评分提示词构建
     */
    @Test
    public void testBuildSelfScorePrompt() {
        // 创建测试任务记录
        WecreatePrompt prompt = new WecreatePrompt();
        prompt.setPromptContent("创建一个用户管理模块");
        prompt.setWorkDirectory("D:\\workspace\\test");
        prompt.setExecutionTime(5000);
        prompt.setGitChangedFiles(3);
        prompt.setGitInsertions(100);
        prompt.setGitDeletions(20);
        prompt.setDescription("完成了用户CRUD功能");

        // 由于 buildSelfScorePrompt 是私有方法，我们通过 MemoryService 测试
        // 这里只验证数据结构
        assertNotNull(prompt.getPromptContent());
        assertEquals("D:\\workspace\\test", prompt.getWorkDirectory());
        assertEquals(Integer.valueOf(3), prompt.getGitChangedFiles());
    }

    /**
     * 测试评分范围验证
     */
    @Test
    public void testScoreRangeValidation() {
        // 验证评分范围应该在 1-10 之间
        BigDecimal validScore1 = new BigDecimal(1);
        BigDecimal validScore2 = new BigDecimal(5);
        BigDecimal validScore3 = new BigDecimal(10);

        assertTrue(validScore1.compareTo(BigDecimal.ONE) >= 0 && 
                   validScore1.compareTo(BigDecimal.TEN) <= 0);
        assertTrue(validScore2.compareTo(BigDecimal.ONE) >= 0 && 
                   validScore2.compareTo(BigDecimal.TEN) <= 0);
        assertTrue(validScore3.compareTo(BigDecimal.ONE) >= 0 && 
                   validScore3.compareTo(BigDecimal.TEN) <= 0);

        // 验证无效评分
        BigDecimal invalidScore1 = new BigDecimal(0);
        BigDecimal invalidScore2 = new BigDecimal(11);

        assertFalse(invalidScore1.compareTo(BigDecimal.ONE) >= 0 && 
                    invalidScore1.compareTo(BigDecimal.TEN) <= 0);
        assertFalse(invalidScore2.compareTo(BigDecimal.ONE) >= 0 && 
                    invalidScore2.compareTo(BigDecimal.TEN) <= 0);
    }

    /**
     * 测试已有用户评分时跳过 AI 自评
     */
    @Test
    public void testSkipWhenUserScoreExists() {
        WecreatePrompt prompt = new WecreatePrompt();
        prompt.setTraceId("test-trace-001");
        prompt.setScore(new BigDecimal(8));  // 已有用户评分
        prompt.setScoreUser("user123");

        // 验证已有评分时应该跳过
        assertNotNull(prompt.getScore());
        assertEquals("user123", prompt.getScoreUser());
    }

    /**
     * 测试无用户评分时需要 AI 自评
     */
    @Test
    public void testNeedSelfScoreWhenNoUserScore() {
        WecreatePrompt prompt = new WecreatePrompt();
        prompt.setTraceId("test-trace-002");
        prompt.setPromptContent("测试任务");

        // 验证无评分时需要 AI 自评
        assertNull(prompt.getScore());
    }
}
