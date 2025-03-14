import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import org.dvsa.testing.lib.ChromiumManager;
import org.dvsa.testing.lib.SpiderCrawler;
import org.dvsa.testing.lib.Util.PlaywrightManager;
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
        SpiderCrawler.crawler(1, getBaseURL(), new ArrayList<>());
    }

    public static String getWordAfterWWW(String url) {
        url = url.replaceFirst("https?://", ""); // Remove "http://" or "https://"
        String[] parts = url.split("\\."); // Split by "."

        if (parts.length > 1 && parts[0].equalsIgnoreCase("www")) {
            return parts[1]; // Return the word after "www"
        }
        return "Not found";
    }
}