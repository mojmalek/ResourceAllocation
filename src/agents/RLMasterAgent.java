package agents;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;
import model.*;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;


public class RLMasterAgent extends Agent {

    private boolean debugMode = false;
    private String logFileName, resultFileName;
    private String agentType;

    private Map<AID, ArrayList<JSONObject>> tasksInfo = new LinkedHashMap<>();
    private Map<AID, ArrayList<JSONObject>> resourcesInfo = new LinkedHashMap<>();
    private Map<AID, ArrayList<Long>> utilitiesInfo = new LinkedHashMap<>();

    private SortedSet<Task> toDoTasks = new TreeSet<>(new Task.taskComparator());
    private SortedSet<Task> doneTasks = new TreeSet<>(new Task.taskComparator());
    private long totalUtil;
    private int numberOfRounds;
    private int numberOfAgents;

    private Map<ResourceType, SortedSet<ResourceItem>> availableResources = new LinkedHashMap<>();
    private Map<ResourceType, ArrayList<ResourceItem>> expiredResources = new LinkedHashMap<>();

    private Map<MasterStateAction, Double> masterQFunction1 = new LinkedHashMap<>();
    private Map<MasterStateAction, Double> masterQFunction2 = new LinkedHashMap<>();

    private final double alpha = 0.2; // Learning rate
    private final double gamma = 0.9; // Eagerness - 0 looks in the near future, 1 looks in the distant future
    private final double epsilon = 0.1; // With a small probability of epsilon, we choose to explore, i.e., not to exploit what we have learned so far

    private boolean doubleLearning = true;

    @Override
    protected void setup() {

        // Get ids of other agents as arguments
        Object[] args = getArguments();
        if (args != null && args.length > 0) {
            numberOfAgents = (int) args[0];
            numberOfRounds = (int) args[1];
            logFileName = (String) args[2];
            resultFileName = (String) args[3];
            agentType = (String) args[4];
        }

        for (int i = 1; i <= numberOfAgents; i++) {
            AID aid = new AID(agentType + i, AID.ISLOCALNAME);
            tasksInfo.put( aid, new ArrayList<>());
            resourcesInfo.put( aid, new ArrayList<>());
            utilitiesInfo.put( aid, new ArrayList<>());
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

                if (receivedInfoFromAll()) {
                    for (int r = 0; r < numberOfRounds; r++) {
//                        logInf( myAgent.getLocalName() + " Round: " + r+1);
                        for (var taskInfo : tasksInfo.entrySet() ) {
                            findNewTasks (taskInfo.getValue().get(r));
                        }
                        for (var resourceInfo : resourcesInfo.entrySet() ) {
                            findNewResources (resourceInfo.getValue().get(r));
                        }
//                        performTasksOptimal( myAgent);
//                        performTasksGreedy( myAgent);
                        performTasksRL( myAgent);
                        expireResourceItems( myAgent);
                        expireTasks( myAgent);

                        if (r == numberOfRounds-1) {
                            System.out.println("masterQFunction1 size: " + masterQFunction1.size());
                            if( doubleLearning == true) {
                                System.out.println("masterQFunction2 size: " + masterQFunction2.size());
                            }
                        }
                    }

                    System.out.println ("Centralized total util for " + agentType + " : " + totalUtil);
                    System.out.println ("Decentralized total util for " + agentType + " : " + agentUtilitiesSum());
                    System.out.println ("Percentage ratio for " + agentType + " : " + ((double) agentUtilitiesSum() / totalUtil * 100));
                    System.out.println ("");
//                    printUtils();
//                    logResults( String.valueOf(agentUtilitiesSum()));

                    block();
                }
            }
        });
    }


    void findNewTasks (JSONObject joNewTasks) {

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
            Task newTask = new Task(id, utility.intValue(), 20, requiredResources);
            newTasks.add( newTask);
        }
        toDoTasks.addAll( newTasks);
    }


    void findNewResources (JSONObject joNewResources) {

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
                ResourceItem item = new ResourceItem(id, ResourceType.valueOf(resourceType), lifetime.intValue());
                if (availableResources.containsKey( ResourceType.valueOf(resourceType)) == false) {
                    availableResources.put(ResourceType.valueOf(resourceType), new TreeSet<>(new ResourceItem.resourceItemComparator()));
                }
                availableResources.get(ResourceType.valueOf(resourceType)).add(item);
            }
        }
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
                item.setExpiryTime( item.getExpiryTime() - 1);
                if (item.getExpiryTime() == 0) {
                    expiredItemsInThisRound.add( item);
                    expiredItems.add( item);
                }
            }
            int initialSize = availableItems.size();
            availableItems.removeAll( expiredItemsInThisRound);
            if ( initialSize - expiredItemsInThisRound.size() != availableItems.size()) {
                logErr("Error!!");
            }
        }

