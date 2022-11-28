import javax.servlet.ServletException;
import javax.servlet.sip.*;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class Redirect extends SipServlet {
    static private Map<String, String> registrarDB;
    static private SipFactory sipFactory;

    /**
     * SipServlet functions
     */
    @Override
    public void init() {
        sipFactory = (SipFactory) getServletContext().getAttribute(SIP_FACTORY);
        registrarDB = new HashMap<>();
    }

    @Override
    protected void doRegister(SipServletRequest request) throws IOException {
        String toHeader = request.getHeader("To");
        String contactHeader = request.getHeader("Contact");
        String aor = getAttr(toHeader, "sip:");
        String contact = getAttr(contactHeader, "sip:");
        registrarDB.put(aor, contact);
        request.createResponse(200).send();
    }

    @Override
    protected void doInvite(SipServletRequest request) throws IOException, TooManyHopsException, ServletParseException {
        String aor = getAttr(request.getHeader("To"), "sip:");

        if (!registrarDB.containsKey(aor)) {
            request.createResponse(404).send();
        } else {
            SipServletResponse response = request.createResponse(300);
            response.setHeader("Contact", registrarDB.get(aor));
            response.send();
        }
    }

    @Override
    protected void doMessage(SipServletRequest request) throws ServletException, IOException {

    }

    /**
     * Customs functions
     */
    protected boolean verifyDomain(SipServletRequest request) {
        return false;
    }

    protected boolean verifyUserType(SipServletRequest request, String type) {
        return false;
    }

    /**
     * Auxiliary function for extracting attribute values
     *
     * @param str  the complete string
     * @param attr the attr name
     * @return attr name and value
     */
    protected String getAttr(String str, String attr) {
        int indexStart = str.indexOf(attr);
        int indexStop = str.indexOf(">", indexStart);
        if (indexStop == -1) {
            indexStop = str.indexOf(";", indexStart);
            if (indexStop == -1) {
                indexStop = str.length();
            }
        }
        return str.substring(indexStart, indexStop);
    }
}