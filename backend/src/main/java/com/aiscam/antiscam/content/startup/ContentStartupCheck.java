package com.aiscam.antiscam.content.startup;

import com.aiscam.antiscam.content.service.ContentService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.content.startup-check.enabled", havingValue = "true", matchIfMissing = true)
public class ContentStartupCheck implements ApplicationRunner {
    private final ContentService content;
    public ContentStartupCheck(ContentService content) { this.content = content; }

    @Override
    public void run(ApplicationArguments args) { content.verify(); }
}
