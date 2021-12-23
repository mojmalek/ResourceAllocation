package model;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.lang.acl.ACLMessage;

import java.util.*;

import org.json.simple.JSONObject;
import org.json.simple.parser.*;


public class ResourceAllocationAgent extends Agent {

    private ArrayList<Task> toDoTasks = new ArrayList<>();

    private ArrayList<Task> doneTasks = new ArrayList<>();

    private ArrayList<Task> blockedTasks = new ArrayList<>();

    private Map<ResourceType, PriorityQueue<ResourceItem>> availableResources = new LinkedHashMap<>();

    private ArrayList<AID> otherAgents = new ArrayList<>();

    // reqId
    public Map<String, Set<Bid>> receivedBids = new LinkedHashMap<>();

    // reqId
    public Map<String, Request> sentRequests = new LinkedHashMap<>();

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
            PriorityQueue<ResourceItem> resourceItems = new PriorityQueue<>(new ResourceItem.resourceItemComparator());;
            resourceItems.addAll(simulationEngine.findResourceItems(ResourceType.A, 10, 50));
            resourceItems.addAll(simulationEngine.findResourceItems(ResourceType.A, 9, 30));
            resourceItems.addAll(simulationEngine.findResourceItems(ResourceType.A, 8, 20));
            availableResources.put(ResourceType.A, resourceItems);


//            availableResources.put(ResourceType.B, simulationEngine.findResourceItems(ResourceType.B, 20, 100));
//            availableResources.put(ResourceType.AB, simulationEngine.findResourceItems(ResourceType.AB, 10, 100));
//            availableResources.put(ResourceType.O, simulationEngine.findResourceItems(ResourceType.O, 3, 100));
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

                for (Task task : toDoTasks) {
                    if (hasEnoughResources(task)) {
                        processTask (task);
                    }
                    else {
                        blockedTasks.add(task);
                    }
                }

                if (blockedTasks.size() > 0) {
                    createRequest( blockedTasks, myAgent);
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
                            System.out.println (myAgent.getLocalName() + " received a BID message from " + msg.getSender().getLocalName() + " with content: " + content);

                            try {
                                storeBid(myAgent, msg);
                            } catch (ParseException e) {
                                e.printStackTrace();
                            }

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


    private void createRequest (ArrayList<Task> blockedTasks, Agent myAgent) {

        // creates a request based on the missing quantity for each resource type

        Map<ResourceType, Integer> totalRequiredResources = new LinkedHashMap<>();

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
            sendRequest (entry.getKey(), missingQuantity, utilityFunction, myAgent);
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


    private void sendRequest (ResourceType resourceType, int missingQuantity, Map<Integer, Integer> utilityFunction, Agent myAgent) {

        ACLMessage msg = new ACLMessage(ACLMessage.REQUEST);

        for (int i = 0; i < otherAgents.size(); i++) {
            // Send this message to all other agents
            msg.addReceiver(otherAgents.get(i));
        }

        String reqId = UUID.randomUUID().toString();

        JSONObject jo = new JSONObject();
        jo.put("reqId", reqId);
        jo.put(Ontology.RESOURCE_REQUESTED_QUANTITY, missingQuantity);
        jo.put(Ontology.RESOURCE_TYPE, resourceType.name());
        jo.put(Ontology.TASKS_UTILITY_FUNCTION, utilityFunction);

        msg.setContent( jo.toJSONString());

//      msg.setReplyByDate();

        send(msg);

        sentRequests.put (reqId, new Request(reqId, missingQuantity, resourceType, utilityFunction, myAgent.getLocalName()));

    }


    private void processRequest (Agent myAgent, ACLMessage request) throws ParseException {

        // if agents operate and communicate asynchronously, then a request might be received at any time.
        // the bidder can wait for other requests before bidding.

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

        String content = request.getContent();

        Object obj = new JSONParser().parse(content);
        JSONObject jo = (JSONObject) obj;

        String reqId = (String) jo.get("reqId");

        long requestedQuantity = (long) jo.get(Ontology.RESOURCE_REQUESTED_QUANTITY);
        System.out.println("Requested quantity is " + requestedQuantity);

        String rt = (String) jo.get(Ontology.RESOURCE_TYPE);
        ResourceType resourceType = ResourceType.valueOf(rt);
        Map utilityFunction = (Map) jo.get(Ontology.TASKS_UTILITY_FUNCTION);
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

                exp = computeExpectedUtilityOfResources(resourceType, bidQuantity, availableResources.get(resourceType));

                String util = utilityFunction.get(String.valueOf(bidQuantity)).toString();

                if (exp < Integer.valueOf(util)) {

                    createBid(reqId, resourceType, bidQuantity, availableResources.get(resourceType));
                } else {
                    // reject or cascade the request
                }
            } else {
                // reject or cascade the request
            }
        } else {
            // reject or cascade the request
        }
    }


