import javax.servlet.ServletException;
import javax.servlet.sip.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class Redirect extends SipServlet {
    static private Map<String, String> registrarDB;
    static private SipFactory sipFactory;
    static private ArrayList<String> colaboradores_ative;
    /**
     * SipServlet functions
     */
    @Override
    public void init() {
        sipFactory = (SipFactory) getServletContext().getAttribute(SIP_FACTORY);
        registrarDB = new HashMap<>();
        colaboradores_ative = new ArrayList<>();

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

            if (expires.equals("0")) {
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
            if (contactHeader.split("=")[1].equals("0")) {
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


    }

    //Aqui é que é feita a chamada
    @Override
    protected void doInvite(SipServletRequest request) throws IOException, TooManyHopsException, ServletParseException {
        String aor = getAttr(request.getHeader("To"), "sip:");
        String from = getAttr(request.getHeader("From"), "sip:");

        if (!registrarDB.containsKey(aor)) {
            if (!from.contains("colaborador") && registrarDB.containsKey("sip:gestor@acme.pt") && aor.equals("sip:alerta@acme.pt")) {
                request.getProxy().proxyTo(sipFactory.createURI(registrarDB.get("sip:gestor@acme.pt")));
            } else if(aor.equals("sip:conference@acme.pt") && (from.equals("sip:gestor@acme.pt") || colaboradores_ative.contains(from))) {
                request.getProxy().proxyTo(sipFactory.createURI("sip:conference@127.0.0.1:5070"));
            } else {
                request.createResponse(404).send();
            }
        } else {
            if(aor.equals("sip:gestor@acme.pt")){
                request.createResponse(403).send();
            } else {
                request.getProxy().proxyTo(sipFactory.createURI(registrarDB.get(aor)));
            }

        }
    }




    @Override
    protected void doMessage(SipServletRequest request) throws ServletException, IOException {
        String aorT = getAttr(request.getHeader("To"), "sip:");
        String aorF = getAttr(request.getHeader("From"), "sip:");
        if (registrarDB.containsKey(aorT) || aorT.contains("alerta")  ) {
            String content = request.getContent().toString();
            //Gestor para o Alerta
            if (verifyGestor(request) && aorT.contains("alerta")) {
                //ADD..................................................
                log("Lista pre ADD");
                for (int i = 0; i < colaboradores_ative.size(); i++) {
                    log(colaboradores_ative.get(i));
                }
                if (content.contains("ADD") && content.contains("colaborador")) {
                    if(!colaboradores_ative.contains(content.split(" ")[1])){
                        colaboradores_ative.add(content.split(" ")[1]);
                    }
                    log("Lista pos ADD");
                    for (int i = 0; i < colaboradores_ative.size(); i++) {
                        log(colaboradores_ative.get(i));
                    }
                    request.createResponse(200).send();
                }
                else if (content.contains("REMOVE")) {
                    log("Lista pre remove");
                    for (int i = 0; i < colaboradores_ative.size(); i++) {
                        log(colaboradores_ative.get(i));
                    }
                    colaboradores_ative.remove(content.split(" ")[1]);
                    log("Lista pos remove");
                    for (int i = 0; i < colaboradores_ative.size(); i++) {
                        log(colaboradores_ative.get(i));
                    }
                    request.createResponse(200).send();
                }
                /////////////////////////////////////////////////////////////////////////////////////////////////////
                else if (content.contains("CONF")) {
                    for (String c1 : colaboradores_ative){
                        SipServletRequest res = sipFactory.createRequest(
                                request.getApplicationSession(),
                                "MESSAGE",
                                "sip:alerta@acme.pt",
                                registrarDB.get(c1)
                        );
                        res.setContent("Conference, please call sip:conference@acme.pt", "text/plain");
                        res.send();
                    }
                    request.createResponse(200).send();
                }
                else if (content.contains("ALERTA")) {
                    for (String c : colaboradores_ative){
                        CreateMessage(request,registrarDB.get(c),"sip:gestor@acme.pt",content);
                    }
                    request.createResponse(200).send();
                }
                //////////////////////////////////////////////////////////////////////////////////////////

                else {
                    request.createResponse(403).send();
                }


            }
            else if (verifyComum(request) && aorT.contains("alerta")) {
                log("===========================================================================");
                request.getProxy().proxyTo(sipFactory.createURI(registrarDB.get("sip:gestor@acme.pt")));
                request.createResponse(200).send();
            } else{
                request.getProxy().proxyTo(sipFactory.createURI(registrarDB.get(aorT)));
                request.createResponse(200).send();
            }
        } else {
            request.createResponse(403).send();
        }
    }


    protected void CreateMessage(SipServletRequest request, String to, String From, String text) throws ServletParseException, IOException {
        SipServletRequest res = sipFactory.createRequest(request.getApplicationSession(), "MESSAGE", From, to);
        res.setContent(text.getBytes(), "text/plain");
        res.send();
        request.createResponse(200).send();
    }


    //alteração do seu estado (ligado(188)/não-ligado(190))
    @Override
    protected void doPublish(SipServletRequest sipServletRequest) throws ServletException, IOException {
        if (new String(sipServletRequest.getRawContent(), StandardCharsets.UTF_8).contains("<status><basic>open</basic></status>")) {


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


    protected boolean verifyComum(SipServletRequest request) {

        String toHeader = request.getHeader("From");
        if (!verifyGestor(request)) {
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
