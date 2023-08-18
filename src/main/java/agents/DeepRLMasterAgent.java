package agents;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;
import model.*;
import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.layers.DenseLayer;
import org.deeplearning4j.nn.conf.layers.OutputLayer;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.nn.weights.WeightInit;
import org.deeplearning4j.rl4j.experience.*;
import org.deeplearning4j.rl4j.learning.sync.ExpReplay;
import org.deeplearning4j.rl4j.observation.Observation;

import org.deeplearning4j.rl4j.experience.ExperienceHandler;
import org.deeplearning4j.rl4j.observation.Observation;
import org.deeplearning4j.rl4j.observation.transform.TransformProcess;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.rng.DefaultRandom;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.learning.config.Sgd;
import org.nd4j.linalg.lossfunctions.LossFunctions;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;


public class DeepRLMasterAgent extends Agent {

    private boolean debugMode = false;
    private String logFileName, resultFileName;
    private String agentType;

    private Map<AID, ArrayList<JSONObject>> tasksInfo = new LinkedHashMap<>();
    private Map<AID, ArrayList<JSONObject>> resourcesInfo = new LinkedHashMap<>();
    private Map<AID, ArrayList<Long>> utilitiesInfo = new LinkedHashMap<>();

    private Map<AID, SortedSet<Task>> toDoAgentTasks = new LinkedHashMap<>();
    private SortedSet<Task> toDoTasks = new TreeSet<>(new Task.taskComparator());
    private SortedSet<Task> doneTasks = new TreeSet<>(new Task.taskComparator());
    private long totalUtil;
    private int numberOfRounds;
    private int numberOfAgents;
    Integer[][] adjacency;

    private Map<AID, Map<ResourceType, SortedSet<ResourceItem>>> agentAvailableResources = new LinkedHashMap<>();
    private Map<AID, Map<ResourceType, SortedSet<ResourceItem>>> agentExpiredResources = new LinkedHashMap<>();

    private Map<MasterStateAction, Double> masterQFunction1 = new LinkedHashMap<>();
    private Map<MasterStateAction, Double> masterQFunction2 = new LinkedHashMap<>();

    private final double alpha = 0.01; // Learning rate
    private final double gamma = 0.5; // Eagerness - 0 looks in the near future, 1 looks in the distant future
    private double  epsilon = 0.5; // With a small probability of epsilon, we choose to explore, i.e., not to exploit what we have learned so far

    private boolean cascading = false;
    private boolean doubleLearning = false;
    private boolean deepLearning = true;

    // we may use two networks: policy network and target network
    private MultiLayerNetwork policyNetwork;
    private StateActionExperienceHandler stateActionExperienceHandler;
    private ReplayMemoryExperienceHandler replayMemoryExperienceHandler;

    @Override
    protected void setup() {

//        StateActionExperienceHandler.Configuration configuration = StateActionExperienceHandler.Configuration.builder().build();
//        configuration.setBatchSize(10);
//        stateActionExperienceHandler = new StateActionExperienceHandler(configuration);

        replayMemoryExperienceHandler = new ReplayMemoryExperienceHandler( new ExpReplay(100000, 32, new DefaultRandom()));


        final int numInputs = 4;
        int outputNum = numberOfAgents;
        long seed = 6;

        MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder()
                .seed(seed)
                .activation(Activation.TANH)
                .weightInit(WeightInit.XAVIER)
                .updater(new Sgd(0.1))
                .l2(1e-4)
                .list()
                .layer(new DenseLayer.Builder().nIn(numInputs).nOut(32)
                        .build())
                .layer(new DenseLayer.Builder().nIn(32).nOut(32)
                        .build())
                .layer(new DenseLayer.Builder().nIn(32).nOut(32)
                        .build())
                .layer( new OutputLayer.Builder(LossFunctions.LossFunction.NEGATIVELOGLIKELIHOOD)
                        .activation(Activation.SOFTMAX) //Override the global TANH activation with softmax for this layer
                        .nIn(32).nOut(outputNum).build())
                .build();

        policyNetwork = new MultiLayerNetwork(conf);
        policyNetwork.init();


        // Get ids of other agents as arguments
        Object[] args = getArguments();
        if (args != null && args.length > 0) {
            numberOfAgents = (int) args[0];
            numberOfRounds = (int) args[1];
            adjacency = (Integer[][]) args[2];
            logFileName = (String) args[3];
            resultFileName = (String) args[4];
            agentType = (String) args[5];
        }

        for (int i = 1; i <= numberOfAgents; i++) {
            AID aid = new AID(agentType + i, AID.ISLOCALNAME);
            tasksInfo.put( aid, new ArrayList<>());
            resourcesInfo.put( aid, new ArrayList<>());
            utilitiesInfo.put( aid, new ArrayList<>());
            agentAvailableResources.put(aid, new LinkedHashMap<>());
            agentExpiredResources.put(aid, new LinkedHashMap<>());
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
                        epsilon = 1 - (double) r / numberOfRounds;
//                        logInf( myAgent.getLocalName() + " Round: " + r+1);
                        for (var taskInfo : tasksInfo.entrySet() ) {
                            findNewTasks (taskInfo.getValue().get(r), taskInfo.getKey());
                        }
                        for (var resourceInfo : resourcesInfo.entrySet() ) {
                            findNewResources (resourceInfo.getValue().get(r), resourceInfo.getKey());
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
            Task newTask = new Task(id, utility.intValue(), deadline, requiredResources, agentId);
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
                ResourceItem item = new ResourceItem(id, ResourceType.valueOf(resourceType), lifetime.intValue());
                if (agentAvailableResources.get(agentId).containsKey( ResourceType.valueOf(resourceType)) == false) {
                    agentAvailableResources.get(agentId).put(ResourceType.valueOf(resourceType), new TreeSet<>(new ResourceItem.resourceItemComparator()));
                }
                agentAvailableResources.get(agentId).get(ResourceType.valueOf(resourceType)).add(item);
            }
        }
    }


