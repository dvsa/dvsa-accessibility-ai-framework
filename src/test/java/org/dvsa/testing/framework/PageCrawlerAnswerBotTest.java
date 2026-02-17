package org.dvsa.testing.framework;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.WaitForSelectorState;
import org.dvsa.testing.framework.bots.AnswerBot;
import org.dvsa.testing.framework.config.AppConfig;

import org.dvsa.testing.framework.jsoup.SpiderCrawler;
import org.dvsa.testing.framework.utils.BaseAccessibilityTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import java.util.*;

import static org.dvsa.testing.framework.otp.Generator.generatePin;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class PageCrawlerAnswerBotTest extends BaseAccessibilityTest {

    @Test
    public void mtsRandomAnswerAndCrawlerScanner() {
        String[] urls = AppConfig.getBaseUrls();
        String url = (urls.length > 1) ? urls[1] : urls[0];
        navigate(url);
        performLogin();
        String currentUrl = getUrl();
        AnswerBot.formAutoFill(driver, currentUrl, AppConfig.getString("domain"), true);
        SpiderCrawler.crawler(1, currentUrl, new HashSet<>(), driver);
    }

    private void performLogin() {
        if (driver instanceof Page page) {
            loginPlaywright(page);
        } else if (driver instanceof WebDriver webDriver) {
            loginSelenium(webDriver);
        }
    }

    private void loginPlaywright(Page page) {
        Locator submitButton = page.locator("input[type='submit']");
        Locator otpCodeInput = page.locator("input[id='otp-code']");
        Locator pinInput = page.locator("input[id='pin']");

        if (page.locator("//*[contains(text(),'Accept additional cookies')]").isVisible()) {
            page.click("//*[contains(text(),'Accept additional cookies')]");
        }

        page.fill("#username", AppConfig.getString("username"));
        page.fill("#password", AppConfig.getString("password"));
        page.click("input[type='submit']");

        if (otpCodeInput.isVisible()) {
            otpCodeInput.fill(generatePin(AppConfig.getString("authKey")));
        }
        if (pinInput.isVisible()) {
            pinInput.fill(generatePin(AppConfig.getString("authKey")));
        }
        if (submitButton.isVisible()) {
            submitButton.click();
        }
        syncCookies();
    }

    private void loginSelenium(WebDriver webDriver) {
        clickIfVisible(webDriver, By.xpath("//*[contains(text(),'Accept additional cookies')]"));

        webDriver.findElement(By.id("username")).sendKeys(AppConfig.getString("username"));
        webDriver.findElement(By.id("password")).sendKeys(AppConfig.getString("password"));
        clickIfVisible(webDriver, By.cssSelector("input[type='submit']"));

        String pin = generatePin(AppConfig.getString("authKey"));
        sendIfVisible(webDriver, By.id("otp-code"), pin);
        sendIfVisible(webDriver, By.id("pin"), pin);

        clickIfVisible(webDriver, By.cssSelector("input[type='submit']"));
        syncCookies();
    }

    private void syncCookies() {
        Map<String, String> jsoupCookies = new HashMap<>();
        if (driver instanceof Page p) {
            p.context().cookies().forEach(c -> jsoupCookies.put(c.name, c.value));
        } else if (driver instanceof WebDriver d) {
            d.manage().getCookies().forEach(c -> jsoupCookies.put(c.getName(), c.getValue()));
        }
    }

    private void navigate(String url) {
        if (driver instanceof Page p) {
            p.navigate(url);
        } else if (driver instanceof WebDriver d) {
            d.get(url);
        }
    }

    private String getUrl() {
        if (driver instanceof Page p) {
            return p.url();
        } else if (driver instanceof WebDriver d) {
            return d.getCurrentUrl();
        }
        return "";
    }

    private void clickIfVisible(WebDriver d, By by) {
        List<WebElement> elements = d.findElements(by);
        if (!elements.isEmpty() && elements.get(0).isDisplayed()) elements.get(0).click();
    }

    private void sendIfVisible(WebDriver d, By by, String text) {
        List<WebElement> elements = d.findElements(by);
        if (!elements.isEmpty() && elements.get(0).isDisplayed()) elements.get(0).sendKeys(text);
    }
}
