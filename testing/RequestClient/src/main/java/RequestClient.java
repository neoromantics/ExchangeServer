import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public class RequestClient {
    public static void main(String[] args) {
        // Check usage: first argument must be the XML file.
        if (args.length < 1) {
            System.err.println("Usage: java -jar RequestClient.jar <xmlFile> [host] [port]");
            System.exit(1);
        }

        // Read the XML file content.
        String xmlFile = args[0];
        String xmlContent;
        try {
            xmlContent = new String(Files.readAllBytes(Paths.get(xmlFile)), StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("Failed to read XML file: " + xmlFile);
            e.printStackTrace();
            return;
        }

        // Get host and port or use defaults.
        String host = (args.length >= 2) ? args[1] : "localhost";
        int port = (args.length >= 3) ? Integer.parseInt(args[2]) : 12345;

        // Calculate byte length of the XML (UTF-8 encoded)
        byte[] xmlBytes = xmlContent.getBytes(StandardCharsets.UTF_8);
        String request = xmlBytes.length + "\n" + xmlContent;

        try (Socket socket = new Socket(host, port);
             PrintWriter out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {

            // Send the request.
            out.println(request);

            // Read and print the server's response.
            String line;
            while ((line = in.readLine()) != null) {
                System.out.println(line);
            }
        } catch(IOException e) {
            e.printStackTrace();
        }
    }
}