    private void createBid (String reqId, ResourceType resourceType, long bidQuantity, PriorityQueue<ResourceItem> resourceItems) {

        Map<Integer, Integer> costFunction = new LinkedHashMap<>();
        for (int q=1; q<=bidQuantity; q++) {
            int cost = computeExpectedUtilityOfResources(resourceType, q, resourceItems);
            costFunction.put(q, cost);
        }

        Map<String, Integer> offeredItems = new LinkedHashMap<>();
        for (int q=1; q<=bidQuantity; q++) {
            ResourceItem item = resourceItems.poll();
            offeredItems.put(item.getId(), item.getLifetime());
        }

        sendBid(reqId, resourceType, bidQuantity, costFunction, offeredItems);

        System.out.println( "createBid for resourceType: " + resourceType.name() + " with bidQuantity: " + bidQuantity);
    }


    private void sendBid (String reqId, ResourceType resourceType, long bidQuantity, Map<Integer, Integer> costFunction, Map<String, Integer> offeredItems) {

        ACLMessage msg = new ACLMessage(ACLMessage.PROPOSE);

        for (int i = 0; i < otherAgents.size(); i++) {
            // Send this message to all other agents
            msg.addReceiver(otherAgents.get(i));
        }

        JSONObject jo = new JSONObject();
        jo.put("reqId", reqId);
        jo.put(Ontology.RESOURCE_BID_QUANTITY, bidQuantity);
        jo.put(Ontology.RESOURCE_TYPE, resourceType.name());
        jo.put(Ontology.BID_COST_FUNCTION, costFunction);
        jo.put(Ontology.BID_OFFERED_ITEMS, offeredItems);

        msg.setContent( jo.toJSONString());

//      msg.setReplyByDate();

        send(msg);
    }


    private void storeBid (Agent myAgent, ACLMessage msg) throws ParseException {

        // if agents operate and communicate asynchronously, then a bid might be received at any time.
        // the requester can wait for other bids before confirming.

        String content = msg.getContent();

        Object obj = new JSONParser().parse(content);
        JSONObject jo = (JSONObject) obj;

        long bidQuantity = (long) jo.get(Ontology.RESOURCE_BID_QUANTITY);
        System.out.println("Requested quantity is " + bidQuantity);

        String reqId = (String) jo.get("reqId");
        String rt = (String) jo.get(Ontology.RESOURCE_TYPE);
        ResourceType resourceType = ResourceType.valueOf(rt);
        Map costFunction = ((Map) jo.get(Ontology.BID_COST_FUNCTION));
        Map offeredItems = ((Map) jo.get(Ontology.BID_OFFERED_ITEMS));

        String bidId = UUID.randomUUID().toString();
        Bid bid = new Bid(bidId, reqId, bidQuantity, resourceType, costFunction, offeredItems, msg.getSender().getLocalName(), myAgent.getLocalName());

        Set<Bid> bids = receivedBids.get(reqId);
        if (bids != null) {
            bids.add( bid);
        }
        else {
            bids = new HashSet<>();
        }

        receivedBids.put( reqId, bids);

    }


    void confirmBids () {

        Map<Request, Map<Bid, Integer>> selectedBidsForAllRequests = new LinkedHashMap<>();

        for (var request : sentRequests.entrySet()) {
            if ( receivedBids.containsKey(request.getKey())) {
                Map<Bid, Integer> selectedBids = processBids( request.getValue());
                selectedBidsForAllRequests.put( request.getValue(), selectedBids);
            }
        }

        Map<ResourceType, PriorityQueue<ResourceItem>> allResources = availableResources;

        for (var selectedBids : selectedBidsForAllRequests.entrySet()) {
            Map<Bid, Integer> bidQuantities = selectedBids.getValue();

//            allResources.put( selectedBids.getKey().resourceType, availableResources.get(selectedBids.getKey().resourceType) + );
        }



    }


