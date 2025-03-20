package org.dvsa.testing.lib.Util;

import com.microsoft.playwright.ElementHandle;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;


public class AnswerBot {
    static List<ElementHandle> inputElements;
    private static final Logger LOGGER = LogManager.getLogger(AnswerBot.class);

    public static void formAutoFill(Page page, String url) {
        page.navigate(url);
        inputElements = page.querySelectorAll("input");

        int attempts = 0; // Keep track of attempts

        while (attempts < inputElements.size()) {
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
                                String inputValue = locator.inputValue();

                                if (inputValue == null || inputValue.isEmpty()) {
                                    String fieldLabel = Optional.ofNullable(locator.getAttribute("name"))
                                            .or(() -> Optional.ofNullable(locator.getAttribute("aria-label")))
                                            .or(() -> Optional.ofNullable(locator.getAttribute("placeholder")))
                                            .orElse("")
                                            .toLowerCase();

                                    if (fieldLabel.contains("email")) {
                                        locator.fill("testing.crawler@dvsa.gov.uk");

                                    } else if (fieldLabel.contains("phone") || fieldLabel.contains("contact") || fieldLabel.matches(".*\\d.*")) {
                                        String phoneNumber = "07" + ThreadLocalRandom.current().nextInt(100000000, 999999999);
                                        locator.fill(phoneNumber);

                                    } else if (fieldLabel.contains("first name") || fieldLabel.contains("given name")) {
                                        String firstName = RandomStringUtils.randomAlphabetic(6, 10);
                                        locator.fill(firstName);
                                    } else if (fieldLabel.contains("last name") || fieldLabel.contains("surname")) {
                                        String lastName = RandomStringUtils.randomAlphabetic(6, 10);
                                        locator.fill(lastName);
                                    } else if (fieldLabel.contains("middle name")) {
                                        String middleName = RandomStringUtils.randomAlphabetic(4, 8);
                                        locator.fill(middleName);
                                    } else if (fieldLabel.contains("name")) {
                                        String randomName = RandomStringUtils.randomAlphabetic(6, 12);
                                        locator.fill(randomName);

                                    } else if (fieldLabel.contains("postcode") || fieldLabel.contains("zip")) {
                                        locator.fill("NG2 1AY");

                                    } else {
                                        String randomText = RandomStringUtils.randomAlphabetic(9).toLowerCase();
                                        locator.fill(randomText);
                                        locator.press("Tab");
                                    }
                                }
                            }
                            case "checkbox" -> {
                                List<Locator> checkboxes = locator.all();
                                if (!checkboxes.isEmpty()) {
                                    int randomNum = ThreadLocalRandom.current().nextInt(0, checkboxes.size());
                                    locator.nth(randomNum).click();
                                } else {
                                    LOGGER.warn("No checkboxes buttons found");
                                }
                            }
                            default -> LOGGER.warn("Unsupported input type: {}", type);
                        }
                    }
                }

                // Click the submit button after processing all elements
                Locator submitButton = page.locator("//*[@name='form-actions[submit]']");
                if (submitButton.isVisible()) {
                    submitButton.click();
                    LOGGER.info("Form submitted successfully.");
                } else {
                    LOGGER.info("Submit button not found.");
                }

            } catch (PlaywrightException e) {
                LOGGER.info("Encountered a stale element issue, retrying... Attempt " + (attempts + 1));
                attempts++; // Increment retry attempts
            }
        }
    }
}
