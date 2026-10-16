package com.aiscam.antiscam.home.web;

import com.aiscam.antiscam.common.api.ApiResponse;
import com.aiscam.antiscam.home.service.HomeService;
import com.aiscam.antiscam.home.web.dto.Home;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/anti.scam/v1")
public class HomeController {
    private final HomeService service;
    public HomeController(HomeService service) { this.service = service; }

    @GetMapping("/home")
    public ApiResponse<Home> home() { return ApiResponse.ok(service.getHome()); }

    @GetMapping("/news")
    public ApiResponse<List<Object>> news() { return ApiResponse.ok(service.listNews()); }
}
