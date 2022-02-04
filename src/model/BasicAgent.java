package model;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.TickerBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.util.*;


public class BasicAgent extends Agent {

    SimulationEngine simulationEngine = new SimulationEngine();

    private ArrayList<AID> otherAgents = new ArrayList<>();
    private Map<AID, ProtocolPhase> otherAgentsPhases = new LinkedHashMap<>();

    private SortedSet<Task> toDoTasks = new TreeSet<>(new Task.taskComparator());
    private SortedSet<Task> blockedTasks = new TreeSet<>(new Task.taskComparator());
    private SortedSet<Task> doneTasks = new TreeSet<>(new Task.taskComparator());
    private int totalUtil;

    private Map<ResourceType, SortedSet<ResourceItem>> availableResources = new LinkedHashMap<>();
    private Map<ResourceType, ArrayList<ResourceItem>> expiredResources = new LinkedHashMap<>();

    // reqId
    public Map<String, Request> sentRequests = new LinkedHashMap<>();
    public Map<ResourceType, ArrayList<Request>> receivedRequests = new LinkedHashMap<>();
    // bidId
    public Map<String, Bid> sentBids = new LinkedHashMap<>();
    // reqId
    public Map<String, Set<Bid>> receivedBids = new LinkedHashMap<>();


    @Override
    protected void setup() {

        System.out.println("Hello World. I’m a Basic agent! My local-name is " + getAID().getLocalName());
        // Get ids of other agents as arguments
        Object[] args = getArguments();
        if (args != null && args.length > 0) {
            int numberOfAgents = (int) args[0];
            int myId = (int) args[1];
            for (int i = 1; i <= numberOfAgents; i++) {
                if ( i != myId) {
                    AID aid = new AID("Agent"+i, AID.ISLOCALNAME);
                    otherAgents.add(aid);
                    otherAgentsPhases.put(aid, ProtocolPhase.REQUESTING);
                }
            }
        }

        addBehaviour (new TickerBehaviour(this, 1) {
            protected void onTick() {
                if (this.getTickCount() < 1001) {
                    System.out.println( myAgent.getLocalName() + " Round: " + this.getTickCount());

                    findTasks(myAgent);
                    findResources(myAgent);
                    negotiate(myAgent);
                    performTasks(myAgent);
                }
            }
        });
    }


    private void findTasks(Agent myAgent) {

//        System.out.println (myAgent.getLocalName() + " is finding tasks.");

        SortedSet<Task> newTasks = simulationEngine.findTasks( myAgent);
        toDoTasks.addAll(newTasks);

        sendNewTasksToMasterAgent (newTasks, myAgent);

//        System.out.println (myAgent.getLocalName() + " has " + toDoTasks.size() + " tasks to do.");
    }


    private void findResources(Agent myAgent) {

//        System.out.println (myAgent.getLocalName() + " is finding resources.");

        // decrease lifetime of remaining resources
        perishResourceItems( myAgent);

        Map<ResourceType, SortedSet<ResourceItem>> newResources = simulationEngine.findResources( myAgent);

        // add to available resources
        for (var newResource : newResources.entrySet()) {
            if (availableResources.containsKey(newResource.getKey())) {
                SortedSet<ResourceItem> availableItems = availableResources.get( newResource.getKey());
                availableItems.addAll( newResource.getValue());
                availableResources.put( newResource.getKey(), availableItems);
            } else {
                availableResources.put( newResource.getKey(), newResource.getValue());
            }
        }

//        for (var entry : availableResources.entrySet()) {
//            System.out.println( myAgent.getLocalName() + " has " + entry.getValue().size() + " available item of type: " + entry.getKey().name());
//        }

        sendNewResourcesToMasterAgent (newResources, myAgent);
    }


    void perishResourceItems( Agent myAgent) {

        SortedSet<ResourceItem> availableItems;
        ArrayList<ResourceItem> expiredItems;
        ArrayList<ResourceItem> expiredItemsInThisRound = new ArrayList<>();
        for (var resource : availableResources.entrySet()) {
            availableItems = availableResources.get( resource.getKey());
            if (expiredResources.containsKey( resource.getKey())) {
                expiredItems = expiredResources.get( resource.getKey());
            } else {
                expiredItems = new ArrayList<>();
                expiredResources.put( resource.getKey(), expiredItems);
            }
            for (ResourceItem item : availableItems) {
                item.setLifetime( item.getLifetime() - 1);
                if (item.getLifetime() == 0) {
                    expiredItemsInThisRound.add( item);
                    expiredItems.add( item);
                }
            }
            availableItems.removeAll( expiredItemsInThisRound);
//            expiredResources.put( resource.getKey(), expiredItems);
//            availableResources.put( resource.getKey(), availableItems);
        }

        for (var entry : expiredResources.entrySet()) {
            System.out.println( myAgent.getLocalName() + " has " + entry.getValue().size() + " expired item of type: " + entry.getKey().name());
        }
    }


