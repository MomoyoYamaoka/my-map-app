package com.example.demo.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * http://localhost:8080/ でフロント（SPA）を表示するため、ルートを index.html にフォワードする。
 */
@Controller
public class HomeController {

    @GetMapping("/")
    public String index() {
        return "forward:/index.html";
    }
}
