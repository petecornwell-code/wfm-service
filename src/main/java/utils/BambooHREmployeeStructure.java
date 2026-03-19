package utils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Standalone app that connects to the BambooHR API and retrieves
 * the structure (field metadata) of an Employee.
 *
 * Usage:
 *   javac utils/BambooHREmployeeStructure.java
 *   java utils.BambooHREmployeeStructure
 *
 * Configure the two constants below before running.
 */
public class BambooHREmployeeStructure {

    // ── Configuration ────────────────────────────────────────────
    // Company subdomain, e.g. "acme" if you log in at acme.bamboohr.com
    private static final String COMPANY_DOMAIN = "YOUR_COMPANY_DOMAIN";

    // API key generated from BambooHR (Account → API Keys)
    private static final String API_KEY = "YOUR_API_KEY";
    // ─────────────────────────────────────────────────────────────

    private static final String BASE_URL =
            "https://api.bamboohr.com/api/gateway.php/" + COMPANY_DOMAIN;

    public static void main(String[] args) throws Exception {
        String domain = COMPANY_DOMAIN;
        String apiKey = API_KEY;

        // Allow overriding via command-line args: <domain> <apiKey>
        if (args.length >= 2) {
            domain = args[0];
            apiKey = args[1];
        } else if (COMPANY_DOMAIN.equals("YOUR_COMPANY_DOMAIN")
                || API_KEY.equals("YOUR_API_KEY")) {
            System.err.println("ERROR: Set COMPANY_DOMAIN and API_KEY in the source,");
            System.err.println("       or pass them as arguments: java utils.BambooHREmployeeStructure <domain> <apiKey>");
            System.exit(1);
        }

        String baseUrl = "https://api.bamboohr.com/api/gateway.php/" + domain;

        System.out.println("=== BambooHR Employee Field Structure ===\n");

        // 1. Fetch employee field metadata
        System.out.println("--- Employee Fields ---\n");
        String fieldsJson = get(baseUrl + "/v1/meta/fields/", apiKey);
        printFormatted(fieldsJson);

        // 2. Fetch tabular (table) field metadata
        System.out.println("\n--- Tabular Fields ---\n");
        String tablesJson = get(baseUrl + "/v1/meta/tables/", apiKey);
        printFormatted(tablesJson);

        // 3. Fetch list field metadata (dropdowns / enumerations)
        System.out.println("\n--- List Fields ---\n");
        String listsJson = get(baseUrl + "/v1/meta/lists/", apiKey);
        printFormatted(listsJson);
    }

    /**
     * Performs an authenticated GET request and returns the response body.
     */
    private static String get(String urlString, String apiKey) throws Exception {
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Accept", "application/json");

        // BambooHR uses Basic Auth: apiKey as username, "x" as password
        String credentials = apiKey + ":x";
        String encoded = Base64.getEncoder()
                .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        conn.setRequestProperty("Authorization", "Basic " + encoded);

        int status = conn.getResponseCode();
        if (status != 200) {
            String error = readStream(conn.getErrorStream() != null
                    ? new BufferedReader(new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8))
                    : new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8)));
            throw new RuntimeException(
                    "HTTP " + status + " from " + urlString + "\n" + error);
        }

        return readStream(
                new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8)));
    }

    private static String readStream(BufferedReader reader) throws Exception {
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line).append('\n');
        }
        reader.close();
        return sb.toString();
    }

    /**
     * Simple JSON pretty-printer (no external libraries required).
     * Handles basic JSON arrays and objects with indentation.
     */
    private static void printFormatted(String json) {
        int indent = 0;
        boolean inString = false;
        boolean escaped = false;

        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);

            if (escaped) {
                System.out.print(c);
                escaped = false;
                continue;
            }

            if (c == '\\' && inString) {
                System.out.print(c);
                escaped = true;
                continue;
            }

            if (c == '"') {
                inString = !inString;
                System.out.print(c);
                continue;
            }

            if (inString) {
                System.out.print(c);
                continue;
            }

            switch (c) {
                case '{':
                case '[':
                    System.out.print(c);
                    System.out.println();
                    indent++;
                    printIndent(indent);
                    break;
                case '}':
                case ']':
                    System.out.println();
                    indent--;
                    printIndent(indent);
                    System.out.print(c);
                    break;
                case ',':
                    System.out.print(c);
                    System.out.println();
                    printIndent(indent);
                    break;
                case ':':
                    System.out.print(": ");
                    break;
                case ' ':
                case '\n':
                case '\r':
                case '\t':
                    // skip whitespace outside strings
                    break;
                default:
                    System.out.print(c);
                    break;
            }
        }
        System.out.println();
    }

    private static void printIndent(int level) {
        for (int i = 0; i < level; i++) {
            System.out.print("  ");
        }
    }
}
