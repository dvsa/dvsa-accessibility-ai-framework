package org.dvsa.testing.framework.browser;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitForSelectorState;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class PlayWrightWaits {
    private static final Logger LOGGER = LogManager.getLogger(PlayWrightWaits.class);

    public static void waitAndEnterText(Page page, Locator locator, String inputText) {
        int timeoutMills = 10000;
        int pollInterval = 200;
        long startTime = System.currentTimeMillis();

        while (System.currentTimeMillis() - startTime < timeoutMills) {
            try {
                String fieldSnapshot = locator.getAttribute("name");
                String inputValue = locator.inputValue();

                if (fieldSnapshot != null && inputValue.isEmpty()) {
                    page.waitForTimeout(1000);
                    locator.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.ATTACHED));
                    locator.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
                    locator.fill(inputText);
                    page.keyboard().press("Tab");
                    return;
                }
                page.reload();
                Thread.sleep(pollInterval);
            } catch (Exception e) {
                LOGGER.error(e);
            }
        }
        throw new RuntimeException("textbox not found ");
    }
}