package agents;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.core.behaviours.WakerBehaviour;
import jade.lang.acl.ACLMessage;
import model.ResourceItem;
import model.ResourceType;
import model.Task;
import org.jgrapht.Graph;
import org.jgrapht.alg.interfaces.ShortestPathAlgorithm;
import org.jgrapht.alg.shortestpath.DijkstraShortestPath;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleWeightedGraph;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;


public class TimedMasterAgent extends Agent {

    private boolean debugMode = true;
    private String logFileName;

    private Map<AID, Long> utilitiesInfo = new LinkedHashMap<>();

    private SortedSet<Task> toDoTasks = new TreeSet<>(new Task.taskComparator());
    private SortedSet<Task> doneTasks = new TreeSet<>(new Task.taskComparator());
    private long totalUtil, transferCost;
    private long endTime;
    private long currentTime;
    private int numberOfAgents;
    Integer[][] adjacency;
    Graph<String, DefaultWeightedEdge> graph;
    ShortestPathAlgorithm shortestPathAlgorithm;

    private Map<AID, Map<ResourceType, SortedSet<ResourceItem>>> agentAvailableResources = new LinkedHashMap<>();
    private Map<AID, Map<ResourceType, SortedSet<ResourceItem>>> agentExpiredResources = new LinkedHashMap<>();


