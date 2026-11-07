// SPDX-License-Identifier: Apache-2.0
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.microg.gms.common.HttpFormClient;

/** Exercise the real HttpFormClient using a network-free URLConnection fixture. */
public class FormPrivacyTest {
    static final String SECRET = "SYNTHETIC-CREDENTIAL-NEVER-LOG";
    static final String EMAIL = "synthetic@example.invalid";
    static final ByteArrayOutputStream sent = new ByteArrayOutputStream();
    public static class Request extends HttpFormClient.Request {
        @HttpFormClient.RequestContent("Token") public String token = SECRET;
        @HttpFormClient.RequestContent("Email") public String email = EMAIL;
    }
    public static class Response {
        @HttpFormClient.ResponseField("Auth") public String token;
        @HttpFormClient.ResponseField("Number") public long number;
        @HttpFormClient.ResponseHeader("Sensitive-Number") public int header;
        public Response() {}
    }
    public static void main(String[] args) throws Exception {
        URL.setURLStreamHandlerFactory(scheme -> !"fixture".equals(scheme) ? null : new URLStreamHandler() {
            protected URLConnection openConnection(URL url) {
                return new HttpURLConnection(url) {
                    public void disconnect() {}
                    public boolean usingProxy() { return false; }
                    public void connect() {}
                    public int getResponseCode() { return 200; }
                    public OutputStream getOutputStream() { return sent; }
                    public InputStream getInputStream() {
                        // Unknown fields and invalid numbers also must not leak values.
                        String body = "Auth=" + SECRET + "\nNumber=" + SECRET + "\nUnknown=" + EMAIL;
                        return new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8));
                    }
                    public Map<String, List<String>> getHeaderFields() {
                        return Collections.singletonMap("Sensitive-Number", Collections.singletonList(SECRET));
                    }
                };
            }
        });
        Response response = HttpFormClient.request("fixture://local/auth", new Request(), Response.class);
        if (!SECRET.equals(response.token)) throw new AssertionError("Auth result changed");
        String request = sent.toString("UTF-8");
        if (!request.contains(SECRET) || !request.contains("synthetic%40example.invalid")) {
            throw new AssertionError("Request data changed");
        }
        String log = android.util.Log.messages.toString();
        if (log.contains(SECRET) || log.contains(EMAIL) || log.contains("synthetic%40")) {
            throw new AssertionError("Credential leaked to Log");
        }
        if (!log.contains("Unrecognized response field") || !log.contains("Could not assign response field")) {
            throw new AssertionError("Failure cases not exercised");
        }
        System.out.println("Form privacy roundtrip: PASS");
    }
}