    private void negotiate (Agent myAgent) {

//        System.out.println (myAgent.getLocalName() +  " is negotiating.");
        resetRound();
        deliberateOnRequesting (myAgent);
        sendNextPhaseNotification (ProtocolPhase.BIDDING);
        waitForRequests( myAgent);
        if (receivedRequests.size() > 0) {
            deliberateOnBidding( myAgent);
        }
        sendNextPhaseNotification (ProtocolPhase.CONFORMING);
        waitForBids( myAgent);
        if (receivedBids.size() > 0) {
            deliberateOnConfirming( myAgent);
        }
        sendNextPhaseNotification (ProtocolPhase.REQUESTING);
        waitForConfirmations( myAgent);
    }


    void deliberateOnRequesting (Agent myAgent) {

        Map<ResourceType, SortedSet<ResourceItem>> remainingResources = deepCopyResourcesMap( availableResources);

        for (Task task : toDoTasks) {
            if (hasEnoughResources(task, remainingResources)) {
                remainingResources = evaluateTask( task, remainingResources);
            } else {
                blockedTasks.add((task));
            }
        }

        if (blockedTasks.size() > 0) {
            createRequests( blockedTasks, remainingResources, myAgent);
        }
    }


    void sendNextPhaseNotification (ProtocolPhase phase) {

        ACLMessage msg = new ACLMessage(ACLMessage.INFORM);

        for (int i = 0; i < otherAgents.size(); i++) {
            // Send this message to all other agents
            msg.addReceiver(otherAgents.get(i));
        }

        JSONObject jo = new JSONObject();
        jo.put(Ontology.PROTOCOL_PHASE, phase.name());

        msg.setContent( jo.toJSONString());
        send(msg);
    }


    void waitForRequests( Agent myAgent) {

        while(inRequestingPhase()) {
            myAgent.doWait(1);
            receiveMessages( myAgent, ACLMessage.INFORM);
        }
        receiveMessages( myAgent, ACLMessage.REQUEST);
    }


    void waitForBids( Agent myAgent) {

        while(inBiddingPhase()) {
            myAgent.doWait(1);
            receiveMessages( myAgent, ACLMessage.INFORM);
        }
        receiveMessages( myAgent, ACLMessage.PROPOSE);
    }


    void waitForConfirmations( Agent myAgent) {

        while(inConfirmingPhase()) {
            myAgent.doWait(1);
            receiveMessages( myAgent, ACLMessage.INFORM);
        }
        receiveMessages( myAgent, ACLMessage.CONFIRM);
    }


    boolean inRequestingPhase () {

        boolean requesting = false;
        for (var agentPhase : otherAgentsPhases.entrySet() ) {
            if (agentPhase.getValue() == ProtocolPhase.REQUESTING) {
                requesting = true;
                break;
            }
        }
        return requesting;
    }


    boolean inBiddingPhase () {

        boolean bidding = false;
        for (var agentPhase : otherAgentsPhases.entrySet() ) {
            if (agentPhase.getValue() == ProtocolPhase.BIDDING) {
                bidding = true;
            }
        }
        return bidding;
    }


    boolean inConfirmingPhase () {

        boolean confirming = false;
        for (var agentPhase : otherAgentsPhases.entrySet() ) {
            if (agentPhase.getValue() == ProtocolPhase.CONFORMING) {
                confirming = true;
                break;
            }
        }
        return confirming;
    }


    void resetRound() {

        blockedTasks.clear();
        receivedRequests.clear();
        sentRequests.clear();
        receivedBids.clear();
        sentBids.clear();
    }


    private void performTasks(Agent myAgent) {

//        System.out.println (myAgent.getLocalName() +  " is performing tasks.");
        int count = 0;
        SortedSet<Task> doneTasksInThisRound = new TreeSet<>(new Task.taskComparator());
        // Greedy algorithm: tasks are sorted by utility in toDoTasks
        for (Task task : toDoTasks) {
            if (hasEnoughResources(task, availableResources)) {
                processTask(task);
                doneTasksInThisRound.add(task);
                boolean check = doneTasks.add(task);
                if (check == false) {
                    System.out.println("Error!!");
                }
                totalUtil = totalUtil + task.utility;
                count += 1;
            }
        }

        if (doneTasksInThisRound.size() != count) {
            System.out.println("Error!!");
        }

        int initialSize = toDoTasks.size();

        toDoTasks.removeAll (doneTasks);

        if ( initialSize - count != toDoTasks.size()) {
             System.out.println("Error!!");
        }

        System.out.println( myAgent.getLocalName() + " has performed " + doneTasks.size() + " tasks and gained total utility of " + totalUtil);
        sendTotalUtilToMasterAgent (totalUtil, myAgent);
    }