    @Override
    protected void setup() {

        Object[] args = getArguments();
        if (args != null && args.length > 0) {
            numberOfAgents = (int) args[0];
            for (int i = 1; i <= numberOfAgents; i++) {
                AID aid = new AID("A" + i, AID.ISLOCALNAME);
                utilitiesInfo.put( aid, 0L);
                agentAvailableResources.put(aid, new LinkedHashMap<>());
                agentExpiredResources.put(aid, new LinkedHashMap<>());
            }
            endTime = (long) args[1];
            graph = (Graph) args[2];
            adjacency = (Integer[][]) args[3];
            logFileName = (String) args[4];
        }

//        graph = createGraph(adjacency);
        shortestPathAlgorithm = new DijkstraShortestPath(graph);


        addBehaviour (new WakerBehaviour(this, new Date(endTime + 500)) {
            protected void onWake() {
                System.out.println ("Sum of " + numberOfAgents + " agents utilities: " + agentUtilitiesSum());
                System.out.println ("Master agent total utility: " + totalUtil + " and transfer cost: " + transferCost);
                System.out.println ("Efficiency of the protocol for " + numberOfAgents + " agents: " + ((double) agentUtilitiesSum() / totalUtil * 100));
            }
        } );


        addBehaviour (new TickerBehaviour(this, 400) {
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
                ACLMessage msg = myAgent.receive();
                if (msg != null) {
                    String content = msg.getContent();
                    switch (msg.getPerformative()) {
                        case ACLMessage.INFORM:
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
    }


    void findNewTasks (JSONObject joNewTasks, AID agentId) {

        SortedSet<Task> newTasks = new TreeSet<>(new Task.taskComparator());
        String id, resourceType;
        Long utility, deadline, quantity;
        Map<ResourceType, Long> requiredResources;
        Iterator<String> keysIterator1 = joNewTasks.keySet().iterator();
        while (keysIterator1.hasNext()) {
            requiredResources = new LinkedHashMap<>();
            id = keysIterator1.next();
            JSONObject joTask = (JSONObject) joNewTasks.get(id);
            utility = (Long) joTask.get("utility");
            deadline = (Long) joTask.get("deadline");
            JSONObject joRequiredResources = (JSONObject) joTask.get("requiredResources");
            Iterator<String> keysIterator2 = joRequiredResources.keySet().iterator();
            while (keysIterator2.hasNext()) {
                resourceType = keysIterator2.next();
                quantity = (Long) joRequiredResources.get(resourceType);
                requiredResources.put( ResourceType.valueOf(resourceType), quantity);
            }
            Task newTask = new Task(id, utility, deadline, requiredResources, agentId);
            newTasks.add( newTask);
            if( debugMode) {
                logInf("received new task with id: " + newTask.id);
            }
        }
        toDoTasks.addAll( newTasks);
//        System.out.println( "New tasks size:" + newTasks.size());
//        System.out.println( "To Do tasks size:" + toDoTasks.size());
        if (newTasks.size() == 0) {
           System.out.println("Error!!");
        }
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
                ResourceItem item = new ResourceItem(id, ResourceType.valueOf(resourceType), lifetime, agentId);
                if (agentAvailableResources.get(agentId).containsKey( ResourceType.valueOf(resourceType)) == false) {
                    agentAvailableResources.get(agentId).put(ResourceType.valueOf(resourceType), new TreeSet<>(new ResourceItem.resourceItemComparator()));
                }
                agentAvailableResources.get(agentId).get(ResourceType.valueOf(resourceType)).add(item);
                if( debugMode) {
                    logInf("received new resource item with id: " + item.getId() + " and lifetime: " + item.getExpiryTime());
                }
            }
        }
    }


    void expireResourceItems(Agent myAgent) {

        SortedSet<ResourceItem> availableItems;
        SortedSet<ResourceItem> expiredItems;
        SortedSet<ResourceItem> expiredItemsNow = new TreeSet<>(new ResourceItem.resourceItemComparator());
        for (var agentResources : agentAvailableResources.entrySet()) {
            for (var resource : agentResources.getValue().entrySet()) {
                expiredItemsNow.clear();
                availableItems = agentAvailableResources.get(agentResources.getKey()).get(resource.getKey());
                if (agentExpiredResources.get(agentResources.getKey()).containsKey(resource.getKey())) {
                    expiredItems = agentExpiredResources.get(agentResources.getKey()).get(resource.getKey());
                } else {
                    expiredItems = new TreeSet<>(new ResourceItem.resourceItemComparator());
                    agentExpiredResources.get(agentResources.getKey()).put(resource.getKey(), expiredItems);
                }
                for (ResourceItem item : availableItems) {
                    currentTime = System.currentTimeMillis();
                    if (currentTime > item.getExpiryTime()) {
                        expiredItemsNow.add(item);
                        expiredItems.add(item);
                        if( debugMode) {
                            logInf("resource item expired with id: " + item.getId());
                        }
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
                if( debugMode) {
                    logInf("task expired with id: " + task.id);
                }
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


    private void performTasks(Agent myAgent) {

        int count = 0;
        SortedSet<Task> doneTasksNow = new TreeSet<>(new Task.taskComparator());
        // Centralized greedy algorithm: tasks are sorted by utility in toDoTasks
        for (Task task : toDoTasks) {
            currentTime = System.currentTimeMillis();
            if (task.deadline - currentTime < 3000) {
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

        if( debugMode) {
            logInf("processed task with id: " + task.id + ", manager: " + task.manager.getLocalName() + ", util: " + task.utility);
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
            int providerId = Integer.valueOf(providerName.replace("A", ""));
            Integer[] providerNeighbors = adjacency[providerId-1];
            for (int i = 0; i < providerNeighbors.length; i++) {
                if (providerNeighbors[i] != null) {
                    newNeighbors.add(new AID("A" + (i+1), AID.ISLOCALNAME));
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

        String taskManagerName = taskManager.getLocalName();
        String taskManagerId = taskManagerName.replace("A", "");
        String providerName = provider.getLocalName();
        String providerId = providerName.replace("A", "");

        int i = Integer.valueOf(taskManagerId);
        int j = Integer.valueOf(providerId);
        double distance = 0;
        if (adjacency[i-1][j-1] == null) {
            try {
                distance = shortestPathAlgorithm.getPathWeight (taskManagerName, providerName);
            } catch (Exception e) {
                System.out.println("");
            }
        } else {
            // when there is an edge, we consider it as the selected path even if it is not the shortest path
            distance = adjacency[i-1][j-1];
        }

        if( debugMode) {
            logInf("transfer cost from " + provider.getLocalName() + " to " + taskManager.getLocalName() + " : " + distance);
        }

//        totalUtil -= (long) distance;
        transferCost += (long) distance;
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
//            System.out.println("Available resource quantity: " + availableQuantity);
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
            findNewTasks( joNewTasks, agentId);
        }

        if (joNewResources != null) {
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


    Graph<String, DefaultWeightedEdge> createGraph(Integer[][] adjacency) {

        Graph<String, DefaultWeightedEdge> graph = new SimpleWeightedGraph<>(DefaultWeightedEdge.class);

        for (int i=0; i<adjacency.length; i++) {
            graph.addVertex (String.valueOf(i+1));
        }

        String aid1, aid2;
        for (int i=0; i<adjacency.length; i++) {
            aid1 = String.valueOf(i+1);
            for( int j=0; j<adjacency.length; j++) {
                aid2 = String.valueOf(j+1);
                if(adjacency[i][j] != null && graph.containsEdge(aid1, aid2) == false) {
                    graph.addEdge(aid1, aid2);
                    graph.setEdgeWeight(aid1, aid2, Double.valueOf(adjacency[i][j]));
                }
            }
        }

        return graph;
    }


    protected void logInf(String msg) {

//      System.out.println("Time:" + System.currentTimeMillis() + " " + "A0: " + msg);

        try {
            PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(logFileName, true)));
            out.println("Time:" + System.currentTimeMillis() + " " + "A0: " + msg);
            out.close();
        } catch (IOException e) {
            System.err.println("Error writing file..." + e.getMessage());
        }
    }


}
