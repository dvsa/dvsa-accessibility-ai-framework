package org.dvsa.testing.lib.Util;

import com.microsoft.playwright.*;

public class PlaywrightSetUp {

    public static Page page(){
        try (Playwright playwright = Playwright.create()) {
            // Launch chromium, firefox or webkit.
            Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
            return browser.newPage();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
