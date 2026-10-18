package com.aiscam.antiscam.agent;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Minimal {@code {{name}}} substitution. A missing or an unused variable fails fast so template and code cannot
 * drift apart silently. Values are inserted verbatim and treated as untrusted text by the prompts.
 */
public final class PromptRenderer {
    private static final Pattern VARIABLE = Pattern.compile("\\{\\{([a-z_]+)}}");
    private PromptRenderer() {}

    public static String render(String template, Map<String, String> variables) {
        Set<String> used = new HashSet<>();
        Matcher matcher = VARIABLE.matcher(template);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String name = matcher.group(1);
            String value = variables.get(name);
            if (value == null) throw new IllegalArgumentException("Missing prompt variable " + name);
            used.add(name);
            matcher.appendReplacement(out, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(out);
        for (String name : variables.keySet()) {
            if (!used.contains(name)) throw new IllegalArgumentException("Unused prompt variable " + name);
        }
        return out.toString();
    }
}
