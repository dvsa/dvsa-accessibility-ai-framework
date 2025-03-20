package org.dvsa.testing.lib.Util;

import com.microsoft.playwright.*;
import org.junit.jupiter.api.TestInstance;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class PlayWrightManager {

    // Shared browser instance for all tests in the class.
    private Browser browser;
    // New instance for each test method.
    private BrowserContext context;
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
        context = browser.newContext();
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