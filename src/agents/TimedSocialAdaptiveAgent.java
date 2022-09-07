package agents;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
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

    SimulationEngine simulationEngine = new SimulationEngine();
    private boolean debugMode = true;
    private String logFileName;

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
    private int totalConsumedResource;
    private int totalExpiredResource;

    // reqId
    public Map<String, Request> sentRequests = new LinkedHashMap<>();

    public Map<ResourceType, ArrayList<Request>> receivedRequests = new LinkedHashMap<>();
    // offerId
    public Map<String, Offer> sentOffers = new LinkedHashMap<>();
    // reqId
    public Map<String, Set<Offer>> receivedOffers = new LinkedHashMap<>();

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
        }


        addBehaviour (new WakerBehaviour(this, new Date(endTime + 500)) {
            protected void onWake() {
                int totalAvailable = 0;
                for (var resource : availableResources.entrySet()) {
                    totalAvailable += resource.getValue().size();
                }
                System.out.println (myAgent.getLocalName() + " totalReceivedResources " + totalReceivedResources + " totalConsumedResource " + totalConsumedResource + " totalExpiredResource " + totalExpiredResource + " totalAvailable " + totalAvailable);
                if (totalReceivedResources - totalConsumedResource - totalExpiredResource != totalAvailable ) {
                    System.out.println ("Error!! " + myAgent.getLocalName() + " has INCORRECT number of resources left.");
                }
            }
        } );


        addBehaviour (new TickerBehaviour(this, 1000) {
            protected void onTick() {
                currentTime = System.currentTimeMillis();
                if (currentTime <= endTime - 500) {
                    findTasks(myAgent);
                    findResources(myAgent);
                    deliberateOnRequesting (myAgent);
                }
            }
        });


        addBehaviour (new TickerBehaviour(this, 40) {
            protected void onTick() {
                currentTime = System.currentTimeMillis();
                if (currentTime < endTime) {
                    negotiate(myAgent);
                }
            }
        });


        addBehaviour (new TickerBehaviour(this, 400) {
            protected void onTick() {
                currentTime = System.currentTimeMillis();
                if (currentTime <= endTime) {
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
            if (availableResources.containsKey(newResource.getKey())) {
                SortedSet<ResourceItem> availableItems = availableResources.get( newResource.getKey());
                availableItems.addAll( newResource.getValue());
                totalReceivedResources += newResource.getValue().size();
                availableResources.put( newResource.getKey(), availableItems);
            } else {
                availableResources.put( newResource.getKey(), newResource.getValue());
            }
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
            if (expiredResources.containsKey( resource.getKey())) {
                expiredItems = expiredResources.get( resource.getKey());
            } else {
                expiredItems = new TreeSet<>(new ResourceItem.resourceItemComparator());
                expiredResources.put( resource.getKey(), expiredItems);
            }
            for (ResourceItem item : availableItems) {
                currentTime = System.currentTimeMillis();
                if (currentTime > item.getExpiryTime()) {
                    expiredItemsNow.add( item);
                    expiredItems.add( item);
                }
            }
            int initialSize = availableItems.size();
            availableItems.removeAll( expiredItemsNow);
            totalExpiredResource += expiredItemsNow.size();
            if ( initialSize - expiredItemsNow.size() != availableItems.size()) {
                System.out.println("Error!!");
            }
        }

        if (debugMode) {
//            for (var entry : expiredResources.entrySet()) {
//                System.out.println(myAgent.getLocalName() + " has " + entry.getValue().size() + " expired item of type: " + entry.getKey().name());
//            }
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
        deliberateOnCascadingRequest(myAgent);
        expireOffers();
        deliberateOnOffering( myAgent);
        deliberateOnCascadingOffers( myAgent);
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
            if (currentTime < task.deadline - 1000 &&  hasEnoughResources(task, remainingResources)) {
                remainingResources = evaluateTask( task, remainingResources);
            } else {
                blockedTasks.add((task));
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
            if (currentTime > sentRequest.timeout + 500) {
                if( sentRequest.cascaded == true) {
                    restoreReservedItems( sentRequest);
                }
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
            if (receivedOffers.containsKey(expiredRequest.id)) {
                receivedOffers.remove(expiredRequest.id);
            }
        }
    }


    void expireOffers() {

        Set<Offer> expiredSentOffers = new HashSet<>();
        for( Offer sentOffer : sentOffers.values()) {
            currentTime = System.currentTimeMillis();
            if (currentTime > sentOffer.timeout + 500) {
                expiredSentOffers.add( sentOffer);
            }
        }
        Request cascadedRequest;
        for (Offer expiredOffer : expiredSentOffers) {
            sentOffers.remove( expiredOffer.id);
            cascadedRequest = null;
            for (Request sentRequest : sentRequests.values()) {
                if (sentRequest.previousId == expiredOffer.reqId) {
                    cascadedRequest = sentRequest;
                }
            }
            restoreResources( expiredOffer, cascadedRequest, 0);
            logInf( this.getLocalName(), "restoreResources for expired offerId " + expiredOffer.id + " confirmQuantity 0");
            if (cascadedRequest != null) {
                sentRequests.remove(cascadedRequest.id);
                logInf( this.getLocalName(), "cascadedRequest with id " + cascadedRequest.id + " is removed because expiredOffer with id " + expiredOffer.id + " is removed");
                if (receivedOffers.containsKey(cascadedRequest.id)) {
                    receivedOffers.remove(cascadedRequest.id);
                }
            }
        }
    }


    private void performTasks(Agent myAgent) {

//        if( myAgent.getLocalName().equals("4Agent1")) {
//            System.out.print("");
//        }

        if (debugMode) {
//            System.out.println(myAgent.getLocalName() + " Number of To Do Tasks: " + toDoTasks.size());
//            System.out.println (myAgent.getLocalName() +  " is performing tasks.");
        }
        boolean performed = false;
        int count = 0;
        SortedSet<Task> doneTasksNow = new TreeSet<>(new Task.taskComparator());
        // Greedy algorithm: tasks are sorted by utility in toDoTasks
        for (Task task : toDoTasks) {
            currentTime = System.currentTimeMillis();
            if (task.deadline - currentTime < 3000) {
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

        for (var entry : task.requiredResources.entrySet()) {
            SortedSet<ResourceItem> resourceItems = availableResources.get(entry.getKey());
            for (int i = 0; i < entry.getValue(); i++) {
                ResourceItem item = resourceItems.first();
                resourceItems.remove(item);
                totalConsumedResource++;
            }
            availableResources.replace(entry.getKey(), resourceItems);
        }
    }


    private void createRequests (SortedSet<Task> blockedTasks, Map<ResourceType, SortedSet<ResourceItem>> remainingResources, Agent myAgent) {

        // creates a request based on the missing quantity for each resource type
        Map<ResourceType, Long> totalRequiredResources = new LinkedHashMap<>();
        long requestTimeout = Long.MAX_VALUE;

        for (Task task : blockedTasks) {
            for (var entry : task.requiredResources.entrySet()) {
                totalRequiredResources.put(entry.getKey(),  totalRequiredResources.getOrDefault(entry.getKey(), 0L) + entry.getValue());
            }
            //TODO: minimum or maximum task deadline
            if( task.deadline < requestTimeout) {
                requestTimeout = task.deadline;
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

            if (missingQuantity > 0 && !hasSentRequest(resourceTypeQuantity.getKey())) {
                Map<Long, Long> utilityFunction = computeRequestUtilityFunction(blockedTasks, resourceTypeQuantity.getKey(), remainingResources, missingQuantity);
                Set<Integer> allReceivers = new HashSet<>();
                Set<AID> receiverIds = new HashSet<>();
                for (int i = 0; i < neighbors.length; i++) {
                    if (neighbors[i] != null) {
                        allReceivers.add(i+1);
                        AID aid = new AID(numberOfAgents + "Agent" + (i+1), AID.ISLOCALNAME);
                        receiverIds.add(aid);
                    }
                }
                String reqId = UUID.randomUUID().toString();
                currentTime = System.currentTimeMillis();
                if( debugMode) {
                    logInf(this.getLocalName(), "created request with id " + reqId + " with quantity: " + missingQuantity + " for " + resourceTypeQuantity.getKey().name() + " to " + getReceiverNames(receiverIds));
                }
                sendRequest( reqId, null, resourceTypeQuantity.getKey(), missingQuantity, utilityFunction, allReceivers, receiverIds, currentTime, requestTimeout, null);
                sentRequests.put( reqId, new Request(reqId, null, null, false, missingQuantity, resourceTypeQuantity.getKey(), utilityFunction, null, null, allReceivers, null, currentTime, requestTimeout));
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
//                expectedCost = computeExpectedUtilityOfResources(resourceType, q, availableResources.get(resourceType));
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


    private void sendRequest (String reqId, String originalId, ResourceType resourceType, long missingQuantity, Map<Long, Long> utilityFunction, Set<Integer> allReceivers, Set<AID> receiverIds, long timeSent, long timeout, AID originalSender) {

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
        AID originalSender = (jo.get("originalSender") == null) ? msg.getSender() : new AID ((String) jo.get("originalSender"), AID.ISLOCALNAME);

        JSONArray joReceivers = (JSONArray) jo.get("allReceivers");
        Set<Integer> allReceivers = new HashSet<>();
        for (int i=0; i<joReceivers.size(); i++) {
            Long value = (Long) joReceivers.get(i);
            allReceivers.add(Integer.valueOf(value.intValue()));
        }

        JSONObject joUtilityFunction = (JSONObject) jo.get(Ontology.REQUEST_UTILITY_FUNCTION);

        if( debugMode) {
//            logInf(myAgent.getLocalName(), "received request with id " + reqId + " originalId " + originalId + " with quantity " + requestedQuantity + " for resource type " + resourceType.name() + " from " + msg.getSender().getLocalName());
        }

        Map<Long, Long> utilityFunction = new LinkedHashMap<>();
        Iterator<String> keysIterator = joUtilityFunction.keySet().iterator();
        while (keysIterator.hasNext()) {
            String key = keysIterator.next();
            Long value = (Long) joUtilityFunction.get(key);
            utilityFunction.put( Long.valueOf(key), value);
        }

        long timeSent = (long) jo.get("requestTimeSent");
        long timeout = (long) jo.get("requestTimeout");

        Request request = new Request(reqId, null, originalId, cascaded, requestedQuantity.intValue(), resourceType, utilityFunction, msg.getSender(), originalSender, allReceivers, null, timeSent, timeout);

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
                    currentTime = System.currentTimeMillis();
                    if(currentTime < selectedRequest.timeout - 500 && receiverIds.size() > 0) {
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
                    currentTime = System.currentTimeMillis();
                    if(currentTime < request.timeout && receiverIds.size() > 0) {
                        cascadeRequest(request, 0, receiverIds);
                        cascadedRequests.add( request);
                    }
                }
            } else {
                for (Request request : copyOfRequests) {
                    receiverIds = findNeighborsToCascadeRequest( request);
                    currentTime = System.currentTimeMillis();
                    if(currentTime < request.timeout && receiverIds.size() > 0) {
                        cascadeRequest(request, 0, receiverIds);
                        cascadedRequests.add( request);
                    }
                }
            }
            requests.removeAll( cascadedRequests);
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
                    if (currentTime < selectedRequest.timeout - 500) {
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
                            createOffer(selectedRequest.id, selectedRequest.originalId, myAgent.getAID(), selectedRequest.sender, selectedRequest.resourceType, offerQuantity, costFunction, availableResources.get(selectedRequest.resourceType), selectedRequest.timeout);
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
        long currentUtil = 0;
        if( offerQuantity > 0) {
            currentUtil = request.utilityFunction.get(offerQuantity);
        }
        for (long i=1; i<=missingQuantity; i++) {
            utilityFunction.put(i, request.utilityFunction.get(offerQuantity+i) - currentUtil);
        }

        SortedSet<ResourceItem> availableItems = availableResources.get(request.resourceType);
        Map<String, Long> reservedItems = new LinkedHashMap<>();
        for (long q=0; q<offerQuantity; q++) {
            ResourceItem item = availableItems.first();
            reservedItems.put(item.getId(), item.getExpiryTime());
            availableItems.remove( item);
        }

        Set<Integer> allReceivers = new HashSet<>();
        allReceivers.addAll( request.allReceivers);
        for (AID aid : receiverIds) {
            int receiver = Integer.valueOf(aid.getLocalName().replace(numberOfAgents+"Agent", ""));
            allReceivers.add( receiver);
        }

        String reqId = UUID.randomUUID().toString();
//        String originalId = request.originalId != null ? request.originalId : request.id;
//        AID originalSender = request.originalSender != null ? request.originalSender : request.sender;
        if( debugMode) {
            logInf(this.getLocalName(), "cascaded request with id " + reqId + " previousId " + request.id + " originId " + request.originalId + " quan " + missingQuantity + " for " + request.resourceType.name() + " to " + getReceiverNames(receiverIds));
        }
        sendRequest(reqId, request.originalId, request.resourceType, missingQuantity, utilityFunction, allReceivers, receiverIds, request.timeSent, request.timeout, request.originalSender);
        sentRequests.put(reqId, new Request(reqId, request.id, request.originalId, true, missingQuantity, request.resourceType, request.utilityFunction, request.sender, request.originalSender, allReceivers, reservedItems, request.timeSent, request.timeout));
    }


    Set<AID> findNeighborsToCascadeRequest( Request request) {

        Set<AID> receiverIds = new HashSet<>();
        for (int i = 0; i < neighbors.length; i++) {
            AID aid = new AID(numberOfAgents+"Agent"+(i+1), AID.ISLOCALNAME);
            if (neighbors[i] != null && !request.allReceivers.contains(i+1) && !request.sender.equals(aid)) {
                receiverIds.add(aid);
            }
        }

        return receiverIds;
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


    private void createOffer(String reqId, String originalReqId, AID offerer, AID requester, ResourceType resourceType, long offerQuantity, Map<Long, Long> costFunction, SortedSet<ResourceItem> availableItems, long requestTimeout) {

        Map<String, Long> offeredItems = new LinkedHashMap<>();

        for (long q=0; q<offerQuantity; q++) {
            ResourceItem item = availableItems.first();
            offeredItems.put(item.getId(), item.getExpiryTime());
            availableItems.remove( item);
        }

        if (offeredItems.size() != (int) offerQuantity) {
            System.out.println("");
        }

        String offerId = UUID.randomUUID().toString();
        Offer offer = new Offer(offerId, reqId, offerQuantity, resourceType, costFunction, offeredItems, offerer, requester, null, requestTimeout);
        offer.originalReqId = originalReqId;
        sentOffers.put( offerId, offer);
        if( debugMode) {
            logInf(this.getLocalName(), "created offer with id " + offerId + " reqId " + reqId + " originReqId " + originalReqId + " for " + resourceType.name() + " quan " + offerQuantity + " to " + requester.getLocalName());
        }
        sendOffer(reqId, offerId, requester, resourceType, offerQuantity, costFunction, offeredItems, requestTimeout);
    }


    private void sendOffer(String reqId, String offerId, AID requester, ResourceType resourceType, long offerQuantity, Map<Long, Long> costFunction, Map<String, Long> offeredItems, long offerTimeout) {

        if (offeredItems.size() != (int) offerQuantity) {
            System.out.println("");
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

        if( sentRequests.keySet().contains(reqId) == true) {
            if (debugMode) {
                logInf(myAgent.getLocalName(), "received offer with id " + offerId + " with quantity " + offerQuantity + " for reqId " + reqId + " for resource type " + resourceType.name() + " from " + msg.getSender().getLocalName());
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
                System.out.println("");
            }

            long offerTimeout = (long) jo.get("offerTimeout");

            Offer offer = new Offer(offerId, reqId, offerQuantity, resourceType, costFunction, offeredItems, msg.getSender(), myAgent.getAID(), null, offerTimeout);

            Set<Offer> offers = receivedOffers.get(reqId);
            if (offers == null) {
                offers = new HashSet<>();
            }
            offers.add(offer);
            receivedOffers.put(reqId, offers);

        } else {
            if (debugMode) {
                logInf(myAgent.getLocalName(), "received late offer with id " + offerId + " with quantity " + offerQuantity + " for removed reqId " + reqId + " for resource type " + resourceType.name() + " from " + msg.getSender().getLocalName());
            }
            sendConfirmation( offerId, msg.getSender(), resourceType, 0);
        }
    }


    void deliberateOnCascadingOffers(Agent myAgent) {

        for (var request : sentRequests.entrySet()) {
            if (request.getValue().cascaded == true && request.getValue().processed == false) {
                currentTime = System.currentTimeMillis();
                if (currentTime < request.getValue().timeout - 500 && currentTime - request.getValue().timeSent > 400) {
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

        Set<Offer> offers = null;
        Map<Offer, Long> offerQuantities = new LinkedHashMap<>();
        if (receivedOffers.containsKey(cascadedRequest.id)) {
            offers = receivedOffers.get(cascadedRequest.id);
            String requesterName = cascadedRequest.sender.getLocalName();
            int requesterId = Integer.valueOf(requesterName.replace(numberOfAgents+"Agent", ""));
            int distance = neighbors[requesterId-1];
            long minCost, cost;
            Offer lowCostOffer;
            for (Offer offer : offers) {
                currentTime = System.currentTimeMillis();
                if(currentTime < offer.timeout) {
                    offerQuantities.put(offer, 0L);
                }
            }
//            if( this.getLocalName().equals("4Agent3")) {
//                System.out.print("");
//            }
            for (long q=offerQuantity+1; q<=offerQuantity+cascadedRequest.quantity; q++) {
                minCost = Integer.MAX_VALUE;
                cost = 0;
                lowCostOffer = null;
                for (Offer offer : offers) {
                    currentTime = System.currentTimeMillis();
                    if (currentTime < offer.timeout) {
                        if (hasExtraItem(offer, offerQuantities)) {
                            cost = totalCost(offer, offerQuantities);
                            if (cost < minCost) {
                                minCost = cost;
                                lowCostOffer = offer;
                            }
                        }
                    }
                }
                if (lowCostOffer != null) {
                    cost += distance * 1;
                    if(q > 1) {
//                        costFunction.put(q, cost + costFunction.get(q-1));
                        costFunction.put(q, cost);
                    } else {
                        costFunction.put(q, cost);
                    }
                    offerQuantities.put(lowCostOffer, offerQuantities.get(lowCostOffer) + 1);
                } else {
                    break;
                }
            }
            for ( var offer : offerQuantities.entrySet()) {
                offerQuantity += offer.getValue();
            }

            if( cascadedRequest.reservedItems.size() > 0 && offerQuantity > cascadedRequest.reservedItems.size()) {
//                System.out.println("");
            }

            for ( var offer : offerQuantities.entrySet()) {
                Iterator itr = offer.getKey().offeredItems.keySet().iterator();
                long q=1;
                try {
                    while (q <= offer.getValue()) {
                        String itemId = (String) itr.next();
                        offeredItems.put(itemId, offer.getKey().offeredItems.get(itemId));
                        q++;
                    }
                } catch (Exception e) {
                    System.out.println("");
                }
            }
        }

        if (offerQuantity > 0) {
//            if( this.getLocalName().equals("4Agent3")) {
//                System.out.print("");
//            }
//            long cost = costFunction.get(offerQuantity);
//            long benefit = cascadedRequest.utilityFunction.get(offerQuantity);
//            if (cost < benefit) {
                String offerId = UUID.randomUUID().toString();
                Offer offer = new Offer(offerId, cascadedRequest.previousId, offerQuantity, cascadedRequest.resourceType, costFunction, offeredItems, this.getAID(), cascadedRequest.sender, offers, cascadedRequest.timeout);
                offer.originalReqId = cascadedRequest.originalId;
                sentOffers.put(offerId, offer);
                if( debugMode) {
                    if (offers != null) {
                        logInf(this.getLocalName(), "cascaded offer with id " + offerId + " with " + offers.size() + " includedOffers reqId " + cascadedRequest.id + " originReqId " + cascadedRequest.originalId + " " + cascadedRequest.resourceType.name() + " quan " + offerQuantity + " to " + cascadedRequest.sender.getLocalName());
                    } else {
                        logInf(this.getLocalName(), "cascaded offer with id " + offerId + " with 0 includedOffers reqId " + cascadedRequest.id + " originReqId " + cascadedRequest.originalId + " " + cascadedRequest.resourceType.name() + " quan " + offerQuantity + " to " + cascadedRequest.sender.getLocalName());
                    }
                }

                if (offeredItems.size() != (int) offerQuantity) {
                    System.out.println("");
                }

                sendOffer(cascadedRequest.previousId, offerId, cascadedRequest.sender, cascadedRequest.resourceType, offerQuantity, costFunction, offeredItems, cascadedRequest.timeout);
//            } else {
//                restoreReservedItems( cascadedRequest);
//            }
            cascadedRequest.processed = true;
        }
    }


    void restoreReservedItems (Request cascadedRequest) {
        // create a sorted set of reserved items
        SortedSet<ResourceItem> reserevedItems = new TreeSet<>(new ResourceItem.resourceItemComparator());
        for (var offeredItem : cascadedRequest.reservedItems.entrySet()) {
            reserevedItems.add(new ResourceItem(offeredItem.getKey(), cascadedRequest.resourceType, offeredItem.getValue()));
        }
        availableResources.get(cascadedRequest.resourceType).addAll(reserevedItems);
        totalReceivedResources += reserevedItems.size();
    }


    void deliberateOnConfirming( Agent myAgent) {

//        if( myAgent.getLocalName().equals("4Agent1")) {
//            System.out.print("");
//        }

        Map<Request, Map<Offer, Long>> selectedOffersForAllRequests = new LinkedHashMap<>();

        for (var request : sentRequests.entrySet()) {
            currentTime = System.currentTimeMillis();
            if (currentTime < request.getValue().timeout && currentTime - request.getValue().timeSent > 700) {
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
            if (thereIsBenefitToConfirmOffers( selectedOffersForAllRequests)) {
                createConfirmation( selectedOffersForAllRequests);
                addResourceItemsInOffers(selectedOffersForAllRequests);
            } else {
                createRejection( selectedOffersForAllRequests);
            }
            for (var selectedOffersForReq : selectedOffersForAllRequests.entrySet()) {
                sentRequests.remove(selectedOffersForReq.getKey().id);
                logInf( this.getLocalName(), "sentRequest with id " + selectedOffersForReq.getKey().id + " is confirmed and removed");
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
                try {
                    while (q <= offerQuantity.getValue()) {
                        ResourceItem item = itr.next();
                        resourceItems.add(item);
                        q++;
                        totalReceivedResources++;
                    }
                } catch (Exception e) {
                    System.out.println("");
                }
            }

            availableResources.put( confirmQuantitiesForReq.getKey().resourceType, resourceItems);
        }

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

        logInf(this.getLocalName(), "totalUtilityAfterConfirm: " + totalUtilityAfterConfirm + " totalUtilityBeforeConfirm: " + totalUtilityBeforeConfirm + " maxCost: " + maxCost);

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
            if(currentTime < offer.timeout - 200) {
                confirmQuantities.put(offer, 0L);
            }
        }

        for (long q=1; q<=request.quantity; q++) {
            minCost = Integer.MAX_VALUE;
            lowCostOffer = null;
            for (Offer offer : offers) {
                currentTime = System.currentTimeMillis();
                if(currentTime < offer.timeout - 200) {
                    if (hasExtraItem(offer, confirmQuantities)) {
                        cost = totalCost(offer, confirmQuantities);
                        if (cost < minCost) {
                            minCost = cost;
                            lowCostOffer = offer;
                        }
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
                if (debugMode) {
                    if (offerQuantity.getValue() > 0) {
                        logInf(this.getLocalName(), "created confirmation with quantity " + offerQuantity.getValue() + " for offerId  " + offerQuantity.getKey().id + " for resource type " + offerQuantity.getKey().resourceType.name() + " to " + offerQuantity.getKey().sender.getLocalName());
                    } else {
                        logInf(this.getLocalName(), "created rejection with quantity " + offerQuantity.getValue() + " for offerId  " + offerQuantity.getKey().id + " for resource type " + offerQuantity.getKey().resourceType.name() + " to " + offerQuantity.getKey().sender.getLocalName());
                    }
                }
                sendConfirmation (offerQuantity.getKey().id, offerQuantity.getKey().sender, offerQuantity.getKey().resourceType, offerQuantity.getValue());
            }
        }
    }


    private void createRejection (Map<Request, Map<Offer, Long>> confirmQuantitiesForAllRequests) {

        for (var confirmQuantitiesForReq : confirmQuantitiesForAllRequests.entrySet()) {
            for (var offerQuantity : confirmQuantitiesForReq.getValue().entrySet()) {
                if( debugMode) {
                    logInf(this.getLocalName(), "created rejection with quantity 0 for offerId  " + offerQuantity.getKey().id + " for resource type " + offerQuantity.getKey().resourceType.name() + " to " + offerQuantity.getKey().sender.getLocalName());
                }
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

        if (debugMode) {
            if( confirmQuantity > 0) {
                logInf(myAgent.getLocalName(), "received confirmation with quantity " + confirmQuantity + " for offerId " + offerId + " for resource type " + resourceType.name() + " from " + confirmation.getSender().getLocalName());
            } else {
                logInf(myAgent.getLocalName(), "received rejection with quantity " + confirmQuantity + " for offerId " + offerId + " for resource type " + resourceType.name() + " from " + confirmation.getSender().getLocalName());
            }
        }

        Offer sentOffer = sentOffers.get(offerId);

        if( sentOffer == null) {
            logInf(this.getLocalName(), "sentOffer is null !!!");
            logInf(this.getLocalName(), "sentOffers size: " + sentOffers.size());
        }

        Set<Offer> includedOffers = sentOffer.includedOffers;

        Request cascadedRequest = null;

        for (Request sentRequest : sentRequests.values()) {
            if (sentRequest.previousId == sentOffer.reqId) {
                cascadedRequest = sentRequest;
            }
        }

        if (includedOffers != null && cascadedRequest == null) {
            logInf(this.getLocalName(), "cascadedRequest is null for reqId " + sentOffer.reqId + " originReqId " + sentOffer.originalReqId + " offerId " + offerId);
        }

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

        restoreResources(sentOffer, cascadedRequest, confirmQuantity);
        logInf( this.getLocalName(), "restoreResources for offerId " + offerId + " confirmQuantity " + confirmQuantity);
        sentOffers.remove( offerId);
        if (cascadedRequest != null) {
            sentRequests.remove(cascadedRequest.id);
            logInf( this.getLocalName(), "cascadedRequest with id " + cascadedRequest.id + " originId " + cascadedRequest.originalId + " is confirmed and removed");
        }
    }


    private void cascadePartialConfirmations(Map<Offer, Long> offerQuantities) {

        // TODO: decrease the cost of transfer between sender and receiver from totalUtil when cascading confirmations

        for (var offerQuantity : offerQuantities.entrySet()) {
            sendConfirmation (offerQuantity.getKey().id, offerQuantity.getKey().sender, offerQuantity.getKey().resourceType, offerQuantity.getValue());

            if( debugMode) {
                if (offerQuantity.getValue() == 0) {
                    logInf(this.getLocalName(), "cascaded rejection with quantity " + offerQuantity.getValue() + " for offerId  " + offerQuantity.getKey().id + " for resource type " + offerQuantity.getKey().resourceType.name() + " to " + offerQuantity.getKey().sender.getLocalName());
                } else {
                    logInf(this.getLocalName(), "cascaded confirmation with quantity " + offerQuantity.getValue() + " for offerId  " + offerQuantity.getKey().id + " for resource type " + offerQuantity.getKey().resourceType.name() + " to " + offerQuantity.getKey().sender.getLocalName());
                }
            }
        }
    }


    private void restoreResources(Offer sentOffer, Request cascadedRequest, long confirmQuantity) {

        if (cascadedRequest != null) {
            if (confirmQuantity < cascadedRequest.reservedItems.size()) {
                // create a sorted set of offered items
                SortedSet<ResourceItem> offeredItems = new TreeSet<>(new ResourceItem.resourceItemComparator());
                for (var offeredItem : cascadedRequest.reservedItems.entrySet()) {
                    offeredItems.add(new ResourceItem(offeredItem.getKey(), cascadedRequest.resourceType, offeredItem.getValue()));
                }
                Iterator<ResourceItem> itr = offeredItems.iterator();
                long q=1;
                while (q<=confirmQuantity) {
                    ResourceItem item = itr.next();
                    offeredItems.remove(item);
                    itr = offeredItems.iterator();
                    q++;
                }
                availableResources.get(cascadedRequest.resourceType).addAll(offeredItems);
                totalReceivedResources += offeredItems.size();
            }
        } else if (confirmQuantity < sentOffer.quantity) {
            // create a sorted set of offered items
            SortedSet<ResourceItem> offeredItems = new TreeSet<>(new ResourceItem.resourceItemComparator());
            for (var offeredItem : sentOffer.offeredItems.entrySet()) {
                offeredItems.add(new ResourceItem(offeredItem.getKey(), sentOffer.resourceType, offeredItem.getValue()));
            }
            Iterator<ResourceItem> itr = offeredItems.iterator();
            long q=1;
            while (q<=confirmQuantity) {
                ResourceItem item = itr.next();
                offeredItems.remove(item);
                itr = offeredItems.iterator();
                q++;
            }
            availableResources.get(sentOffer.resourceType).addAll(offeredItems);
            totalReceivedResources += offeredItems.size();
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
        AID aid = new AID(numberOfAgents + "Agent0", AID.ISLOCALNAME);
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
        AID aid = new AID(numberOfAgents + "Agent0", AID.ISLOCALNAME);
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
        AID aid = new AID(numberOfAgents + "Agent0", AID.ISLOCALNAME);
        msg.addReceiver(aid);

        JSONObject jo = new JSONObject();
        jo.put( "totalUtil", totalUtil);

        msg.setContent( jo.toJSONString());
        send(msg);

//        System.out.println( myAgent.getLocalName() + " sent total utility: " + totalUtil + " to the master agent" );
    }


    protected void logInf (String agentId, String msg) {

//        System.out.println(System.currentTimeMillis() + " " + agentId + " " + msg);

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
