package com.xiaou.web.controller;

import com.xiaou.common.utils.SpringUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.http.MediaType.TEXT_HTML;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = IndexController.class, properties = "spring.application.name=AECP")
@Import(SpringUtils.class)
class IndexControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void rootReturnsAecpWelcomePage() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(TEXT_HTML))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("欢迎使用AECP")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("飞机与发动机协同研发")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("会议 → 任务 → 文件 → 部件追溯")));
    }
}
