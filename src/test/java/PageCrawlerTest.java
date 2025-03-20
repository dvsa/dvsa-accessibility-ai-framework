import com.microsoft.playwright.Page;
import org.dvsa.testing.lib.SpiderCrawler;
import org.dvsa.testing.lib.Util.AnswerBot;
import org.dvsa.testing.lib.Util.PlayWrightManager;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;


public class PageCrawlerTest {

   private String baseURL;


    public String getBaseURL() {
       return baseURL;
   }

   public void setBaseURL(String baseURL) {
       this.baseURL = baseURL;
   }

    @Test
    public void someTest() {
        setBaseURL("https://ssweb.qa.olcs.dev-dvsacloud.uk/register/");
        var browser = new PlayWrightManager();
        browser.selectBrowser("chrome");
        Page page = browser.getPage();
        AnswerBot.formAutoFill(page, getBaseURL());

//        SpiderCrawler.crawler(1, getBaseURL(), new ArrayList<>());

    }
}