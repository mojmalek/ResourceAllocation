package model;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.lang.acl.ACLMessage;

import java.util.*;

import org.json.simple.JSONObject;
import org.json.simple.parser.*;


public class ResourceAllocationAgent extends Agent {

    SimulationEngine simulationEngine = new SimulationEngine();

    private ArrayList<AID> otherAgents = new ArrayList<>();
    private Map<AID, ProtocolPhase> otherAgentsPhases = new LinkedHashMap<>();

    private SortedSet<Task> toDoTasks = new TreeSet<>(new Task.taskComparator());
    private SortedSet<Task> blockedTasks = new TreeSet<>(new Task.taskComparator());
    private SortedSet<Task> doneTasks = new TreeSet<>(new Task.taskComparator());

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

        System.out.println("Hello World. I’m an agent! My local-name is " + getAID().getLocalName());

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


        addBehaviour(new TickerBehaviour(this, 3000) {
            protected void onTick() {
                System.out.println( "Round: " + this.getTickCount());

                findTasks (myAgent);

                findResources (myAgent);

                negotiate (myAgent);

                performTasks (myAgent);
            }
        });


        addBehaviour(new CyclicBehaviour() {
            @Override
            public void action() {
                ACLMessage msg = myAgent.receive();
                if (msg != null) {
                    String content = msg.getContent();

                    switch (msg.getPerformative()) {
                        case ACLMessage.REQUEST:
                            System.out.println (myAgent.getLocalName() + " received a REQUEST message from " + msg.getSender().getLocalName() + " with content: " + content);

                            try {
                                storeRequest( myAgent, msg);
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

                        case ACLMessage.CONFIRM:
                            System.out.println (myAgent.getLocalName() + " received a CONFIRM message from " + msg.getSender().getLocalName() + " with content: " + content);

                            try {
                                processConfirmation(myAgent, msg);
                            } catch (ParseException e) {
                                e.printStackTrace();
                            }

                            break;

                        case ACLMessage.REFUSE:
                            System.out.println (myAgent.getLocalName() + " received a REFUSE message from " + msg.getSender().getLocalName() + " with content: " + content);

                            break;

                        case ACLMessage.REJECT_PROPOSAL:
                            System.out.println (myAgent.getLocalName() + " received a REJECT_PROPOSAL message from " + msg.getSender().getLocalName() + " with content: " + content);


                            break;

                        case ACLMessage.INFORM:
                            System.out.println (myAgent.getLocalName() + " received a INFORM message from " + msg.getSender().getLocalName() + " with content: " + content);

                            try {
                                processNotification(myAgent, msg);
                            } catch (ParseException e) {
                                e.printStackTrace();
                            }
                            break;
                    }

                } else {
                    block();
                }
            }
        });
    }


    private void findTasks(Agent myAgent) {

        System.out.println (myAgent.getLocalName() +  " : Step 1");

        SortedSet<Task> newTasks = simulationEngine.findTasks();
        toDoTasks.addAll(newTasks);

    }


    private void findResources(Agent myAgent) {

        System.out.println (myAgent.getLocalName() +  " : Step 2");

        // decrease lifetime of remaining resources
        perishResourceItems();

        Map<ResourceType, SortedSet<ResourceItem>> newResources = simulationEngine.findResources();

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

        for (var entry : availableResources.entrySet()) {
            System.out.println( entry.getKey().name() + " " + entry.getValue().size());
        }
    }


    void perishResourceItems() {

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
    }


    private void negotiate (Agent myAgent) {

        System.out.println (myAgent.getLocalName() +  " : Step 3");

        deliberateOnRequesting (myAgent);

        sendNextPhaseNotification (ProtocolPhase.BIDDING);

        waitForRequests();

        if (receivedRequests.size() > 0) {
            deliberateOnBidding();
        }

        sendNextPhaseNotification (ProtocolPhase.CONFORMING);

        waitForBids();

        if (receivedBids.size() > 0) {
            confirmBids();
        }

        reset();
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

        System.out.println();
    }


    void waitForRequests() {

        while(inRequestingPhase()) {
            try {
                this.wait();
            } catch (InterruptedException var2) {
            }
        }
    }