    void expireResourceItems(Agent myAgent) {

        SortedSet<ResourceItem> availableItems;
        SortedSet<ResourceItem> expiredItems;
        SortedSet<ResourceItem> expiredItemsInThisRound = new TreeSet<>(new ResourceItem.resourceItemComparator());
        for (var agentResource : agentAvailableResources.entrySet()) {
            for (var resource : agentResource.getValue().entrySet()) {
                expiredItemsInThisRound.clear();
                availableItems = agentAvailableResources.get(agentResource.getKey()).get(resource.getKey());
                if (agentExpiredResources.get(agentResource.getKey()).containsKey(resource.getKey())) {
                    expiredItems = agentExpiredResources.get(agentResource.getKey()).get(resource.getKey());
                } else {
                    expiredItems = new TreeSet<>(new ResourceItem.resourceItemComparator());
                    agentExpiredResources.get(agentResource.getKey()).put(resource.getKey(), expiredItems);
                }
                for (ResourceItem item : availableItems) {
                    item.setExpiryTime(item.getExpiryTime() - 1);
                    if (item.getExpiryTime() == 0) {
                        expiredItemsInThisRound.add(item);
                        expiredItems.add(item);
                    }
                }
                int initialSize = availableItems.size();
                availableItems.removeAll(expiredItemsInThisRound);
                if (initialSize - expiredItemsInThisRound.size() != availableItems.size()) {
                    logErr("Error!!");
                }
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
            if (hasEnoughResources(task, agentAvailableResources)) {
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

            MasterState currentState = generateMasterState (currentAllocatedQuantity);

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

            MasterState nextState = generateMasterState (currentAllocatedQuantity);

            updateMasterQFunction(currentStateAction, nextState, reward);
        }
    }


    MasterState generateMasterState (long currentAllocatedQuantity) {

        ArrayList<Double> efficiencies = new ArrayList<>();
        for (Task task : toDoTasks) {
            efficiencies.add( task.efficiency());
        }

        Map<ResourceType, Long> availableQuantities = new LinkedHashMap<>();
        for (ResourceType resourceType : ResourceType.getValues()) {
            availableQuantities.put( resourceType, 0L);
        }
        for (var agentResource : agentAvailableResources.entrySet()) {
            for (var resource : agentResource.getValue().entrySet()) {
                availableQuantities.put(resource.getKey(), availableQuantities.get(resource.getKey()) + (long) resource.getValue().size());
            }
        }

        // because these sets are being updated
        SortedSet<Task> tasksCopy = new TreeSet<>(toDoTasks);
        Map<AID, Map<ResourceType, SortedSet<ResourceItem>>> agentResourcesCopy = deepCopyAgentResourcesMap( agentAvailableResources);

        // efficienciesSum, available resources per type
        double[][] array = new double[numberOfAgents][2];

        for (int i=0; i<numberOfAgents; i++) {
            double efficienciesSum = 0;
            AID aid = new AID(agentType + (i+1), AID.ISLOCALNAME);
            for (Task task : toDoTasks) {
                if( task.manager == aid) {
                    efficienciesSum += task.efficiency();
                }
            }
            array[i][0] = efficienciesSum;
            array[i][1] = agentAvailableResources.get(aid).get(ResourceType.A).size();
        }

        INDArray indArray = Nd4j.create(array);

        Observation observation = new Observation(indArray);

        MasterState masterState = new MasterState( tasksCopy, efficiencies, agentResourcesCopy, availableQuantities, currentAllocatedQuantity);
        masterState.deepObservation = observation;
        return masterState;
    }


    Set<MasterAction> generatePossibleMasterActions (MasterState currentState) {

        Set<MasterAction> actions = new HashSet<>();
        MasterAction masterAction;
        MasterStateAction masterStateAction;
        for (Task task : currentState.toDoTasks) {
            if (hasEnoughResources(task, agentAvailableResources)) {
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

            if (deepLearning == true) {

                // Get the data from the Observation
//                double[] data = observation.getChannelData(0);

                replayMemoryExperienceHandler.addExperience(currentStateAction.state.deepObservation, currentStateAction.action.selectedAgent, reward, false);

                if (replayMemoryExperienceHandler.isTrainingBatchReady()) {

                    List<StateActionRewardState> trainingBatch = replayMemoryExperienceHandler.generateTrainingBatch();

                    INDArray input = null;
                    INDArray labels = null;

//                    DataSet dataSet = null;

                    policyNetwork.fit( input, labels);
                }
            } else {
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

        // When No cascading, it only uses resources of the task manager and its direct neighbors, already checked in hasEnoughResources method.
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
//                incurTransferCost(task.manager, selectedProvider);
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
            int providerId = Integer.valueOf(providerName.replace(agentType, ""));
            Integer[] providerNeighbors = adjacency[providerId-1];
            for (int i = 0; i < providerNeighbors.length; i++) {
                if (providerNeighbors[i] != null) {
                    newNeighbors.add(new AID(agentType + (i+1), AID.ISLOCALNAME));
                }
            }
        }
        providers.addAll( newNeighbors);
        return providers;
    }


    AID selectBestProvider( Set<AID> providers, ResourceType resourceType) {

        //TODO: sort the providers by their workload + shortest distance from task manager

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


    private boolean hasEnoughResources (Task task, Map<AID, Map<ResourceType, SortedSet<ResourceItem>>> agentAvailableResources) {
        boolean enough = true;
        long availableQuantity;
        for (var requiredResource : task.requiredResources.entrySet()) {
            availableQuantity = 0;
            if (cascading) {
                // it can use resources of all agents in the network
                for (var agentResource : agentAvailableResources.entrySet()) {
                    if (agentResource.getValue().containsKey(requiredResource.getKey()) == true) {
                        availableQuantity += agentResource.getValue().get(requiredResource.getKey()).size();
                    }
                }
            } else {
                // it can only use resources of the task manager and its direct neighbors
                Set<AID> providers = new HashSet<>();
                providers.add(task.manager);
                String managerName = task.manager.getLocalName();
                int managerId = Integer.valueOf(managerName.replace(agentType, ""));
                Integer[] managerNeighbors = adjacency[managerId-1];
                for (int i = 0; i < managerNeighbors.length; i++) {
                    if (managerNeighbors[i] != null) {
                        providers.add(new AID(agentType + (i+1), AID.ISLOCALNAME));
                    }
                }
                for (AID aid : providers) {
                    if (agentAvailableResources.get(aid).containsKey(requiredResource.getKey()) == true) {
                        availableQuantity += agentAvailableResources.get(aid).get(requiredResource.getKey()).size();
                    }
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


    private static Map<AID, Map<ResourceType, SortedSet<ResourceItem>>> deepCopyAgentResourcesMap (Map<AID, Map<ResourceType, SortedSet<ResourceItem>>> original) {
        Map<AID, Map<ResourceType, SortedSet<ResourceItem>>> agentResourcesCopy = new LinkedHashMap<>();
        for (var agentResource : original.entrySet()) {
            Map<ResourceType, SortedSet<ResourceItem>> resourcesCopy = new LinkedHashMap<>();
            agentResourcesCopy.put( agentResource.getKey(), resourcesCopy);
            for (var resource : agentResource.getValue().entrySet()) {
                resourcesCopy.put(resource.getKey(), new TreeSet<>(resource.getValue()));
            }
        }
        return agentResourcesCopy;
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
