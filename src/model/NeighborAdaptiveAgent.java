package model;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.TickerBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.util.*;


public class NeighborAdaptiveAgent extends Agent {

    SimulationEngine simulationEngine = new SimulationEngine();
    private boolean debugMode = false;

    private Integer[] neighbors;
    private Map<AID, ProtocolPhase> neighborsPhases = new LinkedHashMap<>();

    private SortedSet<Task> toDoTasks = new TreeSet<>(new Task.taskComparator());
    private SortedSet<Task> blockedTasks = new TreeSet<>(new Task.taskComparator());
    private SortedSet<Task> doneTasks = new TreeSet<>(new Task.taskComparator());
    private long totalUtil;
    private int numberOfRounds;
    private int numberOfAgents;

    private Map<ResourceType, SortedSet<ResourceItem>> availableResources = new LinkedHashMap<>();
    private Map<ResourceType, ArrayList<ResourceItem>> expiredResources = new LinkedHashMap<>();

    // reqId
    public Map<String, Request> sentRequests = new LinkedHashMap<>();
    // reqId
//    public Map<String, Request> cascadedRequests = new LinkedHashMap<>();
    public Map<ResourceType, ArrayList<Request>> receivedRequests = new LinkedHashMap<>();
    // offerId
    public Map<String, Offer> sentOffers = new LinkedHashMap<>();
    // reqId
    public Map<String, Set<Offer>> receivedOffers = new LinkedHashMap<>();

    private int count;
    private int errorCount;

