package com.xiaou.web.controller;

import com.xiaou.common.utils.SpringUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = IndexController.class, properties = "spring.application.name=AECP")
@Import(SpringUtils.class)
class IndexControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void rootReturnsWelcomeMessage() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string("欢迎使用AECP，请通过前端地址访问。"));
    }
}
