package org.dvsa.testing.framework.browser;

import com.microsoft.playwright.*;
import org.junit.jupiter.api.TestInstance;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class PlayWrightManager {

    private Browser browser;
    private Page page;

    public Page getPage() {
        return page;
    }


    public void selectBrowser(String browserName) {
        var playwright = Playwright.create();

        var headlessMode = "headless".equalsIgnoreCase(browserName);
        var browserType = switch (browserName.toLowerCase()) {
            case "headless", "chrome" -> playwright.chromium();
            case "firefox" -> playwright.firefox();
            default -> throw new IllegalStateException("Unsupported browser: " + browserName);
        };

        browser = browserType.launch(new BrowserType.LaunchOptions().setHeadless(headlessMode));
        // New instance for each test method.
        BrowserContext context = browser.newContext();
        page = context.newPage();
    }

    public void closeBrowserAndPage() {
        if (page != null) {
            page.close();
            page = null;
        }
        if (browser != null) {
            browser.close();
            browser = null;
        }
    }
}