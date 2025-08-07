package com.github.istin.dmtools;

import com.github.istin.dmtools.ai.dial.BasicDialAI;
import okhttp3.Headers;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.junit.Test;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

public class NannyaTest {
    private static final String CACHE_DIRECTORY = "cache_nanny";
    private static final String NANNY_DATA_DIRECTORY = CACHE_DIRECTORY + "/nanny_data";
    public static class NannyScraper {

        private static final String BASE_URL = "http://www.nashanyanya.by";
        private static final String LIST_URL_TEMPLATE = BASE_URL + "/nyani/Grodno_bez_posrednikov_obyavleniya.asp?PAGENUMBER=%d&BACKSEARCH=1&ORDERBY=LASTLOGIN&SORTORDER=DESC";
        private static final String DETAIL_URL_PREFIX = "nanny.asp?ID=";

        private static final OkHttpClient client = new OkHttpClient();
        private static final Set<String> processedUrls = new HashSet<>();



        public static void main(String[] args) throws Exception {
            createDirectories();

            int pageNumber = 1;
            boolean hasMorePages = true;

            while (hasMorePages) {
                String listUrl = String.format(LIST_URL_TEMPLATE, pageNumber);
                System.out.println("Load list: " + listUrl);
                try {
                    Document doc = fetchDocument(listUrl);
                    if (doc == null) {
                        System.err.println("It's empty doc");
                        break;
                    }

                    // Parse the detail page links
                    Elements links = doc.select("a[href^=" + DETAIL_URL_PREFIX + "]");
                    for (Element link : links) {
                        String detailUrl = BASE_URL + "/" + link.attr("href");
                        if (!processedUrls.contains(detailUrl)) {
                            parseDetailPage(detailUrl);
                            processedUrls.add(detailUrl);
                        }
                    }

                    // Check if there's another page
                    hasMorePages = hasNextPage(doc);
                    pageNumber++;

                } catch (IOException e) {
                    System.err.println("Failed to fetch page: " + e.getMessage());
                    break;
                }
            }

            // Read nanny data files and make requests to Dial
            File nannyDataDir = new File(NANNY_DATA_DIRECTORY);
            File[] nannyFiles = nannyDataDir.listFiles((dir, name) -> name.startsWith("nanny_") && name.endsWith(".html"));

            if (nannyFiles != null) {
                BasicDialAI ai = new BasicDialAI();
                String outputFilePath = NANNY_DATA_DIRECTORY + "/output.txt";

                for (File nannyFile : nannyFiles) {
                    try {
                        String nannyInfo = new String(Files.readAllBytes(nannyFile.toPath()), StandardCharsets.UTF_8);
                        String prompt = "Я хочу найти няню для своего ребенка 1.5 года. У него проблемы с речью, поэтому было бы хорошо найти няню с опытом логопеда или кто работал с такими детьми. Поставь оценку профилю няни по моим критериям от 1 до 10. " +
                                "Твой ответ должен содержать цифры и потом объяснение почему. " +
                                "Пример: 10, Опыт работы логопедом. А также другие позитивные моменты\n\n" +
                                "Профиль няни:\n" + nannyInfo;

                        String chatResponse = ai.chat(prompt);

                        // Write the response to the output file
                        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputFilePath, true))) {
                            writer.write("Nanny ID: " + nannyFile.getName() + "\n");
                            writer.write("DIAL Response: " + chatResponse + "\n\n");
                        }

                        System.out.println("Processed and saved response for: " + nannyFile.getName());

                    } catch (IOException e) {
                        System.err.println("Error processing nanny file: " + nannyFile.getName() + " - " + e.getMessage());
                    }
                }
            }
        }

        private static void createDirectories() {
            File cacheDir = new File(CACHE_DIRECTORY);
            File nannyDataDir = new File(NANNY_DATA_DIRECTORY);
            if (!cacheDir.exists()) {
                cacheDir.mkdir();
            }
            if (!nannyDataDir.exists()) {
                nannyDataDir.mkdir();
            }
        }

        private static Document fetchDocument(String url) throws IOException {
            String cacheFileName = getCacheFileName(url);
            File cacheFile = new File(CACHE_DIRECTORY, cacheFileName);

            if (cacheFile.exists()) {
                System.out.println("Loading from cache: " + url);
                String html = new String(Files.readAllBytes(cacheFile.toPath()), StandardCharsets.UTF_8);
                return Jsoup.parse(html, url);
            }

            Headers headers = new Headers.Builder()
                    .add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                    .add("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9")
                    .add("Accept-Language", "en-US,en;q=0.9")
                    .add("Accept-Encoding", "gzip, deflate")
                    .add("Connection", "keep-alive")
                    .build();

            Request request = new Request.Builder()
                    .url(url)
                    .headers(headers)
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    byte[] bytes = response.body().bytes();
                    String html = new String(bytes, Charset.forName("Windows-1251"));

                    // Save to cache
                    Files.write(cacheFile.toPath(), html.getBytes(StandardCharsets.UTF_8));

                    return Jsoup.parse(html, url);
                } else {
                    System.err.println("Failed to fetch the URL: " + url);
                    return null;
                }
            }
        }

        private static String getCacheFileName(String url) {
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                byte[] hash = digest.digest(url.getBytes(StandardCharsets.UTF_8));
                StringBuilder hexString = new StringBuilder();
                for (byte b : hash) {
                    String hex = Integer.toHexString(0xff & b);
                    if (hex.length() == 1) hexString.append('0');
                    hexString.append(hex);
                }
                return hexString.toString() + ".html";
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException("SHA-256 algorithm not found", e);
            }
        }

        private static void parseDetailPage(String detailUrl) throws IOException {
            Document detailDoc = fetchDocument(detailUrl);
            if (detailDoc != null) {
                Element table = detailDoc.selectFirst("table#table54");
                if (table != null) {
                    String tableHtml = table.outerHtml();
                    String nannyId = extractNannyId(detailUrl);
                    saveNannyData(nannyId, detailUrl, tableHtml);
                    System.out.println("Saved data for: " + detailUrl);
                } else {
                    System.err.println("Table not found in: " + detailUrl);
                }
            } else {
                System.err.println("Details are empty for: " + detailUrl);
            }
        }

        private static String extractNannyId(String detailUrl) {
            int idIndex = detailUrl.lastIndexOf("=");
            return idIndex != -1 ? detailUrl.substring(idIndex + 1) : "unknown";
        }

        private static void saveNannyData(String nannyId, String detailUrl, String tableHtml) {
            String fileName = NANNY_DATA_DIRECTORY + "/nanny_" + nannyId + ".html";
            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                    new FileOutputStream(fileName), StandardCharsets.UTF_8))) {
                writer.write(detailUrl + "\n" + tableHtml);
            } catch (IOException e) {
                System.err.println("Error writing nanny data to file: " + e.getMessage());
            }
        }

        private static boolean hasNextPage(Document doc) {
            Elements nextPageLink = doc.select("a:contains(След)");
            return nextPageLink.size() > 0;
        }
    }

    @Test
    public void test() throws Exception {
        NannySorter.main(null);
    }

    // Define a class to hold information about a nanny
    static class Nanny {
        String id;
        int rating;
        String explanation;

        // Constructor to initialize a Nanny object
        Nanny(String id, int rating, String explanation) {
            this.id = id;
            this.rating = rating;
            this.explanation = explanation;
        }

        @Override
        public String toString() {
            // Extract the numeric part of the Nanny ID and build the URL
            String numericId = id.replaceAll("\\D+", ""); // Remove non-digit characters
            String url = "http://www.nashanyanya.by/nanny.asp?ID=" + numericId;
            return "URL: " + url + ", Rating: " + rating + ", Explanation: " + explanation;
        }
    }

    public class NannySorter {

        public static void main(String[] args) {
            String filePath = NANNY_DATA_DIRECTORY + "/output.txt";  // Path to the input file
            List<Nanny> nannies = new ArrayList<>();

            try (BufferedReader reader = Files.newBufferedReader(Paths.get(filePath))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("Nanny ID:")) {
                        // Extract the ID
                        String id = line.substring(10).trim();

                        // Read the next line for the response
                        line = reader.readLine();
                        if (line != null && line.startsWith("DIAL Response:")) {
                            // Split the line by the comma
                            String[] parts = line.split(",", 2);

                            // Check if the rating is a valid number
                            try {
                                String ratingStr = parts[0].replace("DIAL Response:", "").trim();
                                int rating = Integer.parseInt(ratingStr);
                                String explanation = (parts.length > 1) ? parts[1].trim() : "No explanation available";
                                nannies.add(new Nanny(id, rating, explanation));
                            } catch (NumberFormatException e) {
                                System.err.println("Invalid rating for Nanny ID: " + id + ". Skipping...");
                            }
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

            // Sort the nannies in descending order by rating
            Collections.sort(nannies, Comparator.comparingInt(n -> -n.rating));

            // Print each nanny in the sorted list
            for (Nanny nanny : nannies) {
                System.out.println(nanny);
            }
        }
    }
}