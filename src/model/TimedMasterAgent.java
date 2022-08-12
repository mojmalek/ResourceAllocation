package model;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.lang.acl.ACLMessage;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.util.*;


public class TimedMasterAgent extends Agent {

    private boolean debugMode = false;
    private String logFileName;

//    private Map<AID, ArrayList<JSONObject>> tasksInfo = new LinkedHashMap<>();
//    private Map<AID, ArrayList<JSONObject>> resourcesInfo = new LinkedHashMap<>();
//    private Map<AID, ArrayList<Long>> utilitiesInfo = new LinkedHashMap<>();
    private Map<AID, Long> utilitiesInfo = new LinkedHashMap<>();

    private SortedSet<Task> toDoTasks = new TreeSet<>(new Task.taskComparator());
    private SortedSet<Task> doneTasks = new TreeSet<>(new Task.taskComparator());
    private long totalUtil;
    private long endTime;
    private long currentTime;
    private int numberOfAgents;
    Integer[][] socialNetwork;

//    private Map<ResourceType, SortedSet<ResourceItem>> availableResources = new LinkedHashMap<>();
//    private Map<ResourceType, ArrayList<ResourceItem>> expiredResources = new LinkedHashMap<>();
    private Map<AID, Map<ResourceType, SortedSet<ResourceItem>>> agentAvailableResources = new LinkedHashMap<>();
    private Map<AID, Map<ResourceType, ArrayList<ResourceItem>>> agentExpiredResources = new LinkedHashMap<>();