//        for (var entry : expiredResources.entrySet()) {
//            logInf( myAgent.getLocalName() + " has " + entry.getValue().size() + " expired item of type: " + entry.getKey().name());
//        }
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
            logErr("Error!!");
        }
        int initialSize = toDoTasks.size();
        toDoTasks.removeAll( lateTasksInThisRound);
        if ( initialSize - count != toDoTasks.size()) {
            logErr("Error!!");
        }
    }


    boolean receivedInfoFromAll() {

        for (var taskInfo : tasksInfo.entrySet() ) {
            if (taskInfo.getValue().size() < numberOfRounds) {
                return false;
            }
        }
        for (var taskInfo : resourcesInfo.entrySet() ) {
            if (taskInfo.getValue().size() < numberOfRounds) {
                return false;
            }
        }
        for (var taskInfo : utilitiesInfo.entrySet() ) {
            if (taskInfo.getValue().size() < numberOfRounds) {
                return false;
            }
        }
        return true;
    }


    private void performTasksOptimal(Agent myAgent) {


    }


    private void performTasksGreedy(Agent myAgent) {

//        logInf (myAgent.getLocalName() +  " is performing tasks.");
        int count = 0;
        SortedSet<Task> doneTasksInThisRound = new TreeSet<>(new Task.taskComparator());
        // Centralized greedy algorithm: tasks are sorted by utility in toDoTasks
        for (Task task : toDoTasks) {
            if (hasEnoughResources(task, availableResources)) {
                processTask(task);
                doneTasksInThisRound.add(task);
                boolean check = doneTasks.add(task);
                if (check == false) {
                    logErr("Error!!");
                }
                totalUtil = totalUtil + task.utility;
                count += 1;
            }
        }

        if (doneTasksInThisRound.size() != count) {
            logErr("Error!!");
        }

        int initialSize = toDoTasks.size();

        toDoTasks.removeAll (doneTasks);

        if ( initialSize - count != toDoTasks.size()) {
            logErr("Error!!");
        }

//        logInf( myAgent.getLocalName() + " has performed " + doneTasks.size() + " tasks and gained total utility of " + totalUtil);
    }


    private void performTasksRL(Agent myAgent) {

        // Centralized reinforcement learning

        long currentAllocatedQuantity = 0;

        while (toDoTasks.size() > 0) {
            MasterState currentState = generateMasterState( currentAllocatedQuantity);

            Set<MasterAction> possibleActions = generatePossibleMasterActions (currentState);

            if (possibleActions.size() == 0) {
                break;
            }

            // Choose action from state using epsilon-greedy policy derived from Q
            MasterAction action =  selectEplisonGreedyMasterAction (currentState, possibleActions);

            processTask(action.selectedTask);
            doneTasks.add(action.selectedTask);
            toDoTasks.remove (action.selectedTask);
            totalUtil += action.selectedTask.utility;

//            long reward = (long) action.selectedTask.efficiency();
            long reward = (long) action.selectedTask.utility;

            MasterStateAction currentStateAction = new MasterStateAction (currentState, action);

            for (var resource : action.selectedTask.requiredResources.entrySet()) {
                currentAllocatedQuantity += resource.getValue();

            }

            MasterState nextState = generateMasterState(currentAllocatedQuantity);

            updateMasterQFunction(currentStateAction, nextState, reward);
        }
    }


    MasterState generateMasterState ( long currentAllocatedQuantity) {

        ArrayList<Double> efficiencies = new ArrayList<>();
        for (Task task : toDoTasks) {
            efficiencies.add( task.efficiency());
        }

        Map<ResourceType, Long> availableQuantities = new LinkedHashMap<>();
        for (var resource : availableResources.entrySet()) {
            availableQuantities.put( resource.getKey(), (long) resource.getValue().size());
        }

        // because these set are being updated
        SortedSet<Task> copyOfTasks = new TreeSet<>(toDoTasks);
        Map<ResourceType, SortedSet<ResourceItem>> resources = deepCopyResourcesMap( availableResources);

        MasterState masterState = new MasterState( copyOfTasks, efficiencies, resources, availableQuantities, currentAllocatedQuantity);
        return masterState;
    }


    Set<MasterAction> generatePossibleMasterActions (MasterState currentState) {

        Set<MasterAction> actions = new HashSet<>();
        MasterAction masterAction;
        MasterStateAction masterStateAction;
        for (Task task : currentState.toDoTasks) {
            if (hasEnoughResources(task, availableResources)) {
                masterAction = new MasterAction(task, task.efficiency());
                actions.add(masterAction);
                masterStateAction = new MasterStateAction(currentState, masterAction);
                if (masterQFunction1.containsKey(masterStateAction) == false) {
                    masterQFunction1.put(masterStateAction, Double.valueOf(task.utility));
//                    masterQFunction.put(masterStateAction, 1.0);
                } else {
                        System.out.println(this.getLocalName() + " masterQFunction1 contains masterStateAction");
                }
                if (doubleLearning == true) {
                    if (masterQFunction2.containsKey(masterStateAction) == false) {
                        masterQFunction2.put(masterStateAction, Double.valueOf(task.utility));
//                    masterQFunction.put(masterStateAction, 1.0);
                    } else {
                        System.out.println(this.getLocalName() + " masterQFunction2 contains masterStateAction");
                    }
                }
            }
        }
        return actions;
    }


    MasterAction selectEplisonGreedyMasterAction (MasterState currentState, Set<MasterAction> possibleActions) {

        MasterAction selectedAction = null;
        MasterStateAction masterStateAction;
        Random random = new Random();
        double r = random.nextDouble();
        Iterator<MasterAction> iter1 = possibleActions.iterator();
        Iterator<MasterAction> iter2 = possibleActions.iterator();
        if (r < epsilon) {
            //exploration: pick a random action from possible actions in this state
            int index = random.nextInt(possibleActions.size());
            for (int i = 0; i < index; i++) {
                iter1.next();
            }
            selectedAction = iter1.next();
        } else {
            //exploitation: pick the best known action from possible actions in this state using Q table
            MasterAction action;
            Double Q;
            Double highestQ = -Double.MAX_VALUE;
            for (int i = 0; i < possibleActions.size(); i++) {
                action = iter2.next();
                masterStateAction = new MasterStateAction(currentState, action);
                if (doubleLearning) {
                    Q = masterQFunction1.get(masterStateAction) + masterQFunction2.get(masterStateAction);
                } else {
                    Q = masterQFunction1.get(masterStateAction);
                }
                if (Q > highestQ) {
                    highestQ = Q;
                    selectedAction = action;
                }
            }
        }
        return selectedAction;
    }


    void updateMasterQFunction( MasterStateAction currentStateAction, MasterState nextState, long reward) {

        Set<MasterAction> possibleNextActions = generatePossibleMasterActions (nextState);

        if (possibleNextActions.size() > 0) {
            if (doubleLearning == true) {
                Random random = new Random();
                double r = random.nextDouble();
                if (r < 0.5) {
                    MasterAction bestNextAction1 = selectBestMasterAction1(nextState, possibleNextActions);
                    MasterStateAction bestNextStateAction1 = new MasterStateAction(nextState, bestNextAction1);
                    double updatedQ = masterQFunction1.get(currentStateAction) + alpha * (reward + (gamma * masterQFunction2.get(bestNextStateAction1)) - masterQFunction1.get(currentStateAction));
                    masterQFunction1.put(currentStateAction, updatedQ);
                } else {
                    MasterAction bestNextAction2 = selectBestMasterAction2(nextState, possibleNextActions);
                    MasterStateAction bestNextStateAction2 = new MasterStateAction(nextState, bestNextAction2);
                    double updatedQ = masterQFunction2.get(currentStateAction) + alpha * (reward + (gamma * masterQFunction1.get(bestNextStateAction2)) - masterQFunction2.get(currentStateAction));
                    masterQFunction2.put(currentStateAction, updatedQ);
                }
            } else {
                MasterAction bestNextAction1 = selectBestMasterAction1(nextState, possibleNextActions);
                MasterStateAction bestNextStateAction1 = new MasterStateAction(nextState, bestNextAction1);
                double updatedQ = masterQFunction1.get(currentStateAction) + alpha * (reward + (gamma * masterQFunction1.get(bestNextStateAction1)) - masterQFunction1.get(currentStateAction));
                masterQFunction1.put(currentStateAction, updatedQ);
            }
        }
    }


    MasterAction selectBestMasterAction1 (MasterState state, Set<MasterAction> possibleActions) {

        MasterAction selectedAction = null;
        MasterStateAction masterStateAction;
        Iterator<MasterAction> iter = possibleActions.iterator();

        MasterAction action;
        Double Q;
        Double highestQ = -Double.MAX_VALUE;
        for (int i = 0; i < possibleActions.size(); i++) {
            action = iter.next();
            masterStateAction = new MasterStateAction(state, action);
            Q = masterQFunction1.get(masterStateAction);
            if (Q > highestQ) {
                highestQ = Q;
                selectedAction = action;
            }
        }

        return selectedAction;
    }


    MasterAction selectBestMasterAction2 (MasterState state, Set<MasterAction> possibleActions) {

        MasterAction selectedAction = null;
        MasterStateAction masterStateAction;
        Iterator<MasterAction> iter = possibleActions.iterator();

        MasterAction action;
        Double Q;
        Double highestQ = -Double.MAX_VALUE;
        for (int i = 0; i < possibleActions.size(); i++) {
            action = iter.next();
            masterStateAction = new MasterStateAction(state, action);
            Q = masterQFunction2.get(masterStateAction);
            if (Q > highestQ) {
                highestQ = Q;
                selectedAction = action;
            }
        }

        return selectedAction;
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


    private void storeInfo(Agent myAgent, ACLMessage msg) throws ParseException {

        String content = msg.getContent();

        Object obj = new JSONParser().parse(content);
        JSONObject jo = (JSONObject) obj;

        AID agentId = msg.getSender();
        JSONObject joNewTasks = (JSONObject) jo.get("newTasks");
        JSONObject joNewResources = (JSONObject) jo.get("newResources");
        Long totalUtil = (Long) jo.get("totalUtil");

        if (joNewTasks != null) {
            tasksInfo.get(agentId).add( joNewTasks);
        }

        if (joNewResources != null) {
            resourcesInfo.get(agentId).add( joNewResources);
        }

        if (totalUtil != null) {
            utilitiesInfo.get(agentId).add( totalUtil);
        }
    }


    long agentUtilitiesSum() {
        long sum = 0;
        for( var utilInfo: utilitiesInfo.entrySet()) {
            sum += utilInfo.getValue().get( numberOfRounds - 1);
        }
        return sum;
    }


    void printUtils() {
        long sum;
        for( int r=0; r<=10000; r=r+1000) {
            sum = 0;
            for( var utilInfo: utilitiesInfo.entrySet()) {
                if (r==0) {
                    sum += utilInfo.getValue().get(r);
                } else {
                    sum += utilInfo.getValue().get(r) - utilInfo.getValue().get(r-1000);
                }
            }
            System.out.println("At round " + String.valueOf(r+1) + " : " + sum);
        }
    }


    private static Map<ResourceType, SortedSet<ResourceItem>> deepCopyResourcesMap(Map<ResourceType, SortedSet<ResourceItem>> original) {
        Map<ResourceType, SortedSet<ResourceItem>> copy = new LinkedHashMap<>();
        for (var entry : original.entrySet()) {
            copy.put(entry.getKey(), new TreeSet<>(entry.getValue()));
        }
        return copy;
    }


    protected void logErr(String msg) {

      System.out.println( agentType + "0: " + msg);

        try {
            PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(logFileName, true)));
            out.println( agentType + "0: " + msg);
            out.close();
        } catch (IOException e) {
            System.err.println("Error writing file..." + e.getMessage());
        }
    }


    protected void logResults(String msg) {

//        System.out.println(msg);

        try {
            PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(resultFileName, true)));
            out.println(msg);
            out.close();
        } catch (IOException e) {
            System.err.println("Error writing file..." + e.getMessage());
        }
    }


}
