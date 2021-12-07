package model;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.lang.acl.ACLMessage;

import java.util.*;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.*;


public class ResourceAllocationAgent extends Agent {

    private ArrayList<Task> toDoTasks = new ArrayList<>();

    private ArrayList<Task> doneTasks = new ArrayList<>();

    private HashMap<ResourceType, ArrayList<ResourceItem>> availableResources = new HashMap<>();

    private ArrayList<AID> otherAgents = new ArrayList<>();

    SimulationEngine simulationEngine = new SimulationEngine();

    @Override
    protected void setup() {

        // Printout a welcome message
        System.out.println("Hello World. I’m an agent!");
        System.out.println("My local-name is " + getAID().getLocalName());
//        System.out.println("My GUID is " + getAID().getName());
//        System.out.println("My addresses are:");
//        Iterator it = getAID().getAllAddresses();
//        while (it.hasNext()) {
//            System.out.println("- "+it.next());
//        }


        // Get ids of other agents as arguments
        Object[] args = getArguments();
        if (args != null && args.length > 0) {

            int numberOfAgents = (int) args[0];
            int myId = (int) args[1];

            for (int i = 1; i <= numberOfAgents; i++) {
                if ( i != myId) {
                    AID aid = new AID("Agent"+i, AID.ISLOCALNAME);
                    otherAgents.add(aid);
                }
            }
        }

//        System.out.println(getAID().getLocalName());
        if ( getAID().getLocalName().equals("Agent2")) {
            availableResources.put(ResourceType.A, simulationEngine.findResourceItems(ResourceType.A, 5, 100));
            availableResources.put(ResourceType.B, simulationEngine.findResourceItems(ResourceType.B, 5, 100));
            availableResources.put(ResourceType.AB, simulationEngine.findResourceItems(ResourceType.AB, 5, 100));
            availableResources.put(ResourceType.O, simulationEngine.findResourceItems(ResourceType.O, 5, 100));
        }

//        addBehaviour(new TickerBehaviour(this, 5000) {
//
//            protected void onTick() {
//
//                ResourceItem resourceItem = simulationEngine.findResourceItem();
//                availableResources.add(resourceItem);
//
//                System.out.println("My availableResources are:");
//
//                for (int i = 0; i < availableResources.size(); i++) {
//                    System.out.println(availableResources.get(i));
//                }
//            }
//        });


        addBehaviour(new OneShotBehaviour() {

            public void action() {
//                System.out.println(getAID().getLocalName());
                if ( getAID().getLocalName().equals("Agent1")) {

                    ArrayList<Task> newTasks = simulationEngine.findTasks();
                    toDoTasks.addAll(newTasks);

//                System.out.println( myAgent.getLocalName() + ": I have a new task to perform: " + newTask);
                }
            }
        });


        addBehaviour(new OneShotBehaviour() {

            public void action() {

                ArrayList<Task> blockedTasks = new ArrayList<>();

                for (Task task : toDoTasks) {
                    if (hasEnoughResources(task)) {
                        processTask (task);
                    }
                    else {
                        blockedTasks.add(task);
                    }
                }

                if (blockedTasks.size() > 0) {
                    createRequest( blockedTasks);
                }

//                System.out.println( myAgent.getLocalName() + ": I have a new task to perform: " + newTask);

            }
        });



//        addBehaviour(new OneShotBehaviour() {
//            @Override
//            public void action() {
//
//                ACLMessage msg = new ACLMessage(ACLMessage.REQUEST);
//
//                for (int i = 0; i < otherAgents.size(); i++) {
//                // Send this message to all other agents
//                    msg.addReceiver(otherAgents.get(i));
//                }
//
//                HashMap<String,String> fields = new HashMap<String,String>();
//                fields.put(Ontology.RESOURCE_REQUESTED_QUANTITY, "10");
//
////                msg.setLanguage("English");
////                msg.setOntology("Weather-forecast-ontology");
//
//                msg.setContent( fields.toString());
//
////                msg.setReplyByDate();
//
//                send(msg);
//                System.out.println("Message sent by " + myAgent.getLocalName());
//
//            }
//        });


        addBehaviour(new CyclicBehaviour() {
            @Override
            public void action() {
                ACLMessage msg = myAgent.receive();
                if (msg != null) {
                    // Message received. Process it
                    String content = msg.getContent();

                    switch (msg.getPerformative()) {
                        case ACLMessage.REQUEST:
                            System.out.println (myAgent.getLocalName() + " received a REQUEST message from " + msg.getSender().getLocalName() + " with content: " + content);

                            try {
                                processRequest(myAgent, msg);
                            } catch (ParseException e) {
                                e.printStackTrace();
                            }

                            break;

                        case ACLMessage.PROPOSE:
                            System.out.println (myAgent.getLocalName() + " received a PROPOSE message from " + msg.getSender().getLocalName() + " with content: " + content);


                            break;

                        case ACLMessage.ACCEPT_PROPOSAL:
                            System.out.println (myAgent.getLocalName() + " received a ACCEPT_PROPOSAL message from " + msg.getSender().getLocalName() + " with content: " + content);


                            break;

                        case ACLMessage.REFUSE:
                            System.out.println (myAgent.getLocalName() + " received a REFUSE message from " + msg.getSender().getLocalName() + " with content: " + content);

                            break;

                        case ACLMessage.REJECT_PROPOSAL:
                            System.out.println (myAgent.getLocalName() + " received a REJECT_PROPOSAL message from " + msg.getSender().getLocalName() + " with content: " + content);


                            break;

                        case ACLMessage.INFORM:
                            System.out.println (myAgent.getLocalName() + " received a INFORM message from " + msg.getSender().getLocalName() + " with content: " + content);


                            break;
                    }

                } else {
                    block();
                }
            }
        });


    }


