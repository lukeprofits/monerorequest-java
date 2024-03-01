package monerorequest;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.text.SimpleDateFormat;
import java.net.URL;
import java.net.URI;
import java.util.Random;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.ZoneId;
import java.io.ByteArrayInputStream;
import java.util.Base64;
import java.util.zip.GZIPInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.zip.GZIPOutputStream;


/**
 * Utility class for handling JSON operations, including parsing JSON strings into Maps and converting Maps to JSON strings.
 */
class JsonUtils {
    // Parses a JSON string into a Map
    public static Map<String, Object> parseJson(String jsonStr) {
        Map<String, Object> result = new HashMap<>();
        String jsonContent = jsonStr.trim().substring(1, jsonStr.length() - 1).trim(); // Remove surrounding braces
        String[] keyValuePairs = jsonContent.split(",");

        Pattern numberPattern = Pattern.compile("-?\\d+(\\.\\d+)?");

        for (String pair : keyValuePairs) {
            String[] parts = pair.split(":", 2);
            String key = parts[0].trim().replace("\"", "");
            String value = parts[1].trim();
            Object processedValue;

            if ("null".equalsIgnoreCase(value)) {
                processedValue = null;
            } else if ("true".equalsIgnoreCase(value)) {
                processedValue = Boolean.TRUE;
            } else if ("false".equalsIgnoreCase(value)) {
                processedValue = Boolean.FALSE;
            } else if (numberPattern.matcher(value).matches()) {
                processedValue = Double.parseDouble(value); // Use Double for number representation
            } else if (value.startsWith("\"") && value.endsWith("\"")) {
                processedValue = value.substring(1, value.length() - 1);
            } else {
                processedValue = value;
            }

            result.put(key, processedValue);
        }

        return result;
    }


    // Converts a Map to a JSON string
    public static String mapToJson(Map<String, Object> map) {
        StringBuilder jsonBuilder = new StringBuilder("{");
        boolean first = true;

        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) {
                jsonBuilder.append(",");
            }
            jsonBuilder.append("\"").append(escapeString(entry.getKey())).append("\":");

            Object value = entry.getValue();
            if (value instanceof String) {
                jsonBuilder.append("\"").append(escapeString((String) value)).append("\"");
            } else {
                jsonBuilder.append(value);
            }

            first = false;
        }

        jsonBuilder.append("}");
        return jsonBuilder.toString();
    }


    // Escapes special characters in a JSON string
    private static String escapeString(String str) {
        return str.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }


}


/**
 * Contains functions for creating & reading Monero Payment Requests
 */
class MoneroPaymentRequest {
    public static void printMoneroLogo() {
        String logo = """
                    k                                     d
                    0Kx                                 dOX
                    KMWKx                             dONMN
                    KMMMWKx                         dONMMMN
                    KMMMMMWKk                     d0NMMMMMN
                    KMMMMMMMMXk                 dKWMMMMMMMN
                    KMMMMMMMMMMXk             dKWMMMMMMMMMN
                    KMMMMMMMMMMMMXk         xKWMMMMMMMMMMMN
                    KMMMMMXkNMMMMMMXk     dKWMMMMMW00MMMMMN
                    KMMMMM0  xNMMMMMMXk dKWMMMMMWOc dMMMMMN
                    KMMMMM0    xNMMMMMMNWMMMMMWOc   dMMMMMN
                    KMMMMM0      dXMMMMMMMMMNkc     dMMMMMN
                    KMMMMM0        oXMMMMMNx;       dMMMMMN
KMMMMMMMMMMMMMMMMMMMMMMMMM0          dNMWk:         dMMMMMMMMMMMMMMMMMMMMMMMMK
KMMMMMMMMMMMMMMMMMMMMMMMMM0            o            dMMMMMMMMMMMMMMMMMMMMMMMMK
KMMMMMMMMMMMMMWNNNNNNNNNNNO                         oNNNNNNNNNNNNMMMMMMMMMMMMO""";
        System.out.println(logo);
    }


    public static Map<String, Object> read(String moneroPaymentRequest) {
        return Decode.moneroPaymentRequest(moneroPaymentRequest);
    }


