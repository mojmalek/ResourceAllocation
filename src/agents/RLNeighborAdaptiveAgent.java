package agents;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.TickerBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import model.*;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;


public class RLNeighborAdaptiveAgent extends Agent {

    SimulationEngine simulationEngine;
    private boolean debugMode = false;
    private String logFileName, agentLogFileName;
    private String agentType;

    private Integer[] neighbors;
    private Map<AID, ProtocolPhase> neighborsPhases = new LinkedHashMap<>();

    private SortedSet<Task> toDoTasks = new TreeSet<>(new Task.taskComparator());
    private SortedSet<Task> blockedTasks = new TreeSet<>(new Task.taskComparator());
    private SortedSet<Task> doneTasks = new TreeSet<>(new Task.taskComparator());
    private long totalUtil;
    private int numberOfRounds;
    private int numberOfAgents;

    private Map<ResourceType, SortedSet<ResourceItem>> availableResources = new LinkedHashMap<>();
    private Map<ResourceType, SortedSet<ResourceItem>> expiredResources = new LinkedHashMap<>();

    private int totalReceivedResources;
    private int totalConsumedResource;
    private int totalExpiredResource;

    // reqId
    public Map<String, Request> sentRequests = new LinkedHashMap<>();

    public Map<ResourceType, ArrayList<Request>> receivedRequests = new LinkedHashMap<>();
    // offerId
    public Map<String, Offer> sentOffers = new LinkedHashMap<>();
    // reqId
    public Map<String, Set<Offer>> receivedOffers = new LinkedHashMap<>();

    private Map<OfferingStateAction, Long> offeringQFunction;
    private Map<ConfirmingStateAction, Long> confirmingQFunction;

    private final double alpha = 0.1; // Learning rate
    private final double gamma = 0.9; // Eagerness - 0 looks in the near future, 1 looks in the distant future
    private final double epsilon = 0.1; // With a small probability of epsilon, we choose to explore, i.e., not to exploit what we have learned so far

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
            logFileName = (String) args[4];
            simulationEngine = (SimulationEngine) args[5];
            agentType = (String) args[6];
        }

        for (int i = 0; i < neighbors.length; i++) {
            if (neighbors[i] != null) {
                AID aid = new AID(agentType + (i+1), AID.ISLOCALNAME);
                neighborsPhases.put(aid, ProtocolPhase.REQUESTING);
            }
        }

        for (ResourceType resourceType : ResourceType.getValues()) {
            availableResources.put( resourceType, new TreeSet<>(new ResourceItem.resourceItemComparator()));
            expiredResources.put( resourceType, new TreeSet<>(new ResourceItem.resourceItemComparator()));
        }

        agentLogFileName = "logs/" + this.getLocalName() + "-" + new Date() + ".txt";

        addBehaviour (new TickerBehaviour(this, 1) {
            protected void onTick() {
                if (this.getTickCount() <= numberOfRounds) {
//                    System.out.println(myAgent.getLocalName() + " Round: " + this.getTickCount());
                    findTasks(myAgent);
                    findResources(myAgent);
                    negotiate(myAgent);
                    performTasks(myAgent);
                }
                if (this.getTickCount() == numberOfRounds + 1) {
                    int totalAvailable = 0;
                    for (var resource : availableResources.entrySet()) {
                        totalAvailable += resource.getValue().size();
                    }
                    System.out.println (myAgent.getLocalName() + " totalReceivedResources " + totalReceivedResources + " totalConsumedResource " + totalConsumedResource + " totalExpiredResource " + totalExpiredResource + " totalAvailable " + totalAvailable);
                    if (totalReceivedResources - totalConsumedResource - totalExpiredResource != totalAvailable ) {
                        int difference = totalReceivedResources - totalConsumedResource - totalExpiredResource - totalAvailable;
                        System.out.println ("Error!! " + myAgent.getLocalName() + " has INCORRECT number of resources left. Diff: " + difference);
                    }
                }
            }
        });


