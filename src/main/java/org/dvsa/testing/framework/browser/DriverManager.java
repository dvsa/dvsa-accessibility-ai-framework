package org.dvsa.testing.framework.browser;

public class DriverManager {
    private static PlayWrightManager playwright;
    private static SeleniumManager selenium;

    public static Object init(String framework, String browser) {
        String activeFramework = framework.toLowerCase();

        if ("playwright".equals(activeFramework)) {
            playwright = new PlayWrightManager();
            playwright.selectBrowser(browser);
            return playwright.getPage();
        } else {
            selenium = new SeleniumManager();
            selenium.selectBrowser(browser);
            return selenium.getDriver();
        }
    }

    public static void quit() {
        if (playwright != null) playwright.closeBrowserAndPage();
        if (selenium != null) selenium.quit();
    }
}
