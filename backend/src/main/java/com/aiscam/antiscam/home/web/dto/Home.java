package com.aiscam.antiscam.home.web.dto;

import java.util.List;

public record Home(String title, String introduction, String newsState, List<Entry> entries) {
    public enum Entry { CONFIGURATION, HISTORY }
}
