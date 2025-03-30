
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
     *   <created id="ACCOUNT_ID" balance="INITIAL_BALANCE"/>
     * On error, returns:
     *   <error id="ACCOUNT_ID">Error message</error>
     */
    public String createAccount(String accountId, BigDecimal initialBalance) {
        try {
            Document doc = createNewDocument();
            Element rootEl;
            try {
                db.createAccount(accountId, initialBalance);
                rootEl = doc.createElement("created");
                rootEl.setAttribute("id", accountId);
                rootEl.setAttribute("balance", initialBalance.toPlainString());
            } catch (DatabaseException ex) {
                rootEl = doc.createElement("error");
                rootEl.setAttribute("id", accountId);
                rootEl.setTextContent(ex.getMessage());
            }
            doc.appendChild(rootEl);
            return convertDocumentToString(doc);
        } catch (Exception e) {
            return buildFatalError(e);
        }
    }

    /**
     * Creates or adds shares of a symbol to an account.
     * On success, returns:
     *   <created sym="SYM" id="ACCOUNT_ID"/>
     * On error, returns:
     *   <error sym="SYM" id="ACCOUNT_ID">Error message</error>
     */
    public String createOrAddSymbol(String symbol, String accountId, BigDecimal shares) {
        try {
            Document doc = createNewDocument();
            Element rootEl;
            try {
                db.createOrAddSymbol(symbol, accountId, shares);
                rootEl = doc.createElement("created");
                rootEl.setAttribute("sym", symbol);
                rootEl.setAttribute("id", accountId);
            } catch (DatabaseException ex) {
                rootEl = doc.createElement("error");
                rootEl.setAttribute("sym", symbol);
                rootEl.setAttribute("id", accountId);
                rootEl.setTextContent(ex.getMessage());
            }
            doc.appendChild(rootEl);
            return convertDocumentToString(doc);
        } catch (Exception e) {
            return buildFatalError(e);
        }
    }

    /**
     * Opens a new buy/sell order.
     * On success, returns:
     *   <opened sym="SYM" amount="AMT" limit="LIMIT" id="ORDER_ID"/>
     * On error, returns:
     *   <error sym="SYM" amount="AMT" limit="LIMIT">Error message</error>
     */
    public String openOrder(String accountId, String symbol, BigDecimal amount, BigDecimal limitPrice) {
        try {
            Document doc = createNewDocument();
            Element rootEl;
            try {
                Order order = new Order(accountId, symbol, amount, limitPrice);
                Order placed = engine.openOrder(order);
                rootEl = doc.createElement("opened");
                rootEl.setAttribute("sym", symbol);
                rootEl.setAttribute("amount", amount.toPlainString());
                rootEl.setAttribute("limit", limitPrice.toPlainString());
                rootEl.setAttribute("id", String.valueOf(placed.getOrderId()));
            } catch (MatchingEngineException ex) {
                rootEl = doc.createElement("error");
                rootEl.setAttribute("sym", symbol);
                rootEl.setAttribute("amount", amount.toPlainString());
                rootEl.setAttribute("limit", limitPrice.toPlainString());
                rootEl.setTextContent(ex.getMessage());
            }
            doc.appendChild(rootEl);
            return convertDocumentToString(doc);
        } catch (Exception e) {
            return buildFatalError(e);
        }
    }

    /**
     * Cancels an open order.
     * On success, returns:
     *   <canceled id="ORDER_ID">
     *     <executed shares="..." price="..." time="..."/>
     *     ...
     *     <canceled shares="..." time="..."/>
     *   </canceled>
     * On error, returns:
     *   <error id="ORDER_ID">Error message</error>
     */
    public String cancelOrder(long orderId) {
        try {
            Document doc = createNewDocument();
            Element rootEl;
            try {
                engine.cancelOrder(orderId);
                QueryResult qr = engine.queryOrder(orderId);
                rootEl = doc.createElement("canceled");
                rootEl.setAttribute("id", String.valueOf(orderId));
                // Add each execution record.
                for (QueryResult.ExecutionRecord er : qr.executions) {
                    Element execEl = doc.createElement("executed");
                    execEl.setAttribute("shares", er.shares.toPlainString());
                    execEl.setAttribute("price", er.price.toPlainString());
                    execEl.setAttribute("time", String.valueOf(er.timestamp));
                    rootEl.appendChild(execEl);
                }
                // Add remaining unfilled portion, if any.
                if (qr.openShares.compareTo(BigDecimal.ZERO) > 0) {
                    Element cancelPartEl = doc.createElement("canceled");
                    cancelPartEl.setAttribute("shares", qr.openShares.toPlainString());
                    cancelPartEl.setAttribute("time", String.valueOf(System.currentTimeMillis() / 1000));
                    rootEl.appendChild(cancelPartEl);
                }
            } catch (MatchingEngineException ex) {
                rootEl = doc.createElement("error");
                rootEl.setAttribute("id", String.valueOf(orderId));
                rootEl.setTextContent(ex.getMessage());
            }
            doc.appendChild(rootEl);
            return convertDocumentToString(doc);
        } catch (Exception e) {
            return buildFatalError(e);
        }
    }

    /**
     * Queries the status of an order.
     * On success, returns:
     *   <status id="ORDER_ID">
     *     <open shares="..."/>
     *     <executed shares="..." price="..." time="..."/>
     *     ...
     *   </status>
     * On error, returns:
     *   <error id="ORDER_ID">Error message</error>
     */
    public String queryOrder(long orderId) {
        try {
            Document doc = createNewDocument();
            Element rootEl;
            try {
                QueryResult qr = engine.queryOrder(orderId);
                rootEl = doc.createElement("status");
                rootEl.setAttribute("id", String.valueOf(orderId));

                if (qr.status == OrderStatus.OPEN && qr.openShares.compareTo(BigDecimal.ZERO) > 0) {
                    Element openEl = doc.createElement("open");
                    openEl.setAttribute("shares", qr.openShares.toPlainString());
                    rootEl.appendChild(openEl);
                } else if (qr.status == OrderStatus.CANCELED && qr.openShares.compareTo(BigDecimal.ZERO) > 0) {
                    Element canceledEl = doc.createElement("canceled");
                    canceledEl.setAttribute("shares", qr.openShares.toPlainString());
                    canceledEl.setAttribute("time", String.valueOf(System.currentTimeMillis() / 1000));
                    rootEl.appendChild(canceledEl);
                }

                for (QueryResult.ExecutionRecord er : qr.executions) {
                    Element execEl = doc.createElement("executed");
                    execEl.setAttribute("shares", er.shares.toPlainString());
                    execEl.setAttribute("price", er.price.toPlainString());
                    execEl.setAttribute("time", String.valueOf(er.timestamp));
                    rootEl.appendChild(execEl);
                }
            } catch (MatchingEngineException ex) {
                rootEl = doc.createElement("error");
                rootEl.setAttribute("id", String.valueOf(orderId));
                rootEl.setTextContent(ex.getMessage());
            }
            doc.appendChild(rootEl);
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
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        StringWriter writer = new StringWriter();
        transformer.transform(new DOMSource(doc), new StreamResult(writer));
        return writer.getBuffer().toString();
    }

    /**
     * Builds a fatal error XML string.
     */
    private String buildFatalError(Exception e) {
        return "<error>" + e.getMessage() + "</error>";
    }
}
