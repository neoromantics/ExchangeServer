
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import db.DatabaseException;
import db.DatabaseManager;
import engine.MatchingEngine;
import engine.MatchingEngineException;
import engine.QueryResult;
import model.Order;
import model.OrderStatus;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.StringWriter;
import java.math.BigDecimal;

public class RequestHandler {

    private final DatabaseManager db;
    private final MatchingEngine engine;

    public RequestHandler(DatabaseManager dbManager, MatchingEngine matchingEngine) {
        this.db = dbManager;
        this.engine = matchingEngine;
    }

    /**
     * Creates a new account.
     * On success, returns:
     * <results>
     *   <created id="ACCOUNT_ID" balance="INITIAL_BALANCE"/>
     * </results>
     * On error, returns:
     * <results>
     *   <error id="ACCOUNT_ID">Error message</error>
     * </results>
     */
    public String createAccount(String accountId, BigDecimal initialBalance) {
        try {
            Document doc = createNewDocument();
            Element resultsEl = doc.createElement("results");
            doc.appendChild(resultsEl);

            try {
                db.createAccount(accountId, initialBalance);
                Element createdEl = doc.createElement("created");
                createdEl.setAttribute("id", accountId);
                createdEl.setAttribute("balance", initialBalance.toPlainString());
                resultsEl.appendChild(createdEl);
            } catch (DatabaseException ex) {
                Element errorEl = doc.createElement("error");
                errorEl.setAttribute("id", accountId);
                errorEl.setTextContent(ex.getMessage());
                resultsEl.appendChild(errorEl);
            }
            return convertDocumentToString(doc);
        } catch (Exception e) {
            return buildFatalError(e);
        }
    }

    /**
     * Creates or adds shares of a symbol to an account.
     * On success:
     * <results>
     *   <created sym="SYM" id="ACCOUNT_ID"/>
     * </results>
     * On error:
     * <results>
     *   <error sym="SYM" id="ACCOUNT_ID">Error message</error>
     * </results>
     */
    public String createOrAddSymbol(String symbol, String accountId, BigDecimal shares) {
        try {
            Document doc = createNewDocument();
            Element resultsEl = doc.createElement("results");
            doc.appendChild(resultsEl);

            try {
                db.createOrAddSymbol(symbol, accountId, shares);
                Element createdEl = doc.createElement("created");
                createdEl.setAttribute("sym", symbol);
                createdEl.setAttribute("id", accountId);
                resultsEl.appendChild(createdEl);
            } catch (DatabaseException ex) {
                Element errorEl = doc.createElement("error");
                errorEl.setAttribute("sym", symbol);
                errorEl.setAttribute("id", accountId);
                errorEl.setTextContent(ex.getMessage());
                resultsEl.appendChild(errorEl);
            }
            return convertDocumentToString(doc);
        } catch (Exception e) {
            return buildFatalError(e);
        }
    }

    /**
     * Opens a new buy/sell order.
     * On success:
     * <results>
     *   <opened sym="SYM" amount="AMT" limit="LIMIT" id="ORDER_ID"/>
     * </results>
     * On error:
     * <results>
     *   <error sym="SYM" amount="AMT" limit="LIMIT">Error message</error>
     * </results>
     */
    public String openOrder(String accountId, String symbol,
                            BigDecimal amount, BigDecimal limitPrice) {
        try {
            Document doc = createNewDocument();
            Element resultsEl = doc.createElement("results");
            doc.appendChild(resultsEl);

            try {
                Order order = new Order(accountId, symbol, amount, limitPrice);
                Order placed = engine.openOrder(order);
                Element openedEl = doc.createElement("opened");
                openedEl.setAttribute("sym", symbol);
                openedEl.setAttribute("amount", amount.toPlainString());
                openedEl.setAttribute("limit", limitPrice.toPlainString());
                openedEl.setAttribute("id", String.valueOf(placed.getOrderId()));
                resultsEl.appendChild(openedEl);
            } catch (MatchingEngineException ex) {
                Element errorEl = doc.createElement("error");
                errorEl.setAttribute("sym", symbol);
                errorEl.setAttribute("amount", amount.toPlainString());
                errorEl.setAttribute("limit", limitPrice.toPlainString());
                errorEl.setTextContent(ex.getMessage());
                resultsEl.appendChild(errorEl);
            }
            return convertDocumentToString(doc);
        } catch (Exception e) {
            return buildFatalError(e);
        }
    }