    private void createRequest (ArrayList<Task> blockedTasks) {

        // creates a request based on the missing quantity for each resource type

        HashMap<ResourceType, Integer> totalRequiredResources = new HashMap();

        for (Task task : blockedTasks) {
            for (var entry : task.requiredResources.entrySet()) {
                totalRequiredResources.put(entry.getKey(),  totalRequiredResources.getOrDefault(entry.getKey(), 0) + entry.getValue());
            }
        }

        for (var entry : totalRequiredResources.entrySet()) {
            int missingQuantity;
            if ( availableResources.containsKey( entry.getKey())) {
                missingQuantity = entry.getValue() - availableResources.get(entry.getKey()).size();
            } else {
                missingQuantity = entry.getValue();
            }

            Map<Integer, Integer> utilityFunction = computeUtilityFunction (blockedTasks, entry.getKey(), missingQuantity);
            sendRequest (entry.getKey(), missingQuantity, utilityFunction);
        }

//        System.out.println( "This is a new request");
    }


    Map<Integer, Integer> computeUtilityFunction (ArrayList<Task> blockedTasks, ResourceType resourceType, int missingQuantity) {

//        HashMap<Integer, Integer> utilityFunction = new HashMap<>();

        Map<Integer, Integer> utilityFunction = new LinkedHashMap<>();

        //TODO: sort the tasks by their efficiency=utility/requiredResources or emergency

        for (int i=1; i<=missingQuantity; i++) {
            int q = i;
            int totalUtility = 0;
            for (Task task : blockedTasks) {
                if (task.requiredResources.containsKey(resourceType)) {
                    if (q >= task.requiredResources.get(resourceType)) {
                        totalUtility = totalUtility + task.utility;
                        q = q - task.requiredResources.get(resourceType);
                    }
                }
            }
            utilityFunction.put(i, totalUtility);
        }

        return utilityFunction;
    }


    private void sendRequest (ResourceType resourceType, int missingQuantity, Map<Integer, Integer> utilityFunction) {

        ACLMessage msg = new ACLMessage(ACLMessage.REQUEST);

        for (int i = 0; i < otherAgents.size(); i++) {
            // Send this message to all other agents
            msg.addReceiver(otherAgents.get(i));
        }

        JSONObject jo = new JSONObject();
        jo.put(Ontology.RESOURCE_REQUESTED_QUANTITY, missingQuantity);
        jo.put(Ontology.RESOURCE_TYPE, resourceType.name());
        jo.put(Ontology.TASKS_UTILITY_FUNCTION, utilityFunction);

        msg.setContent( jo.toJSONString());

//      msg.setReplyByDate();

        send(msg);

    }


