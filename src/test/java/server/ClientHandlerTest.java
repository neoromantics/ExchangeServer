package server;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import server.RequestHandler;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class ClientHandlerTest {

    private RequestHandler mockHandler;

    @BeforeEach
    public void setUp() {
        mockHandler = mock(RequestHandler.class);
    }

    @Test
    public void testHandleClientCreate() throws IOException {
        // The XML content
        String xmlContent = "<create><account id=\"999\" balance=\"1000\"/></create>";

        // Calculate the length dynamically and prepend it to the request
        String request = xmlContent.length() + "\n" + xmlContent; // The length is now dynamically calculated
        ByteArrayInputStream requestStream = new ByteArrayInputStream(request.getBytes(StandardCharsets.UTF_8));
        ByteArrayOutputStream responseStream = new ByteArrayOutputStream();

        // Create a mock Socket that returns requestStream as input, responseStream as output
        Socket mockSocket = mock(Socket.class);
        when(mockSocket.getInputStream()).thenReturn(requestStream);
        when(mockSocket.getOutputStream()).thenReturn(responseStream);

        // Suppose processCreate returns "<results><created id=\"999\"/></results>"
        when(mockHandler.processCreate("<create><account id=\"999\" balance=\"1000\"/></create>"))
                .thenReturn("<results><created id=\"999\"/></results>");

        // Create the client handler and run it
        ClientHandler handler = new ClientHandler(mockSocket, mockHandler);
        handler.run();

        // The output should contain the returned string
        String output = responseStream.toString(StandardCharsets.UTF_8);
        System.out.println("Response:\n" + output);
        assertTrue(output.contains("<results><created id=\"999\"/></results>"));

        // Also verify that we called processCreate exactly once
        verify(mockHandler).processCreate("<create><account id=\"999\" balance=\"1000\"/></create>");
    }


    @Test
    public void testHandleClientTransactions() throws IOException {
        // Request length should match the length of XML content
        String xmlContent = "<transactions id=\"acctA\"><order sym=\"ABC\" amount=\"100\" limit=\"50.0\"/></transactions>";
        String request = xmlContent.length() + "\n" + xmlContent; // The length is now dynamically calculated
        ByteArrayInputStream requestStream = new ByteArrayInputStream(request.getBytes(StandardCharsets.UTF_8));
        ByteArrayOutputStream responseStream = new ByteArrayOutputStream();

        Socket mockSocket = mock(Socket.class);
        when(mockSocket.getInputStream()).thenReturn(requestStream);
        when(mockSocket.getOutputStream()).thenReturn(responseStream);

        // Suppose the requestHandler returns <results><opened .../></results>
        String mockResponse = "<results><opened sym=\"ABC\" amount=\"100\" limit=\"50.0\" id=\"123\"/></results>";
        when(mockHandler.processTransactions(xmlContent)).thenReturn(mockResponse);

        ClientHandler handler = new ClientHandler(mockSocket, mockHandler);
        handler.run();

        String output = responseStream.toString(StandardCharsets.UTF_8);
        System.out.println("Response:\n" + output);
        assertTrue(output.contains("<opened"));
        assertTrue(output.contains("sym=\"ABC\""));
        assertTrue(output.contains("amount=\"100\""));
        assertTrue(output.contains("limit=\"50.0\""));
        assertTrue(output.contains("id=\"123\""));
        verify(mockHandler).processTransactions(xmlContent);
    }

}