    /**
     * Cancels an open order.
     * On success, returns:
     * <results>
     *   <canceled id="ORDER_ID">
     *     <executed shares="..." price="..." time="..."/>
     *     ...
     *     <canceled shares="..." time="..."/>
     *   </canceled>
     * </results>
     * On error, returns:
     * <results>
     *   <error id="ORDER_ID">Error message</error>
     * </results>
     */
    public String cancelOrder(long orderId) {
        try {
            Document doc = createNewDocument();
            Element resultsEl = doc.createElement("results");
            doc.appendChild(resultsEl);

            try {
                engine.cancelOrder(orderId);
                QueryResult qr = engine.queryOrder(orderId);

                Element canceledEl = doc.createElement("canceled");
                canceledEl.setAttribute("id", String.valueOf(orderId));
                resultsEl.appendChild(canceledEl);

                // Add each execution record
                for (QueryResult.ExecutionRecord er : qr.executions) {
                    Element execEl = doc.createElement("executed");
                    execEl.setAttribute("shares", er.shares.toPlainString());
                    execEl.setAttribute("price", er.price.toPlainString());
                    execEl.setAttribute("time", String.valueOf(er.timestamp));
                    canceledEl.appendChild(execEl);
                }
                // Add remaining unfilled portion, if any
                if (qr.openShares.compareTo(BigDecimal.ZERO) > 0) {
                    Element cancelPartEl = doc.createElement("canceled");
                    cancelPartEl.setAttribute("shares", qr.openShares.toPlainString());
                    // Using current epoch seconds as time
                    cancelPartEl.setAttribute("time", String.valueOf(System.currentTimeMillis() / 1000));
                    canceledEl.appendChild(cancelPartEl);
                }
            } catch (MatchingEngineException ex) {
                Element errorEl = doc.createElement("error");
                errorEl.setAttribute("id", String.valueOf(orderId));
                errorEl.setTextContent(ex.getMessage());
                resultsEl.appendChild(errorEl);
            }
            return convertDocumentToString(doc);
        } catch (Exception e) {
            return buildFatalError(e);
        }
    }

    /**
     * Queries the status of an order.
     * On success, returns:
     * <results>
     *   <status id="ORDER_ID">
     *     <open shares="..."/>
     *     <executed shares="..." price="..." time="..."/>
     *     ...
     *   </status>
     * </results>
     * On error, returns:
     * <results>
     *   <error id="ORDER_ID">Error message</error>
     * </results>
     */
    public String queryOrder(long orderId) {
        try {
            Document doc = createNewDocument();
            Element resultsEl = doc.createElement("results");
            doc.appendChild(resultsEl);

            try {
                QueryResult qr = engine.queryOrder(orderId);
                Element statusEl = doc.createElement("status");
                statusEl.setAttribute("id", String.valueOf(orderId));
                resultsEl.appendChild(statusEl);

                if (qr.status == OrderStatus.OPEN && qr.openShares.compareTo(BigDecimal.ZERO) > 0) {
                    Element openEl = doc.createElement("open");
                    openEl.setAttribute("shares", qr.openShares.toPlainString());
                    statusEl.appendChild(openEl);
                } else if (qr.status == OrderStatus.CANCELED && qr.openShares.compareTo(BigDecimal.ZERO) > 0) {
                    Element canceledEl = doc.createElement("canceled");
                    canceledEl.setAttribute("shares", qr.openShares.toPlainString());
                    canceledEl.setAttribute("time", String.valueOf(System.currentTimeMillis() / 1000));
                    statusEl.appendChild(canceledEl);
                }

                for (QueryResult.ExecutionRecord er : qr.executions) {
                    Element execEl = doc.createElement("executed");
                    execEl.setAttribute("shares", er.shares.toPlainString());
                    execEl.setAttribute("price", er.price.toPlainString());
                    execEl.setAttribute("time", String.valueOf(er.timestamp));
                    statusEl.appendChild(execEl);
                }
            } catch (MatchingEngineException ex) {
                Element errorEl = doc.createElement("error");
                errorEl.setAttribute("id", String.valueOf(orderId));
                errorEl.setTextContent(ex.getMessage());
                resultsEl.appendChild(errorEl);
            }
            return convertDocumentToString(doc);
        } catch (Exception e) {
            return buildFatalError(e);
        }
    }

    /**
     * Creates a new empty XML Document.
     */
    private Document createNewDocument() throws ParserConfigurationException {
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        return dBuilder.newDocument();
    }

    /**
     * Converts a Document into its String representation.
     */
    private String convertDocumentToString(Document doc) throws TransformerException {
        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer transformer = tf.newTransformer();
        // Optional: configure transformer properties
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");

        StringWriter writer = new StringWriter();
        transformer.transform(new DOMSource(doc), new StreamResult(writer));
        return writer.getBuffer().toString();
    }

    /**
     * Builds a fatal error XML string.
     */
    private String buildFatalError(Exception e) {
        return "<results><error>" + e.getMessage() + "</error></results>";
    }
}
