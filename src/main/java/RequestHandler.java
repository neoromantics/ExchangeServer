import db.DatabaseManager;
import engine.MatchingEngine;

public class RequestHandler {

    private final DatabaseManager dbManager;
    private final MatchingEngine engine;

    public RequestHandler(DatabaseManager dbManager, MatchingEngine engine) {
        this.dbManager = dbManager;
        this.engine = engine;
    }

    /**
     * Handle the <create> top-level request.
     * Parse each child (<account ...> or <symbol ...>) and create or add shares.
     * Return an XML string or DOM Document for the <results> response.
     */
    public String handleCreateRequest(org.w3c.dom.Element createElement) {
        // For each child:
        //   if <account>, call dbManager.createAccount(...)
        //     produce either <created.../> or <error.../>
        //   if <symbol>, for each <account>, call dbManager.addSymbolShares(...)
        //     produce <created/> or <error/>
        // Build up <results>...
        return "<results>...</results>";
    }

    /**
     * Handle the <transactions> top-level request.
     *   - for each <order>, call engine.openOrder()
     *   - for each <cancel>, call engine.cancelOrder()
     *   - for each <query>, call engine.getOrderStatus()
     * Return the XML <results> with <opened>, <canceled>, <status>, or <error> children.
     */
    public String handleTransactionsRequest(org.w3c.dom.Element transactionsElement) {
        // parse account id = transactionsElement.getAttribute("id")
        // For each child (<order>, <cancel>, <query>):
        //   parse the attributes
        //   call engine accordingly
        //   build <results> child: <opened ...>, <canceled ...>, <status>..., or <error>...
        return "<results>...</results>";
    }
}