    private void processRequest (Agent myAgent, ACLMessage msg) throws ParseException {

        // if agents operate and communicate asynchronously, then a request might be received at any time.
        // The bidder can wait for other requests before bidding.

        // if the rounds are synchronous, there can be more than one request, then we can consider two approaches:

        // Greedy approach:
        // sort requests based on their utilities, and while there are available resources,
        // create bid for request with the highest utility.

        // Optimal:
        // select the optimal combination of requests to maximize the utility.
        // max SUM xi Ui(j)
        // subject to:
        // xi in (0, 1)
        //  1 < j < qi
        // SUM xi <= 1

        String content = msg.getContent();

        Object obj = new JSONParser().parse(content);
        JSONObject jo = (JSONObject) obj;

        long requestedQuantity = (long) jo.get(Ontology.RESOURCE_REQUESTED_QUANTITY);
        System.out.println("Requested quantity is " + requestedQuantity);

        String rt = (String) jo.get(Ontology.RESOURCE_TYPE);
        ResourceType resourceType = ResourceType.valueOf(rt);
        Map utilityFunction = ((Map) jo.get(Ontology.TASKS_UTILITY_FUNCTION));
        int exp = 0;
        if (availableResources.get(resourceType) != null) {
            int availableQuantity = availableResources.get(resourceType).size();
            if (availableQuantity > 0) {
                long bidQuantity = 0;
                if (availableQuantity < requestedQuantity) {

                    bidQuantity = availableQuantity;
                } else {
                    bidQuantity = requestedQuantity;
                }

                exp = computeExpectedUtilityOfResources(resourceType, bidQuantity);

                String util = utilityFunction.get(String.valueOf(bidQuantity)).toString();

                if (exp < Integer.valueOf(util)) {

                    createBid(resourceType, bidQuantity);
                } else {
                    // discard or cascade the request
                }
            } else {
                // discard or cascade the request
            }
        } else {
            // discard or cascade the request
        }
    }


    private void createBid (ResourceType resourceType, long bidQuantity) {


        // compute the cost function


        System.out.println( "createBid for resourceType: " + resourceType.name() + " with bidQuantity: " + bidQuantity);
    }


    private void createConfirm () {

        System.out.println( "This is a new confirmation");
    }


    private HashMap<String,String> parseMessageContent (String content) {

        String mainDelim = ",";
        String fieldDelim = "=";

        StringBuilder sb = new StringBuilder(content);

        sb.deleteCharAt(0);
        sb.deleteCharAt(content.length()-2);

//        content = content.replace('{', '');

        content = sb.toString();

        String[] contentList = content.split(mainDelim);

        HashMap<String,String> fields = new HashMap<>();

        for (int i=0;i<contentList.length;i++)
        {
            String[] fieldTuple = contentList[i].split(fieldDelim);
            fields.put(fieldTuple[0], fieldTuple[1]);
        }

        return fields;
    }


    private boolean hasEnoughResources (Task task) {
        boolean enough = true;

        for (var entry : task.requiredResources.entrySet()) {
            if (availableResources.containsKey(entry.getKey()) == false) {
                enough = false;
                break;
            }
            else if (entry.getValue() > availableResources.get(entry.getKey()).size()) {
                enough = false;
                break;
            }
        }

        return enough;
    }


    void processTask (Task task) {

        try {

            for (var entry : task.requiredResources.entrySet()) {

                ArrayList<ResourceItem> resourceItems = availableResources.get(entry.getKey());

//                System.out.println("Size: " + resourceItems.size());

                for (int i = 0; i < entry.getValue(); i++) {
//                    System.out.println("Size: " + resourceItems.size());
                    resourceItems.remove(0);
                }

                availableResources.replace(entry.getKey(), resourceItems);

            }
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }

        toDoTasks.remove(task);
        doneTasks.add((task));
    }


    int computeExpectedUtilityOfResources ( ResourceType resourceType, long quantity ) {

        int exp = 0;
        ArrayList<Task> doneTasksWithThisResourceType = new ArrayList<>();
        int totalUtilityWithThisResourceType = 0;
        int totalQuantityOfThisResourceType = 0;

        for (Task task : doneTasks) {
            if (task.requiredResources.containsKey(resourceType)) {
                doneTasksWithThisResourceType.add( task);
                totalUtilityWithThisResourceType = totalUtilityWithThisResourceType + task.utility;
                totalQuantityOfThisResourceType = totalQuantityOfThisResourceType + task.requiredResources.get(resourceType);
            }
        }

        if (doneTasks.size() > 0 && totalQuantityOfThisResourceType > 0) {
            exp = (int) (quantity * (doneTasksWithThisResourceType.size() / doneTasks.size()) * (totalUtilityWithThisResourceType / totalQuantityOfThisResourceType));
        }

        return exp;
    }


}
