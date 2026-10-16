package com.aiscam.antiscam.home.service;

import com.aiscam.antiscam.home.web.dto.Home;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class HomeService {
    public Home getHome() {
        return new Home("AI反诈情景推演平台", "观看AI情景演绎，结合逐轮解说、风险与复盘学习反诈知识。",
                "EMPTY", List.of(Home.Entry.CONFIGURATION, Home.Entry.HISTORY));
    }

    public List<Object> listNews() { return List.of(); }
}
