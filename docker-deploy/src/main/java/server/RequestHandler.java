package server;

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
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import java.io.StringReader;
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
//                rootEl.setAttribute("balance", initialBalance.toPlainString());
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


    /**
     * Process the entire <create> XML containing multiple <account> and/or <symbol> children
     * in the order they appear, building a single <results> response with <created> or <error>.
     */
    public String processCreate(String rawXml) {
        // Build up a single final string with <results> as the root.
        // For each <account> or <symbol> child, we call existing single-step methods
        // and append either <created> or <error> to the results in order.

        StringBuilder sb = new StringBuilder("<results>");
        try {
            Document doc = parseXml(rawXml);
            Element root = doc.getDocumentElement();
            if (!"create".equals(root.getTagName())) {
                sb.append("<error>Root is not <create></error></results>");
                return sb.toString();
            }

            NodeList children = root.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                if (children.item(i).getNodeType() != Node.ELEMENT_NODE) {
                    continue; // skip text nodes, whitespace, etc.
                }
                Element childEl = (Element) children.item(i);
                String tagName = childEl.getTagName();

                if ("account".equals(tagName)) {
                    // <account id="ACCOUNT_ID" balance="BALANCE"/>
                    String acctId = childEl.getAttribute("id");
                    String balStr = childEl.getAttribute("balance");
                    try {
                        // Reuse single createAccount method
                        String singleResult = createAccount(acctId, new BigDecimal(balStr));
                        // singleResult is something like <created ...> or <error ...>
                        // But that returns a top-level element (no <results>).
                        // We only want the inner <created>/<error>. Let's strip out the root if needed.
                        sb.append(extractInnerTag(singleResult));
                    } catch (Exception ex) {
                        // If something unexpectedly fails
                        sb.append("<error id=\"").append(acctId).append("\">")
                                .append(ex.getMessage()).append("</error>");
                    }
                }
                else if ("symbol".equals(tagName)) {
                    // <symbol sym="SYM">
                    //   <account id="ACCOUNT_ID">NUM</account>
                    //   ...
                    // </symbol>
                    String sym = childEl.getAttribute("sym");
                    NodeList subNodes = childEl.getChildNodes();
                    for (int j = 0; j < subNodes.getLength(); j++) {
                        if (subNodes.item(j).getNodeType() != Node.ELEMENT_NODE) {
                            continue;
                        }
                        Element acctEl = (Element) subNodes.item(j);
                        if (!"account".equals(acctEl.getTagName())) {
                            continue;
                        }
                        String acctId = acctEl.getAttribute("id");
                        String sharesStr = acctEl.getTextContent().trim();
                        try {
                            String singleResult = createOrAddSymbol(sym, acctId, new BigDecimal(sharesStr));
                            sb.append(extractInnerTag(singleResult));
                        } catch (Exception ex) {
                            sb.append("<error sym=\"").append(sym)
                                    .append("\" id=\"").append(acctId)
                                    .append("\">").append(ex.getMessage()).append("</error>");
                        }
                    }
                }
                else {
                    // unknown child tag in <create>?
                    sb.append("<error>Unknown create child: ").append(tagName).append("</error>");
                }
            }
        } catch (Exception e) {
            sb.append("<error>").append(e.getMessage()).append("</error>");
        }
        sb.append("</results>");
        return sb.toString();
    }

    /**
     * Process the entire <transactions id="ACCOUNT_ID"> with one or more
     * <order sym=".." amount=".." limit=".."/> or <cancel id=".."/> or <query id=".."/> children.
     * Build a single <results> output with <opened>, <error>, <status>, <canceled> tags
     * in the same order as the children.
     */
    public String processTransactions(String rawXml) {
        StringBuilder sb = new StringBuilder("<results>");
        try {
            Document doc = parseXml(rawXml);
            Element root = doc.getDocumentElement();
            if (!"transactions".equals(root.getTagName())) {
                sb.append("<error>Root is not <transactions></error></results>");
                return sb.toString();
            }
            // The <transactions> tag must have an attribute: id="ACCOUNT_ID"
            String accountId = root.getAttribute("id");
            // If account is invalid, we produce <error> for each child
            if (db.getAccount(accountId) == null) {
                NodeList children = root.getChildNodes();
                for (int i = 0; i < children.getLength(); i++) {
                    if (children.item(i).getNodeType() == Node.ELEMENT_NODE) {
                        Element childEl = (Element) children.item(i);
                        String ctag = childEl.getTagName();
                        if ("order".equals(ctag)) {
                            String sym = childEl.getAttribute("sym");
                            String amtStr = childEl.getAttribute("amount");
                            String limStr = childEl.getAttribute("limit");
                            sb.append("<error sym=\"").append(sym)
                                    .append("\" amount=\"").append(amtStr)
                                    .append("\" limit=\"").append(limStr)
                                    .append("\">Invalid account</error>");
                        } else if ("cancel".equals(ctag)) {
                            String tid = childEl.getAttribute("id");
                            sb.append("<error id=\"").append(tid)
                                    .append("\">Invalid account</error>");
                        } else if ("query".equals(ctag)) {
                            String tid = childEl.getAttribute("id");
                            sb.append("<error id=\"").append(tid)
                                    .append("\">Invalid account</error>");
                        }
                    }
                }
                sb.append("</results>");
                return sb.toString();
            }

            // If the account is valid, we can handle each child.
            NodeList children = root.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                if (children.item(i).getNodeType() != Node.ELEMENT_NODE) {
                    continue;
                }
                Element el = (Element) children.item(i);
                String tag = el.getTagName();
                switch (tag) {
                    case "order": {
                        // <order sym="SYM" amount="AMT" limit="LMT"/>
                        String sym = el.getAttribute("sym");
                        String amtStr = el.getAttribute("amount");
                        String limStr = el.getAttribute("limit");
                        try {
                            BigDecimal amt = new BigDecimal(amtStr);
                            BigDecimal lim = new BigDecimal(limStr);
                            // Reuse openOrder(...)
                            String singleResult = openOrder(accountId, sym, amt, lim);
                            sb.append(extractInnerTag(singleResult));
                        } catch (Exception ex) {
                            sb.append("<error sym=\"").append(sym)
                                    .append("\" amount=\"").append(amtStr)
                                    .append("\" limit=\"").append(limStr)
                                    .append("\">")
                                    .append(ex.getMessage())
                                    .append("</error>");
                        }
                        break;
                    }
                    case "cancel": {
                        // <cancel id="TRANS_ID"/>
                        String tid = el.getAttribute("id");
                        try {
                            long orderId = Long.parseLong(tid);
                            String singleResult = cancelOrder(orderId);
                            sb.append(extractInnerTag(singleResult));
                        } catch (Exception ex) {
                            sb.append("<error id=\"").append(tid)
                                    .append("\">").append(ex.getMessage()).append("</error>");
                        }
                        break;
                    }
                    case "query": {
                        // <query id="TRANS_ID"/>
                        String tid = el.getAttribute("id");
                        try {
                            long orderId = Long.parseLong(tid);
                            String singleResult = queryOrder(orderId);
                            sb.append(extractInnerTag(singleResult));
                        } catch (Exception ex) {
                            sb.append("<error id=\"").append(tid)
                                    .append("\">").append(ex.getMessage()).append("</error>");
                        }
                        break;
                    }
                    default: {
                        // unknown child
                        sb.append("<error>Unknown transactions child: ")
                                .append(tag).append("</error>");
                    }
                }
            }
        } catch (Exception e) {
            sb.append("<error>").append(e.getMessage()).append("</error>");
        }
        sb.append("</results>");
        return sb.toString();
    }

    // Utility: parse raw XML into a DOM Document
    private Document parseXml(String xml) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = dbf.newDocumentBuilder();
        return builder.parse(new InputSource(new StringReader(xml)));
    }

    private String extractInnerTag(String singleResult) {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = dbf.newDocumentBuilder();
            Document doc = builder.parse(new InputSource(new StringReader(singleResult)));
            Element root = doc.getDocumentElement();
            // Convert just this root element to a string, no extra doc wrapper
            TransformerFactory tf = TransformerFactory.newInstance();
            Transformer t = tf.newTransformer();
            t.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            t.setOutputProperty(OutputKeys.INDENT, "no");
            StringWriter sw = new StringWriter();
            t.transform(new DOMSource(root), new StreamResult(sw));
            return sw.toString();
        } catch (Exception e) {
            // If any error, fallback to returning singleResult as-is
            return singleResult;
        }
    }
}
