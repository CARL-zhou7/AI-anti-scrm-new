package com.aiscam.antiscam.content.web.dto;

import java.util.List;

public record PageView<T>(List<T> items, String nextCursor, boolean hasMore) {}
