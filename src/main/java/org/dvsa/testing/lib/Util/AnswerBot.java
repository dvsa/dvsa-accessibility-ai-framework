package org.dvsa.testing.lib.Util;

import com.microsoft.playwright.ElementHandle;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static org.dvsa.testing.lib.Util.AXEScanner.scan;


public class AnswerBot {
    static List<ElementHandle> inputElements;
    private static final Logger LOGGER = LogManager.getLogger(AnswerBot.class);

    public static void formAutoFill(Page page, String url) {
        page.navigate(url);
        inputElements = page.querySelectorAll("input");

        int attempts = 0; // Keep track of attempts
        int maxAttempts = 10; // Prevent infinite loops

        while (attempts < maxAttempts) {
            try {
                inputElements = page.querySelectorAll("input");

                for (ElementHandle element : inputElements) {
                    if (element == null) continue;

                    String type = element.getAttribute("type");
                    boolean isDisabled = element.isDisabled();
                    boolean isHidden = element.isHidden();

                    if (!isDisabled && !isHidden && type != null) {
                        Locator locator = page.locator("input[name='" + element.getAttribute("name") + "']");

                        switch (type) {
                            case "radio" -> {
                                if (!element.isChecked()) {
                                    List<Locator> radioButtons = locator.all();
                                    boolean clicked = false;

                                    if (!radioButtons.isEmpty()) {
                                        for (Locator radioButton : radioButtons) {
                                            String label = radioButton.getAttribute("aria-label");
                                            String value = radioButton.getAttribute("value");

                                            if ((label != null && label.toLowerCase().contains("no")) ||
                                                    (value != null && value.equalsIgnoreCase("n"))) {

                                                if (!radioButton.isChecked()) {
                                                    radioButton.click();
                                                    clicked = true;
                                                    break;
                                                }
                                            }
                                        }
                                        if (!clicked) {
                                            int randomNum = ThreadLocalRandom.current().nextInt(0, radioButtons.size());
                                            locator.nth(randomNum).click();
                                            clicked = true;
                                        }
                                    } else {
                                        LOGGER.warn("No radio buttons found.");
                                    }
                                }
                            }
                            case "text" -> {
                                String fieldSnapshot = locator.ariaSnapshot();
                                if (fieldSnapshot != null && fieldSnapshot.toLowerCase().contains("email") && locator.inputValue().isEmpty()) {
                                    locator.fill("testing.crawler@dvsa.gov.uk");
                                } else if (fieldSnapshot != null && fieldSnapshot.toLowerCase().contains("password") && locator.inputValue().isEmpty()) {
                                    locator.fill(RandomStringUtils.randomAlphabetic(9).toLowerCase());
                                } else if (fieldSnapshot != null && fieldSnapshot.toLowerCase().contains("username") && locator.inputValue().isEmpty()) {
                                    locator.fill(RandomStringUtils.randomAlphabetic(9).toLowerCase());
                                } else if (fieldSnapshot != null && fieldSnapshot.toLowerCase().contains("phone") && locator.inputValue().isEmpty()) {
                                    String phoneNumber = "07" + ThreadLocalRandom.current().nextInt(100000000, 999999999);
                                    locator.fill(phoneNumber);
                                } else if (fieldSnapshot != null && fieldSnapshot.toLowerCase().contains("postcode") && locator.inputValue().isEmpty()) {
                                    locator.fill("NG2 1AY");
                                } else if (fieldSnapshot != null && fieldSnapshot.toLowerCase().contains("city") && locator.inputValue().isEmpty()) {
                                    locator.fill(RandomStringUtils.randomAlphabetic(9).toLowerCase());
                                } else if (fieldSnapshot != null && fieldSnapshot.toLowerCase().contains("code") && locator.inputValue().isEmpty()) {
                                    locator.fill(String.valueOf(ThreadLocalRandom.current().nextInt(0, 99999)));
                                } else {
                                    locator.fill(RandomStringUtils.randomAlphabetic(9).toLowerCase());
                                }
                            }
                            case "checkbox" -> element.click();
                            default -> System.out.println("Unsupported input type: " + type);
                        }
                    }
                }
                scan(page);

                // Click the submit button after processing all elements
                Locator submitButton = page.locator("button, input[type='submit']").filter(new Locator.FilterOptions().setHasText("Continue"));

                if (submitButton.isVisible() && submitButton.count() > 0){
                    submitButton.first().click();
                    LOGGER.info("Form submitted successfully.");
                } else {
                    LOGGER.info("Submit button not found.");
                }

            } catch (PlaywrightException e) {
                LOGGER.info("Encountered a stale element issue, retrying... Attempt {}", attempts + 1);
                attempts++; // Increment retry attempts
            }
        }
    }
}