    Map<ResourceType, SortedSet<ResourceItem>> evaluateTask (Task task, Map<ResourceType, SortedSet<ResourceItem>> remainingResources) {

        try {
            for (var entry : task.requiredResources.entrySet()) {
                SortedSet<ResourceItem> resourceItems = remainingResources.get(entry.getKey());
                for (int i = 0; i < entry.getValue(); i++) {
                    ResourceItem item = resourceItems.first();
                    resourceItems.remove(item);
                }
//                remainingResources.replace(entry.getKey(), resourceItems);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        return remainingResources;
    }


    void processTask (Task task) {

        try {
            for (var entry : task.requiredResources.entrySet()) {
                SortedSet<ResourceItem> resourceItems = availableResources.get(entry.getKey());
                for (int i = 0; i < entry.getValue(); i++) {
                    ResourceItem item = resourceItems.first();
                    resourceItems.remove(item);
                }
                availableResources.replace(entry.getKey(), resourceItems);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }


    private void createRequests (SortedSet<Task> blockedTasks, Map<ResourceType, SortedSet<ResourceItem>> remainingResources, Agent myAgent) {

        // creates a request based on the missing quantity for each resource type

        Map<ResourceType, Integer> totalRequiredResources = new LinkedHashMap<>();

        for (Task task : blockedTasks) {
            for (var entry : task.requiredResources.entrySet()) {
                totalRequiredResources.put(entry.getKey(),  totalRequiredResources.getOrDefault(entry.getKey(), 0) + entry.getValue());
            }
        }

        for (var entry : totalRequiredResources.entrySet()) {
            int missingQuantity = 0;
            if ( remainingResources.containsKey( entry.getKey())) {
                if (remainingResources.get(entry.getKey()).size() < entry.getValue()) {
                    missingQuantity = entry.getValue() - remainingResources.get(entry.getKey()).size();
                }
            } else {
                missingQuantity = entry.getValue();
            }

            if (missingQuantity > 0) {
                Map<Integer, Integer> utilityFunction = computeUtilityFunction(blockedTasks, entry.getKey(), remainingResources, missingQuantity);
                sendRequest(entry.getKey(), missingQuantity, utilityFunction, myAgent);
            }
        }
    }


    Map<Integer, Integer> computeUtilityFunction (SortedSet<Task> blockedTasks, ResourceType resourceType, Map<ResourceType, SortedSet<ResourceItem>> remainingResources, int missingQuantity) {

        Map<Integer, Integer> utilityFunction = new LinkedHashMap<>();
        for (int i=1; i<=missingQuantity; i++) {
            int q = remainingResources.get(resourceType).size() + i;
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
        jo.put(Ontology.REQUEST_UTILITY_FUNCTION, utilityFunction);

        msg.setContent( jo.toJSONString());
//      msg.setReplyByDate();
        send(msg);

        sentRequests.put (reqId, new Request(reqId, missingQuantity, resourceType, utilityFunction, myAgent.getAID()));

//        System.out.println( myAgent.getLocalName() + " sent a request with quantity: " + missingQuantity + " for resourceType: " + resourceType.name());
    }


    private void storeRequest (Agent myAgent, ACLMessage msg) throws ParseException {

        String content = msg.getContent();

        Object obj = new JSONParser().parse(content);
        JSONObject jo = (JSONObject) obj;

        String reqId = (String) jo.get("reqId");
        Long requestedQuantity = (Long) jo.get(Ontology.RESOURCE_REQUESTED_QUANTITY);
        String rt = (String) jo.get(Ontology.RESOURCE_TYPE);
        ResourceType resourceType = ResourceType.valueOf(rt);
        JSONObject joUtilityFunction = (JSONObject) jo.get(Ontology.REQUEST_UTILITY_FUNCTION);

//        System.out.println( myAgent.getLocalName() + " received request with quantity " + requestedQuantity + " for resource type " + resourceType.name() + " from " + msg.getSender().getLocalName());

        Map<Integer, Integer> utilityFunction = new LinkedHashMap<>();
        Iterator<String> keysIterator = joUtilityFunction.keySet().iterator();
        while (keysIterator.hasNext()) {
            String key = keysIterator.next();
            Long value = (Long) joUtilityFunction.get(key);
            utilityFunction.put( Integer.valueOf(key), value.intValue());
        }

        Request request = new Request(reqId, requestedQuantity.intValue(), resourceType, utilityFunction, msg.getSender());

        if ( receivedRequests.containsKey(resourceType) == false) {
            receivedRequests.put(resourceType, new ArrayList<>());
        }
        receivedRequests.get(resourceType).add(request);
    }


    private void deliberateOnBidding( Agent myAgent) {

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


        Map<ResourceType, SortedSet<ResourceItem>> remainingResources = deepCopyResourcesMap( availableResources);
        // bid only with remaining resources after performing tasks in this round
        for (Task task : toDoTasks) {
            if (hasEnoughResources(task, remainingResources)) {
                remainingResources = evaluateTask( task, remainingResources);
            }
        }

        int bidQuantity;
        for (var requestsForType : receivedRequests.entrySet()) {
            if (remainingResources.get(requestsForType.getKey()) != null) {
                int remainingQuantity = remainingResources.get(requestsForType.getKey()).size();
                ArrayList<Request> requests = requestsForType.getValue();
                while (remainingQuantity > 0 && requests.size() > 0) {
                    // Greedy approach
                    Request selectedRequest = selectBestRequest( requests, remainingQuantity);
                    if (remainingQuantity < selectedRequest.quantity) {
                        bidQuantity = remainingQuantity;
                    } else {
                        bidQuantity = selectedRequest.quantity;
                    }
//                    int exp = computeExpectedUtilityOfResources(selectedRequest.resourceType, bidQuantity, availableResources.get(selectedRequest.resourceType));
//                    int util = selectedRequest.utilityFunction.get(bidQuantity);
//                    if (exp < util) {
                        createBid(selectedRequest.id, myAgent.getAID(), selectedRequest.sender, selectedRequest.resourceType, bidQuantity, availableResources.get(selectedRequest.resourceType));
                        remainingQuantity = remainingQuantity - bidQuantity;
//                    } else {
                        // reject or cascade the request
//                    }
                    requests.remove( selectedRequest);
                }
                // reject or cascade the rest of requests
            } else {
            // reject or cascade the requests
            }
        }
    }


    Request selectBestRequest(ArrayList<Request> requests, int remainingQuantity) {

        Request selectedRequest = requests.get(0);
        int highestUtility = 0;
        int bidQuantity;

        for (Request request : requests) {
            if (remainingQuantity < request.quantity) {
                bidQuantity = remainingQuantity;
            } else {
                bidQuantity = request.quantity;
            }
            int util = request.utilityFunction.get(bidQuantity);
            if (util > highestUtility) {
                highestUtility = util;
                selectedRequest = request;
            }
        }

        return selectedRequest;
    }


    private void createBid (String reqId, AID bidder, AID requester, ResourceType resourceType, int bidQuantity, SortedSet<ResourceItem> availableItems) {

//        Map<Integer, Integer> costFunction = new LinkedHashMap<>();
//        for (int q=1; q<=bidQuantity; q++) {
//            int cost = computeExpectedUtilityOfResources(resourceType, q, availableItems);
//            costFunction.put(q, cost);
//        }

        Map<String, Integer> offeredItems = new LinkedHashMap<>();
        Iterator<ResourceItem> itr = availableItems.iterator();
        int q=1;
        while (q<=bidQuantity) {
            ResourceItem item = itr.next();
            offeredItems.put(item.getId(), item.getLifetime());
            availableItems.remove( item);
            itr = availableItems.iterator();
            q++;
        }

        String bidId = UUID.randomUUID().toString();
        Bid bid = new Bid(bidId, reqId, bidQuantity, resourceType, null, offeredItems, bidder, requester);

        sentBids.put( bidId, bid);
//        availableResources.put( resourceType, availableItems);

        sendBid(reqId, bidId, requester, resourceType, bidQuantity, null, offeredItems);

//        System.out.println( "createBid for resourceType: " + resourceType.name() + " with bidQuantity: " + bidQuantity);
    }


    private void sendBid (String reqId, String bidId, AID requester, ResourceType resourceType, int bidQuantity, Map<Integer, Integer> costFunction, Map<String, Integer> offeredItems) {

        ACLMessage msg = new ACLMessage(ACLMessage.PROPOSE);

        msg.addReceiver( requester);

        JSONObject jo = new JSONObject();
        jo.put("reqId", reqId);
        jo.put("bidId", bidId);
        jo.put(Ontology.RESOURCE_BID_QUANTITY, bidQuantity);
        jo.put(Ontology.RESOURCE_TYPE, resourceType.name());
//        jo.put(Ontology.BID_COST_FUNCTION, costFunction);
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

        Long bidQuantity = (Long) jo.get(Ontology.RESOURCE_BID_QUANTITY);

        String reqId = (String) jo.get("reqId");
        String bidId = (String) jo.get("bidId");
        String rt = (String) jo.get(Ontology.RESOURCE_TYPE);
        ResourceType resourceType = ResourceType.valueOf(rt);
//        JSONObject joCostFunction = (JSONObject) jo.get(Ontology.BID_COST_FUNCTION);
        JSONObject joOfferedItems = (JSONObject) jo.get(Ontology.BID_OFFERED_ITEMS);

        System.out.println( myAgent.getLocalName() + " received bid with quantity " + bidQuantity + " for resource type " + resourceType.name() + " from " + msg.getSender().getLocalName());

//        Map<Integer, Integer> costFunction = new LinkedHashMap<>();
//        Iterator<String> keysIterator1 = joCostFunction.keySet().iterator();
//        while (keysIterator1.hasNext()) {
//            String key = keysIterator1.next();
//            Long value = (Long) joCostFunction.get(key);
//            costFunction.put( Integer.valueOf(key), value.intValue());
//        }

        Map<String, Integer> offeredItems = new LinkedHashMap<>();
        Iterator<String> keysIterator2 = joOfferedItems.keySet().iterator();
        while (keysIterator2.hasNext()) {
            String key = keysIterator2.next();
            Long value = (Long) joOfferedItems.get(key);
            offeredItems.put( key, value.intValue());
        }

        Bid bid = new Bid(bidId, reqId, bidQuantity.intValue(), resourceType, null, offeredItems, msg.getSender(), myAgent.getAID());

        Set<Bid> bids = receivedBids.get(reqId);
        if (bids == null) {
            bids = new HashSet<>();
        }
        bids.add( bid);
        receivedBids.put( reqId, bids);
    }


    void deliberateOnConfirming( Agent myAgent) {

        Map<Request, Map<Bid, Integer>> selectedBidsForAllRequests = new LinkedHashMap<>();

        for (var request : sentRequests.entrySet()) {
            if ( receivedBids.containsKey(request.getKey())) {
                Map<Bid, Integer> selectedBids = processBids( request.getValue());
                if (selectedBids.size() > 0) {
                    selectedBidsForAllRequests.put(request.getValue(), selectedBids);
                }
            }
        }

//        if (hasEnoughResourcesByConfirmBids( selectedBidsForAllRequests)) {
            if ( selectedBidsForAllRequests.size() > 0) {
                createConfirmation( myAgent, selectedBidsForAllRequests);
                addResourceItemsInBids(selectedBidsForAllRequests);
            }
//        }
    }


    void addResourceItemsInBids (Map<Request, Map<Bid, Integer>> selectedBidsForAllRequests) {

        for (var selectedBidsForReq : selectedBidsForAllRequests.entrySet()) {
            SortedSet<ResourceItem> resourceItems;
            if (availableResources.containsKey(selectedBidsForReq.getKey().resourceType)) {
                resourceItems = availableResources.get( selectedBidsForReq.getKey().resourceType);
            } else {
                resourceItems = new TreeSet<>(new ResourceItem.resourceItemComparator());
            }
            Map<Bid, Integer> bidQuantities = selectedBidsForReq.getValue();
            for (var bidQuantity : bidQuantities.entrySet()) {
                // create a sorted set of offered items
                SortedSet<ResourceItem> offeredItems = new TreeSet<>(new ResourceItem.resourceItemComparator());
                for (var offeredItem : bidQuantity.getKey().offeredItems.entrySet()) {
                    offeredItems.add(new ResourceItem(offeredItem.getKey(), bidQuantity.getKey().resourceType, offeredItem.getValue()));
                }
                Iterator<ResourceItem> itr = offeredItems.iterator();
                int q=1;
                while (q<=bidQuantity.getValue()) {
                    ResourceItem item = itr.next();
                    resourceItems.add(item);
                    q++;
                }
            }

            availableResources.put( selectedBidsForReq.getKey().resourceType, resourceItems);
        }
    }


    boolean hasEnoughResourcesByConfirmBids (Map<Request, Map<Bid, Integer>> selectedBidsForAllRequests) {

        Map<ResourceType, SortedSet<ResourceItem>> allResourcesByConfirmBids = new LinkedHashMap<>(availableResources);

        for (var selectedBidsForReq : selectedBidsForAllRequests.entrySet()) {
            SortedSet<ResourceItem> resourceItems;
            if (allResourcesByConfirmBids.containsKey(selectedBidsForReq.getKey().resourceType)) {
                resourceItems = allResourcesByConfirmBids.get( selectedBidsForReq.getKey().resourceType);
            } else {
                resourceItems = new TreeSet<>();
            }
            Map<Bid, Integer> bidQuantities = selectedBidsForReq.getValue();
            for (var bidQuantity : bidQuantities.entrySet()) {
                int q=1;
                for (var offeredItem : bidQuantity.getKey().offeredItems.entrySet()) {
                    if (q<=bidQuantity.getValue()) {
                        resourceItems.add(new ResourceItem(offeredItem.getKey(), bidQuantity.getKey().resourceType, offeredItem.getValue()));
                        q++;
                    }
                    else {
                        break;
                    }
                }
            }

            allResourcesByConfirmBids.put( selectedBidsForReq.getKey().resourceType, resourceItems);
        }

        // check if there is enough resource to perform at least one task
        boolean enough = false;
        for (Task task : toDoTasks) {
            if (hasEnoughResources( task, allResourcesByConfirmBids)) {
                enough = true;
                break;
            }
        }

        return enough;
    }


    public Map<Bid, Integer> processBids (Request request) {

        // the requester randomly selects a combination of bids
        // it is allowed to take partial amounts of oﬀered resources in multiple bids up to the requested amount.
        // a greedy approach: we randomly add 1 item from one bid in a loop up to the requested amount, without backtracking.

        Set<Bid> bids = receivedBids.get(request.id);

        Map<Bid, Integer> selectedBids = new LinkedHashMap<>();

        for (int q=1; q<=request.quantity; q++) {
            boolean hasExtraItem = false;
            for (Bid bid : bids) {
                if (hasExtraItem(bid, selectedBids)) {
                    hasExtraItem = true;
                    if (selectedBids.containsKey(bid)) {
                        selectedBids.put(bid, selectedBids.get(bid) + 1);
                    } else {
                        selectedBids.put(bid, 1);
                    }
                }
            }
            if (hasExtraItem == false) {
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


//    private int totalCosts (Bid bid, Map<Bid, Integer> selectedBids) {
//
//        int totalCosts = 0;
//
//        Map<Bid, Integer> tempBids =  new LinkedHashMap<>();
//        for (var entry : selectedBids.entrySet()) {
//            tempBids.put(entry.getKey(), entry.getValue());
//        }
//
//        if (tempBids.containsKey(bid)) {
//            tempBids.put(bid, tempBids.get(bid) + 1);
//        } else {
//            tempBids.put(bid, 1);
//        }
//
//        for (var entry : tempBids.entrySet()) {
//            totalCosts = totalCosts + entry.getKey().costFunction.get(entry.getValue());
//        }
//
//        return totalCosts;
//    }


//    public Set<Set<Bid>> getSubsets(Set<Bid> set) {
//        if (set.isEmpty()) {
//            return Collections.singleton(Collections.emptySet());
//        }
//
//        Set<Set<Bid>> subSets = set.stream().map(item -> {
//                    Set<Bid> clone = new HashSet<>(set);
//                    clone.remove(item);
//                    return clone;
//                }).map(group -> getSubsets(group))
//                .reduce(new HashSet<>(), (x, y) -> {
//                    x.addAll(y);
//                    return x;
//                });
//
//        subSets.add(set);
//        return subSets;
//    }


    private void createConfirmation (Agent myAgent, Map<Request, Map<Bid, Integer>> selectedBidsForAllRequests) {

        for (var selectedBidsForReq : selectedBidsForAllRequests.entrySet()) {
            for (var bidQuantity : selectedBidsForReq.getValue().entrySet()) {
                sendConfirmation (myAgent, bidQuantity.getKey().id, bidQuantity.getKey().sender, bidQuantity.getKey().resourceType, bidQuantity.getValue());
            }
        }
    }


    void sendConfirmation (Agent myAgent, String bidId, AID bidder, ResourceType resourceType, int confirmQuantity) {

        ACLMessage msg = new ACLMessage(ACLMessage.CONFIRM);

        msg.addReceiver (bidder);

        JSONObject jo = new JSONObject();
        jo.put("bidId", bidId);
        jo.put(Ontology.RESOURCE_TYPE, resourceType.name());
        jo.put(Ontology.RESOURCE_CONFIRM_QUANTITY, confirmQuantity);

        msg.setContent( jo.toJSONString());
        send(msg);

//        System.out.println( myAgent.getLocalName() + " sent confirmation with quantity " + confirmQuantity + " for resource type " + resourceType.name() + " to bidder " + bidder.getLocalName());
    }


    private void processConfirmation (Agent myAgent, ACLMessage confirmation) throws ParseException {

        String content = confirmation.getContent();

        Object obj = new JSONParser().parse(content);
        JSONObject jo = (JSONObject) obj;

        String bidId = (String) jo.get("bidId");

        String rt = (String) jo.get(Ontology.RESOURCE_TYPE);
        ResourceType resourceType = ResourceType.valueOf(rt);

        Long confirmQuantity = (Long) jo.get(Ontology.RESOURCE_CONFIRM_QUANTITY);
        System.out.println( myAgent.getLocalName() + " received confirmation with quantity " + confirmQuantity + " for resource type " + resourceType.name() + " from " + confirmation.getSender().getLocalName());

        restoreResources(bidId, resourceType, confirmQuantity.intValue());
    }


    private void restoreResources(String bidId, ResourceType resourceType, int confirmQuantity) {

        Bid sentBid = sentBids.get( bidId);

        if (confirmQuantity < sentBid.quantity) {
            // create a sorted set of offered items
            SortedSet<ResourceItem> offeredItems = new TreeSet<>(new ResourceItem.resourceItemComparator());
            for (var offeredItem : sentBid.offeredItems.entrySet()) {
                offeredItems.add(new ResourceItem(offeredItem.getKey(), resourceType, offeredItem.getValue()));
            }
            Iterator<ResourceItem> itr = offeredItems.iterator();
            int q=1;
            while (q<=confirmQuantity) {
                ResourceItem item = itr.next();
                offeredItems.remove(item);
                itr = offeredItems.iterator();
                q++;
            }

            int unusedQuantity = sentBid.quantity - confirmQuantity;
            SortedSet<ResourceItem> resourceItems = availableResources.get( resourceType);
            itr = offeredItems.iterator();
            q=1;
            while (q<=unusedQuantity) {
                ResourceItem item = itr.next();
                resourceItems.add(item);
                q++;
            }
            availableResources.put( resourceType, resourceItems);
        }
    }


    private boolean hasEnoughResources (Task task, Map<ResourceType, SortedSet<ResourceItem>> availableResources) {
        boolean enough = true;

        for (var entry : task.requiredResources.entrySet()) {
            if (availableResources.containsKey(entry.getKey()) == false) {
                enough = false;
                break;
            } else if (entry.getValue() > availableResources.get(entry.getKey()).size()) {
                enough = false;
                break;
            }
        }

        return enough;
    }


//    int computeExpectedUtilityOfResources ( ResourceType resourceType, int quantity, SortedSet<ResourceItem> resourceItems) {
//
//        int exp = 0;
//        ArrayList<Task> doneTasksWithThisResourceType = new ArrayList<>();
//        int totalUtilityWithThisResourceType = 0;
//        int totalQuantityOfThisResourceType = 0;
//
//        for (Task task : doneTasks) {
//            if (task.requiredResources.containsKey(resourceType)) {
//                doneTasksWithThisResourceType.add( task);
//                totalUtilityWithThisResourceType = totalUtilityWithThisResourceType + task.utility;
//                totalQuantityOfThisResourceType = totalQuantityOfThisResourceType + task.requiredResources.get(resourceType);
//            }
//        }
//
//        if (doneTasks.size() > 0 && totalQuantityOfThisResourceType > 0) {
//            Iterator<ResourceItem> itr = resourceItems.iterator();
//            int q=1;
//            while (q <= quantity) {
//                ResourceItem item = itr.next();
//                exp = exp + (item.getLifetime() * (doneTasksWithThisResourceType.size() / doneTasks.size()) * (totalUtilityWithThisResourceType / totalQuantityOfThisResourceType));
////                exp = exp + ((doneTasksWithThisResourceType.size() / doneTasks.size()) * (totalUtilityWithThisResourceType / totalQuantityOfThisResourceType));
//                q++;
//            }
//        }
//
//        return exp;
//    }


    public static Map<ResourceType, SortedSet<ResourceItem>> deepCopyResourcesMap(Map<ResourceType, SortedSet<ResourceItem>> original) {
        Map<ResourceType, SortedSet<ResourceItem>> copy = new LinkedHashMap<>();
        for (var entry : original.entrySet()) {
            copy.put(entry.getKey(), new TreeSet<>(entry.getValue()));
        }
        return copy;
    }


    private void processNotification (Agent myAgent, ACLMessage msg) throws ParseException {

        String content = msg.getContent();

        Object obj = new JSONParser().parse(content);
        JSONObject jo = (JSONObject) obj;

        String pp = (String) jo.get(Ontology.PROTOCOL_PHASE);
        ProtocolPhase protocolPhase = ProtocolPhase.valueOf(pp);

        otherAgentsPhases.put( msg.getSender(), protocolPhase);
    }


    void sendNewTasksToMasterAgent (SortedSet<Task> newTasks, Agent myAgent) {

        ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
        AID aid = new AID("Agent0", AID.ISLOCALNAME);
        msg.addReceiver(aid);

        JSONObject joNewTasks = new JSONObject();

        for (Task task : newTasks) {
            JSONObject joTask = new JSONObject();
            joTask.put("utility", task.utility);
            JSONObject joRequiredResources = new JSONObject();
            for (var entry : task.requiredResources.entrySet()) {
                joRequiredResources.put( entry.getKey().name(), entry.getValue());
            }
            joTask.put("requiredResources", joRequiredResources);
            joNewTasks.put( task.id, joTask);
        }

        JSONObject jo = new JSONObject();
        jo.put( "newTasks", joNewTasks);

        msg.setContent( jo.toJSONString());
        send(msg);

//        System.out.println( myAgent.getLocalName() + " sent new tasks info to the master agent" );
    }


    void sendNewResourcesToMasterAgent (Map<ResourceType, SortedSet<ResourceItem>> newResources, Agent myAgent) {

        ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
        AID aid = new AID("Agent0", AID.ISLOCALNAME);
        msg.addReceiver(aid);

        JSONObject joNewResources = new JSONObject();

        for (var newResource : newResources.entrySet()) {
            JSONObject joItems = new JSONObject();
            for( ResourceItem item : newResource.getValue()) {
                joItems.put( item.getId(), item.getLifetime());
            }
            joNewResources.put( newResource.getKey().name(), joItems);
        }

        JSONObject jo = new JSONObject();
        jo.put( "newResources", joNewResources);

        msg.setContent( jo.toJSONString());
        send(msg);

//        System.out.println( myAgent.getLocalName() + " sent new resources info to the master agent" );
    }


    void sendTotalUtilToMasterAgent (int totalUtil, Agent myAgent) {

        ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
        AID aid = new AID("Agent0", AID.ISLOCALNAME);
        msg.addReceiver(aid);

        JSONObject jo = new JSONObject();
        jo.put( "totalUtil", totalUtil);

        msg.setContent( jo.toJSONString());
        send(msg);

//        System.out.println( myAgent.getLocalName() + " sent total utility: " + totalUtil + " to the master agent" );
    }


    void receiveMessages(Agent myAgent, int performative) {

        MessageTemplate mt = MessageTemplate.MatchPerformative( performative);

        ACLMessage msg = myAgent.receive( mt);

        while (msg != null) {
            String content = msg.getContent();

            switch (performative) {
                case ACLMessage.REQUEST:
//                    System.out.println(myAgent.getLocalName() + " received a REQUEST message from " + msg.getSender().getLocalName());

                    try {
                        storeRequest(myAgent, msg);
                    } catch (ParseException e) {
                        e.printStackTrace();
                    }

                    break;

                case ACLMessage.PROPOSE:
//                    System.out.println(myAgent.getLocalName() + " received a BID message from " + msg.getSender().getLocalName());

                    try {
                        storeBid(myAgent, msg);
                    } catch (ParseException e) {
                        e.printStackTrace();
                    }

                    break;

                case ACLMessage.CONFIRM:
//                    System.out.println(myAgent.getLocalName() + " received a CONFIRM message from " + msg.getSender().getLocalName());

                    try {
                        processConfirmation(myAgent, msg);
                    } catch (ParseException e) {
                        e.printStackTrace();
                    }

                    break;

                case ACLMessage.REFUSE:
                    System.out.println(myAgent.getLocalName() + " received a REFUSE message from " + msg.getSender().getLocalName());

                    break;

                case ACLMessage.REJECT_PROPOSAL:
                    System.out.println(myAgent.getLocalName() + " received a REJECT_PROPOSAL message from " + msg.getSender().getLocalName());


                    break;

                case ACLMessage.INFORM:
//                    System.out.println(myAgent.getLocalName() + " received an INFORM message from " + msg.getSender().getLocalName());

                    try {
                        processNotification(myAgent, msg);
                    } catch (ParseException e) {
                        e.printStackTrace();
                    }
                    break;
            }

            msg = myAgent.receive( mt);
        }

//        System.out.println( "This is the end!");
    }

}
