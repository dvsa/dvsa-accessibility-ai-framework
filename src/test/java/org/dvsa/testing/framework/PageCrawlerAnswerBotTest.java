package org.dvsa.testing.framework;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.Cookie;
import com.typesafe.config.*;
import org.dvsa.testing.framework.browser.PlayWrightManager;
import org.dvsa.testing.framework.jsoup.SpiderCrawler;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.dvsa.testing.framework.bots.AnswerBot.formAutoFill;
import static org.dvsa.testing.framework.otp.Generator.generatePin;


public class PageCrawlerAnswerBotTest {

    private static final Config config = ConfigFactory.defaultApplication();
    private String baseURL;
    private static Map<String, String> cookies;

    public static Map<String, String> getCookies() {
        return cookies;
    }

    public static void setCookies(Map<String, String> cookies) {
        PageCrawlerAnswerBotTest.cookies = cookies;
    }

    public String getBaseURL() {
        return baseURL;
    }

    public void setBaseURL(String baseURL) {
        this.baseURL = baseURL;
    }

    @Test
    public void mtsRandomAnswerAndCrawlerScanner() throws IOException {
        setBaseURL(config.getString("baseURL"));

        var browser = new PlayWrightManager();
        browser.selectBrowser(System.getProperty("browser"));
        Page page = browser.getPage();
        page.navigate(getBaseURL());

        page.locator("input[id='username']").fill(config.getString("username"));
        page.locator("input[id='password']").fill(config.getString("password"));
        page.locator("input[type='submit']").click();
        page.locator("input[id='pin']").fill(generatePin(config.getString("authKey")));
        page.locator("input[type='submit']").click();

        List<Cookie> playwrightCookies = page.context().cookies();
        Map<String, String> jsoupCookies = new HashMap<>();

        for (Cookie cookie : playwrightCookies) {
            jsoupCookies.put(cookie.name, cookie.value);
        }

        setCookies(jsoupCookies);

        formAutoFill(page, page.url());
        SpiderCrawler.crawler(1, page.url(), new ArrayList<>(), page);
    }
}