    public static String create(String customLabel,
                                String sellersWallet,
                                String currency,
                                String amount,
                                String paymentId,
                                String startDate,
                                Integer daysPerBillingCycle,
                                Integer numberOfPayments,
                                String changeIndicatorUrl
                                //, String version
    ) throws IOException {
        // Defaults To Use
        String version = "1"; // Can disable this and set "version" as an argument above if you want.
        String finalPaymentId = paymentId != null && !paymentId.isEmpty() ? paymentId : makeRandomPaymentId();
        String finalStartDate = startDate != null && !startDate.isEmpty() ? startDate : convertToTruncatedRFC3339(ZonedDateTime.now());

        // Make sure all arguments are valid
        if (!Check.name(customLabel)) {throw new IllegalArgumentException("customLabel is not a string.");}

        boolean allowStandard = true;
        boolean allowIntegratedAddress = true;
        boolean allowSubaddress = false;
        if (!Check.wallet(sellersWallet, allowStandard, allowIntegratedAddress, allowSubaddress)) {throw new IllegalArgumentException("sellersWallet is not valid");}
        if (!Check.currency(currency)) {throw new IllegalArgumentException("Currency is not a string, or is not supported.");}
        if (!Check.amount(amount)) {throw new IllegalArgumentException("amount is not a string, or invalid characters in amount. Amount can only contain ',', '.', and numbers.");}
        if (!Check.paymentID(finalPaymentId)) {throw new IllegalArgumentException("paymentId is not a string, is not exactly 16 characters long, or contains invalid character(s).");}
        if (!Check.startDate(finalStartDate)) {throw new IllegalArgumentException("startDate is not a string, or is not in the correct format.");}
        if (!Check.daysPerBillingCycle(daysPerBillingCycle)) {throw new IllegalArgumentException("billingCycle is not an integer, or the value set was lower than 0.");}
        if (!Check.numberOfPayments(numberOfPayments)) {throw new IllegalArgumentException("numberOfPayments is not an integer, or is less than 1.");}
        if (!Check.changeIndicatorUrl(changeIndicatorUrl)) {throw new IllegalArgumentException("changeIndicatorUrl is not a string, or is not a valid URL.");}

        Map<String, Object> jsonData = new HashMap<>();
        jsonData.put("custom_label", customLabel);
        jsonData.put("sellers_wallet", sellersWallet);
        jsonData.put("currency", currency);
        jsonData.put("amount", amount);
        jsonData.put("payment_id", finalPaymentId);
        jsonData.put("start_date", finalStartDate);
        jsonData.put("days_per_billing_cycle", daysPerBillingCycle);
        jsonData.put("number_of_payments", numberOfPayments);
        jsonData.put("change_indicator_url", changeIndicatorUrl);

        // process data to create code
        return Encode.moneroPaymentRequestFromJson(jsonData, version);
    }


    public static String makeRandomPaymentId() {
        StringBuilder paymentId = new StringBuilder();
        Random random = new Random();
        String hexChars = "0123456789abcdef";

        for (int i = 0; i < 16; i++) {
            int randomIndex = random.nextInt(hexChars.length());
            paymentId.append(hexChars.charAt(randomIndex));
        }
        return paymentId.toString();
    }


    public static String convertToTruncatedRFC3339(ZonedDateTime zonedDateTime) {
        // Convert the input datetime to UTC
        ZonedDateTime utcDateTime = zonedDateTime.withZoneSameInstant(ZoneId.of("UTC"));

        // Define the formatter
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");

        // Format the datetime object to a string
        return utcDateTime.format(formatter);
    }


}


/**
 * Handles encoding of data into a format suitable for Monero Payment Requests.
 */
class Encode {
    public static String moneroPaymentRequestFromJson(Map<String, Object> jsonData, String version) throws IOException {
        String encodedStr = "";

        if (version.equals("1")) {
            encodedStr = Encode.v1MoneroPaymentRequest(jsonData);
        }

        // Add the Monero Payment Request identifier & version number
        String moneroPaymentRequest = String.format("monero-request:%s:%s", version, encodedStr);

        if (encodedStr.isEmpty()) {
            throw new IllegalArgumentException("Invalid input");
        }
        return moneroPaymentRequest;
    }


    // VERSIONS ////////////////////////////////////////////////////////////////////////////////////////////////////////
    public static String v1MoneroPaymentRequest(Map<String, Object> jsonData) throws IOException {
        // Convert the JSON data to a string
        String jsonStr = JsonUtils.mapToJson(jsonData);
        // Compress the string using gzip compression
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try (GZIPOutputStream gzipOutputStream = new GZIPOutputStream(byteArrayOutputStream)) {
            gzipOutputStream.write(jsonStr.getBytes(StandardCharsets.UTF_8));
        } // The try-with-resources statement ensures that the GZIPOutputStream is closed automatically

        // Convert the compressed byte array to a Base64-encoded string
        String encodedStr = Base64.getEncoder().encodeToString(byteArrayOutputStream.toByteArray());

        return encodedStr;
    }


}


