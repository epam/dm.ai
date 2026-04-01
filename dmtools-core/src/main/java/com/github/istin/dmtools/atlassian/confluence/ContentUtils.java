// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.atlassian.confluence;

import com.github.istin.dmtools.atlassian.confluence.model.Content;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.File;
import java.io.IOException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ContentUtils {

    private static final Logger logger = LogManager.getLogger(ContentUtils.class);

    public interface UrlToImageFile {

        boolean isValidImageUrl(String url) throws IOException;

        File convertUrlToFile(String href) throws Exception;
    }

    public static String convertLinksToImages(BasicConfluence basicConfluence, Content content, UrlToImageFile... urlToImageFiles) throws Exception {
        Document doc = Jsoup.parse(content.getStorage().getValue());

        // Select all anchor tags with href attributes
        Elements anchorTags = doc.select("a[href]");

        // Iterate over each anchor tag
        for (Element anchor : anchorTags) {
            String href = anchor.attr("href");
            logger.info("image url : " + href);
            String text = anchor.text();

            for (UrlToImageFile urlToImageFile : urlToImageFiles) {
                // Check if the href can be converted to file
                if ((urlToImageFile.isValidImageUrl(href)) && !isAlreadyWrapped(anchor)) {
                    // Get the file name from the URL
                    File file = urlToImageFile.convertUrlToFile(href);
                    if (file != null) {
                        basicConfluence.attachFileToPage(content.getId(), file);
                        anchor.after(new Element("p").html("<ac:image ac:height=\"250\"><ri:attachment ri:filename=\"" + file.getName() + "\" /></ac:image>"));
                    }
                }
            }
        }

        // Return the modified HTML
        return doc.body().html();
    }

    private static boolean isAlreadyWrapped(Element anchor) {
        // Check if the parent of the anchor tag is a <p> tag and contains an <ac:image> tag
        Element element = anchor.nextElementSibling();
        return element != null && "p".equals(element.tagName()) && element.html().contains("<ac:image");
    }

}