    @Override
    protected void setup() {

        // Get ids of other agents as arguments
        Object[] args = getArguments();
        if (args != null && args.length > 0) {
            numberOfAgents = (int) args[0];
            for (int i = 1; i <= numberOfAgents; i++) {
                AID aid = new AID(numberOfAgents + "Agent" + i, AID.ISLOCALNAME);
//                tasksInfo.put( aid, new ArrayList<>());
//                resourcesInfo.put( aid, new ArrayList<>());
                utilitiesInfo.put( aid, 0L);
                agentAvailableResources.put(aid, new LinkedHashMap<>());
            }
            endTime = (long) args[1];
            socialNetwork = (Integer[][]) args[2];
            logFileName = (String) args[3];
        }

        if (debugMode) {
            logInf("Hello World. I’m the Master agent! My local-name is " + getAID().getLocalName());
        }

        addBehaviour(new CyclicBehaviour() {
            @Override
            public void action() {
                ACLMessage msg = myAgent.receive();
                if (msg != null) {
                    String content = msg.getContent();
                    switch (msg.getPerformative()) {
                        case ACLMessage.INFORM:
//                            logInf( myAgent.getLocalName() + " received an INFORM message from " + msg.getSender().getLocalName());
                            try {
                                storeInfo(myAgent, msg);
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


        addBehaviour (new TickerBehaviour(this, 50) {
            protected void onTick() {
                currentTime = System.currentTimeMillis();
                if (currentTime <= endTime) {
                    expireResourceItems (myAgent);
                    expireTasks (myAgent);
                    performTasks (myAgent);
                }
            }
        });


        addBehaviour(new CyclicBehaviour() {
            @Override
            public void action() {
                currentTime = System.currentTimeMillis();
                if (currentTime > endTime + 1000) {
                    logInf ("Sum of " + numberOfAgents + " agents utilities: " + agentUtilitiesSum());
                    logInf ("Efficiency of the protocol for " + numberOfAgents + " agents: " + ((double) agentUtilitiesSum() / totalUtil * 100));
                    block();
                }
            }
        });
    }


    void findNewTasks (JSONObject joNewTasks, AID agentId) {

        SortedSet<Task> newTasks = new TreeSet<>(new Task.taskComparator());
        String id, resourceType;
        Long utility, quantity;
        Map<ResourceType, Long> requiredResources;
        Iterator<String> keysIterator1 = joNewTasks.keySet().iterator();
        while (keysIterator1.hasNext()) {
            requiredResources = new LinkedHashMap<>();
            id = keysIterator1.next();
            JSONObject joTask = (JSONObject) joNewTasks.get(id);
            utility = (Long) joTask.get("utility");
            JSONObject joRequiredResources = (JSONObject) joTask.get("requiredResources");
            Iterator<String> keysIterator2 = joRequiredResources.keySet().iterator();
            while (keysIterator2.hasNext()) {
                resourceType = keysIterator2.next();
                quantity = (Long) joRequiredResources.get(resourceType);
                requiredResources.put( ResourceType.valueOf(resourceType), quantity);
            }
            Task newTask = new Task(id, utility.intValue(), 20, requiredResources, agentId);
            newTasks.add( newTask);
        }
        toDoTasks.addAll( newTasks);
    }


    void findNewResources (JSONObject joNewResources, AID agentId) {

        String resourceType;
        String id;
        Long lifetime;
        Iterator<String> keysIterator1 = joNewResources.keySet().iterator();
        while (keysIterator1.hasNext()) {
            resourceType = keysIterator1.next();
            JSONObject joItems = (JSONObject) joNewResources.get(resourceType);
            Iterator<String> keysIterator2 = joItems.keySet().iterator();
            while (keysIterator2.hasNext()) {
                id = keysIterator2.next();
                lifetime = (Long) joItems.get(id);
                ResourceItem item = new ResourceItem(id, ResourceType.valueOf(resourceType), lifetime.intValue(), agentId);
                if (agentAvailableResources.get(agentId).containsKey( ResourceType.valueOf(resourceType)) == false) {
                    agentAvailableResources.get(agentId).put(ResourceType.valueOf(resourceType), new TreeSet<>(new ResourceItem.resourceItemComparator()));
                }
                agentAvailableResources.get(agentId).get(ResourceType.valueOf(resourceType)).add(item);
            }
        }
    }


    void expireResourceItems(Agent myAgent) {

        SortedSet<ResourceItem> availableItems;
        ArrayList<ResourceItem> expiredItems;
        ArrayList<ResourceItem> expiredItemsNow = new ArrayList<>();
        for (var agentResources : agentAvailableResources.entrySet()) {
            for (var resource : agentResources.getValue().entrySet()) {
                expiredItemsNow.clear();
                availableItems = agentAvailableResources.get(agentResources.getKey()).get(resource.getKey());
                if (agentExpiredResources.get(agentResources.getKey()).containsKey(resource.getKey())) {
                    expiredItems = agentExpiredResources.get(agentResources.getKey()).get(resource.getKey());
                } else {
                    expiredItems = new ArrayList<>();
                    agentExpiredResources.get(agentResources.getKey()).put(resource.getKey(), expiredItems);
                }
                for (ResourceItem item : availableItems) {
                    currentTime = System.currentTimeMillis();
                    if (currentTime > item.getExpiryTime()) {
                        expiredItemsNow.add(item);
                        expiredItems.add(item);
                    }
                }
                int initialSize = availableItems.size();
                availableItems.removeAll(expiredItemsNow);
                if (initialSize - expiredItemsNow.size() != availableItems.size()) {
                    System.out.println("Error!!");
                }
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


//    boolean receivedInfoFromAll() {
//
//        for (var taskInfo : tasksInfo.entrySet() ) {
//            if (taskInfo.getValue().size() < numberOfRounds) {
//                return false;
//            }
//        }
//        for (var taskInfo : resourcesInfo.entrySet() ) {
//            if (taskInfo.getValue().size() < numberOfRounds) {
//                return false;
//            }
//        }
//        for (var taskInfo : utilitiesInfo.entrySet() ) {
//            if (taskInfo.getValue().size() < numberOfRounds) {
//                return false;
//            }
//        }
//        return true;
//    }


    private void performTasks(Agent myAgent) {

//        logInf (myAgent.getLocalName() +  " is performing tasks.");
        int count = 0;
        SortedSet<Task> doneTasksNow = new TreeSet<>(new Task.taskComparator());
        // Centralized greedy algorithm: tasks are sorted by utility in toDoTasks
        for (Task task : toDoTasks) {
            currentTime = System.currentTimeMillis();
            if (currentTime <= task.deadline && hasEnoughResources(task, agentAvailableResources)) {
                processTask(task);
                doneTasksNow.add(task);
                boolean check = doneTasks.add(task);
                if (check == false) {
                    logInf("Error!!");
                }
                totalUtil = totalUtil + task.utility;
                count += 1;
            }
        }

        if (doneTasksNow.size() != count) {
            logInf("Error!!");
        }

        int initialSize = toDoTasks.size();

        toDoTasks.removeAll (doneTasks);

        if ( initialSize - count != toDoTasks.size()) {
            logInf("Error!!");
        }

//        logInf( myAgent.getLocalName() + " has performed " + doneTasks.size() + " tasks and gained total utility of " + totalUtil);
    }


    void processTask (Task task) {

        long allocatedQuantity;
        Set<AID> providers;
        for (var requiredResource : task.requiredResources.entrySet()) {
            allocatedQuantity = 0;
            providers = new HashSet<>();
            providers.add(task.manager);
            while (allocatedQuantity < requiredResource.getValue()) {

                while (isMissingResource( providers, requiredResource.getKey())) {
                    providers = addNeighbors( providers);
                }

                AID selectedProvider = selectBestProvider( providers, requiredResource.getKey());

                allocateResource (selectedProvider, requiredResource.getKey());
                incurTransferCost(task.manager, selectedProvider);

                allocatedQuantity++;
            }
        }
    }


    boolean isMissingResource( Set<AID> providers, ResourceType resourceType) {
        boolean missing = true;
        for (AID aid : providers) {
            if( agentAvailableResources.get(aid).containsKey(resourceType)) {
                if (agentAvailableResources.get(aid).get(resourceType).size() > 0) {
                    missing = false;
                    break;
                }
            }
        }
        return missing;
    }


    Set<AID> addNeighbors(Set<AID> providers) {
        Set<AID> newNeighbors = new HashSet<>();
        for (AID aid : providers) {
            String providerName = aid.getLocalName();
            int providerId = Integer.valueOf(providerName.replace(numberOfAgents+"Agent", ""));
            Integer[] providerNeighbors = socialNetwork[providerId-1];
            for (int i = 0; i < providerNeighbors.length; i++) {
                if (providerNeighbors[i] != null) {
                    newNeighbors.add(new AID(numberOfAgents + "Agent" + (i + 1), AID.ISLOCALNAME));
                }
            }
        }
        providers.addAll( newNeighbors);
        return providers;
    }


    AID selectBestProvider( Set<AID> providers, ResourceType resourceType) {

        //TODO: sort the providers by their workload + shortest distance from resource manager

        AID selectedProvider = null;

        for (AID aid : providers) {
            if( agentAvailableResources.get(aid).containsKey(resourceType)) {
                if (agentAvailableResources.get(aid).get(resourceType).size() > 0) {
                    selectedProvider = aid;
                    break;
                }
            }
        }

        return selectedProvider;
    }


    void allocateResource (AID selectedProvider, ResourceType resourceType) {

        ResourceItem item = agentAvailableResources.get(selectedProvider).get(resourceType).first();
        agentAvailableResources.get(selectedProvider).get(resourceType).remove((item));
    }


    void incurTransferCost(AID taskManager, AID provider) {

//        String requesterName = requester.getLocalName();
//        int requesterId = Integer.valueOf(requesterName.replace(numberOfAgents+"Agent", ""));
//        int distance = neighbors[requesterId-1];

        long distance = 0;

        totalUtil -= distance;
    }


    private boolean hasEnoughResources (Task task, Map<AID, Map<ResourceType, SortedSet<ResourceItem>>> agentAvailableResources) {
        boolean enough = true;
        long availableQuantity;
        for (var requiredResource : task.requiredResources.entrySet()) {
            availableQuantity = 0;
            for (var agentResource : agentAvailableResources.entrySet()) {
                if (agentResource.getValue().containsKey(requiredResource.getKey()) == true) {
                    availableQuantity += agentResource.getValue().get(requiredResource.getKey()).size();
                }
            }
            if (requiredResource.getValue() > availableQuantity) {
                enough = false;
                break;
            }
        }

        return enough;
    }


    private void storeInfo(Agent myAgent, ACLMessage msg) throws ParseException {

        String content = msg.getContent();

        Object obj = new JSONParser().parse(content);
        JSONObject jo = (JSONObject) obj;

        AID agentId = msg.getSender();
        JSONObject joNewTasks = (JSONObject) jo.get("newTasks");
        JSONObject joNewResources = (JSONObject) jo.get("newResources");
        Long totalUtil = (Long) jo.get("totalUtil");

        if (joNewTasks != null) {
//            tasksInfo.get(agentId).add( joNewTasks);
            findNewTasks( joNewTasks, agentId);
        }

        if (joNewResources != null) {
//            resourcesInfo.get(agentId).add( joNewResources);
            findNewResources( joNewResources, agentId);
        }

        if (totalUtil != null) {
            utilitiesInfo.put(agentId, totalUtil);
        }
    }


    long agentUtilitiesSum() {
        long sum = 0;
        for( var utilInfo: utilitiesInfo.entrySet()) {
            sum += utilInfo.getValue();
        }
        return sum;
    }


    protected void logInf(String msg) {

            System.out.println(numberOfAgents + "Agent0: " + msg);
//            try {
//                PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(logFileName, true)));
//                out.println(numberOfAgents + "Agent0: " + msg);
//                out.close();
//            } catch (IOException e) {
//                System.err.println("Error writing file..." + e.getMessage());
//            }
    }


}
