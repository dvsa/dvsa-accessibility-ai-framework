package org.dvsa.testing.framework.browser;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitForSelectorState;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class PlayWrightWaits {
    private static final Logger LOGGER = LogManager.getLogger(PlayWrightWaits.class);

    public static void waitAndEnterText(Page page, Locator locator, String inputText) {
        int timeoutMills = 5000; 
        long startTime = System.currentTimeMillis();

        while (System.currentTimeMillis() - startTime < timeoutMills) {
            try {
                String fieldSnapshot = locator.getAttribute("name");
                
                if (fieldSnapshot != null) {
                    // Wait for element to be ready without excessive delays
                    locator.waitFor(new Locator.WaitForOptions()
                        .setState(WaitForSelectorState.VISIBLE)
                        .setTimeout(2000));
                    
                    String inputValue = locator.inputValue();
                    if (inputValue.isEmpty()) {
                        locator.fill(inputText);
                        return;
                    } else {
                        locator.clear();
                        locator.fill(inputText);
                        return;
                    }
                }
                
                page.waitForTimeout(100); 
            } catch (Exception e) {
                LOGGER.warn("Retry text input: {}", e.getMessage());
                page.waitForTimeout(200);
            }
        }
        throw new RuntimeException("textbox not found or not fillable within timeout");
    }
}