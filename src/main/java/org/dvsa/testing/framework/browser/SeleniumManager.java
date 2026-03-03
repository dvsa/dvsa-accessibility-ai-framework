package org.dvsa.testing.framework.browser;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.firefox.FirefoxDriver;

public class SeleniumManager {
    private WebDriver driver;

    public WebDriver getDriver() {
        return driver;
    }

    public void selectBrowser(String browserName) {
        boolean headless = browserName.toLowerCase().contains("headless");
        String baseBrowser = browserName.toLowerCase().replace("headless", "").trim();

        switch (baseBrowser) {
            case "chrome", "" -> {
                WebDriverManager.chromedriver().setup();
                ChromeOptions options = new ChromeOptions();
                if (headless) options.addArguments("--headless=new");
                driver = new ChromeDriver(options);
            }
            case "firefox" -> {
                WebDriverManager.firefoxdriver().setup();
                driver = new FirefoxDriver();
            }
            default -> throw new IllegalStateException("Unsupported Selenium browser: " + browserName);
        }
    }

    public void quit() {
        if (driver != null) {
            driver.quit();
            driver = null;
        }
    }
}
