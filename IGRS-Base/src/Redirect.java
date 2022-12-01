import com.sun.org.apache.xpath.internal.functions.FuncFalse;

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


        String expires = request.getHeader("Expires");
        // linphone
        if (expires != null) {

            if(expires.equals("0")){
                registrarDB.remove(aor);
                request.createResponse(200).send();
            } else {
                if (verifyDomain(request)) {
                    registrarDB.put(aor, contact);
                    request.createResponse(200).send();
                } else {
                    //Utilizadores que não pertençam ao domínio @acme.pt levam um 403
                    request.createResponse(403).send();
                }
            }


        }
        // twinkle
        else {
            if(contactHeader.split("=")[1].equals("0")){
                registrarDB.remove(aor);
                request.createResponse(200).send();
            } else{
                if (verifyDomain(request)) {
                    registrarDB.put(aor, contact);
                    request.createResponse(200).send();
                } else {
                    //Utilizadores que não pertençam ao domínio @acme.pt levam um 403
                    request.createResponse(403).send();
                }
            }


        }


    }

    //Aqui é que é feita a chamada
    @Override
    protected void doInvite(SipServletRequest request) throws IOException, TooManyHopsException, ServletParseException {
        String aor = getAttr(request.getHeader("To"), "sip:");

        if (!registrarDB.containsKey(aor)) {
            request.createResponse(404).send();
        } else if (verifyComum(request) & aor.contains("alerta")) {
            request.getProxy().proxyTo(sipFactory.createURI(registrarDB.get(aor)));
        } else {
            request.getProxy().proxyTo(sipFactory.createURI(registrarDB.get(aor)));
        }
    }

    @Override
    protected void doMessage(SipServletRequest request) throws ServletException, IOException {
        String aor = getAttr(request.getHeader("To"), "sip:");

        if(!registrarDB.containsKey(aor) & !aor.contains("alerta") ){
            request.createResponse(401).send();
        }else{
            request.createResponse(200,request.getContent().toString());
        }

    }

    //alteração do seu estado (ligado/não-ligado)
    @Override
    protected void doPublish(SipServletRequest sipServletRequest) throws ServletException, IOException {
        super.doPublish(sipServletRequest);
    }

    //2ºparte do Projeto, Conferências
    @Override
    protected void doResponse(SipServletResponse sipServletResponse) throws ServletException, IOException {
        String aor = getAttr(sipServletResponse.getHeader("To"), "sip:");

        if (!registrarDB.containsKey(aor) & !verifyGestor(sipServletResponse.getRequest()) ) {
            sipServletResponse.getRequest().createResponse(404).send();

        }else {

            sipServletResponse.getRequest().createResponse(200).send();

        }

    }



    /*@Override
    protected void doBye(SipServletRequest sipServletRequest) throws ServletException, IOException {
        super.doBye(sipServletRequest);
    }*/



    /**
     * Customs functions
     */
    protected boolean verifyDomain(SipServletRequest request) {

        String toHeader = request.getHeader("From");
        return toHeader.contains("@acme.pt");
    }

    protected boolean verifyUserType(SipServletRequest request, String type) {

        String toHeader = request.getHeader("From");
        return toHeader.contains(type);
    }

    protected boolean verifyGestor(SipServletRequest request) {

        String toHeader = request.getHeader("From");
        return toHeader.contains("gestor@acme.pt");
    }

    protected boolean verifyColaborador(SipServletRequest request) {

        String toHeader = request.getHeader("From");
        return toHeader.contains("colaborador");
    }


    protected boolean verifyComum(SipServletRequest request) {

        String toHeader = request.getHeader("From");
        if(!verifyGestor(request) & !verifyColaborador( request)){
            return true;
        }
        return false;
    }

    /**
     * Auxiliary function for extracting attribute values
     *
     * @param str  the complete string
     * @param attr the attr name
     * @return attr name and value
     */

    //Vai retornar -> sip:x@domain.pt
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
