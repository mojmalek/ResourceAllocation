package agents;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.core.behaviours.WakerBehaviour;
import jade.lang.acl.ACLMessage;
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


public class TimedSocialAdaptiveAgent extends Agent {

    TimedSimulationEngine simulationEngine;
    private boolean debugMode = true;
    private boolean cascading = true;
    private String logFileName, agentLogFileName;
    private String agentType;

    private Integer[] neighbors;

    private SortedSet<Task> toDoTasks = new TreeSet<>(new Task.taskComparator());
    private SortedSet<Task> blockedTasks = new TreeSet<>(new Task.taskComparator());
    private SortedSet<Task> doneTasks = new TreeSet<>(new Task.taskComparator());
    private long totalUtil;
    private long endTime;
    private long currentTime;
    private int numberOfAgents;

    private Map<ResourceType, SortedSet<ResourceItem>> availableResources = new LinkedHashMap<>();
    private Map<ResourceType, SortedSet<ResourceItem>> expiredResources = new LinkedHashMap<>();

    private int totalReceivedResources;
    private int totalConsumedResources;
    private int totalExpiredResources;

    // reqId
    public Map<String, Request> sentRequests = new LinkedHashMap<>();

    public Map<ResourceType, ArrayList<Request>> receivedRequests = new LinkedHashMap<>();
    // offerId
    public Map<String, Offer> sentOffers = new LinkedHashMap<>();
    // reqId
    public Map<String, Set<Offer>> receivedOffers = new LinkedHashMap<>();

    private long requestLifetime = 500;
    private long minTimeToCascadeRequest = 200;
    private long minTimeToOffer = 200;
    private long requestTimeoutReduction = 30;
    private long offerTimeoutExtension = 200;
    private long waitUntilCascadeOffer = 100;
    private long waitUntilConfirmOffer = 50;

    private int errorCount;


