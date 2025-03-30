package server;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * ClientHandler reads the line for size, then reads that many bytes of XML,
 * determines if root is <create> or <transactions>, calls requestHandler, and
 * returns the resulting XML.
 */
public class ClientHandler implements Runnable {

    private final Socket clientSocket;
    private final RequestHandler requestHandler;

    public ClientHandler(Socket clientSocket, RequestHandler requestHandler) {
        this.clientSocket = clientSocket;
        this.requestHandler = requestHandler;
    }

    @Override
    public void run() {
        try (BufferedReader in = new BufferedReader(
                new InputStreamReader(clientSocket.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter out = new PrintWriter(
                     new OutputStreamWriter(clientSocket.getOutputStream(), StandardCharsets.UTF_8), true))
        {
            // 1) read line with integer size
            String sizeLine = in.readLine();
            if (sizeLine == null) {
                System.err.println("No size line. Closing.");
                return;
            }
            int xmlSize = Integer.parseInt(sizeLine.trim());

            // 2) read xml data
            char[] xmlBuf = new char[xmlSize];
            int totalRead = 0;
            while (totalRead < xmlSize) {
                int readNow = in.read(xmlBuf, totalRead, xmlSize - totalRead);
                if (readNow == -1) {
                    System.err.println("EOF reached prematurely. Closing.");
                    return;
                }
                totalRead += readNow;
            }
            String xmlData = new String(xmlBuf);

            // 3) parse top-level tag
            String response;
            try {
                DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                DocumentBuilder builder = dbf.newDocumentBuilder();
                Document doc = builder.parse(new ByteArrayInputStream(xmlData.getBytes(StandardCharsets.UTF_8)));
                doc.getDocumentElement().normalize();
                Element root = doc.getDocumentElement();
                String rootName = root.getTagName();

                if ("create".equals(rootName)) {
                    // handle <create>
                    response = requestHandler.processCreate(xmlData);
                } else if ("transactions".equals(rootName)) {
                    // handle <transactions>
                    response = requestHandler.processTransactions(xmlData);
                } else {
                    response = "<results><error>Unknown root tag: " + rootName + "</error></results>";
                }
            } catch (Exception e) {
                e.printStackTrace();
                response = "<results><error>XML parse error: " + e.getMessage() + "</error></results>";
            }

            // 4) write back
            out.println(response);

        } catch (IOException io) {
            io.printStackTrace();
        } finally {
            // close socket
            try { clientSocket.close(); } catch (IOException ignore) {}
        }
    }
}
