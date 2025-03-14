package org.dvsa.testing.lib;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import org.dvsa.testing.lib.Util.PlaywrightManager;

public class ChromiumManager extends PlaywrightManager {
    @Override
    protected Browser launchBrowser(BrowserType.LaunchOptions options) {
        return playwright.chromium().launch(options);
    }
}