    void waitForBids() {

        while(inBiddingPhase()) {
            try {
                this.wait();
            } catch (InterruptedException var2) {
            }
        }
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


    void reset() {

        blockedTasks.clear();
        receivedRequests.clear();
        sentRequests.clear();
        receivedBids.clear();
        sentBids.clear();
        resetOtherAgentPhases();
    }


    void resetOtherAgentPhases() {

        for (var agentPhase : otherAgentsPhases.entrySet() ) {
            agentPhase.setValue( ProtocolPhase.REQUESTING);
        }
        System.out.println();
    }


    private void performTasks(Agent myAgent) {

        System.out.println (myAgent.getLocalName() +  " : Step 4");

        SortedSet<Task> doneTasksInThisRound = new TreeSet<>(new Task.taskComparator());

        for (Task task : toDoTasks) {
            if (hasEnoughResources(task, availableResources)) {
                processTask (task);
                doneTasksInThisRound.add (task);
                doneTasks.add (task);
            }
        }

        toDoTasks.removeAll (doneTasksInThisRound);

        System.out.println( myAgent.getLocalName() + " has performed " + doneTasks.size() + " tasks.");
    }


    Map<ResourceType, SortedSet<ResourceItem>> evaluateTask (Task task, Map<ResourceType, SortedSet<ResourceItem>> remainingResources) {

        try {
            for (var entry : task.requiredResources.entrySet()) {
                SortedSet<ResourceItem> resourceItems = remainingResources.get(entry.getKey());
                for (int i = 0; i < entry.getValue(); i++) {
                    ResourceItem item = resourceItems.first();
                    resourceItems.remove(item);
                }
                remainingResources.replace(entry.getKey(), resourceItems);
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
                Map<Integer, Integer> utilityFunction = computeUtilityFunction(blockedTasks, entry.getKey(), missingQuantity);
                sendRequest(entry.getKey(), missingQuantity, utilityFunction, myAgent);
            }
        }
    }


    Map<Integer, Integer> computeUtilityFunction (SortedSet<Task> blockedTasks, ResourceType resourceType, int missingQuantity) {

//        HashMap<Integer, Integer> utilityFunction = new HashMap<>();

        Map<Integer, Integer> utilityFunction = new LinkedHashMap<>();

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
        jo.put(Ontology.REQUEST_UTILITY_FUNCTION, utilityFunction);

        msg.setContent( jo.toJSONString());
//      msg.setReplyByDate();
        send(msg);

        sentRequests.put (reqId, new Request(reqId, missingQuantity, resourceType, utilityFunction, myAgent.getAID()));

        System.out.println( myAgent.getLocalName() + " sent a request with quantity: " + missingQuantity + " for resourceType: " + resourceType.name());
    }


    private void storeRequest (Agent myAgent, ACLMessage msg) throws ParseException {

        String content = msg.getContent();

        Object obj = new JSONParser().parse(content);
        JSONObject jo = (JSONObject) obj;

        String reqId = (String) jo.get("reqId");
        long requestedQuantity = (long) jo.get(Ontology.RESOURCE_REQUESTED_QUANTITY);
        String rt = (String) jo.get(Ontology.RESOURCE_TYPE);
        ResourceType resourceType = ResourceType.valueOf(rt);
        Map utilityFunction = (Map) jo.get(Ontology.REQUEST_UTILITY_FUNCTION);

        Request request = new Request(reqId, requestedQuantity, resourceType, utilityFunction, msg.getSender());

        if ( receivedRequests.containsKey(resourceType) == false) {
            receivedRequests.put(resourceType, new ArrayList<>());
        }
        receivedRequests.get(resourceType).add(request);
    }


    private void deliberateOnBidding() {





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

        AID requester = request.getSender();

        String content = request.getContent();

        Object obj = new JSONParser().parse(content);
        JSONObject jo = (JSONObject) obj;

        String reqId = (String) jo.get("reqId");

        long requestedQuantity = (long) jo.get(Ontology.RESOURCE_REQUESTED_QUANTITY);
        System.out.println("Requested quantity is " + requestedQuantity);

        String rt = (String) jo.get(Ontology.RESOURCE_TYPE);
        ResourceType resourceType = ResourceType.valueOf(rt);
        Map utilityFunction = (Map) jo.get(Ontology.REQUEST_UTILITY_FUNCTION);
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

                    createBid(reqId, myAgent.getAID(), requester, resourceType, bidQuantity, availableResources.get(resourceType));
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


    private void createBid (String reqId, AID bidder, AID requester, ResourceType resourceType, long bidQuantity, SortedSet<ResourceItem> availableItems) {

        Map<Integer, Integer> costFunction = new LinkedHashMap<>();
        for (int q=1; q<=bidQuantity; q++) {
            int cost = computeExpectedUtilityOfResources(resourceType, q, availableItems);
            costFunction.put(q, cost);
        }

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
        Bid bid = new Bid(bidId, reqId, bidQuantity, resourceType, costFunction, offeredItems, bidder, requester);

        sentBids.put( bidId, bid);
        availableResources.put( resourceType, availableItems);

        sendBid(reqId, bidId, requester, resourceType, bidQuantity, costFunction, offeredItems);

        System.out.println( "createBid for resourceType: " + resourceType.name() + " with bidQuantity: " + bidQuantity);
    }


    private void sendBid (String reqId, String bidId, AID requester, ResourceType resourceType, long bidQuantity, Map<Integer, Integer> costFunction, Map<String, Integer> offeredItems) {

        ACLMessage msg = new ACLMessage(ACLMessage.PROPOSE);

        msg.addReceiver( requester);

        JSONObject jo = new JSONObject();
        jo.put("reqId", reqId);
        jo.put("bidId", bidId);
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
        System.out.println("Bid quantity is " + bidQuantity);

        String reqId = (String) jo.get("reqId");
        String bidId = (String) jo.get("bidId");
        String rt = (String) jo.get(Ontology.RESOURCE_TYPE);
        ResourceType resourceType = ResourceType.valueOf(rt);
        Map costFunction = (Map) jo.get(Ontology.BID_COST_FUNCTION);
        Map offeredItems = (Map) jo.get(Ontology.BID_OFFERED_ITEMS);

        Bid bid = new Bid(bidId, reqId, bidQuantity, resourceType, costFunction, offeredItems, msg.getSender(), myAgent.getAID());

        Set<Bid> bids = receivedBids.get(reqId);
        if (bids == null) {
            bids = new HashSet<>();
        }

        bids.add( bid);

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

//        if (hasEnoughResourcesByConfirmBids( selectedBidsForAllRequests)) {
            if ( selectedBidsForAllRequests.size() > 0) {
                createConfirmation(selectedBidsForAllRequests);
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
                resourceItems = new TreeSet<>();
            }
            Map<Bid, Integer> bidQuantities = selectedBidsForReq.getValue();
            for (var bidQuantity : bidQuantities.entrySet()) {
                // create a sorted set of offered items
                SortedSet<ResourceItem> offeredItems = new TreeSet<>();
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


    private void createConfirmation (Map<Request, Map<Bid, Integer>> selectedBidsForAllRequests) {

        for (var selectedBidsForReq : selectedBidsForAllRequests.entrySet()) {
            for (var bidQuantity : selectedBidsForReq.getValue().entrySet()) {
                sendConfirmation ( bidQuantity.getKey().id, bidQuantity.getKey().sender, bidQuantity.getKey().resourceType, bidQuantity.getValue());
            }
        }
    }


    void sendConfirmation (String bidId, AID bidder, ResourceType resourceType, long confirmQuantity) {

        ACLMessage msg = new ACLMessage(ACLMessage.CONFIRM);

        msg.addReceiver (bidder);

        JSONObject jo = new JSONObject();
        jo.put("bidId", bidId);
        jo.put(Ontology.RESOURCE_TYPE, resourceType.name());
        jo.put(Ontology.RESOURCE_CONFIRM_QUANTITY, confirmQuantity);

        msg.setContent( jo.toJSONString());
        send(msg);
    }


    private void processConfirmation (Agent myAgent, ACLMessage confirmation) throws ParseException {

        String content = confirmation.getContent();

        Object obj = new JSONParser().parse(content);
        JSONObject jo = (JSONObject) obj;

        String bidId = (String) jo.get("bidId");

        String rt = (String) jo.get(Ontology.RESOURCE_TYPE);
        ResourceType resourceType = ResourceType.valueOf(rt);

        long confirmedQuantity = (long) jo.get(Ontology.RESOURCE_CONFIRM_QUANTITY);
        System.out.println("Confirmed quantity is " + confirmedQuantity);

        restoreResources(bidId, resourceType, confirmedQuantity);

    }


    private void restoreResources(String bidId, ResourceType resourceType, long confirmedQuantity) {

        Bid sentBid = sentBids.get( bidId);

        if (confirmedQuantity < sentBid.quantity) {
            // create a sorted set of offered items
            SortedSet<ResourceItem> offeredItems = new TreeSet<>();
            for (var offeredItem : sentBid.offeredItems.entrySet()) {
                offeredItems.add(new ResourceItem(offeredItem.getKey(), resourceType, offeredItem.getValue()));
            }
            Iterator<ResourceItem> itr = offeredItems.iterator();
            int q=1;
            while (q<=confirmedQuantity) {
                ResourceItem item = itr.next();
                offeredItems.remove(item);
                itr = offeredItems.iterator();
                q++;
            }

            long unusedQuantity = sentBid.quantity - confirmedQuantity;
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


    int computeExpectedUtilityOfResources ( ResourceType resourceType, long quantity, SortedSet<ResourceItem> resourceItems) {

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
            Iterator<ResourceItem> itr = resourceItems.iterator();
            int q=1;
            while (q <= quantity) {
                ResourceItem item = itr.next();
                exp = exp + (item.getLifetime() * (doneTasksWithThisResourceType.size() / doneTasks.size()) * (totalUtilityWithThisResourceType / totalQuantityOfThisResourceType));
                q++;
            }
        }

//        if (doneTasks.size() > 0 && totalQuantityOfThisResourceType > 0) {
//            exp = (int) (quantity * (doneTasksWithThisResourceType.size() / doneTasks.size()) * (totalUtilityWithThisResourceType / totalQuantityOfThisResourceType));
//        }

        return exp;
    }


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

        System.out.println("");
    }

}
