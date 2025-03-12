import org.dvsa.testing.lib.SpiderCrawler;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;


public class PageCrawlerTest {

    String baseURL = "https://ssweb.qa.olcs.dev-dvsacloud.uk/auth/login/";


    @Test
    public void someTest() {
        SpiderCrawler.crawler(1, baseURL, new ArrayList<>());
    }
}