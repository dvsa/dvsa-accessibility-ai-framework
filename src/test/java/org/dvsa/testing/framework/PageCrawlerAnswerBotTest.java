package org.dvsa.testing.framework;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.WaitForSelectorState;
import org.dvsa.testing.framework.bots.AnswerBot;
import org.dvsa.testing.framework.config.AppConfig;

import org.dvsa.testing.framework.jsoup.SpiderCrawler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import java.util.*;

import static org.dvsa.testing.framework.axe.AXEScanner.scan;
import static otp.Generator.generatePin;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class PageCrawlerAnswerBotTest extends BaseAccessibilityTest {

    //Commented out unit tests after moving to framework
    //Use as examples

//    @Test
    public void mtsRandomAnswerAndCrawlerScanner() {
        String[] urls = AppConfig.getBaseUrls();
        String url = (urls.length > 1) ? urls[1] : urls[0];
        navigate(url);
        performLogin();
        AnswerBot.formAutoFill(driver, getUrl(), AppConfig.getString("domain"), true);
        SpiderCrawler.crawler(1, getUrl(), new HashSet<>(), driver);
    }

//    @Test
    public void volRandomAnswerAndCrawlerScanner() {
        navigate("http://ssweb.qa.olcs.dev-dvsacloud.uk/");
        performLogin();
        String currentUrl = getUrl();
        AnswerBot.formAutoFill(driver, currentUrl, "qa.olcs.dev", true);
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
        page.getByText("Site information").click();
        scan(page);
        page.locator("input[id='site_number']").fill("VTS001084");
        submitButton.click();
        page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Site review")).click();
        scan(page);
        Locator link = page.locator("#site-assessment-action-link");
        link.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
        link.click();
        syncCookies();
    }

    private void loginSelenium(WebDriver webDriver) {
        clickIfVisible(webDriver, By.xpath("//*[contains(text(),'Accept additional cookies')]"));

        webDriver.findElement(By.id("username")).sendKeys(AppConfig.getString("username"));
        webDriver.findElement(By.id("password")).sendKeys(AppConfig.getString("password"));
        clickIfVisible(webDriver, By.cssSelector("input[type='submit']"));

        sendIfVisible(webDriver, By.id("otp-code"), generatePin(AppConfig.getString("authKey")));
        sendIfVisible(webDriver, By.id("pin"), generatePin(AppConfig.getString("authKey")));

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
