import java.io.*;
import java.util.*;

import org.w3c.dom.*;

import javax.xml.parsers.*;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

class Main {
    public static void main(String[] args) {
        String modelFile = "Model5.pnml";
        String logFile = "Log5.pnml";
        getLogOfModel(modelFile, logFile);
    }

    public static void getLogOfModel(String modelFile, String logFile) {
        readFileAndInitGraph(modelFile);
        findLogs();
        writeFile(logFile);
    }

    private static ArrayList<NNode> graph = new ArrayList<NNode>();
    private static ArrayList<ArrayList<NNode>> cases = new ArrayList<ArrayList<NNode>>();

    private static void readFileAndInitGraph(String modelFile) {
        NodeList placeList = null;
        NodeList transList = null;
        NodeList arcList = null;
        try {
            File f = new File(modelFile);
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(f);
            placeList = doc.getElementsByTagName("place");
            transList = doc.getElementsByTagName("transition");
            arcList = doc.getElementsByTagName("arc");
        } catch (Exception e) {
            e.printStackTrace();
        }
        Map<String, NNode> map = new HashMap<String, NNode>();
        for (int i = 0; i < placeList.getLength(); i++) {
            Element place = (Element) placeList.item(i);
            String id = place.getAttribute("id");
            String name = place.getElementsByTagName("name").item(0).getTextContent().trim();
            NNode nn = new NNode(id, name, Kind.PLACE);
            graph.add(nn);
            map.put(id, nn);
        }
        for (int i = 0; i < transList.getLength(); i++) {
            Element trans = (Element) transList.item(i);
            String id = trans.getAttribute("id");
            String name = trans.getElementsByTagName("name").item(0).getTextContent().trim();
            NNode nn = new NNode(id, name, Kind.TRANSITION);
            graph.add(nn);
            map.put(id, nn);
        }
        for (int i = 0; i < arcList.getLength(); i++) {
            Node arc = arcList.item(i);
            NamedNodeMap namedNodeMap = arc.getAttributes();
            String source = namedNodeMap.getNamedItem("source").getTextContent();
            String target = namedNodeMap.getNamedItem("target").getTextContent();
            NNode s = map.get(source);
            NNode t = map.get(target);
            s.suf.add(t);
            t.pre.add(s);
        }
    }

    private static void findLogs() {
        NNode begin = null;
        NNode end = null;
        for (NNode n : graph) {
            if (n.pre.size() == 0)
                begin = n;
            if (n.suf.size() == 0)
                end = n;
        }

        assert begin != null;

        Map<NNode, Integer> hasToken = new HashMap<>();
        begin.token++;
        for (NNode n : graph)
            if (n.kind == Kind.PLACE)
                hasToken.put(n, n.token);


        ArrayList<NNode> cas = new ArrayList<>();

        Stack<State> state = new Stack<>();
        for (NNode n : begin.suf)
            state.push(new State(n, hasToken, cas));


        while (!state.empty()) {
            State curState = state.pop();

            Map<NNode, Integer> curHasToken = curState.hasToken;
            for (NNode n : graph)
                if (n.kind == Kind.PLACE)
                    n.token = curHasToken.get(n);

            NNode curTrans = curState.transition;
            for (NNode n : curTrans.pre) n.token--;
            for (NNode n : curTrans.suf) n.token++;


            ArrayList<NNode> curCase = new ArrayList<>(curState.curCase);
            curCase.add(curTrans);

            Map<NNode, Integer> newHasToken = new HashMap<>();
            for (NNode n : graph)
                if (n.kind == Kind.PLACE)
                    newHasToken.put(n, n.token);

            if (newHasToken.get(end) == 1) {
                cases.add(curCase);
                continue;
            }

            for (NNode n : graph) {
                if (n.kind == Kind.TRANSITION) {
                    boolean isAC = true;
                    for (NNode nn : n.pre)
                        if (newHasToken.get(nn) == 0)
                            isAC = false;
                    boolean isCircle = false;
                    if (curCase.contains(n)) {
                        isCircle = isCircle(curCase, n);
                    }
                    if (isAC && !isCircle) {
                        State newState = new State(n, newHasToken, curCase);
                        state.push(newState);
                    }
                }
            }
        }

    }

    private static boolean isCon(NNode f, NNode t){
        for (NNode n: f.suf) 
            if(n.suf.contains(t))
                return true;
        return false;
    }

    private static boolean isCircle(ArrayList<NNode> road, NNode dup) {
        if (isCon(dup, dup))
            return true;

        ArrayList<NNode> circle = new ArrayList<>();
        circle.add(dup);
        int i;
        for (i = road.size()-1; i >= (road.size()+1)/2; i--) {
            NNode node = road.get(i);
            NNode first = circle.get(0);
            NNode last = circle.get(circle.size()-1);
            if (isCon(node, first))
                circle.add(0, node);
            else
                continue;
            if(isCon(last, node))
                break;
        }
        int size = circle.size();
        int count = 0;
        for (NNode nNode : circle) {
            for (int k = 0; k < i; k++) {
                if (road.get(k) == nNode) {
                    count++;
                    break;
                }
            }
        }
        return count == size;
    }

    private static void writeFile(String logFile) {
        System.out.println(cases.size());
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.newDocument();
            Element root = doc.createElement("cases");
            root.setAttribute("size", Integer.toString(cases.size()));
            for (int i = 0; i < cases.size(); i++) {
                ArrayList<NNode> arr = cases.get(i);
                Element cas = doc.createElement("case");
                cas.setAttribute("id", Integer.toString(i));
                cas.setAttribute("length", Integer.toString(arr.size()));
                for (NNode nn : arr) {
                    Element n = doc.createElement("node");
                    n.setAttribute("id", nn.id);
                    n.setAttribute("name", nn.name);
                    n.setAttribute("kind", nn.kind.toString());
                    cas.appendChild(n);
                }
                root.appendChild(cas);
            }
            doc.appendChild(root);

            TransformerFactory factory2 = TransformerFactory.newInstance();
            Transformer transformer = factory2.newTransformer();
            DOMSource domSource = new DOMSource(doc);
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            File f = new File(logFile);
            StreamResult result = new StreamResult(new FileOutputStream(f));
            transformer.transform(domSource, result);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static class State {
        NNode transition;
        Map<NNode, Integer> hasToken;
        ArrayList<NNode> curCase;

        State(NNode transition, Map<NNode, Integer> hasToken, ArrayList<NNode> curCase) {
            this.transition = transition;
            this.hasToken = hasToken;
            this.curCase = curCase;
        }
    }

    private static class NNode {
        String id;
        String name;
        Enum kind;
        int token;
        ArrayList<NNode> pre;
        ArrayList<NNode> suf;

        NNode(String id, String name, Enum kind) {
            this.id = id;
            this.name = name;
            this.kind = kind;
            this.token = 0;
            this.pre = new ArrayList<NNode>();
            this.suf = new ArrayList<NNode>();
        }

    }

    private enum Kind {
        PLACE, TRANSITION
    }
}