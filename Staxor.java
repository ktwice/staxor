package eisfile;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/**
 * XMLStreamReader Helper
 * @author ktwice
 */
public class Staxor implements AutoCloseable {
    private static final XMLInputFactory FACTORY = XMLInputFactory.newInstance();
    private final List<String> ePath = new ArrayList<>();
    private final XMLStreamReader reader;
    private int eDeep = 0; // element-deep of current element-tree position
    private int eStep = 0; // last move (+step(dive) into deep, -back(up) from deep)
    
    private void _back(int istep) throws XMLStreamException {
        for(;;) {
            int e = reader.next();
            if(e == XMLStreamConstants.START_ELEMENT) --istep;
            else if(e == XMLStreamConstants.END_ELEMENT)
                if(++istep == 0) return;
        } 
    }
    private String _stepFinish(String ename) throws XMLStreamException {
        if(ePath.size() == eDeep) ePath.add(ename);
        else ePath.set(eDeep, ename);
        eDeep += (eStep = 1);
        return ename;
    }
    public String step() throws XMLStreamException {
        if(eDeep == 0 && eStep < 0) return null; // eof
        for(;;) {
            int e = reader.next();
            if(e == XMLStreamConstants.START_ELEMENT) 
                return _stepFinish(reader.getLocalName());
            if(e == XMLStreamConstants.END_ELEMENT) {
                eDeep += (eStep = -1); // back finish
                return null;
            }
        }
    }
    public String step(String name) throws XMLStreamException {
        if(eDeep == 0 && eStep < 0) return null; // eof
        int deep = eDeep; // root ?
        for(;;) {
            int e = reader.next();
            if(e == XMLStreamConstants.START_ELEMENT) {
                String ename = reader.getLocalName();
                if(ename.equalsIgnoreCase(name)) return _stepFinish(ename);
                _back(-1);
                if(deep == 0) {// single root
                    eDeep += (eStep = -1); // back finish
                    return null;
                }
            } else if(e == XMLStreamConstants.END_ELEMENT) {
                eDeep += (eStep = -1); // back finish
                return null;
            }
        }
    }
    public String back() throws XMLStreamException {
        String text = "";
        for(;;) {
            int e = reader.next();
            if(e == XMLStreamConstants.CHARACTERS) {
                text += reader.getText();
            } else if(e == XMLStreamConstants.END_ELEMENT) {
                eDeep += (eStep = -1); // back finish
                return text; // text-only element
            } else if(e == XMLStreamConstants.START_ELEMENT) {
                _stepFinish(reader.getLocalName());
                return null; // ignore text if childs
            }
        } 
    }
    public int path(String[] path) throws XMLStreamException, Exception {
        int i = 0;
        for(;;) {
            String name = path[i];
            if(("*".equals(name) ? step() : step(name)) != null) {
                if(++i == path.length) return eDeep; // saved deep
            }
            else if(--i < 0) return -1;
        }
    }
    public int path(String[] path, int pathDeep) throws XMLStreamException {
        int deep = pathDeep - 1; // target deep
        if(eDeep < deep) return -1; // path-control zone
        if(eDeep > deep) _back(deep - eDeep);
        int i = path.length - 1; // target path-index
        for(;;) {
            String name = path[i];
            if(("*".equals(name) ? step() : step(name)) != null) {
                if(++i == path.length) return eDeep;
            }
            else if(--i < 0) return -1;
        }
    } 
    public int scan(String name) throws XMLStreamException {
        int deep = eDeep; // scan-deep stop
        for(;;) {
            if(name.equalsIgnoreCase(step())) return deep; // for next scan
            if(eDeep < deep) return -1; // check scan-deep stop
        }
    }
    public int scan(String name, int deep) throws XMLStreamException {
        for(;;) {
            if(eDeep < deep) return -1; // deep=0 for non-stop
            if(name.equalsIgnoreCase(step())) return deep;
        }
    }
    public void back(int istep) throws XMLStreamException, Exception {
        if(istep >= 0) throw new Exception("Non negative back()");
        if(eDeep == 0 && eStep < 0) return; // eof
        if(eDeep + istep < 0) istep = -eDeep; // root-stop correction
        _back(istep);
        eDeep += (eStep = istep);
    }
    public void backTo(int targetDeep) throws XMLStreamException, Exception {
        Staxor.this.back(targetDeep - eDeep);
    }
    public Staxor(InputStream is) throws XMLStreamException {
       reader = FACTORY.createXMLStreamReader(is);
    }
    public XMLStreamReader getReader() {
       return reader;
    }
    public String[] getPath() {
        return ePath.subList(0, eDeep).toArray(new String[0]);
    }
    public int getDeep() {
        return eDeep;
    }
    public boolean hasStep() {
        return !(eDeep == 0 && eStep < 0); // not eof
    }
    public String getDeepName() {
        if(eDeep <= 0) return null; // no deep
        return ePath.get(eDeep - 1); // deeper name
    }
    public int getStep() {
        return eStep;
    }
    @Override
    public void close() {
        if (reader == null) return;
        try { reader.close(); }
        catch (XMLStreamException e) { }
    }
    public void print() throws XMLStreamException {
        String margin = " ";
        while(hasStep()) { // simple check eof
            String name = step(); // try to deep for child-element name ...
            if(name == null){ // ... but is up
                for(int i=0; i<getDeep(); i++) System.out.print(margin); 
                System.out.print("</"+reader.getLocalName()+">");
                System.out.println();
                continue;
            }
            for(int i=1; i<getDeep(); i++) System.out.print(margin); 
            System.out.print("<"+name); // name == getDeepName()
            int acount = reader.getAttributeCount();
            if(acount > 0) System.out.print(" a=\""+acount+"\"");
            String text;
            while((text = back()) == null) { // try to up for full-text, but dive
                System.out.print(">");
                System.out.println();
                for(int i=1; i<getDeep(); i++) System.out.print(margin); 
                System.out.print("<"+getDeepName()); // new child-element name
                acount = reader.getAttributeCount();
                if(acount > 0) System.out.print(" a=\""+acount+"\"");
            } 
            if(text.length() > 0)
                System.out.print(">" + text.length() + "</" + reader.getLocalName() + ">");
            else System.out.print("/>");
            System.out.println();
        }
    }
}
