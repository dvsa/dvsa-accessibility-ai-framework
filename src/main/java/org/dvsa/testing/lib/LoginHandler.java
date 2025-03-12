package org.dvsa.testing.lib;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.util.HashMap;
import java.util.Map;

public class LoginHandler {
    private static final Logger LOGGER = LogManager.getLogger(LoginHandler.class);

    public static Document login(String loginUrl, String username, String password) {
        try {
            // Fetch login page and get cookies
            Connection.Response loginPageResponse = Jsoup.connect(loginUrl)
                    .method(Connection.Method.GET)
                    .execute();

            //Extract cookies
            Map<String, String> cookies = loginPageResponse.cookies();

            //Create login form data
            Map<String, String> formData = new HashMap<>();
            formData.put("username", username);
            formData.put("password", password);

            //Submit the login form
            Connection.Response response = Jsoup.connect(loginUrl)
                    .cookies(cookies)
                    .data(formData)
                    .method(Connection.Method.POST)
                    .execute();

            return Jsoup.parse(response.body());
        } catch (Exception e) {
            LOGGER.info("Login failed: {}", e.getMessage());
            return null;
        }
    }

    public static Document changePassword(String loginUrl, String sessionCookie, String oldPassword, String newPassword) {
        try {
            //Prepare change password form
            Map<String, String> formData = new HashMap<>();
            formData.put("oldPassword", oldPassword);
            formData.put("newPassword", newPassword);
            formData.put("confirm_password", newPassword);

            //Submit form
            Connection.Response response = Jsoup.connect(loginUrl)
                    .cookie("session", sessionCookie)
                    .data(formData)
                    .method(Connection.Method.POST)
                    .execute();

            //return response
            return response.parse();
        } catch (Exception e) {
            LOGGER.info("Failed to change password: {}", e.getMessage());
            return null;
        }
    }
}