    @Override
    protected void setup() {

        Object[] args = getArguments();
        if (args != null && args.length > 0) {
            numberOfAgents = (int) args[0];
            int myId = (int) args[1];
            endTime = (long) args[2];
            neighbors = (Integer[]) args[3];
            logFileName = (String) args[4];
            simulationEngine = (TimedSimulationEngine) args[5];
            cascading = (boolean) args[6];
            agentType = (String) args[7];
        }

        for (ResourceType resourceType : ResourceType.getValues()) {
            availableResources.put( resourceType, new TreeSet<>(new ResourceItem.resourceItemComparator()));
            expiredResources.put( resourceType, new TreeSet<>(new ResourceItem.resourceItemComparator()));
        }

        agentLogFileName = "logs/" + this.getLocalName() + "-" + new Date() + ".txt";

        addBehaviour (new WakerBehaviour(this, new Date(endTime + 1000)) {
            protected void onWake() {
                int totalAvailable = 0;
                for (var resource : availableResources.entrySet()) {
                    totalAvailable += resource.getValue().size();
                }
//                System.out.println (myAgent.getLocalName() + " totalReceivedResources " + totalReceivedResources + " totalConsumedResource " + totalConsumedResource + " totalExpiredResource " + totalExpiredResource + " totalAvailable " + totalAvailable);
                if (totalReceivedResources - totalConsumedResources - totalExpiredResources != totalAvailable ) {
                    int difference = totalReceivedResources - totalConsumedResources - totalExpiredResources - totalAvailable;
                    System.out.println ("Error!! " + myAgent.getLocalName() + " has INCORRECT number of resources left. Diff: " + difference);
                }
            }
        });


        addBehaviour (new OneShotBehaviour() {
            @Override
            public void action() {
//                System.out.println("0");
                findTasks(myAgent);
                findResources(myAgent);
            }
        });


        addBehaviour (new TickerBehaviour(this, 1000) {
            protected void onTick() {
                currentTime = System.currentTimeMillis();
                if (currentTime <= endTime - 2000) {
//                    System.out.println(getTickCount());
                    findTasks(myAgent);
                    findResources(myAgent);
                }
            }
        });


        addBehaviour (new TickerBehaviour(this, 1) {
            protected void onTick() {
                currentTime = System.currentTimeMillis();
                if (currentTime <= endTime) {
                    negotiate(myAgent);
                    expireResourceItems( myAgent);
                    expireTasks( myAgent);
                    performTasks(myAgent);
                }
            }
        });


        addBehaviour(new CyclicBehaviour() {
            @Override
            public void action() {
                ACLMessage msg=receive();
                if (msg != null) {
                    int performative = msg.getPerformative();
                    switch (performative) {
                        case ACLMessage.REQUEST:
                            try {
                                storeRequest(myAgent, msg);
                            } catch (ParseException e) {
                                e.printStackTrace();
                            }
                            break;
                        case ACLMessage.PROPOSE:
                            try {
                                storeOffer(myAgent, msg);
                            } catch (ParseException e) {
                                e.printStackTrace();
                            }
                            break;
                        case ACLMessage.CONFIRM:
                            try {
                                processConfirmation(myAgent, msg);
                            } catch (ParseException e) {
                                e.printStackTrace();
                            }
                            break;
                        case ACLMessage.INFORM:
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

        SortedSet<Task> newTasks = simulationEngine.findTasks( myAgent);
        if (newTasks.size() > 0) {
            toDoTasks.addAll(newTasks);
            sendNewTasksToMasterAgent (newTasks, myAgent);
        }
    }


    private void findResources(Agent myAgent) {

        Map<ResourceType, SortedSet<ResourceItem>> newResources = simulationEngine.findResources( myAgent);

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
        SortedSet<ResourceItem> expiredItemsNow = new TreeSet<>(new ResourceItem.resourceItemComparator());
        for (var resource : availableResources.entrySet()) {
            expiredItemsNow.clear();
            availableItems = availableResources.get( resource.getKey());
            expiredItems = expiredResources.get( resource.getKey());
            for (ResourceItem item : availableItems) {
                currentTime = System.currentTimeMillis();
                if (currentTime > item.getExpiryTime()) {
                    expiredItemsNow.add( item);
                    expiredItems.add( item);
//                    logInf(myAgent.getLocalName(), "expired resource item with id: " + item.getId());
                }
            }
            int initialSize = availableItems.size();
            availableItems.removeAll( expiredItemsNow);
            totalExpiredResources += expiredItemsNow.size();
            if ( initialSize - expiredItemsNow.size() != availableItems.size()) {
                logErr( myAgent.getLocalName(), "initialSize - expiredItemsNow.size() != availableItems.size()");
            }
        }
    }


    void expireTasks(Agent myAgent) {

        SortedSet<Task> lateTasks = new TreeSet<>(new Task.taskComparator());
        int count = 0;
        for (Task task : toDoTasks) {
            currentTime = System.currentTimeMillis();
            if (currentTime > task.deadline) {
                lateTasks.add( task);
                count += 1;
            }
        }

        if (lateTasks.size() != count) {
            System.out.println("Error!!");
        }
        int initialSize = toDoTasks.size();
        toDoTasks.removeAll( lateTasks);
        if ( initialSize - count != toDoTasks.size()) {
            System.out.println("Error!!");
        }
    }


    private void negotiate (Agent myAgent) {

        expireRequests();
        deliberateOnRequesting (myAgent);
        if(cascading) {
            deliberateOnCascadingRequest(myAgent);
        }
        expireOffers();
        deliberateOnOffering( myAgent);
        if(cascading) {
            deliberateOnCascadingOffers( myAgent);
        }
        deliberateOnConfirming( myAgent);
    }


    void deliberateOnRequesting (Agent myAgent) {

//        if( myAgent.getLocalName().equals("4Agent1")) {
//            System.out.print("");
//        }

        blockedTasks.clear();
        Map<ResourceType, SortedSet<ResourceItem>> remainingResources = deepCopyResourcesMap( availableResources);

        for (Task task : toDoTasks) {
            currentTime = System.currentTimeMillis();
            if (currentTime < task.deadline - 550) {
                if (hasEnoughResources(task, remainingResources)) {
                    remainingResources = evaluateTask(task, remainingResources);
                } else {
                    blockedTasks.add((task));
                }
            }
        }

        if (blockedTasks.size() > 0) {
            createRequests( blockedTasks, remainingResources, myAgent);
        }
    }


    void expireRequests() {

        ArrayList<Request> expiredReceivedRequests = new ArrayList<>();
        for (var requestsForType : receivedRequests.entrySet()) {
            ArrayList<Request> requests = requestsForType.getValue();
            expiredReceivedRequests.clear();
            for (Request request : requests) {
                currentTime = System.currentTimeMillis();
                if (currentTime > request.timeout + 500) {
                    expiredReceivedRequests.add( request);
                }
            }
            requests.removeAll(expiredReceivedRequests);
        }

        Set<Request> expiredSentRequests = new HashSet<>();
        for( Request sentRequest : sentRequests.values()) {
            currentTime = System.currentTimeMillis();
            if (sentRequest.cascaded == false && currentTime > sentRequest.timeout + 500) {
//                if( sentRequest.cascaded == true) {
//                    restoreReservedItems( sentRequest);
//                }
                expiredSentRequests.add( sentRequest);
            }
        }
        for (Request expiredRequest : expiredSentRequests) {
            sentRequests.remove( expiredRequest.id);
            if (expiredRequest.cascaded == true) {
                logInf(this.getLocalName(), "request expired with id " + expiredRequest.id + " originId " + expiredRequest.originalId + " timeSent " + expiredRequest.timeSent + " originalSender " + expiredRequest.originalSender.getLocalName());
            } else {
                logInf(this.getLocalName(), "request expired with id " + expiredRequest.id + " timeSent " + expiredRequest.timeSent);
            }
            receivedOffers.remove(expiredRequest.id);
        }
    }


    void expireOffers() {

        // No offer should be expired! It should be confirmed or rejected.

        Set<Offer> expiredSentOffers = new HashSet<>();
        for( Offer sentOffer : sentOffers.values()) {
            currentTime = System.currentTimeMillis();
            if (currentTime > sentOffer.timeout + 1000) {
                expiredSentOffers.add( sentOffer);
            }
        }
//        Request cascadedRequest;
        for (Offer expiredOffer : expiredSentOffers) {
            sentOffers.remove( expiredOffer.id);
            logErr( this.getLocalName(), "offer expired with id " + expiredOffer.id);
//            cascadedRequest = null;
//            for (Request sentRequest : sentRequests.values()) {
//                if (sentRequest.previousId == expiredOffer.reqId) {
//                    cascadedRequest = sentRequest;
//                }
//            }
            restoreOfferedResources( expiredOffer, null);
//            if (cascadedRequest != null) {
//                sentRequests.remove(cascadedRequest.id);
//                logInf( this.getLocalName(), "cascadedRequest with id " + cascadedRequest.id + " is removed because expiredOffer with id " + expiredOffer.id + " is removed");
//                receivedOffers.remove(cascadedRequest.id);
//            }
        }
    }


    private void performTasks(Agent myAgent) {

//        if( myAgent.getLocalName().equals("4Agent1")) {
//            System.out.print("");
//        }

        boolean performed = false;
        int count = 0;
        SortedSet<Task> doneTasksNow = new TreeSet<>(new Task.taskComparator());
        // Greedy algorithm: tasks are sorted by utility in toDoTasks
        for (Task task : toDoTasks) {
            currentTime = System.currentTimeMillis();
            if (task.deadline - currentTime < 200) {
                if (currentTime <= task.deadline && hasEnoughResources(task, availableResources)) {
                    processTask(task);
                    doneTasksNow.add(task);
                    boolean check = doneTasks.add(task);
                    if (check == false) {
                        System.out.println("Error!!");
                    }
                    totalUtil = totalUtil + task.utility;
                    count += 1;
                    performed = true;
                }
            }
        }

        if (doneTasksNow.size() != count) {
            System.out.println("Error!!");
        }

        int initialSize = toDoTasks.size();

        toDoTasks.removeAll (doneTasks);

        if ( initialSize - count != toDoTasks.size()) {
             System.out.println("Error!!");
        }

        if (debugMode) {
//            System.out.println(myAgent.getLocalName() + " has performed " + doneTasks.size() + " tasks and gained total utility of " + totalUtil);
        }

        if (performed == true) {
            sendTotalUtilToMasterAgent(totalUtil, myAgent);
        }
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

        for (var requiredResource : task.requiredResources.entrySet()) {
            SortedSet<ResourceItem> resourceItems = availableResources.get(requiredResource.getKey());
            for (int i = 0; i < requiredResource.getValue(); i++) {
                ResourceItem item = resourceItems.first();
                resourceItems.remove(item);
                totalConsumedResources++;
//                logInf( this.getLocalName(), "consumed resource item with id: " + item.getId());
            }
        }
    }


    private void createRequests (SortedSet<Task> blockedTasks, Map<ResourceType, SortedSet<ResourceItem>> remainingResources, Agent myAgent) {

        // creates a request based on the missing quantity for each resource type
        Map<ResourceType, Long> totalRequiredResources = new LinkedHashMap<>();
        currentTime = System.currentTimeMillis();
        long requestTimeout = currentTime + requestLifetime;

        for (Task task : blockedTasks) {
            for (var entry : task.requiredResources.entrySet()) {
                totalRequiredResources.put(entry.getKey(),  totalRequiredResources.getOrDefault(entry.getKey(), 0L) + entry.getValue());
            }
            //TODO: minimum or maximum task deadline
//            if( task.deadline < requestTimeout) {
//                requestTimeout = task.deadline;
//            }
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

            if (missingQuantity > 0 && !hasSentRequest(resourceTypeQuantity.getKey())) {
                Map<Long, Long> utilityFunction = computeRequestUtilityFunction(blockedTasks, resourceTypeQuantity.getKey(), remainingResources, missingQuantity);
                Set<Integer> allReceivers = new HashSet<>();
                Set<AID> receiverIds = new HashSet<>();
                for (int i = 0; i < neighbors.length; i++) {
                    if (neighbors[i] != null) {
                        allReceivers.add(i+1);
                        AID aid = new AID(agentType + (i+1), AID.ISLOCALNAME);
                        receiverIds.add(aid);
                    }
                }
                String reqId = UUID.randomUUID().toString();
//                currentTime = System.currentTimeMillis();
                if( debugMode) {
                    logInf(this.getLocalName(), "created request with id " + reqId + " with quantity: " + missingQuantity + " for " + resourceTypeQuantity.getKey().name() + " to " + getReceiverNames(receiverIds));
                    logAgentInf(this.getLocalName(), "created request U: " + utilityFunction.toString());
                }
                sendRequest( reqId, null, resourceTypeQuantity.getKey(), missingQuantity, utilityFunction, allReceivers, receiverIds, currentTime, requestTimeout, null, null);
                sentRequests.put( reqId, new Request(reqId, null, null, false, missingQuantity, resourceTypeQuantity.getKey(), utilityFunction, null, null, allReceivers, null, currentTime, requestTimeout, null));
            }
        }
    }


    boolean hasSentRequest (ResourceType resourceType) {
        boolean hasSent = false;
        for( Request sentRequest : sentRequests.values()) {
            currentTime = System.currentTimeMillis();
            if (sentRequest.resourceType.equals(resourceType) && sentRequest.cascaded == false && currentTime < sentRequest.timeout) {
//            if (sentRequest.resourceType.equals(resourceType) && sentRequest.cascaded == false) {
                if(currentTime > sentRequest.timeout) {
                    System.out.println("Error!! currentTime > sentRequest.timeout");
                }
                hasSent = true;
                break;
            }
        }
        return hasSent;
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

//        String requesterName = requester.getLocalName();
//        int requesterId = Integer.valueOf(requesterName.replace(agentType, ""));
//        int distance = neighbors[requesterId-1];

        long cost;
        long expectedCost = 0;
        Map<Long, Long> offerCostFunction = new LinkedHashMap<>();
        for (long q=1; q<=offerQuantity; q++) {
            cost = utilityOfResources( resourceType, availableQuantity) - utilityOfResources( resourceType, availableQuantity - q);
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


    private void sendRequest (String reqId, String originalId, ResourceType resourceType, long missingQuantity, Map<Long, Long> utilityFunction, Set<Integer> allReceivers, Set<AID> receiverIds, long timeSent, long timeout, Long originalTimeout, AID originalSender) {

        ACLMessage msg = new ACLMessage(ACLMessage.REQUEST);

        for( AID aid : receiverIds) {
            msg.addReceiver(aid);
        }

        JSONObject jo = new JSONObject();
        jo.put("reqId", reqId);
        if (originalId != null) {
            jo.put("originalId", originalId);
        }
        jo.put(Ontology.RESOURCE_REQUESTED_QUANTITY, missingQuantity);
        jo.put(Ontology.RESOURCE_TYPE, resourceType.name());
        jo.put(Ontology.REQUEST_UTILITY_FUNCTION, utilityFunction);
        if (originalSender != null) {
            jo.put(Ontology.ORIGINAL_SENDER, originalSender.getLocalName());
        }
        jo.put(Ontology.ALL_RECEIVERS, allReceivers);
        jo.put(Ontology.REQUEST_TIME_SENT, timeSent);
        jo.put(Ontology.REQUEST_TIMEOUT, timeout);
        if (originalTimeout != null) {
            jo.put(Ontology.ORIGINAL_REQUEST_TIMEOUT, originalTimeout);
        }

        msg.setContent( jo.toJSONString());
//      msg.setReplyByDate();
        send(msg);
    }


    Set<String> getReceiverNames (Set<AID> receiverIds) {
        Set<String> names = new HashSet<>();
        for(AID aid : receiverIds) {
            names.add( aid.getLocalName());
        }
        return names;
    }


    private void storeRequest (Agent myAgent, ACLMessage msg) throws ParseException {

        String content = msg.getContent();

        Object obj = new JSONParser().parse(content);
        JSONObject jo = (JSONObject) obj;

        String reqId = (String) jo.get("reqId");
        String originalId = (jo.get("originalId") == null) ? reqId : (String) jo.get("originalId");
        boolean cascaded = (reqId == originalId) ? false : true;
        Long requestedQuantity = (Long) jo.get(Ontology.RESOURCE_REQUESTED_QUANTITY);
        String rt = (String) jo.get(Ontology.RESOURCE_TYPE);
        ResourceType resourceType = ResourceType.valueOf(rt);
        AID originalSender = (jo.get(Ontology.ORIGINAL_SENDER) == null) ? msg.getSender() : new AID ((String) jo.get(Ontology.ORIGINAL_SENDER), AID.ISLOCALNAME);

        JSONArray joReceivers = (JSONArray) jo.get(Ontology.ALL_RECEIVERS);
        Set<Integer> allReceivers = new HashSet<>();
        for (int i=0; i<joReceivers.size(); i++) {
            Long value = (Long) joReceivers.get(i);
            allReceivers.add(Integer.valueOf(value.intValue()));
        }

        JSONObject joUtilityFunction = (JSONObject) jo.get(Ontology.REQUEST_UTILITY_FUNCTION);

        if( debugMode) {
//            logInf(myAgent.getLocalName(), "received request with id " + reqId + " originalId " + originalId + " with quantity " + requestedQuantity + " for " + resourceType.name() + " from " + msg.getSender().getLocalName());
        }

        Map<Long, Long> utilityFunction = new LinkedHashMap<>();
        Iterator<String> keysIterator = joUtilityFunction.keySet().iterator();
        while (keysIterator.hasNext()) {
            String key = keysIterator.next();
            Long value = (Long) joUtilityFunction.get(key);
            utilityFunction.put( Long.valueOf(key), value);
        }

        long timeSent = (long) jo.get(Ontology.REQUEST_TIME_SENT);
        long timeout = (long) jo.get(Ontology.REQUEST_TIMEOUT);
        Long originReqTimeout = (jo.get(Ontology.ORIGINAL_REQUEST_TIMEOUT) == null) ? timeout : (long) jo.get(Ontology.ORIGINAL_REQUEST_TIMEOUT);

        Request request = new Request(reqId, null, originalId, cascaded, requestedQuantity.intValue(), resourceType, utilityFunction, msg.getSender(), originalSender, allReceivers, null, timeSent, timeout, originReqTimeout);

        if ( receivedRequests.containsKey(resourceType) == false) {
            receivedRequests.put(resourceType, new ArrayList<>());
        }

        for (Request req : receivedRequests.get(resourceType)) {
            if (req.id.equals(reqId) && req.sender.equals(msg.getSender())) {
                //This is okay. A request with an original id may be cascaded through different paths and received from the same final sender.
//                errorCount++;
//                System.out.println(this.getLocalName() + " storeRequest-receivedRequests errorCount: " + errorCount);
            }
        }

        if (sentRequests.keySet().contains(reqId) == true && sentRequests.get(reqId).cascaded == false) {
                errorCount++;
                System.out.println(this.getLocalName() + " storeRequest-sentRequests errorCount: " + errorCount);
        }

//        if (sentRequests.keySet().contains(reqId) == false) {
            receivedRequests.get(resourceType).add(request);
//        }
    }


    private void deliberateOnCascadingRequest(Agent myAgent) {

//        if( myAgent.getLocalName().equals("4Agent1")) {
//            System.out.print("");
//        }

        long offerQuantity;
        Set<AID> receiverIds;
        Request selectedRequest;
        ArrayList<Request> cascadedRequests = new ArrayList<>();
        for (var requestsForType : receivedRequests.entrySet()) {
            ArrayList<Request> requests = requestsForType.getValue();
            ArrayList<Request> copyOfRequests = new ArrayList<>(requests);
            cascadedRequests.clear();
            if (availableResources.get(requestsForType.getKey()) != null) {
                long availableQuantity = availableResources.get(requestsForType.getKey()).size();
                while (availableQuantity > 0 && copyOfRequests.size() > 0) {
                    // Greedy approach
                    selectedRequest = selectBestRequest( copyOfRequests, availableQuantity);
                    receiverIds = findNeighborsToCascadeRequest( selectedRequest);
                    if(thereIsTimeToCascadeRequest(selectedRequest.timeout) && receiverIds.size() > 0) {
                        if (availableQuantity < selectedRequest.quantity) {
                            offerQuantity = availableQuantity;
                            cascadeRequest(selectedRequest, offerQuantity, receiverIds);
                            cascadedRequests.add( selectedRequest);
                            availableQuantity = availableQuantity - offerQuantity;
                        } else {
                            offerQuantity = selectedRequest.quantity;
                            Map<Long, Long> costFunction = computeOfferCostFunction(selectedRequest.resourceType, availableQuantity, offerQuantity, selectedRequest.sender);
                            long cost = costFunction.get(offerQuantity);
                            long benefit = selectedRequest.utilityFunction.get(offerQuantity);
                            if (cost >= benefit) {
                                cascadeRequest(selectedRequest, 0, receiverIds);
                                cascadedRequests.add( selectedRequest);
                            }
                        }
                    }
                    copyOfRequests.remove(selectedRequest);
                }
                for (Request request : copyOfRequests) {
                    receiverIds = findNeighborsToCascadeRequest( request);
                    if(thereIsTimeToCascadeRequest(request.timeout) && receiverIds.size() > 0) {
                        cascadeRequest(request, 0, receiverIds);
                        cascadedRequests.add( request);
                    }
                }
            } else {
                for (Request request : copyOfRequests) {
                    receiverIds = findNeighborsToCascadeRequest( request);
                    if(thereIsTimeToCascadeRequest(request.timeout) && receiverIds.size() > 0) {
                        cascadeRequest(request, 0, receiverIds);
                        cascadedRequests.add( request);
                    }
                }
            }
            requests.removeAll( cascadedRequests);
        }
    }


    boolean thereIsTimeToCascadeRequest (long timeout) {

        currentTime = System.currentTimeMillis();
        if(currentTime < timeout - minTimeToCascadeRequest) {
            return true;
        } else {
            return false;
        }
    }


    private void deliberateOnOffering(Agent myAgent) {

        // if agents operate and communicate asynchronously, then a request might be received at any time.
        // the offerer can wait for other requests before offering. (TODO: how long ?!)

        // if the rounds are synchronous, there can be more than one request, then we can consider two approaches:

        // Greedy approach:
        // sort requests based on their utilities, and while there are available resources,
        // create offer for request with the highest utility.

        // Optimal:
        // select the optimal combination of requests to maximize the utility.
        // max SUM xi Ui(j)
        // subject to:
        // xi in (0, 1)
        //  1 < j < qi
        // SUM xi <= 1

        long offerQuantity;
        for (var requestsForType : receivedRequests.entrySet()) {
            ArrayList<Request> requests = requestsForType.getValue();
            ArrayList<Request> copyOfRequests = new ArrayList<>(requests);
            if (availableResources.get(requestsForType.getKey()) != null) {
                long availableQuantity = availableResources.get(requestsForType.getKey()).size();
                while (availableQuantity > 0 && copyOfRequests.size() > 0) {
                    // Greedy approach
                    Request selectedRequest = selectBestRequest( copyOfRequests, availableQuantity);
                    currentTime = System.currentTimeMillis();
                    if (currentTime < selectedRequest.timeout - minTimeToOffer) {
                        if( availableQuantity < selectedRequest.quantity) {
                            offerQuantity = availableQuantity;
                        } else {
                            offerQuantity = selectedRequest.quantity;
                        }
                        Map<Long, Long> costFunction = computeOfferCostFunction(selectedRequest.resourceType, availableQuantity, offerQuantity, selectedRequest.sender);
                        long cost = costFunction.get(offerQuantity);
                        long benefit = selectedRequest.utilityFunction.get(offerQuantity);
//                        if( myAgent.getLocalName().equals("4Agent4")) {
//                            logInf( myAgent.getLocalName(), "Cost: " + cost + " Benefit: " + benefit);
//                        }
                        if (cost < benefit) {
                            createOffer(selectedRequest.id, selectedRequest.originalId, myAgent.getAID(), selectedRequest.sender, selectedRequest.resourceType, offerQuantity, costFunction, availableResources.get(selectedRequest.resourceType), selectedRequest.originalTimeout);
                            availableQuantity = availableQuantity - offerQuantity;
                            requests.remove( selectedRequest);
                        }
                    }
                    copyOfRequests.remove( selectedRequest);
                }
            }
        }
    }


    void cascadeRequest (Request request, long offerQuantity, Set<AID> receiverIds) {

        long missingQuantity = request.quantity - offerQuantity;
        Map<Long, Long> utilityFunction = new LinkedHashMap<>();
//        long currentUtil = 0;
//        if( offerQuantity > 0) {
//            currentUtil = request.utilityFunction.get(offerQuantity);
//        }
        for (long i=1; i<=missingQuantity; i++) {
//            utilityFunction.put(i, request.utilityFunction.get(offerQuantity+i) - currentUtil);
            utilityFunction.put(i, request.utilityFunction.get(offerQuantity+i));
        }

        SortedSet<ResourceItem> availableItems = availableResources.get(request.resourceType);
        Map<String, Long> reservedItems = new LinkedHashMap<>();
        for (long q=0; q<offerQuantity; q++) {
            ResourceItem item = availableItems.first();
            reservedItems.put(item.getId(), item.getExpiryTime());
            availableItems.remove( item);
            totalConsumedResources++;
//            logInf( this.getLocalName(), "reserved resource item with id: " + item.getId());
        }

        Set<Integer> allReceivers = new HashSet<>();
        allReceivers.addAll( request.allReceivers);
        for (AID aid : receiverIds) {
            int receiver = Integer.valueOf(aid.getLocalName().replace(agentType, ""));
            allReceivers.add( receiver);
        }

        String reqId = UUID.randomUUID().toString();
        if( debugMode) {
            logInf(this.getLocalName(), "cascaded request with id " + reqId + " preId " + request.id + " originId " + request.originalId + " quan " + missingQuantity + " for " + request.resourceType.name() + " to " + getReceiverNames(receiverIds));
            logAgentInf(this.getLocalName(), "cascaded request U: " + utilityFunction.toString());
        }

        long newTimeout = request.timeout - requestTimeoutReduction;
        //TODO: make sure new timeout is valid

        sendRequest(reqId, request.originalId, request.resourceType, missingQuantity, utilityFunction, allReceivers, receiverIds, currentTime, newTimeout, request.originalTimeout, request.originalSender);
        sentRequests.put(reqId, new Request(reqId, request.id, request.originalId, true, missingQuantity, request.resourceType, request.utilityFunction, request.sender, request.originalSender, allReceivers, reservedItems, currentTime, newTimeout, request.originalTimeout));
    }


    Set<AID> findNeighborsToCascadeRequest( Request request) {

        Set<AID> receiverIds = new HashSet<>();
        for (int i = 0; i < neighbors.length; i++) {
            AID aid = new AID(agentType+(i+1), AID.ISLOCALNAME);
            if (neighbors[i] != null && !request.allReceivers.contains(i+1) && !request.sender.equals(aid)) {
                receiverIds.add(aid);
            }
        }

        return receiverIds;
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


    private void createOffer(String reqId, String originalReqId, AID offerer, AID requester, ResourceType resourceType, long offerQuantity, Map<Long, Long> costFunction, SortedSet<ResourceItem> availableItems, long originReqTimeout) {

        Map<String, Long> offeredItems = new LinkedHashMap<>();

        for (long q=0; q<offerQuantity; q++) {
            ResourceItem item = availableItems.first();
            offeredItems.put(item.getId(), item.getExpiryTime());
            availableItems.remove( item);
            totalConsumedResources++;
//            logInf( this.getLocalName(), "offered resource item with id: " + item.getId() + " to " + requester.getLocalName());
        }

        if (offeredItems.size() != (int) offerQuantity) {
            logErr (this.getLocalName(), "createOffer - offeredItems.size() != (int) offerQuantity");
        }

        long offerTimeout = originReqTimeout + offerTimeoutExtension;
        String offerId = UUID.randomUUID().toString();
        if( debugMode) {
            logInf(this.getLocalName(), "created offer with id " + offerId + " reqId " + reqId + " originReqId " + originalReqId + " for " + resourceType.name() + " quan " + offerQuantity + " to " + requester.getLocalName());
            logAgentInf(this.getLocalName(), "created offer C: " + costFunction.toString());
        }
        sendOffer(reqId, offerId, requester, resourceType, offerQuantity, costFunction, offeredItems, offerTimeout);
        sentOffers.put( offerId, new Offer(offerId, reqId, originalReqId, false, offerQuantity, resourceType, costFunction, offeredItems, offeredItems, offerer, requester, null, offerTimeout));
    }


    private void sendOffer(String reqId, String offerId, AID requester, ResourceType resourceType, long offerQuantity, Map<Long, Long> costFunction, Map<String, Long> offeredItems, long offerTimeout) {

        if (offeredItems.size() != (int) offerQuantity) {
            logErr (this.getLocalName(), "sendOffer - offeredItems.size() != (int) offerQuantity");
        }

        ACLMessage msg = new ACLMessage(ACLMessage.PROPOSE);

        msg.addReceiver( requester);

        JSONObject jo = new JSONObject();
        jo.put("reqId", reqId);
        jo.put("offerId", offerId);
        jo.put(Ontology.RESOURCE_OFFER_QUANTITY, offerQuantity);
        jo.put(Ontology.RESOURCE_TYPE, resourceType.name());
        jo.put(Ontology.OFFER_COST_FUNCTION, costFunction);
        jo.put(Ontology.OFFERED_ITEMS, offeredItems);
        jo.put(Ontology.OFFER_TIMEOUT, offerTimeout);

        msg.setContent( jo.toJSONString());

//      msg.setReplyByDate();

        send(msg);
    }


    private void storeOffer(Agent myAgent, ACLMessage msg) throws ParseException {

        // if agents operate and communicate asynchronously, then an offer might be received at any time.
        // the requester can wait for other offers before confirming. (TODO: how long ?!)

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

        currentTime = System.currentTimeMillis();
        if (sentRequests.keySet().contains(reqId) == true && currentTime < sentRequests.get(reqId).timeout) {
            if (debugMode) {
                logInf(myAgent.getLocalName(), "received offer with id " + offerId + " with quantity " + offerQuantity + " for reqId " + reqId + " for " + resourceType.name() + " from " + msg.getSender().getLocalName());
            }

            Map<Long, Long> costFunction = new LinkedHashMap<>();
            Iterator<String> keysIterator1 = joCostFunction.keySet().iterator();
            while (keysIterator1.hasNext()) {
                String key = keysIterator1.next();
                Long value = (Long) joCostFunction.get(key);
                costFunction.put(Long.valueOf(key), value);
            }

            Map<String, Long> offeredItems = new LinkedHashMap<>();
            Iterator<String> keysIterator2 = joOfferedItems.keySet().iterator();
            while (keysIterator2.hasNext()) {
                String key = keysIterator2.next();
                Long value = (Long) joOfferedItems.get(key);
                offeredItems.put(key, value);
            }

            if (joOfferedItems.size() != offerQuantity) {
                logErr (myAgent.getLocalName(), "storeOffer - joOfferedItems.size() != offerQuantity");
            }

            long offerTimeout = (long) jo.get(Ontology.OFFER_TIMEOUT);

            Offer offer = new Offer(offerId, reqId, null, null, offerQuantity, resourceType, costFunction, offeredItems, null, msg.getSender(), myAgent.getAID(), null, offerTimeout);

            Set<Offer> offers = receivedOffers.get(reqId);
            if (offers == null) {
                offers = new HashSet<>();
            }
            offers.add(offer);
            receivedOffers.put(reqId, offers);

        } else {
            if (debugMode) {
                logErr(myAgent.getLocalName(), "received late offer with id " + offerId + " with quantity " + offerQuantity + " for removed reqId " + reqId + " for " + resourceType.name() + " from " + msg.getSender().getLocalName());
            }
            sendConfirmation( offerId, msg.getSender(), resourceType, 0, null);
        }
    }


    void deliberateOnCascadingOffers(Agent myAgent) {

        for (var request : sentRequests.entrySet()) {
            if (request.getValue().cascaded == true && request.getValue().processed == false) {
                currentTime = System.currentTimeMillis();
                if (currentTime < request.getValue().timeout && request.getValue().timeout - currentTime < waitUntilCascadeOffer) {
                    cascadeOffers(request.getValue());
                }
            }
        }
    }


    private void cascadeOffers(Request cascadedRequest) {

        long availableQuantity;
        if( availableResources.get(cascadedRequest.resourceType) == null) {
            availableQuantity = 0;
        } else {
            availableQuantity = availableResources.get(cascadedRequest.resourceType).size();
        }
        long offerQuantity = cascadedRequest.reservedItems.size();
        Map<Long, Long> costFunction = computeOfferCostFunction(cascadedRequest.resourceType, availableQuantity + offerQuantity, offerQuantity, cascadedRequest.sender);
//        if( this.getLocalName().equals("4Agent3")) {
//            System.out.print("");
//        }
        Map<String, Long> offeredItems = new LinkedHashMap<>();
        for (var item : cascadedRequest.reservedItems.entrySet()) {
            offeredItems.put(item.getKey(), item.getValue());
        }

        long offerTimeout = cascadedRequest.originalTimeout + offerTimeoutExtension;
        Set<Offer> offers = null;
        Map<Offer, Long> offerQuantities = new LinkedHashMap<>();
        if (receivedOffers.containsKey(cascadedRequest.id)) {
            offers = receivedOffers.get(cascadedRequest.id);
//            String requesterName = cascadedRequest.sender.getLocalName();
//            int requesterId = Integer.valueOf(requesterName.replace(agentType, ""));
//            int distance = neighbors[requesterId-1];
            long minCost, cost;
            Offer lowCostOffer;
            for (Offer offer : offers) {
                currentTime = System.currentTimeMillis();
//                if(currentTime < offer.timeout) {
                    offerQuantities.put(offer, 0L);
//                    if (offer.timeout > offerTimeout) {
//                        offerTimeout = offer.timeout;
//                    }
//                }
            }
            for (long q=offerQuantity+1; q<=offerQuantity+cascadedRequest.quantity; q++) {
                minCost = Integer.MAX_VALUE;
                cost = 0;
                lowCostOffer = null;
                for (Offer offer : offers) {
                    currentTime = System.currentTimeMillis();
//                    if (currentTime < offer.timeout) {
                        if (hasExtraItem(offer, offerQuantities)) {
                            cost = totalCost(offer, offerQuantities);
                            if (cost < minCost) {
                                minCost = cost;
                                lowCostOffer = offer;
                            }
                        }
//                    }
                }
                if (lowCostOffer != null) {
//                    cost += distance * 1;
//                    if(q > 1) {
//                        costFunction.put(q, minCost + costFunction.get(q-1));
                    if (offerQuantity > 0) {
                        costFunction.put(q, minCost + costFunction.get(offerQuantity));
                    } else {
                        costFunction.put(q, minCost);
                    }
//                    } else {
//                        costFunction.put(q, minCost);
//                    }
                    offerQuantities.put(lowCostOffer, offerQuantities.get(lowCostOffer) + 1);
                } else {
                    break;
                }
            }
            for ( var offer : offerQuantities.entrySet()) {
                offerQuantity += offer.getValue();
            }

            SortedSet<ResourceItem> itemsInOffer;
            for (var offer : offerQuantities.entrySet()) {
                // create a sorted set of offered items
                itemsInOffer = new TreeSet<>(new ResourceItem.resourceItemComparator());
                for (var itemIdLifetime : offer.getKey().offeredItems.entrySet()) {
                    itemsInOffer.add(new ResourceItem(itemIdLifetime.getKey(), offer.getKey().resourceType, itemIdLifetime.getValue()));
                }
                Iterator<ResourceItem> itr = itemsInOffer.iterator();
                long q=1;
                while (q <= offer.getValue()) {
                    ResourceItem item = itr.next();
                    if (offeredItems.containsKey(item.getId())) {
                        logErr(this.getLocalName(), "duplicated resource item with id: " + item.getId());
                        logErr(this.getLocalName(), "offeredItems.containsKey(item.getId())");
                    }
                    offeredItems.put(item.getId(), item.getExpiryTime());
                    q++;
//                    logInf( this.getLocalName(), "cascaded offered resource item with id: " + item.getId() + " to " + cascadedRequest.sender.getLocalName());
                }
            }
        }

        if (offerQuantity > 0) {
//            long cost = costFunction.get(offerQuantity);
//            long benefit = cascadedRequest.utilityFunction.get(offerQuantity);
//            if (cost < benefit) {
                String offerId = UUID.randomUUID().toString();
                if(debugMode) {
                    if (offers != null) {
                        logInf(this.getLocalName(), "cascaded offer with id " + offerId + " with " + offers.size() + " includedOffers reqId " + cascadedRequest.id + " originReqId " + cascadedRequest.originalId + " " + cascadedRequest.resourceType.name() + " quan " + offerQuantity + " to " + cascadedRequest.sender.getLocalName());
                    } else {
                        logInf(this.getLocalName(), "cascaded offer with id " + offerId + " with 0 includedOffers reqId " + cascadedRequest.id + " originReqId " + cascadedRequest.originalId + " " + cascadedRequest.resourceType.name() + " quan " + offerQuantity + " to " + cascadedRequest.sender.getLocalName());
                    }
                    logAgentInf(this.getLocalName(), "cascaded offer C: " + costFunction.toString());
                }

                if (offeredItems.size() != (int) offerQuantity) {
                    logErr (this.getLocalName(), "cascadeOffers - offeredItems.size() != (int) offerQuantity");
                }

                sendOffer(cascadedRequest.previousId, offerId, cascadedRequest.sender, cascadedRequest.resourceType, offerQuantity, costFunction, offeredItems, offerTimeout);
                sentOffers.put(offerId, new Offer(offerId, cascadedRequest.previousId, cascadedRequest.originalId, true, offerQuantity, cascadedRequest.resourceType, costFunction, offeredItems, cascadedRequest.reservedItems, this.getAID(), cascadedRequest.sender, offers, offerTimeout));
//            } else {
//                restoreReservedItems( cascadedRequest);
//            }
            cascadedRequest.processed = true;
            receivedOffers.remove(cascadedRequest.id);
        }
    }


    void restoreReservedItems (Request cascadedRequest) {
        // create a sorted set of reserved items
        SortedSet<ResourceItem> reserevedItems = new TreeSet<>(new ResourceItem.resourceItemComparator());
        for (var offeredItem : cascadedRequest.reservedItems.entrySet()) {
            reserevedItems.add(new ResourceItem(offeredItem.getKey(), cascadedRequest.resourceType, offeredItem.getValue()));
//            logInf( this.getLocalName(), "(restoreReservedItems) restored resource item with id: " + offeredItem.getKey());
        }
        availableResources.get(cascadedRequest.resourceType).addAll(reserevedItems);
        totalConsumedResources -= reserevedItems.size();
    }


    void deliberateOnConfirming( Agent myAgent) {

//        if( myAgent.getLocalName().equals("4Agent1")) {
//            System.out.print("");
//        }

        Map<Request, Map<Offer, Long>> selectedOffersForAllRequests = new LinkedHashMap<>();

        for (var request : sentRequests.entrySet()) {
            currentTime = System.currentTimeMillis();
            if (currentTime < request.getValue().timeout && request.getValue().timeout - currentTime < waitUntilConfirmOffer) {
//            if (currentTime < request.getValue().timeout) {
                if (request.getValue().cascaded == false && receivedOffers.containsKey(request.getKey())) {
                    Map<Offer, Long> confirmQuantities = processOffers(request.getValue());
                    if (confirmQuantities.size() > 0) {
                        selectedOffersForAllRequests.put(request.getValue(), confirmQuantities);
                    }
                }
            }
        }

        if (selectedOffersForAllRequests.size() > 0) {
//            if (thereIsBenefitToConfirmOffers( selectedOffersForAllRequests)) {
                Map<Request, Map<Offer, Map<String, Long>>> confirmedOfferedItemsForAllRequests = addResourceItemsInOffers(selectedOffersForAllRequests);
                createConfirmation( confirmedOfferedItemsForAllRequests);
//            } else {
//                createRejection( selectedOffersForAllRequests);
//            }
            for (var selectedOffersForReq : selectedOffersForAllRequests.entrySet()) {
                sentRequests.remove(selectedOffersForReq.getKey().id);
                logInf( this.getLocalName(), "sentRequest with id " + selectedOffersForReq.getKey().id + " is confirmed and removed");
                receivedOffers.remove(selectedOffersForReq.getKey().id);
            }
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

        // find the max cost of offers per request
//        long maxCost = 0, cost;
//        for (var selectedOffersForReq : selectedOffersForAllRequests.entrySet()) {
//            cost = 0;
//            for (var offerQuantity : selectedOffersForReq.getValue().entrySet()) {
//                if (offerQuantity.getValue() > 0) {
//                    cost = cost + offerQuantity.getKey().costFunction.get(offerQuantity.getValue());
//                }
//            }
//            if (cost > maxCost) {
//                maxCost = cost;
//            }
//        }

        logInf(this.getLocalName(), "totalUtilityAfterConfirm: " + totalUtilityAfterConfirm + " totalUtilityBeforeConfirm: " + totalUtilityBeforeConfirm);
        logAgentInf(this.getLocalName(), "totalUtilityAfterConfirm: " + totalUtilityAfterConfirm + " totalUtilityBeforeConfirm: " + totalUtilityBeforeConfirm);

//        if (totalUtilityAfterConfirm - totalUtilityBeforeConfirm > maxCost) {
        if (totalUtilityAfterConfirm - totalUtilityBeforeConfirm > 0) {
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
            currentTime = System.currentTimeMillis();
//            if(currentTime < offer.timeout) {
                confirmQuantities.put(offer, 0L);
//            }
        }

        for (long q=1; q<=request.quantity; q++) {
            minCost = Integer.MAX_VALUE;
            lowCostOffer = null;
            for (Offer offer : offers) {
                currentTime = System.currentTimeMillis();
//                if(currentTime < offer.timeout) {
                    if (hasExtraItem(offer, confirmQuantities)) {
                        cost = totalCost(offer, confirmQuantities);
                        if (cost < minCost) {
                            minCost = cost;
                            lowCostOffer = offer;
                        }
                    }
//                }
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

//        if ( confirmQuantities.containsKey(offer)) {
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
                System.out.println( "Error!!");
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

//        if( myAgent.getLocalName().equals("4Agent4")) {
//            System.out.print("");
//        }
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

        Set<Offer> includedOffers = sentOffer.includedOffers;
        Request cascadedRequest = null;
        for (Request sentRequest : sentRequests.values()) {
            if (sentRequest.previousId == sentOffer.reqId) {
                cascadedRequest = sentRequest;
            }
        }

        if (includedOffers != null && cascadedRequest == null) {
            logErr(this.getLocalName(), "cascadedRequest is null for reqId " + sentOffer.reqId + " originReqId " + sentOffer.originalReqId + " offerId " + offerId);
        }

        if (includedOffers != null) {
            Map<Offer, Map<String, Long>> confirmedOfferedItems = new LinkedHashMap<>();
            for (Offer offer : includedOffers) {
                confirmedOfferedItems.put(offer, new LinkedHashMap<>());
            }
            for (var item : confirmedItems.entrySet()) {
                for (Offer offer : includedOffers) {
                    if (offer.offeredItems.containsKey( item.getKey())) {
                        confirmedOfferedItems.get(offer).put(item.getKey(), item.getValue());
                        break;
                    }
                }
            }

            cascadePartialConfirmations (confirmedOfferedItems);
        }

        restoreOfferedResources( sentOffer, confirmedItems);
        sentOffers.remove( offerId);
        if (cascadedRequest != null) {
            sentRequests.remove(cascadedRequest.id);
            logInf( this.getLocalName(), "cascadedRequest with id " + cascadedRequest.id + " originId " + cascadedRequest.originalId + " is confirmed and removed");
            receivedOffers.remove(cascadedRequest.id);
        }
    }


    private void cascadePartialConfirmations (Map<Offer, Map<String, Long>> confirmedOfferedItems) {

        // TODO: decrease the cost of transfer between sender and receiver from totalUtil when cascading confirmations

        for (var items : confirmedOfferedItems.entrySet()) {
            if( debugMode) {
                if (items.getValue().size() == 0) {
                    logInf(this.getLocalName(), "cascaded rejection with quantity " + items.getValue().size() + " for offerId  " + items.getKey().id + " for " + items.getKey().resourceType.name() + " to " + items.getKey().sender.getLocalName());
                } else {
                    logInf(this.getLocalName(), "cascaded confirmation with quantity " + items.getValue().size() + " for offerId  " + items.getKey().id + " for " + items.getKey().resourceType.name() + " to " + items.getKey().sender.getLocalName());
                }
            }
            sendConfirmation (items.getKey().id, items.getKey().sender, items.getKey().resourceType, items.getValue().size(), items.getValue());
        }
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
            for (var offeredItem : sentOffer.reservedItems.entrySet()) {
                if (confirmQuantity == 0 || confirmedItems.containsKey(offeredItem.getKey()) == false) {
                    availableResources.get(sentOffer.resourceType).add( new ResourceItem(offeredItem.getKey(), sentOffer.resourceType, offeredItem.getValue()));
                    totalConsumedResources--;
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
        Set<String> offerers = Set.of("8Agent5", "8Agent6", "8Agent7", "8Agent8");

        double exp = 0.0;
        double averageRequiredQuantity;
        double averageUtil;

//        if( offerers.contains(this.getLocalName()) && resourceType == ResourceType.A ) {
//            averageRequiredQuantity = 4.5 / 2 + 4.5 ;
//        } else {
            averageRequiredQuantity = 3;
//        }

//        if( offerers.contains(this.getLocalName())) {
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

    }


    void sendNewTasksToMasterAgent (SortedSet<Task> newTasks, Agent myAgent) {

        ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
        AID aid = new AID(agentType + "0", AID.ISLOCALNAME);
        msg.addReceiver(aid);

        JSONObject joNewTasks = new JSONObject();

        for (Task task : newTasks) {
            JSONObject joTask = new JSONObject();
            joTask.put("utility", task.utility);
            joTask.put("deadline", task.deadline);
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