    @Override
    protected void setup() {

        if (debugMode) {
            System.out.println("Hello World. I’m a Social Adaptive agent! My local-name is " + getAID().getLocalName());
        }
        // Get ids of other agents as arguments
        Object[] args = getArguments();
        if (args != null && args.length > 0) {
            numberOfAgents = (int) args[0];
            int myId = (int) args[1];
            numberOfRounds = (int) args[2];
            neighbors = (Integer[]) args[3];

            for (int i = 0; i < neighbors.length; i++) {
                if (neighbors[i] != null) {
                    AID aid = new AID(numberOfAgents + "Agent" + (i+1), AID.ISLOCALNAME);
                    neighborsPhases.put(aid, ProtocolPhase.REQUESTING);
                }
            }
        }

        addBehaviour (new TickerBehaviour(this, 1) {
            protected void onTick() {
                if (this.getTickCount() <= numberOfRounds) {
                        System.out.println(myAgent.getLocalName() + " Round: " + this.getTickCount());

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

        expireTasks( myAgent);

        SortedSet<Task> newTasks = simulationEngine.findTasks( myAgent);
        toDoTasks.addAll(newTasks);

//        sendNewTasksToMasterAgent (newTasks, myAgent);

//        System.out.println (myAgent.getLocalName() + " has " + toDoTasks.size() + " tasks to do.");
    }


    private void findResources(Agent myAgent) {

//        System.out.println (myAgent.getLocalName() + " is finding resources.");

        // decrease lifetime of remaining resources
        expireResourceItems( myAgent);

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

//        sendNewResourcesToMasterAgent (newResources, myAgent);
    }


    void expireResourceItems(Agent myAgent) {

        SortedSet<ResourceItem> availableItems;
        ArrayList<ResourceItem> expiredItems;
        ArrayList<ResourceItem> expiredItemsInThisRound = new ArrayList<>();
        for (var resource : availableResources.entrySet()) {
            expiredItemsInThisRound.clear();
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
            int initialSize = availableItems.size();
            availableItems.removeAll( expiredItemsInThisRound);
            if ( initialSize - expiredItemsInThisRound.size() != availableItems.size()) {
                System.out.println("Error!!");
            }
        }

        if (debugMode) {
            for (var entry : expiredResources.entrySet()) {
                System.out.println(myAgent.getLocalName() + " has " + entry.getValue().size() + " expired item of type: " + entry.getKey().name());
            }
        }
    }


    void expireTasks(Agent myAgent) {

        SortedSet<Task> lateTasksInThisRound = new TreeSet<>(new Task.taskComparator());
        int count = 0;
        for (Task task : toDoTasks) {
            task.deadline--;
            if (task.deadline == 0) {
                lateTasksInThisRound.add( task);
                count += 1;
            }
        }

        if (lateTasksInThisRound.size() != count) {
            System.out.println("Error!!");
        }
        int initialSize = toDoTasks.size();
        toDoTasks.removeAll( lateTasksInThisRound);
        if ( initialSize - count != toDoTasks.size()) {
            System.out.println("Error!!");
        }
    }


    private void negotiate (Agent myAgent) {
//        System.out.println (myAgent.getLocalName() +  " is negotiating.");
        resetRound();
        deliberateOnRequesting (myAgent);
        sendNextPhaseNotification (ProtocolPhase.OFFERING);
        waitForRequests( myAgent);
//        if( myAgent.getLocalName().equals("Agent1")) {
//            System.out.print("");
//        }
        if (receivedRequests.size() > 0) {
//            count++;
//            System.out.println();
//            System.out.println(this.getLocalName() + " Count: " + count);
//            System.out.println();
            deliberateOnOffering( myAgent);
        }
        sendNextPhaseNotification (ProtocolPhase.CONFORMING);
        waitForOffers( myAgent);
//        if( myAgent.getLocalName().equals("Agent1")) {
//            System.out.print("");
//        }
        if (receivedOffers.size() > 0) {
//            count++;
//            System.out.println();
//            System.out.println(this.getLocalName() + " Count: " + count);
//            System.out.println();
            deliberateOnConfirming( myAgent);
        }
        sendNextPhaseNotification (ProtocolPhase.REQUESTING);
        waitForConfirmations( myAgent);
    }


    void deliberateOnRequesting (Agent myAgent) {

//        if( myAgent.getLocalName().equals("Agent1")) {
//            System.out.print("");
//        }

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

        for (int i = 0; i < neighbors.length; i++) {
            if (neighbors[i] != null) {
                AID aid = new AID(numberOfAgents + "Agent" + (i+1), AID.ISLOCALNAME);
                msg.addReceiver(aid);
            }
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


    void waitForCascadedRequests( Agent myAgent) {

        while(inCascadingRequestPhase()) {
            myAgent.doWait(1);
            receiveMessages( myAgent, ACLMessage.INFORM);
        }
        receiveMessages( myAgent, ACLMessage.REQUEST);
    }


    void waitForOffers(Agent myAgent) {

        while(inOfferingPhase()) {
            myAgent.doWait(1);
            receiveMessages( myAgent, ACLMessage.INFORM);
        }
        receiveMessages( myAgent, ACLMessage.PROPOSE);
    }


    void waitForCascadedOffers(Agent myAgent) {

        while(inCascadingOfferPhase()) {
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


    void waitForCascadedConfirms( Agent myAgent) {

        while(inCascadingConfirmPhase()) {
            myAgent.doWait(1);
            receiveMessages( myAgent, ACLMessage.INFORM);
        }
        receiveMessages( myAgent, ACLMessage.CONFIRM);
    }


    boolean inRequestingPhase() {

        boolean requesting = false;
        for (var agentPhase : neighborsPhases.entrySet() ) {
            if (agentPhase.getValue() == ProtocolPhase.REQUESTING) {
                requesting = true;
                break;
            }
        }
        return requesting;
    }


    boolean inCascadingRequestPhase() {

        boolean cascadingRequest = false;
        for (var agentPhase : neighborsPhases.entrySet() ) {
            if (agentPhase.getValue() == ProtocolPhase.CASCADING_REQUEST) {
                cascadingRequest = true;
                break;
            }
        }
        return cascadingRequest;
    }


    boolean inOfferingPhase() {

        boolean offering = false;
        for (var agentPhase : neighborsPhases.entrySet() ) {
            if (agentPhase.getValue() == ProtocolPhase.OFFERING) {
                offering = true;
            }
        }
        return offering;
    }


    boolean inCascadingOfferPhase() {

        boolean cascadingOffer = false;
        for (var agentPhase : neighborsPhases.entrySet() ) {
            if (agentPhase.getValue() == ProtocolPhase.CASCADING_OFFER) {
                cascadingOffer = true;
            }
        }
        return cascadingOffer;
    }


    boolean inConfirmingPhase() {

        boolean confirming = false;
        for (var agentPhase : neighborsPhases.entrySet() ) {
            if (agentPhase.getValue() == ProtocolPhase.CONFORMING) {
                confirming = true;
                break;
            }
        }
        return confirming;
    }


    boolean inCascadingConfirmPhase() {

        boolean cascadingConfirm = false;
        for (var agentPhase : neighborsPhases.entrySet() ) {
            if (agentPhase.getValue() == ProtocolPhase.CASCADING_CONFIRM) {
                cascadingConfirm = true;
                break;
            }
        }
        return cascadingConfirm;
    }


    void resetRound() {

        blockedTasks.clear();
        receivedRequests.clear();
        sentRequests.clear();
        receivedOffers.clear();
        sentOffers.clear();
    }


    private void performTasks(Agent myAgent) {

//        if( myAgent.getLocalName().equals("Agent1")) {
//            System.out.print("");
//        }

        if (debugMode) {
            System.out.println(myAgent.getLocalName() + " Number of To Do Tasks: " + toDoTasks.size());
//        System.out.println (myAgent.getLocalName() +  " is performing tasks.");
        }
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

        if (debugMode) {
            System.out.println(myAgent.getLocalName() + " has performed " + doneTasks.size() + " tasks and gained total utility of " + totalUtil);
        }

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
        Map<ResourceType, Long> totalRequiredResources = new LinkedHashMap<>();

        for (Task task : blockedTasks) {
            for (var entry : task.requiredResources.entrySet()) {
                totalRequiredResources.put(entry.getKey(),  totalRequiredResources.getOrDefault(entry.getKey(), 0L) + entry.getValue());
            }
        }

        for (var resourceTypeQuantity : totalRequiredResources.entrySet()) {
            long missingQuantity = 0;
            if ( remainingResources.containsKey( resourceTypeQuantity.getKey())) {
                if (remainingResources.get(resourceTypeQuantity.getKey()).size() < resourceTypeQuantity.getValue()) {
                    missingQuantity = resourceTypeQuantity.getValue() - remainingResources.get(resourceTypeQuantity.getKey()).size();
                }
            } else {
                missingQuantity = resourceTypeQuantity.getValue();
            }

            if (missingQuantity > 0) {
                Map<Long, Long> utilityFunction = computeRequestUtilityFunction(blockedTasks, resourceTypeQuantity.getKey(), remainingResources, missingQuantity);
                Set<Integer> allReceivers = new TreeSet<>();
                Set<AID> receiverIds = new TreeSet<>();
                for (int i = 0; i < neighbors.length; i++) {
                    if (neighbors[i] != null) {
                        allReceivers.add(i+1);
                        AID aid = new AID(numberOfAgents + "Agent" + (i+1), AID.ISLOCALNAME);
                        receiverIds.add(aid);
                    }
                }
                String reqId = UUID.randomUUID().toString();
                sendRequest( reqId, resourceTypeQuantity.getKey(), missingQuantity, utilityFunction, allReceivers, receiverIds);
                sentRequests.put( reqId, new Request(reqId, false, missingQuantity, resourceTypeQuantity.getKey(), utilityFunction, myAgent.getAID(), null, allReceivers, null));
            }
        }
    }


    Map<Long, Long> computeRequestUtilityFunction(SortedSet<Task> blockedTasks, ResourceType resourceType, Map<ResourceType, SortedSet<ResourceItem>> remainingResources, long missingQuantity) {

        Map<Long, Long> utilityFunction = new LinkedHashMap<>();
        for (long i=1; i<=missingQuantity; i++) {
            long q = remainingResources.get(resourceType).size() + i;
            long totalUtility = 0;
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


    Map<Long, Long> computeOfferCostFunction(ResourceType resourceType, long availableQuantity, long offerQuantity, AID requester) {

        String requesterName = requester.getLocalName();
        int requesterId = Integer.valueOf(requesterName.replace(numberOfAgents+"Agent", ""));
        int distance = neighbors[requesterId-1];

        long cost;
        long expectedCost = 0;
        Map<Long, Long> offerCostFunction = new LinkedHashMap<>();
        for (long q=1; q<=offerQuantity; q++) {
            cost = utilityOfResources(resourceType, availableQuantity) - utilityOfResources( resourceType, availableQuantity - q);

            cost += distance * 1;

            if (cost == 0) {
                expectedCost = computeExpectedUtilityOfResources(resourceType, q, availableResources.get(resourceType));
//                System.out.println( expectedCost);
            }
            offerCostFunction.put(q, (long) (cost + 0.05 * expectedCost));
        }

        return offerCostFunction;
    }


    long utilityOfResources (ResourceType resourceType, long quantity) {

        long totalUtility = 0;
//        for (Task task : toDoTasks) {
//            if (task.requiredResources.containsKey(resourceType)) {
//                if (quantity >= task.requiredResources.get(resourceType)) {
//                    totalUtility += task.utility;
//                    quantity = quantity - task.requiredResources.get(resourceType);
//                }
//            }
//        }

        // determine the utility of tasks that can be done given all types of available resources
        Map<ResourceType, SortedSet<ResourceItem>> resources = deepCopyResourcesMap( availableResources);
        Map<ResourceType, Long> resourceQuantities = new LinkedHashMap<>();
        for (var entry: resources.entrySet()) {
            if (entry.getKey() == resourceType) {
                resourceQuantities.put(entry.getKey(), quantity);
            } else {
                resourceQuantities.put(entry.getKey(), (long) entry.getValue().size());
            }
        }

        for (Task task : toDoTasks) {
            boolean enough = true;
            if (task.requiredResources.containsKey(resourceType)) {
                for (var entry: task.requiredResources.entrySet()) {
                    if (resourceQuantities.containsKey(entry.getKey()) == false) {
                        enough = false;
                        break;
                    } else if (entry.getValue() > resourceQuantities.get(entry.getKey())) {
                        enough = false;
                        break;
                    }
                }
                if (enough == true ) {
                    totalUtility += task.utility;
                    for (var entry: task.requiredResources.entrySet()) {
                        resourceQuantities.put( entry.getKey(), resourceQuantities.get(entry.getKey()) - entry.getValue());
                    }
                }
            }
        }

        return totalUtility;
    }


    private void sendRequest (String reqId, ResourceType resourceType, long missingQuantity, Map<Long, Long> utilityFunction, Set<Integer> allReceivers, Set<AID> receiverIds) {

        ACLMessage msg = new ACLMessage(ACLMessage.REQUEST);

        for( AID aid : receiverIds) {
            msg.addReceiver(aid);
        }

        JSONObject jo = new JSONObject();
        jo.put("reqId", reqId);
        jo.put(Ontology.RESOURCE_REQUESTED_QUANTITY, missingQuantity);
        jo.put(Ontology.RESOURCE_TYPE, resourceType.name());
        jo.put(Ontology.REQUEST_UTILITY_FUNCTION, utilityFunction);
        jo.put(Ontology.ALL_RECEIVERS, allReceivers);

        msg.setContent( jo.toJSONString());
//      msg.setReplyByDate();
        send(msg);

//        System.out.println( this.getLocalName() + " sent a request with quantity: " + missingQuantity + " for resourceType: " + resourceType.name());
    }


    private void storeRequest (Agent myAgent, ACLMessage msg) throws ParseException {

        String content = msg.getContent();

        Object obj = new JSONParser().parse(content);
        JSONObject jo = (JSONObject) obj;

        String reqId = (String) jo.get("reqId");
//        String originalId = (String) jo.get("originalId");
        Long requestedQuantity = (Long) jo.get(Ontology.RESOURCE_REQUESTED_QUANTITY);
        String rt = (String) jo.get(Ontology.RESOURCE_TYPE);
        ResourceType resourceType = ResourceType.valueOf(rt);

        JSONArray joReceivers = (JSONArray) jo.get("allReceivers");

        Set<Integer> receivers = new TreeSet<>();
        for (int i=0; i<joReceivers.size(); i++) {
            Long value = (Long) joReceivers.get(i);
            receivers.add(Integer.valueOf(value.intValue()));
        }

        JSONObject joUtilityFunction = (JSONObject) jo.get(Ontology.REQUEST_UTILITY_FUNCTION);

//        System.out.println( myAgent.getLocalName() + " received request with quantity " + requestedQuantity + " for resource type " + resourceType.name() + " from " + msg.getSender().getLocalName());

        Map<Long, Long> utilityFunction = new LinkedHashMap<>();
        Iterator<String> keysIterator = joUtilityFunction.keySet().iterator();
        while (keysIterator.hasNext()) {
            String key = keysIterator.next();
            Long value = (Long) joUtilityFunction.get(key);
            utilityFunction.put( Long.valueOf(key), value);
        }

        Request request = new Request(reqId, null, requestedQuantity.intValue(), resourceType, utilityFunction, msg.getSender(), null, receivers, null);

        if ( receivedRequests.containsKey(resourceType) == false) {
            receivedRequests.put(resourceType, new ArrayList<>());
        }
        receivedRequests.get(resourceType).add(request);
    }


    private void deliberateOnCascadingRequest(Agent myAgent) {

        long offerQuantity;
        for (var requestsForType : receivedRequests.entrySet()) {
            ArrayList<Request> requests = requestsForType.getValue();
            if (availableResources.get(requestsForType.getKey()) != null) {
                long availableQuantity = availableResources.get(requestsForType.getKey()).size();
                while (availableQuantity > 0 && requests.size() > 0) {
                    // Greedy approach
                    Request selectedRequest = selectBestRequest( requests, availableQuantity);
                    if (availableQuantity < selectedRequest.quantity) {
                        offerQuantity = availableQuantity;
                        cascadeRequest( selectedRequest, offerQuantity);
                        availableQuantity = availableQuantity - offerQuantity;
                    } else {
                        offerQuantity = selectedRequest.quantity;
                        Map<Long, Long> costFunction = computeOfferCostFunction(selectedRequest.resourceType, availableQuantity, offerQuantity, selectedRequest.sender);
                        long cost = costFunction.get(offerQuantity);
                        long benefit = selectedRequest.utilityFunction.get(offerQuantity);
                        if (cost < benefit) {
//                            createOffer(selectedRequest.id, myAgent.getAID(), selectedRequest.sender, selectedRequest.resourceType, offerQuantity, costFunction, availableResources.get(selectedRequest.resourceType));
//                            availableQuantity = availableQuantity - offerQuantity;
                        } else {
                            cascadeRequest( selectedRequest, 0);
                        }
                    }
                    requests.remove( selectedRequest);
                }
                for (Request request : requests) {
                    cascadeRequest( request, 0);
                }
            } else {
                for (Request request : requests) {
                    cascadeRequest( request, 0);
                }
            }
        }
    }

    private void deliberateOnOffering(Agent myAgent) {

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

//        if( myAgent.getLocalName().equals("Agent1")) {
//            System.out.print("");
//        }

        long offerQuantity;
        for (var requestsForType : receivedRequests.entrySet()) {
            ArrayList<Request> requests = requestsForType.getValue();
            if (availableResources.get(requestsForType.getKey()) != null) {
                long availableQuantity = availableResources.get(requestsForType.getKey()).size();
                while (availableQuantity > 0 && requests.size() > 0) {
                    // Greedy approach
                    Request selectedRequest = selectBestRequest( requests, availableQuantity);
                    if (availableQuantity >= selectedRequest.quantity) {
                        offerQuantity = selectedRequest.quantity;
                        Map<Long, Long> costFunction = computeOfferCostFunction(selectedRequest.resourceType, availableQuantity, offerQuantity, selectedRequest.sender);
                        long cost = costFunction.get(offerQuantity);
                        long benefit = selectedRequest.utilityFunction.get(offerQuantity);

//                        count++;
//                        System.out.println();
//                        System.out.println(this.getLocalName() + " Count: " + count);
//                        System.out.println();

                        if (cost < benefit) {
                            createOffer(selectedRequest.id, myAgent.getAID(), selectedRequest.sender, selectedRequest.resourceType, offerQuantity, costFunction, availableResources.get(selectedRequest.resourceType));
                            availableQuantity = availableQuantity - offerQuantity;
                        }
                    }
                    requests.remove( selectedRequest);
                }
            }
        }
    }


    void cascadeRequest (Request request, long offerQuantity) {

        long missingQuantity = request.quantity - offerQuantity;
        Map<Long, Long> utilityFunction = new LinkedHashMap<>();
        for (long i=1; i<=missingQuantity; i++) {
            utilityFunction.put(i, request.utilityFunction.get(i+offerQuantity));
        }

        SortedSet<ResourceItem> availableItems = availableResources.get(request.resourceType);
        Map<String, Integer> reservedItems = new LinkedHashMap<>();
        for (long q=0; q<offerQuantity; q++) {
            ResourceItem item = availableItems.first();
            reservedItems.put(item.getId(), item.getLifetime());
            availableItems.remove( item);
        }

        Set<Integer> allReceivers = new TreeSet<>();
        Set<AID> receiverIds = new TreeSet<>();
        allReceivers.addAll( request.allReceivers);
        for (int i = 0; i < neighbors.length; i++) {
            if (neighbors[i] != null && !request.allReceivers.contains(i+1)) {
                allReceivers.add(i+1);
                AID aid = new AID(numberOfAgents + "Agent" + (i+1), AID.ISLOCALNAME);
                receiverIds.add(aid);
            }
        }
        String reqId = UUID.randomUUID().toString();
        sendRequest( reqId, request.resourceType, missingQuantity, utilityFunction, allReceivers, receiverIds);
        sentRequests.put( reqId, new Request(reqId, true, missingQuantity, request.resourceType, utilityFunction, this.getAID(), request.sender, allReceivers, reservedItems));
    }


    Request selectBestRequest(ArrayList<Request> requests, long remainingQuantity) {

        //TODO: select the request with highest efficiency

        Request selectedRequest = requests.get(0);
        long highestUtility = 0;
        long offerQuantity;

        for (Request request : requests) {
            if (remainingQuantity < request.quantity) {
                offerQuantity = remainingQuantity;
            } else {
                offerQuantity = request.quantity;
            }
            long util = request.utilityFunction.get(offerQuantity);
            if (util > highestUtility) {
                highestUtility = util;
                selectedRequest = request;
            }
        }

        return selectedRequest;
    }


    private void createOffer(String reqId, AID offerer, AID requester, ResourceType resourceType, long offerQuantity, Map<Long, Long> costFunction, SortedSet<ResourceItem> availableItems) {

        Map<String, Integer> offeredItems = new LinkedHashMap<>();

        for (long q=0; q<offerQuantity; q++) {
            ResourceItem item = availableItems.first();
            offeredItems.put(item.getId(), item.getLifetime());
            availableItems.remove( item);
        }

        String offerId = UUID.randomUUID().toString();
        Offer offer = new Offer(offerId, reqId, offerQuantity, resourceType, costFunction, offeredItems, offerer, requester);
        sentOffers.put( offerId, offer);

        sendOffer(reqId, offerId, requester, resourceType, offerQuantity, costFunction, offeredItems);
//        System.out.println( "createBid for resourceType: " + resourceType.name() + " with offerQuantity: " + offerQuantity);
    }


    private void sendOffer(String reqId, String offerId, AID requester, ResourceType resourceType, long offerQuantity, Map<Long, Long> costFunction, Map<String, Integer> offeredItems) {

        ACLMessage msg = new ACLMessage(ACLMessage.PROPOSE);

        msg.addReceiver( requester);

        JSONObject jo = new JSONObject();
        jo.put("reqId", reqId);
        jo.put("offerId", offerId);
        jo.put(Ontology.RESOURCE_OFFER_QUANTITY, offerQuantity);
        jo.put(Ontology.RESOURCE_TYPE, resourceType.name());
        jo.put(Ontology.OFFER_COST_FUNCTION, costFunction);
        jo.put(Ontology.OFFERED_ITEMS, offeredItems);

        msg.setContent( jo.toJSONString());

//      msg.setReplyByDate();

        send(msg);
    }


    private void storeOffer(Agent myAgent, ACLMessage msg) throws ParseException {

        // if agents operate and communicate asynchronously, then a bid might be received at any time.
        // the requester can wait for other bids before confirming.

        String content = msg.getContent();

        Object obj = new JSONParser().parse(content);
        JSONObject jo = (JSONObject) obj;

        Long offerQuantity = (Long) jo.get(Ontology.RESOURCE_OFFER_QUANTITY);

        String reqId = (String) jo.get("reqId");
        String offerId = (String) jo.get("offerId");
        String rt = (String) jo.get(Ontology.RESOURCE_TYPE);
        ResourceType resourceType = ResourceType.valueOf(rt);
        JSONObject joCostFunction = (JSONObject) jo.get(Ontology.OFFER_COST_FUNCTION);
        JSONObject joOfferedItems = (JSONObject) jo.get(Ontology.OFFERED_ITEMS);
        if (debugMode) {
            System.out.println(myAgent.getLocalName() + " received offer with quantity " + offerQuantity + " for resource type " + resourceType.name() + " from " + msg.getSender().getLocalName());
        }
        Map<Long, Long> costFunction = new LinkedHashMap<>();
        Iterator<String> keysIterator1 = joCostFunction.keySet().iterator();
        while (keysIterator1.hasNext()) {
            String key = keysIterator1.next();
            Long value = (Long) joCostFunction.get(key);
            costFunction.put( Long.valueOf(key), value);
        }

        Map<String, Integer> offeredItems = new LinkedHashMap<>();
        Iterator<String> keysIterator2 = joOfferedItems.keySet().iterator();
        while (keysIterator2.hasNext()) {
            String key = keysIterator2.next();
            Long value = (Long) joOfferedItems.get(key);
            offeredItems.put( key, value.intValue());
        }

        Offer offer = new Offer(offerId, reqId, offerQuantity.intValue(), resourceType, costFunction, offeredItems, msg.getSender(), myAgent.getAID());

        Set<Offer> offers = receivedOffers.get(reqId);
        if (offers == null) {
            offers = new HashSet<>();
        }
        offers.add(offer);
        receivedOffers.put( reqId, offers);

        if( sentRequests.keySet().contains(reqId) == false) {
            errorCount++;
            System.out.println(this.getLocalName() + " errorCount: " + errorCount);
        }
    }


    void deliberateOnCascadingOffer(Agent myAgent) {

        for (var request : sentRequests.entrySet()) {
            if (request.getValue().cascaded == true) {
//                if (receivedOffers.containsKey(request.getKey())) {
                    cascadeOffers( request.getValue());
//                }
            }
        }
    }


    private void cascadeOffers(Request cascadedRequest) {

        long availableQuantity = availableResources.get(cascadedRequest.resourceType).size();
        Map<Long, Long> costFunction = computeOfferCostFunction(cascadedRequest.resourceType, availableQuantity, cascadedRequest.reservedItems.size(), cascadedRequest.originalSender);

        long offerQuantity = cascadedRequest.reservedItems.size();

        Map<String, Integer> offeredItems = new LinkedHashMap<>();
        for (var item : cascadedRequest.reservedItems.entrySet()) {
            offeredItems.put(item.getKey(), item.getValue());
        }

        Set<Offer> offers = null;
        if (receivedOffers.containsKey(cascadedRequest.id)) {
            offers = receivedOffers.get(cascadedRequest.id);
            String requesterName = cascadedRequest.originalSender.getLocalName();
            int requesterId = Integer.valueOf(requesterName.replace(numberOfAgents+"Agent", ""));
            int distance = neighbors[requesterId-1];

            Map<Offer, Long> offerQuantities = new LinkedHashMap<>();

            long minCost, cost;
            Offer lowCostOffer;
            for (Offer offer : offers) {
                offerQuantities.put(offer, 0L);
            }

            for (long q=cascadedRequest.reservedItems.size()+1; q<= cascadedRequest.quantity; q++) {
                minCost = Integer.MAX_VALUE;
                cost = 0;
                lowCostOffer = null;
                for (Offer offer : offers) {
                    if (hasExtraItem(offer, offerQuantities)) {
                        cost = totalCost(offer, offerQuantities);
                        if (cost < minCost) {
                            minCost = cost;
                            lowCostOffer = offer;
                        }
                    }
                }
                if (lowCostOffer != null) {
                    cost += distance * 1;
                    costFunction.put(q, cost);
                    offerQuantities.put(lowCostOffer, offerQuantities.get(lowCostOffer) + 1);
                } else {
                    break;
                }
            }

            for ( var offer : offerQuantities.entrySet()) {
                offerQuantity += offer.getValue();
            }

            for ( var offer : offerQuantities.entrySet()) {
                Iterator itr = offer.getKey().offeredItems.keySet().iterator();
                long q=1;
                while (q<=offer.getValue()) {
                    String itemId = (String) itr.next();
                    offeredItems.put(itemId, offer.getKey().offeredItems.get(itemId));
                    q++;
                }
            }
        }

        if (offerQuantity > 0) {
            String offerId = UUID.randomUUID().toString();
            Offer offer = new Offer(offerId, cascadedRequest.id, offerQuantity, cascadedRequest.resourceType, costFunction, offeredItems, this.getAID(), cascadedRequest.originalSender, offers);
            sentOffers.put(offerId, offer);
            sendOffer(cascadedRequest.id, offerId, cascadedRequest.originalSender, cascadedRequest.resourceType, offerQuantity, costFunction, offeredItems);
        }
    }


    void deliberateOnConfirming( Agent myAgent) {

//        if( myAgent.getLocalName().equals("Agent1")) {
//            System.out.print("");
//        }

        Map<Request, Map<Offer, Long>> selectedOffersForAllRequests = new LinkedHashMap<>();

        for (var request : sentRequests.entrySet()) {
            if ( receivedOffers.containsKey(request.getKey())) {
                Map<Offer, Long> confirmQuantities = processOffers( request.getValue());
                if (confirmQuantities.size() == 0) {
                    System.out.println("Error!!");
                }
                selectedOffersForAllRequests.put(request.getValue(), confirmQuantities);
            }
        }

        if (selectedOffersForAllRequests.size() > 0) {

            if (thereIsBenefitToConfirmOffers( selectedOffersForAllRequests)) {

//                count++;
//                System.out.println();
//                System.out.println(this.getLocalName() + " Count: " + count);
//                System.out.println();

                createConfirmation( selectedOffersForAllRequests);
                addResourceItemsInOffers(selectedOffersForAllRequests);
            } else {
                createRejection( selectedOffersForAllRequests);
            }
        }
    }


    void addResourceItemsInOffers(Map<Request, Map<Offer, Long>> confirmQuantitiesForAllRequests) {

        for (var confirmQuantitiesForReq : confirmQuantitiesForAllRequests.entrySet()) {
            SortedSet<ResourceItem> resourceItems;
            if (availableResources.containsKey(confirmQuantitiesForReq.getKey().resourceType)) {
                resourceItems = availableResources.get( confirmQuantitiesForReq.getKey().resourceType);
            } else {
                resourceItems = new TreeSet<>(new ResourceItem.resourceItemComparator());
            }
            for (var offerQuantity : confirmQuantitiesForReq.getValue().entrySet()) {
                // create a sorted set of offered items
                SortedSet<ResourceItem> offeredItems = new TreeSet<>(new ResourceItem.resourceItemComparator());
                for (var itemIdLifetime : offerQuantity.getKey().offeredItems.entrySet()) {
                    offeredItems.add(new ResourceItem(itemIdLifetime.getKey(), offerQuantity.getKey().resourceType, itemIdLifetime.getValue()));
                }
                Iterator<ResourceItem> itr = offeredItems.iterator();
                long q=1;
                while (q<=offerQuantity.getValue()) {
                    ResourceItem item = itr.next();
                    resourceItems.add(item);
                    q++;
                }
            }

            availableResources.put( confirmQuantitiesForReq.getKey().resourceType, resourceItems);
        }

        // TODO: decrease the cost of transfer between sender and receiver from totalUtil

    }


    boolean thereIsBenefitToConfirmOffers(Map<Request, Map<Offer, Long>> selectedOffersForAllRequests) {

        Map<ResourceType, SortedSet<ResourceItem>> resources = deepCopyResourcesMap( availableResources);
        Map<ResourceType, Long> resourceQuantities = new LinkedHashMap<>();
        for (var entry: resources.entrySet()) {
            resourceQuantities.put(entry.getKey(), (long) entry.getValue().size());
        }
        long totalUtilityBeforeConfirm = totalUtilityOfResources( resourceQuantities);

        long sum;
        for (var selectedOffersForReq : selectedOffersForAllRequests.entrySet()) {
            sum = 0;
            for (var offerQuantity: selectedOffersForReq.getValue().entrySet()) {
                sum += offerQuantity.getValue();
            }
            if (resourceQuantities.containsKey(selectedOffersForReq.getKey().resourceType)) {
                resourceQuantities.put(selectedOffersForReq.getKey().resourceType, resourceQuantities.get(selectedOffersForReq.getKey().resourceType) + sum);
            } else {
                resourceQuantities.put(selectedOffersForReq.getKey().resourceType, sum);
            }
        }

        long totalUtilityAfterConfirm = totalUtilityOfResources( resourceQuantities);

        // find the max cost of bids per request
        long maxCost = 0, cost;
        for (var selectedOffersForReq : selectedOffersForAllRequests.entrySet()) {
            cost = 0;
            for (var offerQuantity : selectedOffersForReq.getValue().entrySet()) {
                if (offerQuantity.getValue() > 0) {
                    cost = cost + offerQuantity.getKey().costFunction.get(offerQuantity.getValue());
                }
            }
            if (cost > maxCost) {
                maxCost = cost;
            }
        }

        if (totalUtilityAfterConfirm - totalUtilityBeforeConfirm > maxCost) {
//        if (totalUtilityAfterConfirm - totalUtilityBeforeConfirm > 0) {
            return true;
        } else {
            return false;
        }
    }


    long totalUtilityOfResources (Map<ResourceType, Long> resourceQuantities) {

        long totalUtility = 0;
        for (Task task : toDoTasks) {
            boolean enough = true;
            for (var entry: task.requiredResources.entrySet()) {
                if (resourceQuantities.containsKey(entry.getKey()) == false) {
                    enough = false;
                    break;
                } else if (entry.getValue() > resourceQuantities.get(entry.getKey())) {
                    enough = false;
                    break;
                }
            }
            if (enough == true ) {
                totalUtility += task.utility;
                for (var entry: task.requiredResources.entrySet()) {
                    resourceQuantities.put( entry.getKey(), resourceQuantities.get(entry.getKey()) - entry.getValue());
                }
            }
        }
        return totalUtility;
    }


    public Map<Offer, Long> processOffers(Request request) {

        // the requester selects the combination of offers that maximizes the difference between the utility of request and the total cost of all selected offers.
        // it is allowed to take partial amounts of oﬀered resources in multiple offers up to the requested amount.
        // a greedy approach: we add 1 item from one offer in a loop up to the requested amount, without backtracking.

        Set<Offer> offers = receivedOffers.get(request.id);
        long minCost, cost;
        Offer lowCostOffer;
        Map<Offer, Long> confirmQuantities = new LinkedHashMap<>();
        for (Offer offer : offers) {
            confirmQuantities.put(offer, 0L);
        }

        for (long q=1; q<=request.quantity; q++) {
            minCost = Integer.MAX_VALUE;
            lowCostOffer = null;
            for (Offer offer : offers) {
                if (hasExtraItem(offer, confirmQuantities)) {
                    cost = totalCost(offer, confirmQuantities);
                    if (cost < minCost) {
                        minCost = cost;
                        lowCostOffer = offer;
                    }
                }
            }
            if (lowCostOffer != null) {
                    confirmQuantities.put(lowCostOffer, confirmQuantities.get(lowCostOffer) + 1);
            } else {
                break;
            }
        }

        return confirmQuantities;
    }


    private boolean hasExtraItem (Offer offer, Map<Offer, Long> confirmQuantities) {

//        if ( confirmQuantities.containsKey(bid)) {
            if (confirmQuantities.get(offer) < offer.quantity) {
                return true;
            } else {
                return false;
            }
//        } else {
//            return true;
//        }
    }


    private long totalCost(Offer offer, Map<Offer, Long> confirmQuantities) {

        long totalCost = 0;

        Map<Offer, Long> tempQuantities =  new LinkedHashMap<>();
        for (var entry : confirmQuantities.entrySet()) {
            if (entry.getValue() > 0) {
                tempQuantities.put(entry.getKey(), entry.getValue());
            }
        }

        if (tempQuantities.containsKey(offer)) {
            tempQuantities.put(offer, tempQuantities.get(offer) + 1);
        } else {
            tempQuantities.put(offer, 1L);
        }

        for (var entry : tempQuantities.entrySet()) {
            if (entry.getKey().costFunction.get(entry.getValue()) == null) {
                System.out.println( "ERROR!!");
            }
            totalCost = totalCost + entry.getKey().costFunction.get(entry.getValue());
        }

        return totalCost;
    }


    public Set<Set<Offer>> getSubsets(Set<Offer> set) {
        if (set.isEmpty()) {
            return Collections.singleton(Collections.emptySet());
        }

        Set<Set<Offer>> subSets = set.stream().map(item -> {
                    Set<Offer> clone = new HashSet<>(set);
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


    private void createConfirmation (Map<Request, Map<Offer, Long>> confirmQuantitiesForAllRequests) {

        for (var confirmQuantitiesForReq : confirmQuantitiesForAllRequests.entrySet()) {
            for (var offerQuantity : confirmQuantitiesForReq.getValue().entrySet()) {
                sendConfirmation (offerQuantity.getKey().id, offerQuantity.getKey().sender, offerQuantity.getKey().resourceType, offerQuantity.getValue());
            }
        }
    }


    private void createRejection (Map<Request, Map<Offer, Long>> confirmQuantitiesForAllRequests) {

        for (var confirmQuantitiesForReq : confirmQuantitiesForAllRequests.entrySet()) {
            for (var offerQuantity : confirmQuantitiesForReq.getValue().entrySet()) {
                sendConfirmation (offerQuantity.getKey().id, offerQuantity.getKey().sender, offerQuantity.getKey().resourceType, 0);
            }
        }
    }


    void sendConfirmation (String offerId, AID offerer, ResourceType resourceType, long confirmQuantity) {

        ACLMessage msg = new ACLMessage(ACLMessage.CONFIRM);

        msg.addReceiver (offerer);

        JSONObject jo = new JSONObject();
        jo.put("offerId", offerId);
        jo.put(Ontology.RESOURCE_TYPE, resourceType.name());
        jo.put(Ontology.RESOURCE_CONFIRM_QUANTITY, confirmQuantity);

        msg.setContent( jo.toJSONString());
        send(msg);

//        System.out.println( myAgent.getLocalName() + " sent confirmation with quantity " + confirmQuantity + " for resource type " + resourceType.name() + " to offerer " + offerer.getLocalName());
    }


    private void processConfirmation (Agent myAgent, ACLMessage confirmation) throws ParseException {

        String content = confirmation.getContent();
        Object obj = new JSONParser().parse(content);
        JSONObject jo = (JSONObject) obj;

        String offerId = (String) jo.get("offerId");
        String rt = (String) jo.get(Ontology.RESOURCE_TYPE);
        ResourceType resourceType = ResourceType.valueOf(rt);
        Long confirmQuantity = (Long) jo.get(Ontology.RESOURCE_CONFIRM_QUANTITY);

        if (debugMode) {
            System.out.println(myAgent.getLocalName() + " received confirmation with quantity " + confirmQuantity + " for resource type " + resourceType.name() + " from " + confirmation.getSender().getLocalName());
        }

        Offer sentOffer = sentOffers.get(offerId);
        Set<Offer> includedOffers = sentOffer.includedOffers;
        Request cascadedRequest = sentRequests.get(sentOffer.reqId);

        if (includedOffers != null) {
            Map<Offer, Long> offerQuantities = new LinkedHashMap<>();
            for (Offer offer : includedOffers) {
                offerQuantities.put(offer, 0L);
            }
            if (confirmQuantity > cascadedRequest.reservedItems.size()) {
                long minCost, cost;
                Offer lowCostOffer;
                for (long q = cascadedRequest.reservedItems.size()+1; q <= confirmQuantity; q++) {
                    minCost = Integer.MAX_VALUE;
                    lowCostOffer = null;
                    for (Offer offer : includedOffers) {
                        if (hasExtraItem(offer, offerQuantities)) {
                            cost = totalCost(offer, offerQuantities);
                            if (cost < minCost) {
                                minCost = cost;
                                lowCostOffer = offer;
                            }
                        }
                    }
                    if (lowCostOffer != null) {
                        offerQuantities.put(lowCostOffer, offerQuantities.get(lowCostOffer) + 1);
                    } else {
                        break;
                    }
                }
            }
            cascadePartialConfirmations(offerQuantities);
        }

        restoreResources(offerId, resourceType, confirmQuantity);
    }


    private void cascadePartialConfirmations(Map<Offer, Long> offerQuantities) {

        for (var offerQuantity : offerQuantities.entrySet()) {
            sendConfirmation (offerQuantity.getKey().id, offerQuantity.getKey().sender, offerQuantity.getKey().resourceType, offerQuantity.getValue());
        }
    }


    private void restoreResources(String offerId, ResourceType resourceType, long confirmQuantity) {

        Offer sentOffer = sentOffers.get( offerId);
        Set<Offer> includedOffers = sentOffer.includedOffers;
        Request cascadedRequest = sentRequests.get(sentOffer.reqId);

        if (includedOffers != null) {
            if (confirmQuantity < cascadedRequest.reservedItems.size()) {
                // create a sorted set of offered items
                SortedSet<ResourceItem> offeredItems = new TreeSet<>(new ResourceItem.resourceItemComparator());
                for (var offeredItem : cascadedRequest.reservedItems.entrySet()) {
                    offeredItems.add(new ResourceItem(offeredItem.getKey(), resourceType, offeredItem.getValue()));
                }
                Iterator<ResourceItem> itr = offeredItems.iterator();
                long q=1;
                while (q<=confirmQuantity) {
                    ResourceItem item = itr.next();
                    offeredItems.remove(item);
                    itr = offeredItems.iterator();
                    q++;
                }
                availableResources.get(resourceType).addAll(offeredItems);
            }
        } else if (confirmQuantity < sentOffer.quantity) {
            // create a sorted set of offered items
            SortedSet<ResourceItem> offeredItems = new TreeSet<>(new ResourceItem.resourceItemComparator());
            for (var offeredItem : sentOffer.offeredItems.entrySet()) {
                offeredItems.add(new ResourceItem(offeredItem.getKey(), resourceType, offeredItem.getValue()));
            }
            Iterator<ResourceItem> itr = offeredItems.iterator();
            long q=1;
            while (q<=confirmQuantity) {
                ResourceItem item = itr.next();
                offeredItems.remove(item);
                itr = offeredItems.iterator();
                q++;
            }
            availableResources.get(resourceType).addAll(offeredItems);
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


    long computeExpectedUtilityOfResources ( ResourceType resourceType, long quantity, SortedSet<ResourceItem> resourceItems) {

        double exp = 0.0;
        ArrayList<Task> doneTasksWithThisResourceType = new ArrayList<>();
        long totalUtilityWithThisResourceType = 0;
        long totalQuantityOfThisResourceType = 0;

        for (Task task : doneTasks) {
            if (task.requiredResources.containsKey(resourceType)) {
                doneTasksWithThisResourceType.add( task);
                totalUtilityWithThisResourceType = totalUtilityWithThisResourceType + task.utility;
                totalQuantityOfThisResourceType = totalQuantityOfThisResourceType + task.requiredResources.get(resourceType);
            }
        }

        if (doneTasks.size() > 0 && totalQuantityOfThisResourceType > 0) {
            Iterator<ResourceItem> itr = resourceItems.iterator();
            long q=1;
            while (q <= quantity) {
                ResourceItem item = itr.next();
                if (item.getLifetime() > 1) {
//                    exp = exp + ((double) totalUtilityWithThisResourceType / totalQuantityOfThisResourceType);
                    exp = exp + (( (double) doneTasksWithThisResourceType.size() / doneTasks.size()) * ( (double) totalUtilityWithThisResourceType / totalQuantityOfThisResourceType));
                }
//                exp = exp + (item.getLifetime() * (doneTasksWithThisResourceType.size() / doneTasks.size()) * (totalUtilityWithThisResourceType / totalQuantityOfThisResourceType));
                q++;
            }
        }

        return Math.round(exp);
    }


    long computeExpectedCost ( ResourceType resourceType, long quantity, SortedSet<ResourceItem> resourceItems) {

        Set<String> requesters = Set.of("8Agent1", "8Agent2", "8Agent3", "8Agent4");
        Set<String> bidders = Set.of("8Agent5", "8Agent6", "8Agent7", "8Agent8");

        double exp = 0.0;
        double averageRequiredQuantity;
        double averageUtil;

//        if( bidders.contains(this.getLocalName()) && resourceType == ResourceType.A ) {
//            averageRequiredQuantity = 4.5 / 2 + 4.5 ;
//        } else {
            averageRequiredQuantity = 3;
//        }

//        if( bidders.contains(this.getLocalName())) {
//            averageUtil = 22.5;
//        } else {
            averageUtil = 18.14;
//        }

        Iterator<ResourceItem> itr = resourceItems.iterator();
        long q=1;
        while (q <= quantity) {
            ResourceItem item = itr.next();
            if (item.getLifetime() > 1) {
                exp = exp + averageUtil / averageRequiredQuantity;
            }
            q++;
        }

        return Math.round(exp);
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

        neighborsPhases.put( msg.getSender(), protocolPhase);
    }


    void sendNewTasksToMasterAgent (SortedSet<Task> newTasks, Agent myAgent) {

        ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
        AID aid = new AID(numberOfAgents + "Agent0", AID.ISLOCALNAME);
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
        AID aid = new AID(numberOfAgents + "Agent0", AID.ISLOCALNAME);
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


    void sendTotalUtilToMasterAgent (long totalUtil, Agent myAgent) {

        ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
        AID aid = new AID(numberOfAgents + "Agent0", AID.ISLOCALNAME);
        msg.addReceiver(aid);

        JSONObject jo = new JSONObject();
        jo.put( "totalUtil", totalUtil);

        msg.setContent( jo.toJSONString());
        send(msg);

//        System.out.println( myAgent.getLocalName() + " sent total utility: " + totalUtil + " to the master agent" );
    }


    void receiveMessages(Agent myAgent, int performative) {

//        if( myAgent.getLocalName().equals("Agent1")) {
//            System.out.print("");
//        }

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
                        storeOffer(myAgent, msg);
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