/**
 * Handles decoding of Monero Payment Request strings into data structures.
 */
class Decode {
    public static Map<String, Object> moneroPaymentRequest(String moneroPaymentRequest) {
        // Extract prefix, version, and Base64-encoded data
        String[] parts = moneroPaymentRequest.split(":");
        if (parts.length != 3){
            throw new IllegalArgumentException("Invalid input format");
        }

        String prefix = parts[0];
        String version = parts[1];
        String encodedStr = parts[2];

        if (version.equals("1")) {
            Map<String, Object> moneroPaymentRequestData = Decode.v1MoneroPaymentRequest(encodedStr);
            return moneroPaymentRequestData;
        } else {
            throw new IllegalArgumentException("Invalid input");
        }
    }


    // VERSIONS ////////////////////////////////////////////////////////////////////////////////////////////////////////
    public static Map<String, Object> v1MoneroPaymentRequest(String encodedStr) {
        // Decode the Base64-encoded string to bytes
        byte[] decodedBytes = Base64.getDecoder().decode(encodedStr);

        // Decompress the bytes using gzip decompression
        byte[] decompressedData = new byte[0];
        try (GZIPInputStream gzipInputStream = new GZIPInputStream(new ByteArrayInputStream(decodedBytes))) {
            decompressedData = gzipInputStream.readAllBytes();
        } catch (IOException e) {
            throw new RuntimeException("Failed to decode and decompress the string", e);
        }

        // Convert the decompressed bytes into to a JSON string
        String jsonStr = new String(decompressedData, java.nio.charset.StandardCharsets.UTF_8);

        // Parse the JSON string into a MutableMap with **every value** as a String
        Map<String, Object> moneroPaymentRequestData = JsonUtils.parseJson(jsonStr);

        // Convert values that should NOT be strings back to the proper type (defaults to 0 if unsuccessful)
        moneroPaymentRequestData.computeIfPresent("days_per_billing_cycle", (key, value) -> {
            try {
                return Integer.parseInt(value.toString());
            } catch (NumberFormatException e) {
                return 0;
            }
        });
        moneroPaymentRequestData.computeIfPresent("number_of_payments", (key, value) -> {
            try {
                return Integer.parseInt(value.toString());
            } catch (NumberFormatException e) {
                return 0;
            }
        });

        return moneroPaymentRequestData;
    }


}


/**
 * Provides validation methods for various Monero-related data inputs.
 */
class Check{
    public static boolean name(Object input){
        return input instanceof String;
    }


    public static boolean currency(String currency) {
        List<String> supportedCurrencies = new ArrayList<>();
        supportedCurrencies.add("XMR");
        supportedCurrencies.add("USD");
        return supportedCurrencies.contains(currency);
    }


    public static boolean wallet(Object walletAddress, boolean allowStandard, boolean allowIntegratedAddress, boolean allowSubaddress) {
        // Check if walletAddress is a string
        if (!(walletAddress instanceof String)) {
            return false;
        }

        String walletAddressString = (String) walletAddress;

        // Check if the wallet address starts with the number 4 (or 8 for subaddresses)
        List<Character> allowedFirstCharacters = new ArrayList<>();
        if (allowStandard) {
            allowedFirstCharacters.add('4');
        }

        if (allowSubaddress) {
            allowedFirstCharacters.add('8');
        }

        if (!allowedFirstCharacters.contains(walletAddressString.charAt(0))) {
            return false;
        }

        // Check if the wallet address is exactly 95 characters long (or 106 for integrated addresses)
        List<Integer> allowedWalletLengths = new ArrayList<>();
        if (allowStandard || allowSubaddress){
            allowedWalletLengths.add(95);
        }

        if (allowIntegratedAddress) {
            allowedWalletLengths.add(106);
        }

        if (!allowedWalletLengths.contains(walletAddressString.length())) {
            return false;
        }

        // Check if the wallet address contains only valid characters
        String validChars = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";
        for (char c : walletAddressString.toCharArray()) {
            if (!(validChars.contains(String.valueOf(c)))){
                return false;
            }
        }

        // If it passed all these checks
        return true;
    }


    public static boolean paymentID(Object paymentId){
        String validChars = "0123456789abcdef";

        if (!(paymentId instanceof String)) {
            return false;  // Not a string
        }

        String paymentIdString = (String) paymentId;

        if (paymentIdString.length() == 16) {
            for (char c : paymentIdString.toCharArray()) {
                if (!(validChars.contains(String.valueOf(c)))) {
                    return false;  // Invalid character found
                }
            }
            return true;  // All characters are valid
        } else {
            return false;  // Length is not 16 characters
        }
    }


