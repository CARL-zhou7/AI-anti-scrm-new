package com.aiscam.antiscam.home.service;

import com.aiscam.antiscam.home.web.dto.Home;
import com.aiscam.antiscam.home.web.dto.NewsItem;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class HomeService {
    private static final List<NewsItem> NEWS = List.of(
            new NewsItem("江汉警方拦截两起投资诈骗现金交付，止损20万元", "真实案例", "武汉市公安局", "2026-11-04",
                    "https://gaj.wuhan.gov.cn/jmzx/jwdt/202611/t20261104_2873716.html"),
            new NewsItem("武汉警方把反诈课堂搬到江汉路步行街", "反诈新闻", "武汉市公安局", "2026-11-04",
                    "https://gaj.wuhan.gov.cn/jmzx/jqfb/202611/t20261104_2873718.html"),
            new NewsItem("双十一防骗提醒：警惕虚假优惠和冒充客服退款", "防骗提醒", "泰州市公安局", "2026-10-28",
                    "https://gaj.taizhou.gov.cn/jwzx/aqff/art/2026/art_a78c42fb75634cbfb998d998225ec0d9.html"));

    public Home getHome() {
        return new Home("AI反诈情景推演平台", "观看AI情景演绎，结合逐轮解说、风险与复盘学习反诈知识。",
                NEWS.isEmpty() ? "EMPTY" : "AVAILABLE", List.of(Home.Entry.CONFIGURATION, Home.Entry.HISTORY));
    }

    public List<NewsItem> listNews() { return NEWS; }
}
