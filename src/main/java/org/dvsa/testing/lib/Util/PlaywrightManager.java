package org.dvsa.testing.lib.Util;

import com.microsoft.playwright.*;

public abstract class PlaywrightManager {
    protected Playwright playwright;
    protected Browser browser;
    protected BrowserContext context;
    protected Page page;

    public void initialize(BrowserType.LaunchOptions options) {
        playwright = Playwright.create();
        browser = launchBrowser(options);
        context = browser.newContext();
        page = context.newPage();
    }

    protected abstract Browser launchBrowser(BrowserType.LaunchOptions options);

    public Page getPage() {
        return page;
    }

    public void close() {
        if (context != null) context.close();
        if (browser != null) browser.close();
        if (playwright != null) playwright.close();
    }
}