    public static boolean startDate(Object startDate) {
        if (startDate instanceof String) {
            String startDateString = (String) startDate;

            // if it is an empty string
            if (startDateString.isEmpty()) {
                return true;
            }
            // if not empty, make sure it is the proper format
            try {
                new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").parse(startDateString);
                return true;
            } catch (ParseException e) {
                System.out.println(e);
            }
        }
        return false;
    }


    public static boolean amount(Object amount) {
        if (amount instanceof String) {
            String amountString = (String) amount;

            if (amountString.matches("[\\d,.]+")) {
                return true;
            }
        }
        return false;
    }


    public static boolean daysPerBillingCycle(Object billingCycle) {
        if (billingCycle instanceof Integer) {
            int billingCycleInt = (int) billingCycle;

            if (billingCycleInt >= 0) {
                return true;
            }
        }
        return false;
    }


    public static boolean numberOfPayments(Object numberOfPayments) {
        if (numberOfPayments instanceof Integer) {
            int numberOfPaymentsInt = (int) numberOfPayments;
             if (numberOfPaymentsInt >= 0) {
                 return true;
             }
        }
        return false;
    }


    public static boolean changeIndicatorUrl(Object changeIndicatorUrl) {
        if (changeIndicatorUrl instanceof String) {
            String changeIndicatorUrlString = (String) changeIndicatorUrl;

            if (changeIndicatorUrlString.isEmpty()) {
                return true;
            } else {
                try {
                    // Create a URI from the string
                    URI uri = new URI(changeIndicatorUrlString);
                    // Convert URI to URL
                    URL parsedUrl = uri.toURL();
                    if (
                            (parsedUrl.getProtocol() != null) &&
                            (!parsedUrl.getProtocol().isEmpty()) &&
                            (parsedUrl.getHost() != null) &&
                            (!parsedUrl.getHost().isEmpty())
                    ) {
                        return true; // Well-formed URL
                    }
                } catch (Exception e) {
                    System.out.println(e); // URL is not well-formed
                }
            }
        }
        return false;
    }


}


/*

public class Main {
    public static void main(String[] args) throws IOException {
        // Print logo to console
        MoneroPaymentRequest.printMoneroLogo();
        System.out.println("\n");

        // Set data for Monero Payment Request
        String customLabel = "Unlabeled Monero Payment Request";
        String sellersWallet = "4At3X5rvVypTofgmueN9s9QtrzdRe5BueFrskAZi17BoYbhzysozzoMFB6zWnTKdGC6AxEAbEE5czFR3hbEEJbsm4hCeX2S";
        String currency = "USD";
        String amount = "25.99";
        String paymentId = "";
        String startDate = "";
        int daysPerBillingCycle = 30;
        int numberOfPayments = 1;
        String changeIndicatorUrl = "";

        // Create Monero Payment Request
        String moneroPaymentRequest = MoneroPaymentRequest.create(
                customLabel,
                sellersWallet,
                currency,
                amount,
                paymentId,
                startDate,
                daysPerBillingCycle,
                numberOfPayments,
                changeIndicatorUrl
        );

        // Print the created Monero Payment Request
        System.out.println(moneroPaymentRequest);
        System.out.println("\n");

        // Decode & print the Monero Payment Request
        System.out.println(MoneroPaymentRequest.read(moneroPaymentRequest));
        System.out.println("\n");

        // Decode another example Monero Payment Request
        String exampleMoneroPaymentRequest = "monero-request:1:H4sIAAAAAAACEy2OYUvDMBCG/4rk8zaytunWfWtHKygTrEPrvoSkua3BNBlJqrbifzcdwsHd+z7H3fuDWG8G7dEOrVcYE7RAbcf0BajUQrbMG0sHqwKeyWAt6HYMqjnUN8N501PFOMwrXnoFd76T+hKgYKOjV7CUS6WCRduxVYB2eIH00PMAzJle2diD9u5m/wsqRTgmOGSbNCVbzgkQcg4XHSgF1tEvFvocOcl93BD7+Tpej+Z86Qd4ylz27O0kaiDFAJV1H/lJrjeFeefdNDozTeZQFen0po+P4n6f5t9lzsuStFNVx12YHrjrk24PTfQyv/TMeiqYD8FRhKNkiaNlhI8425FQ2xWO8Qn9/gGlA0vcRwEAAA==";
        System.out.println(MoneroPaymentRequest.read(exampleMoneroPaymentRequest));
        System.out.println("\n");
    }
}

*/