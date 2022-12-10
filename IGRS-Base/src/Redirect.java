import com.sun.org.apache.xpath.internal.functions.FuncFalse;

import javax.servlet.ServletException;
import javax.servlet.sip.*;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
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
            if (verifyComum(request) && registrarDB.containsKey("sip:gestor@acme.pt") && aor.equals("sip:alerta@acme.pt")) {
                request.getProxy().proxyTo(sipFactory.createURI(registrarDB.get("sip:gestor@acme.pt")));
            } else {
                request.createResponse(404).send();
            }
        } else {

            request.getProxy().proxyTo(sipFactory.createURI(registrarDB.get(aor)));

        }

    }


    @Override
    protected void doMessage(SipServletRequest request) throws ServletException, IOException {
        String aorT = getAttr(request.getHeader("To"), "sip:");
        String aorF = getAttr(request.getHeader("From"), "sip:");
        colaboradores_ative = new ArrayList<String>();
        log(aorT);

        if (registrarDB.containsKey(aorT) || aorT.contains("alerta")) {
            String content = request.getContent().toString();
            //Gestor para o Alerta
            if (verifyGestor(request) && aorT.contains("alerta")) {
                CreateMessage(request);
                //ADD..................................................
                if (content.contains("ADD") && content.contains("colaborador")) {
                    colaboradores_ative.add(content.split(":")[1].toString());
                }
                for (int i = 0; i < colaboradores_ative.size(); i++) {
                    log(colaboradores_ative.get(i));
                }

//...............................................................................
// Remove.................................................
                if (content.contains("REMOVE")) {
                    log("Lista pre remove");
                    for (int i = 0; i < colaboradores_ative.size(); i++) {
                        log(colaboradores_ative.get(i));
                    }
                    for (int i = 0; i < colaboradores_ative.size(); i++) {
                        if (colaboradores_ative.get(i).contains(content.split(":")[1].toString())) {
                            colaboradores_ative.remove(i);
                        }
                    }
                    log("Lista pos remove");
                    for (int i = 0; i < colaboradores_ative.size(); i++) {
                        log(colaboradores_ative.get(i));
                    }
                }
        }

            //........................................................................................

            //From Gestor To Colaborador
            if (verifyGestor(request) && aorT.contains("colaborador")) {
                log("-------------------------------------------");
                log(content);
                log("-------------------------------------------");
                CreateMessage(request);
            }

                //Colaborador com Colaborador
            if ( aorF.contains("colaborador")&& aorT.contains("colaborador")) {
                log("-------------------------------------------");
                log(content);
                log("-------------------------------------------");
                CreateMessage(request);
            }
                //Comum para o Alerta
            if (verifyComum(request) && aorT.contains("alerta")) {

                request.getProxy().proxyTo(sipFactory.createURI(registrarDB.get("sip:gestor@acme.pt")));
                request.createResponse(200).send();
            }
            //From Colaborador To Gestor
            if (aorT.contains("gestor") && aorF.contains("colaborador") ) {
                log("-------------------------------------------");
                log(content);
                log("-------------------------------------------");
                CreateMessage(request);
            }

        } else {
            request.createResponse(403).send();
        }
    }



    protected void CreateMessage(SipServletRequest request) throws ServletParseException, IOException {
        SipServletRequest res = sipFactory.createRequest(request.getApplicationSession(),"MESSAGE","source","destination");
        res.setContent("text".getBytes(), "text/plain");
        res.send();
        request.createResponse(200).send();
    }



    //alteração do seu estado (ligado(188)/não-ligado(190))
    @Override
    protected void doPublish(SipServletRequest sipServletRequest) throws ServletException, IOException {
        if(new String(sipServletRequest.getRawContent(), StandardCharsets.UTF_8).contains("<status><basic>open</basic></status>")){




        }
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


    protected boolean verifyComum(SipServletRequest request) {

        String toHeader = request.getHeader("From");
        if(!verifyGestor(request) ){
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