//        addBehaviour(new CyclicBehaviour() {
//            @Override
//            public void action() {
//                ACLMessage msg = myAgent.receive();
//                if (msg != null) {
//                    int performative = msg.getPerformative();
//                    switch (performative) {
//                        case ACLMessage.REQUEST:
//                            try {
//                                storeRequest(myAgent, msg);
//                            } catch (ParseException e) {
//                                e.printStackTrace();
//                            }
//                            break;
//                        case ACLMessage.PROPOSE:
//                            try {
//                                storeOffer(myAgent, msg);
//                            } catch (ParseException e) {
//                                e.printStackTrace();
//                            }
//                            break;
//                        case ACLMessage.CONFIRM:
//                            try {
//                                processConfirmation(myAgent, msg);
//                            } catch (ParseException e) {
//                                e.printStackTrace();
//                            }
//                            break;
//                        case ACLMessage.INFORM:
//                            try {
//                                processNotification(myAgent, msg);
//                            } catch (ParseException e) {
//                                e.printStackTrace();
//                            }
//                            break;
//                    }
//                } else {
//                    block();
//                }
//            }
//        });
    }


    private void findTasks(Agent myAgent) {

//        System.out.println (myAgent.getLocalName() + " is finding tasks.");

        expireTasks( myAgent);

        SortedSet<Task> newTasks = simulationEngine.findTasks( myAgent);
        toDoTasks.addAll(newTasks);

        sendNewTasksToMasterAgent (newTasks, myAgent);

//        System.out.println (myAgent.getLocalName() + " has " + toDoTasks.size() + " tasks to do.");
    }


    private void findResources(Agent myAgent) {

        // decrease lifetime of remaining resources
        expireResourceItems( myAgent);

        Map<ResourceType, SortedSet<ResourceItem>> newResources = simulationEngine.findResources( myAgent);

        // add to available resources
        for (var newResource : newResources.entrySet()) {
            availableResources.get(newResource.getKey()).addAll( newResource.getValue());
            totalReceivedResources += newResource.getValue().size();
//            for (ResourceItem item : newResource.getValue()) {
//                logInf( myAgent.getLocalName(), "received resource item with id: " + item.getId());
//            }
        }

        sendNewResourcesToMasterAgent (newResources, myAgent);
    }


    void expireResourceItems(Agent myAgent) {

        SortedSet<ResourceItem> availableItems;
        SortedSet<ResourceItem> expiredItems;
        SortedSet<ResourceItem> expiredItemsInThisRound = new TreeSet<>(new ResourceItem.resourceItemComparator());
        for (var resource : availableResources.entrySet()) {
            expiredItemsInThisRound.clear();
            availableItems = availableResources.get( resource.getKey());
            expiredItems = expiredResources.get( resource.getKey());
            for (ResourceItem item : availableItems) {
                item.setExpiryTime( item.getExpiryTime() - 1);
                if (item.getExpiryTime() == 0) {
                    expiredItemsInThisRound.add( item);
                    expiredItems.add( item);
                }
            }
            int initialSize = availableItems.size();
            availableItems.removeAll( expiredItemsInThisRound);
            totalExpiredResource += expiredItemsInThisRound.size();
            if ( initialSize - expiredItemsInThisRound.size() != availableItems.size()) {
                logErr( myAgent.getLocalName(), "initialSize - expiredItemsNow.size() != availableItems.size()");
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

        resetRound();
        deliberateOnRequesting (myAgent);
        sendNextPhaseNotification (ProtocolPhase.OFFERING);
        waitForRequests( myAgent);
//        if( myAgent.getLocalName().equals("Agent1")) {
//            System.out.print("");
//        }
        if (receivedRequests.size() > 0) {
//            deliberateOnGreedyOffering( myAgent);
            deliberateOnRLOffering( myAgent);
        }
        sendNextPhaseNotification (ProtocolPhase.CONFORMING);
        waitForOffers( myAgent);
//        if( myAgent.getLocalName().equals("Agent1")) {
//            System.out.print("");
//        }
        if (receivedOffers.size() > 0) {
//            deliberateOnConfirmingGreedy( myAgent);
            deliberateOnConfirmingRL( myAgent);
//            deliberateOnConfirmingDP( myAgent);
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
                AID aid = new AID(agentType + (i+1), AID.ISLOCALNAME);
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


    void waitForOffers(Agent myAgent) {

        while(inOfferingPhase()) {
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


    boolean inOfferingPhase() {

        boolean offering = false;
        for (var agentPhase : neighborsPhases.entrySet() ) {
            if (agentPhase.getValue() == ProtocolPhase.OFFERING) {
                offering = true;
            }
        }
        return offering;
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

        for (var entry : task.requiredResources.entrySet()) {
            SortedSet<ResourceItem> resourceItems = availableResources.get(entry.getKey());
            for (int i = 0; i < entry.getValue(); i++) {
                ResourceItem item = resourceItems.first();
                resourceItems.remove(item);
                totalConsumedResource++;
//                logInf( this.getLocalName(), "consumed resource item with id: " + item.getId());
            }
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
                        AID aid = new AID(agentType + (i+1), AID.ISLOCALNAME);
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


    Map<Long, Long> computeOfferCostFunction(ResourceType resourceType, long availableQuantity, long offerQuantity) {

//        String requesterName = requester.getLocalName();
//        int requesterId = Integer.valueOf(requesterName.replace(agentType, ""));
//        int distance = neighbors[requesterId-1];

        long cost;
        long expectedCost = 0;
        Map<Long, Long> offerCostFunction = new LinkedHashMap<>();
        for (long q=1; q<=offerQuantity; q++) {
            cost = utilityOfResources(resourceType, availableQuantity) - utilityOfResources( resourceType, availableQuantity - q);
//            cost += distance * 1;
//            if (cost == 0) {
//                expectedCost = computeExpectedUtilityOfResources(resourceType, q, availableResources.get(resourceType));
//                System.out.println( expectedCost);
//            }
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


    private void deliberateOnGreedyOffering(Agent myAgent) {

        // if agents operate and communicate asynchronously, then a request might be received at any time.
        // the bidder can wait for other requests before bidding.

        // if the rounds are synchronous, there can be more than one request, then we can consider different approaches:

        // Optimal:
        // select the optimal combination of requests to maximize the utility.
        // max SUM xi Ui(j)
        // subject to:
        // xi in (0, 1)
        //  1 < j < qi
        // SUM xi <= 1

        // Greedy approach:
        // sort requests based on their utilities, and while there are available resources,
        // create bid for request with the highest utility.

        // Reinforcement learning approach:
        // learn to better select requests

//        if( myAgent.getLocalName().equals("Agent1")) {
//            System.out.print("");
//        }

        long offerQuantity;
        for (var requestsForType : receivedRequests.entrySet()) {
            if (availableResources.get(requestsForType.getKey()) != null) {
                long availableQuantity = availableResources.get(requestsForType.getKey()).size();
                ArrayList<Request> requests = requestsForType.getValue();
                while (availableQuantity > 0 && requests.size() > 0) {
                    // Greedy approach
                    Request selectedRequest = selectBestRequest( requests, availableQuantity);
                    if (availableQuantity < selectedRequest.quantity) {
                        offerQuantity = availableQuantity;
                    } else {
                        offerQuantity = selectedRequest.quantity;
                    }
                    Map<Long, Long> costFunction = computeOfferCostFunction(selectedRequest.resourceType, availableQuantity, offerQuantity);
                    long cost = costFunction.get(offerQuantity);
                    long benefit = selectedRequest.utilityFunction.get(offerQuantity);
                    if (cost < benefit) {
                        createOffer(selectedRequest.id, myAgent.getAID(), selectedRequest.sender, selectedRequest.resourceType, offerQuantity, costFunction, availableResources.get(selectedRequest.resourceType));
                        availableQuantity = availableQuantity - offerQuantity;
                    }
                    requests.remove( selectedRequest);
                }
            }
        }
    }


    private void deliberateOnRLOffering(Agent myAgent) {

        for (var requestsForType : receivedRequests.entrySet()) {
            ResourceType resourceType = requestsForType.getKey();
            if (availableResources.get(resourceType) != null) {
                long availableQuantity = availableResources.get(requestsForType.getKey()).size();
                ArrayList<Request> requests = requestsForType.getValue();
                while (availableQuantity > 0 && requests.size() > 0) {
                    // RL approach
                    OfferingState state = generateOfferingState (resourceType, requests, availableQuantity);

                    // Choose action from state using epsilon-greedy policy derived from Q
                    OfferingAction action =  selectEplisonGreedyOfferingAction (state);

                    Map<Long, Long> costFunction = computeOfferCostFunction(resourceType, availableQuantity, action.offerQuantity);
                    long cost = costFunction.get(action.offerQuantity);
                    long benefit = action.selectedRequest.utilityFunction.get(action.offerQuantity);
                    if (cost < benefit) {
                        createOffer(action.selectedRequest.id, myAgent.getAID(), action.selectedRequest.sender, resourceType, action.offerQuantity, costFunction, availableResources.get(resourceType));
                        availableQuantity = availableQuantity - action.offerQuantity;
                    }
                    requests.remove( action.selectedRequest);
                }
            }
        }
    }


    OfferingState generateOfferingState (ResourceType resourceType, ArrayList<Request> requests, long availableQuantity) {

        Map<AID, Map<Long, Long>> agentNetUtils = null;

        for (Request request : requests) {
            Map<Long, Long> netUtils = new LinkedHashMap<>();
            long cost;
            for (var utility : request.utilityFunction.entrySet()) {
                if( utility.getKey() <= availableQuantity) {
                    cost = utilityOfResources(resourceType, availableQuantity) - utilityOfResources( resourceType, availableQuantity - utility.getKey());
                    if (cost < utility.getValue()) {
                        netUtils.put( utility.getKey(), utility.getValue() - cost);
                    }
                }
            }
            agentNetUtils.put( request.sender, netUtils);
        }

        OfferingState offeringState = new OfferingState(resourceType, agentNetUtils, availableQuantity);
        return offeringState;
    }



    ConfirmingState generateConfirmingState (Request request, Set<Offer> offers, long currentConfirmedQuantity) {

        Map<AID, Map<Long, Long>> offerCosts = null;

        for (Offer offer : offers) {
            offerCosts.put( offer.sender, offer.costFunction);
        }

        ConfirmingState confirmingState = new ConfirmingState(request.resourceType, offerCosts, request.utilityFunction, currentConfirmedQuantity);
        return confirmingState;
    }


    OfferingAction selectEplisonGreedyOfferingAction (OfferingState currentState) {

        OfferingAction offeringAction = null;

        return offeringAction;
    }


    Set<ConfirmingAction> generatePossibleConfirmingActions (ConfirmingState currentState, Request request, Set<Offer> offers, long currentConfirmedQuantity) {

        Set<ConfirmingAction> actions = new HashSet<>();
        ConfirmingAction confirmingAction;
        ConfirmingStateAction confirmingStateAction;
        Iterator<Offer> iter = offers.iterator();
        Offer offer;
        for (int i = 0; i < offers.size(); i++) {
            offer = iter.next();
            for (long q = 1; q <= offer.quantity; q++) {
                if( q + currentConfirmedQuantity <= request.quantity) {
                    confirmingAction = new ConfirmingAction(offer.resourceType, offer, q);
                    actions.add( confirmingAction);
                    confirmingStateAction = new ConfirmingStateAction(currentState, confirmingAction);
                    if( confirmingQFunction.containsKey(confirmingStateAction) == false) {
                        confirmingQFunction.put( confirmingStateAction, request.utilityFunction.get(q + currentConfirmedQuantity) - offer.costFunction.get(q));
                    }
                }
            }
        }
        return actions;
    }


    ConfirmingAction selectEplisonGreedyConfirmingAction (ConfirmingState currentState, Request request, Set<Offer> offers, Set<ConfirmingAction> possibleActions) {

        ConfirmingAction selectedAction = null;
        ConfirmingStateAction confirmingStateAction;
        Random random = new Random();
        double r = random.nextDouble();
        Iterator<Offer> iter1 = offers.iterator();
        Iterator<ConfirmingAction> iter2 = possibleActions.iterator();
        if (r < epsilon) {
            //exploration: pick a random action from possible actions in this state
            Offer selectedOffer;
            long selectedQuantity;
            int index = random.nextInt(offers.size());
            for (int i = 0; i < index; i++) {
                iter1.next();
            }
            selectedOffer = iter1.next();
            selectedQuantity = random.nextLong(selectedOffer.quantity + 1);
            selectedAction = new ConfirmingAction( selectedOffer.resourceType, selectedOffer, selectedQuantity);
        } else {
            //exploitation: pick the best known action from possible actions in this state using Q table
            ConfirmingAction action = null;
            long Q;
            long highestQ = Long.MIN_VALUE;
            for (int i = 0; i < possibleActions.size(); i++) {
                action = iter2.next();
                confirmingStateAction = new ConfirmingStateAction(currentState, action);
                Q = confirmingQFunction.get(confirmingStateAction);
                if (Q > highestQ) {
                    highestQ = Q;
                    selectedAction = action;
                }
            }
        }
        return selectedAction;
    }


    Request selectBestRequest(ArrayList<Request> requests, long remainingQuantity) {

        // select the request with the highest efficiency
        // the request efficiency is defined as the ratio between its utility and requested quantity.

        Request selectedRequest = requests.get(0);
        double highestEfficiency = 0;
        long offerQuantity;

        for (Request request : requests) {
            if (remainingQuantity < request.quantity) {
                offerQuantity = remainingQuantity;
            } else {
                offerQuantity = request.quantity;
            }
            long util = request.utilityFunction.get(offerQuantity);
            double efficiency = util / offerQuantity;
            if (efficiency > highestEfficiency) {
                highestEfficiency = efficiency;
                selectedRequest = request;
            }
        }

        return selectedRequest;
    }


    private void createOffer(String reqId, AID offerer, AID requester, ResourceType resourceType, long offerQuantity, Map<Long, Long> costFunction, SortedSet<ResourceItem> availableItems) {

        Map<String, Long> offeredItems = new LinkedHashMap<>();

        for (long q=0; q<offerQuantity; q++) {
            ResourceItem item = availableItems.first();
            offeredItems.put(item.getId(), item.getExpiryTime());
            availableItems.remove( item);
            totalConsumedResource++;
//            logInf( this.getLocalName(), "offered resource item with id: " + item.getId() + " to " + requester.getLocalName());

        }

        String offerId = UUID.randomUUID().toString();
        Offer offer = new Offer(offerId, reqId, offerQuantity, resourceType, costFunction, offeredItems, offerer, requester);
        sentOffers.put( offerId, offer);

        sendOffer(reqId, offerId, requester, resourceType, offerQuantity, costFunction, offeredItems);
//        System.out.println( "createBid for resourceType: " + resourceType.name() + " with offerQuantity: " + offerQuantity);
    }


    private void sendOffer(String reqId, String offerId, AID requester, ResourceType resourceType, long offerQuantity, Map<Long, Long> costFunction, Map<String, Long> offeredItems) {

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

        Map<String, Long> offeredItems = new LinkedHashMap<>();
        Iterator<String> keysIterator2 = joOfferedItems.keySet().iterator();
        while (keysIterator2.hasNext()) {
            String key = keysIterator2.next();
            Long value = (Long) joOfferedItems.get(key);
            offeredItems.put( key, value);
        }

        Offer offer = new Offer(offerId, reqId, offerQuantity, resourceType, costFunction, offeredItems, msg.getSender(), myAgent.getAID());

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


    void deliberateOnConfirmingGreedy(Agent myAgent) {

//        if( myAgent.getLocalName().equals("Agent1")) {
//            System.out.print("");
//        }

        Map<Request, Map<Offer, Long>> selectedOffersForAllRequests = new LinkedHashMap<>();

        for (var request : sentRequests.entrySet()) {
            if ( receivedOffers.containsKey(request.getKey())) {
                Map<Offer, Long> confirmQuantities = processOffersGreedy( request.getValue());
                if (confirmQuantities.size() == 0) {
                    System.out.println("Error!!");
                }
                selectedOffersForAllRequests.put(request.getValue(), confirmQuantities);
            }
        }

        if (selectedOffersForAllRequests.size() > 0) {
//            if (thereIsBenefitToConfirmOffers( selectedOffersForAllRequests)) {
                Map<Request, Map<Offer, Map<String, Long>>> confirmedOfferedItemsForAllRequests = addResourceItemsInOffers(selectedOffersForAllRequests);
                createConfirmation( confirmedOfferedItemsForAllRequests);
//            } else {
//                createRejection( selectedOffersForAllRequests);
//            }
        }
    }


    void deliberateOnConfirmingRL(Agent myAgent) {

//        if( myAgent.getLocalName().equals("Agent1")) {
//            System.out.print("");
//        }

        Map<Request, Map<Offer, Long>> selectedOffersForAllRequests = new LinkedHashMap<>();

        for (var request : sentRequests.entrySet()) {
            if ( receivedOffers.containsKey(request.getKey())) {
                Map<Offer, Long> confirmQuantities = processOffersRL( request.getValue());
                if (confirmQuantities.size() == 0) {
                    System.out.println("Error!!");
                }
                selectedOffersForAllRequests.put(request.getValue(), confirmQuantities);
            }
        }

        if (selectedOffersForAllRequests.size() > 0) {
//            if (thereIsBenefitToConfirmOffers( selectedOffersForAllRequests)) {
            Map<Request, Map<Offer, Map<String, Long>>> confirmedOfferedItemsForAllRequests = addResourceItemsInOffers(selectedOffersForAllRequests);
            createConfirmation( confirmedOfferedItemsForAllRequests);
//            } else {
//                createRejection( selectedOffersForAllRequests);
//            }
        }
    }


    void deliberateOnConfirmingDP(Agent myAgent) {

//        if( myAgent.getLocalName().equals("Agent1")) {
//            System.out.print("");
//        }

        Map<Request, Map<Offer, Long>> selectedOffersForAllRequests = new LinkedHashMap<>();

        for (var request : sentRequests.entrySet()) {
            if ( receivedOffers.containsKey(request.getKey())) {
                Map<Offer, Long> confirmQuantities = processOffersDP( request.getValue());
                if (confirmQuantities.size() == 0) {
                    System.out.println("Error!!");
                }
                selectedOffersForAllRequests.put(request.getValue(), confirmQuantities);
            }
        }

        if (selectedOffersForAllRequests.size() > 0) {
//            if (thereIsBenefitToConfirmOffers( selectedOffersForAllRequests)) {
            Map<Request, Map<Offer, Map<String, Long>>> confirmedOfferedItemsForAllRequests = addResourceItemsInOffers(selectedOffersForAllRequests);
            createConfirmation( confirmedOfferedItemsForAllRequests);
//            } else {
//                createRejection( selectedOffersForAllRequests);
//            }
        }
    }


    Map<Request, Map<Offer, Map<String, Long>>> addResourceItemsInOffers(Map<Request, Map<Offer, Long>> selectedOffersForAllRequests) {

        Map<Request, Map<Offer, Map<String, Long>>> confirmedOfferedItemsForAllRequests = new LinkedHashMap<>();
        Map<Offer, Map<String, Long>> confirmedOfferedItems;
        Map<String, Long> confirmedItems;
        for (var selectedOffersForReq : selectedOffersForAllRequests.entrySet()) {
            confirmedOfferedItems = new LinkedHashMap<>();
            for (var offerQuantity : selectedOffersForReq.getValue().entrySet()) {
                confirmedItems = new LinkedHashMap<>();
                long q=1;
                for (var item : offerQuantity.getKey().offeredItems.entrySet()) {
                    if ( q <= offerQuantity.getValue()) {
                        availableResources.get(offerQuantity.getKey().resourceType).add(new ResourceItem(item.getKey(), offerQuantity.getKey().resourceType, item.getValue()));
                        confirmedItems.put(item.getKey(), item.getValue());
                        q++;
                        totalReceivedResources++;
//                        logInf( this.getLocalName(), "received resource item (in offer) with id: " + item.getKey());
                    } else {
                        break;
                    }
                }
                confirmedOfferedItems.put(offerQuantity.getKey(), confirmedItems);
            }
            confirmedOfferedItemsForAllRequests.put(selectedOffersForReq.getKey(), confirmedOfferedItems);
        }

        return confirmedOfferedItemsForAllRequests;

        // TODO: decrease the cost of transfer between sender and receiver from totalUtil
        // since we compute social welfare of all agents, we can decrease the cost of transfer locally
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


    public Map<Offer, Long> processOffersGreedy(Request request) {

        // the requester selects the combination of offers that maximizes the difference between the utility of request and the total cost of all selected offers.
        // it is allowed to take partial amounts of offered resources in multiple offers up to the requested amount.
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


    public Map<Offer, Long> processOffersRL(Request request) {

        // a reinforcement learning approach

        Set<Offer> offers = receivedOffers.get(request.id);

        Map<Offer, Long> confirmQuantities = new LinkedHashMap<>();
        for (Offer offer : offers) {
            confirmQuantities.put(offer, 0L);
        }

        long currentConfirmedQuantity = 0;

        while (currentConfirmedQuantity <= request.quantity && offers.size() > 0) {
            ConfirmingState state = generateConfirmingState (request, offers, currentConfirmedQuantity);

            Set<ConfirmingAction> possibleActions = generatePossibleConfirmingActions (state, request, offers, currentConfirmedQuantity);

            // Choose action from state using epsilon-greedy policy derived from Q
            ConfirmingAction action =  selectEplisonGreedyConfirmingAction (state, request, offers, possibleActions);

            confirmQuantities.put(action.selectedOffer, action.confirmQuantity);
            offers.remove( action.selectedOffer);
            currentConfirmedQuantity -= action.confirmQuantity;
        }

        return confirmQuantities;
    }


    public Map<Offer, Long> processOffersDP(Request request) {

        // a dynamic programming approach

        Set<Offer> offers = receivedOffers.get(request.id);

        Map<Offer, Long> confirmQuantities = new LinkedHashMap<>();
        for (Offer offer : offers) {
            confirmQuantities.put(offer, 0L);
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


    private void createConfirmation (Map<Request, Map<Offer, Map<String, Long>>> confirmedOfferedItemsForAllRequests) {

        for (var confirmedOfferedItemsForReq : confirmedOfferedItemsForAllRequests.entrySet()) {
            for (var confirmedOfferedItems : confirmedOfferedItemsForReq.getValue().entrySet()) {
                if (debugMode) {
                    if (confirmedOfferedItems.getValue().size() > 0) {
                        logInf(this.getLocalName(), "created confirmation with quantity " + confirmedOfferedItems.getValue().size() + " for offerId  " + confirmedOfferedItems.getKey().id + " for " + confirmedOfferedItems.getKey().resourceType.name() + " to " + confirmedOfferedItems.getKey().sender.getLocalName());
                    } else {
                        logInf(this.getLocalName(), "created rejection with quantity " + confirmedOfferedItems.getValue().size() + " for offerId  " + confirmedOfferedItems.getKey().id + " for " + confirmedOfferedItems.getKey().resourceType.name() + " to " + confirmedOfferedItems.getKey().sender.getLocalName());
                    }
                }
                sendConfirmation (confirmedOfferedItems.getKey().id, confirmedOfferedItems.getKey().sender, confirmedOfferedItems.getKey().resourceType, confirmedOfferedItems.getValue().size(), confirmedOfferedItems.getValue());
            }
        }
    }


    private void createRejection (Map<Request, Map<Offer, Long>> confirmQuantitiesForAllRequests) {

        for (var confirmQuantitiesForReq : confirmQuantitiesForAllRequests.entrySet()) {
            for (var offerQuantity : confirmQuantitiesForReq.getValue().entrySet()) {
                if( debugMode) {
                    logInf(this.getLocalName(), "created rejection with quantity 0 for offerId  " + offerQuantity.getKey().id + " for " + offerQuantity.getKey().resourceType.name() + " to " + offerQuantity.getKey().sender.getLocalName());
                }
                sendConfirmation (offerQuantity.getKey().id, offerQuantity.getKey().sender, offerQuantity.getKey().resourceType, 0, null);
            }
        }
    }


    void sendConfirmation (String offerId, AID offerer, ResourceType resourceType, long confirmQuantity, Map<String, Long> confirmedItems) {

        ACLMessage msg = new ACLMessage(ACLMessage.CONFIRM);

        msg.addReceiver (offerer);

        JSONObject jo = new JSONObject();
        jo.put("offerId", offerId);
        jo.put(Ontology.RESOURCE_TYPE, resourceType.name());
        jo.put(Ontology.RESOURCE_CONFIRM_QUANTITY, confirmQuantity);
        if (confirmedItems != null) {
            jo.put(Ontology.CONFIRMED_ITEMS, confirmedItems);
        }

        msg.setContent( jo.toJSONString());
        send(msg);
    }


    private void processConfirmation (Agent myAgent, ACLMessage confirmation) throws ParseException {

        String content = confirmation.getContent();
        Object obj = new JSONParser().parse(content);
        JSONObject jo = (JSONObject) obj;

        String offerId = (String) jo.get("offerId");
        String rt = (String) jo.get(Ontology.RESOURCE_TYPE);
        ResourceType resourceType = ResourceType.valueOf(rt);
        Long confirmQuantity = (Long) jo.get(Ontology.RESOURCE_CONFIRM_QUANTITY);
        JSONObject joConfirmedItems = (JSONObject) jo.get(Ontology.CONFIRMED_ITEMS);

        Map<String, Long> confirmedItems = new LinkedHashMap<>();
        if (joConfirmedItems != null) {
            Iterator<String> keysIterator2 = joConfirmedItems.keySet().iterator();
            while (keysIterator2.hasNext()) {
                String key = keysIterator2.next();
                Long value = (Long) joConfirmedItems.get(key);
                confirmedItems.put(key, value);
            }
        }

        if( confirmQuantity != confirmedItems.size()) {
            logErr (myAgent.getLocalName(), "confirmQuantity != confirmedItems.size()");
        }

        if (debugMode) {
            if( confirmQuantity > 0) {
                logInf(myAgent.getLocalName(), "received confirmation with quantity " + confirmQuantity + " for offerId " + offerId + " for " + resourceType.name() + " from " + confirmation.getSender().getLocalName());
            } else {
                logInf(myAgent.getLocalName(), "received rejection with quantity " + confirmQuantity + " for offerId " + offerId + " for " + resourceType.name() + " from " + confirmation.getSender().getLocalName());
            }
        }

        Offer sentOffer = sentOffers.get(offerId);

        if( sentOffer == null) {
            logErr(this.getLocalName(), "sentOffer is null !!!");
            logErr(this.getLocalName(), "sentOffers size: " + sentOffers.size());
        }

        restoreOfferedResources( sentOffer, confirmedItems);
    }


    private void restoreOfferedResources(Offer sentOffer, Map<String, Long> confirmedItems) {

        int confirmQuantity;
        if (confirmedItems == null) {
            confirmQuantity = 0;
        } else {
            confirmQuantity = confirmedItems.size();
        }

        if (confirmQuantity < sentOffer.quantity) {
            logInf( this.getLocalName(), "restoreOfferedResources for offerId " + sentOffer.id + " offerQuan " + sentOffer.quantity + " confirmQuan " + confirmQuantity);
            for (var offeredItem : sentOffer.offeredItems.entrySet()) {
                if (confirmQuantity == 0 || confirmedItems.containsKey(offeredItem.getKey()) == false) {
                    availableResources.get(sentOffer.resourceType).add( new ResourceItem(offeredItem.getKey(), sentOffer.resourceType, offeredItem.getValue()));
                    totalConsumedResource --;
//                    logInf( this.getLocalName(), "(restoreOfferedResources) restored resource item with id: " + offeredItem.getKey());
                }
            }
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
                if (item.getExpiryTime() > 1) {
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
            if (item.getExpiryTime() > 1) {
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
        AID aid = new AID(agentType + "0", AID.ISLOCALNAME);
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
        AID aid = new AID(agentType + "0", AID.ISLOCALNAME);
        msg.addReceiver(aid);

        JSONObject joNewResources = new JSONObject();

        for (var newResource : newResources.entrySet()) {
            JSONObject joItems = new JSONObject();
            for( ResourceItem item : newResource.getValue()) {
                joItems.put( item.getId(), item.getExpiryTime());
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
        AID aid = new AID(agentType + "0", AID.ISLOCALNAME);
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


    protected void logInf (String agentId, String msg) {

        if (debugMode) {
            try {
                PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(logFileName, true)));
                out.println(System.currentTimeMillis() + " " + agentId + " " + msg);
                out.println();
                out.close();
            } catch (IOException e) {
                System.err.println("Error writing file..." + e.getMessage());
            }
        }
    }


    protected void logErr (String agentId, String msg) {

        System.out.println("Error!! " + agentId + " " + msg);

        try {
            PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(logFileName, true)));
            out.println("Error!! " + agentId + " " + msg);
            out.println();
            out.close();
        } catch (IOException e) {
            System.err.println("Error writing file..." + e.getMessage());
        }
    }


    protected void logAgentInf (String agentId, String msg) {

//        if (debugMode) {
//            try {
//                PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(agentLogFileName, true)));
//                out.println(System.currentTimeMillis() + " " + agentId + " " + msg);
//                out.println();
//                out.close();
//            } catch (IOException e) {
//                System.err.println("Error writing file..." + e.getMessage());
//            }
//        }
    }

}