    public Map<Bid, Integer> processBids (Request request) {

        // the requester selects the combination of bids that maximizes the difference between the utility of request and the total cost of all selected bids.
        // it is allowed to take partial amounts of oﬀered resources in multiple bids up to the requested amount.

        // a greedy approach: we add 1 item from one bid in a loop up to the requested amount, without backtracking.

        Set<Bid> bids = receivedBids.get(request.id);

        int netBenefit = 0;
        int totalCosts = 0;
        Map<Bid, Integer> selectedBids = new LinkedHashMap<>();

        for (int q=1; q<=request.quantity; q++) {

            Bid lowCostBid = null;

            for (Bid bid : bids) {
                if (hasExtraItem(bid, selectedBids)) {
                    totalCosts = totalCosts(bid, selectedBids);
                    if (request.utilityFunction.get(q) - totalCosts > netBenefit) {
                        netBenefit = request.utilityFunction.get(q) - totalCosts;
                        lowCostBid = bid;
                    }
                }
            }

            if (lowCostBid != null) {
                if (selectedBids.containsKey(lowCostBid)) {
                    selectedBids.put(lowCostBid, selectedBids.get(lowCostBid) + 1);
                } else {
                    selectedBids.put(lowCostBid, 1);
                }
            } else {
                break;
            }
        }

        return selectedBids;
    }


    private boolean hasExtraItem (Bid bid, Map<Bid, Integer> selectedBids) {

        if ( selectedBids.containsKey(bid)) {
            if (selectedBids.get(bid) < bid.quantity) {
                return true;
            } else {
                return false;
            }
        } else {
            return true;
        }
    }


    private int totalCosts (Bid bid, Map<Bid, Integer> selectedBids) {

        int totalCosts = 0;

        Map<Bid, Integer> tempBids =  new LinkedHashMap<>();
        for (var entry : selectedBids.entrySet()) {
            tempBids.put(entry.getKey(), entry.getValue());
        }

        if (tempBids.containsKey(bid)) {
            tempBids.put(bid, tempBids.get(bid) + 1);
        } else {
            tempBids.put(bid, 1);
        }

        for (var entry : tempBids.entrySet()) {
            totalCosts = totalCosts + entry.getKey().costFunction.get(entry.getValue());
        }

        return totalCosts;
    }


    public Set<Set<Bid>> getSubsets(Set<Bid> set) {
        if (set.isEmpty()) {
            return Collections.singleton(Collections.emptySet());
        }

        Set<Set<Bid>> subSets = set.stream().map(item -> {
                    Set<Bid> clone = new HashSet<>(set);
                    clone.remove(item);
                    return clone;
                }).map(group -> getSubsets(group))
                .reduce(new HashSet<>(), (x, y) -> {
                    x.addAll(y);
                    return x;
                });

        subSets.add(set);
        return subSets;
    }


    private void createConfirm () {

        System.out.println( "This is a new confirmation");
    }


//    private HashMap<String,String> parseMessageContent (String content) {
//
//        String mainDelim = ",";
//        String fieldDelim = "=";
//
//        StringBuilder sb = new StringBuilder(content);
//
//        sb.deleteCharAt(0);
//        sb.deleteCharAt(content.length()-2);
//
////        content = content.replace('{', '');
//
//        content = sb.toString();
//
//        String[] contentList = content.split(mainDelim);
//
//        HashMap<String,String> fields = new HashMap<>();
//
//        for (int i=0;i<contentList.length;i++)
//        {
//            String[] fieldTuple = contentList[i].split(fieldDelim);
//            fields.put(fieldTuple[0], fieldTuple[1]);
//        }
//
//        return fields;
//    }


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
                PriorityQueue<ResourceItem> resourceItems = availableResources.get(entry.getKey());
                for (int i = 0; i < entry.getValue(); i++) {
                    resourceItems.poll();
                }
                availableResources.replace(entry.getKey(), resourceItems);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        toDoTasks.remove(task);
        doneTasks.add((task));
    }


    int computeExpectedUtilityOfResources ( ResourceType resourceType, long quantity, PriorityQueue<ResourceItem> resourceItems) {

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
            for (int q = 1; q <= quantity; q++) {
                ResourceItem item = resourceItems.poll();
                exp = exp + (item.getLifetime() * (doneTasksWithThisResourceType.size() / doneTasks.size()) * (totalUtilityWithThisResourceType / totalQuantityOfThisResourceType));
            }
        }

//        if (doneTasks.size() > 0 && totalQuantityOfThisResourceType > 0) {
//            exp = (int) (quantity * (doneTasksWithThisResourceType.size() / doneTasks.size()) * (totalUtilityWithThisResourceType / totalQuantityOfThisResourceType));
//        }

        return exp;
    }


}
