import com.sun.org.apache.xpath.internal.functions.FuncFalse;

import javax.servlet.ServletException;
import javax.servlet.sip.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
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

        log(aorT);

        if (!registrarDB.containsKey(aorT) || aorT.contains("alerta")) {

            //Gestor para Colaborador
            if (verifyGestor(request) && aorT.contains("colaborador")) {


                String content = request.getContent().toString(); ////////RECEBR A MENSAGEM
                log("-------------------------------------------");
                log(content);
                log("-------------------------------------------");
                request.createResponse(200).send();


                //Colaborador com Colaborador
            } else if (verifyComum(request) && aorT.contains("colaborador")) {

                //Analisar o conteúdo da mensagem -perceber se é uma ADD (adicionar colaborador) ou REMOVE (remover o colaborador) ou CONFERENCE
                String content = request.getContent().toString();
                log("-------------------------------------------");
                log(content);
                log("-------------------------------------------");
                request.createResponse(200).send();


                //Gestor para o Alerta
            } else if (verifyGestor(request) && aorT.contains("alerta")) {

                //Analisar o conteúdo da mensagem -perceber se é uma ADD (adicionar colaborador) ou REMOVE (remover o colaborador) ou CONFERENCE

                //Percorrer o hashMap RegistrarDB e aplicar os comandos ao Colaboradores, se é para adicionar o colaborador ou removê-lo

                request.createResponse(200).send();


                //Colaborador para o Alerta
            } else if (verifyComum(request) && aorT.contains("alerta")) {

                request.getProxy().proxyTo(sipFactory.createURI(registrarDB.get("sip:gestor@acme.pt")));
                request.createResponse(200).send();

            }

        } else {

            request.createResponse(403).send();

        }
    }



    /*  //Criar uma mensagem
            SipServletRequest res = sipFactory.createRequest(
                    request.getApplicationSession(),
                    "MESSAGE",
                    "source",
                    "destination"
            );
            res.setContent("text".getBytes(), "text/plain");
            res.send();
            request.createResponse(200).send();*/




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
