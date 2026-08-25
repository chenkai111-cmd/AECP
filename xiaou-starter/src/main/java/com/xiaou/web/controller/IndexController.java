package com.xiaou.web.controller;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class IndexController {

    private static final MediaType HTML_UTF_8 = MediaType.parseMediaType("text/html;charset=UTF-8");

    @GetMapping(value = "/", produces = "text/html;charset=UTF-8")
    public ResponseEntity<Resource> index() {
        return ResponseEntity.ok()
                .contentType(HTML_UTF_8)
                .body(new ClassPathResource("static/index.html"));
    }
}
