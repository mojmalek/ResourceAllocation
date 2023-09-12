package agents;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;
import model.*;
import org.deeplearning4j.nn.api.OptimizationAlgorithm;
import org.deeplearning4j.nn.conf.BackpropType;
import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.layers.DenseLayer;
import org.deeplearning4j.nn.conf.layers.OutputLayer;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.nn.weights.WeightInit;
import org.deeplearning4j.rl4j.experience.*;
import org.deeplearning4j.rl4j.learning.sync.ExpReplay;
import org.deeplearning4j.rl4j.observation.Observation;

import org.deeplearning4j.util.ModelSerializer;
import org.jgrapht.Graph;
import org.jgrapht.alg.interfaces.ShortestPathAlgorithm;
import org.jgrapht.alg.shortestpath.DijkstraShortestPath;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.rng.DefaultRandom;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.learning.config.Adam;
import org.nd4j.linalg.lossfunctions.LossFunctions;

import java.io.*;
import java.util.*;


public class DeepRLMasterAgent extends Agent {

    private boolean debugMode = true;
    private String logFileName, resultFileName;
    private String agentType;

    private Map<AID, ArrayList<JSONObject>> tasksInfo = new LinkedHashMap<>();
    private Map<AID, ArrayList<JSONObject>> resourcesInfo = new LinkedHashMap<>();
    private Map<AID, ArrayList<Long>> utilitiesInfo = new LinkedHashMap<>();

    private Map<AID, SortedSet<Task>> toDoAgentTasks = new LinkedHashMap<>();
    private SortedSet<Task> toDoTasks = new TreeSet<>(new Task.taskComparator());
    private SortedSet<Task> doneTasks = new TreeSet<>(new Task.taskComparator());
    private long totalUtil, totalTransferCost;
    private int numberOfRounds;
    private int round;
    private int numberOfAgents;
    Integer[][] adjacency;
    Graph<String, DefaultWeightedEdge> graph;
    ShortestPathAlgorithm shortestPathAlgorithm;

    private Map<AID, Map<ResourceType, SortedSet<ResourceItem>>> agentAvailableResources = new LinkedHashMap<>();
    private Map<AID, Map<ResourceType, SortedSet<ResourceItem>>> agentExpiredResources = new LinkedHashMap<>();

    private final double alpha = 0.001; // Learning rate
    private final double gamma = 0.9; // Eagerness - 0 looks in the near future, 1 looks in the distant future
    private double  epsilon = 0.1; // With a small probability of epsilon, we choose to explore, i.e., not to exploit what we have learned so far

    private boolean cascading = true;
    private boolean doubleLearning = true;
    private boolean loadTrainedModel = false;

    private MultiLayerNetwork policyNetwork;
    private MultiLayerNetwork targetNetwork;
    private long C;
    private long targetUpdateFreq = 10;
    private ReplayMemoryExperienceHandler replayMemoryExperienceHandler;

    @Override
    protected void setup() {

        // Get ids of other agents as arguments
        Object[] args = getArguments();
        if (args != null && args.length > 0) {
            numberOfAgents = (int) args[0];
            numberOfRounds = (int) args[1];
            graph = (Graph) args[2];
            adjacency = (Integer[][]) args[3];
            logFileName = (String) args[4];
            resultFileName = (String) args[5];
            agentType = (String) args[6];
        }

        shortestPathAlgorithm = new DijkstraShortestPath(graph);

        for (int i = 1; i <= numberOfAgents; i++) {
            AID aid = new AID(agentType + i, AID.ISLOCALNAME);
            tasksInfo.put( aid, new ArrayList<>());
            resourcesInfo.put( aid, new ArrayList<>());
            utilitiesInfo.put( aid, new ArrayList<>());
            agentAvailableResources.put(aid, new LinkedHashMap<>());
            agentExpiredResources.put(aid, new LinkedHashMap<>());
            toDoAgentTasks.put(aid, new TreeSet<>(new Task.taskComparator()));
        }

        replayMemoryExperienceHandler = new ReplayMemoryExperienceHandler( new ExpReplay(100000, 16, new DefaultRandom()));

        if (loadTrainedModel) {
            try {
                policyNetwork = ModelSerializer.restoreMultiLayerNetwork("trained_model.zip");
//                policyNetwork = MultiLayerNetwork.load(new File("trained_model.zip"), true);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else {
            final int numInputs = (2 + ResourceType.getSize()) * numberOfAgents;
            int outputNum = numberOfAgents;
            MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder()
                    .seed(123)
                    .optimizationAlgo(OptimizationAlgorithm.STOCHASTIC_GRADIENT_DESCENT)
                    .activation(Activation.RELU)
                    .weightInit(WeightInit.XAVIER)
                    .updater(new Adam(alpha))
                    .l2(0.001)
                    .list()
                    .layer(new DenseLayer.Builder()
                            .nIn(numInputs)
                            .nOut(256)
                            .activation(Activation.RELU)
                            .build())
                    .layer(new DenseLayer.Builder()
                            .nIn(256)
                            .nOut(128)
                            .activation(Activation.RELU)
                            .build())
                    .layer(new DenseLayer.Builder()
                            .nIn(128)
                            .nOut(64)
                            .activation(Activation.RELU)
                            .build())
                    .layer( new OutputLayer.Builder(LossFunctions.LossFunction.MSE)
                            .nIn(64)
                            .nOut(outputNum)
                            .activation(Activation.IDENTITY)
                            .build())
                    .backpropType(BackpropType.Standard)
                    .build();

            policyNetwork = new MultiLayerNetwork(conf);
            policyNetwork.init();
        }
        targetNetwork = policyNetwork;

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
                        round = r + 1;
                        if (epsilon > 0.1) {
                            epsilon -= 0.0005;
                        }
                        if (round % 100 == 0) {
                            System.out.println(myAgent.getLocalName() + "  Round: " + round + "  Epsilon: " + epsilon);
                        }
                        for (var taskInfo : tasksInfo.entrySet() ) {
                            findNewTasks (taskInfo.getValue().get(r), taskInfo.getKey());
                        }
                        for (var resourceInfo : resourcesInfo.entrySet() ) {
                            findNewResources (resourceInfo.getValue().get(r), resourceInfo.getKey());
                        }
                        performTasksOptimal( myAgent);
//                        performTasksGreedy( myAgent);
//                        performTasksRL( myAgent);
                        expireResourceItems( myAgent);
                        expireTasks( myAgent);
                    }

                    try {
                        targetNetwork.save(new File("trained_model.zip"));
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }

                    System.out.println ("Centralized total util for " + agentType + " : " + totalUtil);
                    System.out.println ("Centralized total transferCost for " + agentType + " : " + totalTransferCost);
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
        toDoAgentTasks.get(agentId).addAll( newTasks);
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
    }


    void expireTasks(Agent myAgent) {

        SortedSet<Task> lateTasksInThisRound = new TreeSet<>(new Task.taskComparator());

        for (var agentTasks : toDoAgentTasks.entrySet()) {
            lateTasksInThisRound.clear();
            for (Task task : agentTasks.getValue()) {
                task.deadline--;
                if (task.deadline == 0) {
                    lateTasksInThisRound.add( task);
                }
            }
            agentTasks.getValue().removeAll( lateTasksInThisRound);
            toDoTasks.removeAll( lateTasksInThisRound);
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

        // formulate as an ILP and solve using Gurobi

        int numTasks = toDoTasks.size();
        ResourceType[] resourceTypeValues = ResourceType.getValues();
        int numResourceTypes = resourceTypeValues.length;

        double[] util = new double[numTasks];  // Utility for each task
        double[][] distance = new double[numberOfAgents][numTasks];  // Distance between each agent and task location
        double[][] requirement = new double[numTasks][numResourceTypes];  // Resource requirement of each task for each resource type
        double[][] resource = new double[numberOfAgents][numResourceTypes];  // Resources of each type available to each agent

        int j = 0;
        for (Task task : toDoTasks) {
            util[j] = task.utility;
            for (int i=0; i < numberOfAgents; i++) {
                AID potentialProvider = new AID(agentType + (i+1), AID.ISLOCALNAME);
                distance[i][j] = computeTransferCost(task.manager, potentialProvider);
            }
            for (int k = 0; k < numResourceTypes; k++) {
                requirement[j][k] = task.requiredResources.get(resourceTypeValues[k]);
            }
            j++;
        }

        for (int i = 0; i < numberOfAgents; i++) {
            AID aid = new AID(agentType + (i + 1), AID.ISLOCALNAME);
            for (int k = 0; k < numResourceTypes; k++) {
                if (agentAvailableResources.get(aid).containsKey(resourceTypeValues[k])) {
                    resource[i][k] = agentAvailableResources.get(aid).get(resourceTypeValues[k]).size();
                }
            }
        }

        GurobiOptimizer optimizer = new GurobiOptimizer( numberOfAgents,  numTasks,  ResourceType.getSize(), util, distance, requirement, resource);

        totalUtil = (long) optimizer.run();
    }


    private void performTasksGreedy(Agent myAgent) {

        // Centralized greedy algorithm: tasks are sorted by efficiency in toDoTasks
        for (Task task : toDoTasks) {
            if (hasEnoughResources(task, agentAvailableResources)) {
                double transferCost = processTask(task);
                doneTasks.add(task);
                totalUtil += task.utility;
                totalUtil -= (long) transferCost;
                totalTransferCost += (long) transferCost;

                System.out.println( "Round: " + round + " selected manager: " + task.manager.getLocalName() + " task util: " + task.utility + " transferCost: " + transferCost);
            }
        }

        toDoTasks.removeAll (doneTasks);

//        logInf( myAgent.getLocalName() + " has performed " + doneTasks.size() + " tasks and gained total utility of " + totalUtil);
    }


    private void performTasksRL(Agent myAgent) {

        // Centralized reinforcement learning

        long currentAllocatedQuantity = 0;
        while (toDoTasks.size() > 0) {
            MasterState currentState = generateMasterState (currentAllocatedQuantity);
            Set<AID> possibleManagers = generatePossibleMasterActions (currentState);
            if (possibleManagers.size() == 0) {
                break;
            }
            // Choose selectedManager from state using epsilon-greedy policy derived from Q
            AID selectedManager =  selectEplisonGreedyMasterAction (currentState, possibleManagers);
            Task selectedTask = null;
            for (Task task : toDoAgentTasks.get(selectedManager)) {
                if (hasEnoughResources(task, agentAvailableResources)) {
                    selectedTask = task;
                    break;
                }
            }

            double transferCost = processTask(selectedTask);
            doneTasks.add(selectedTask);
            toDoTasks.remove (selectedTask);
            toDoAgentTasks.get(selectedManager).remove(selectedTask);
            totalUtil += selectedTask.utility;
            totalUtil -= (long) transferCost;
            totalTransferCost += (long) transferCost;

//            double reward = selectedTask.efficiency();
//            double reward = (double) selectedTask.utility - transferCost;
            double reward = (double) selectedTask.utility;
            double reward = (double) selectedTask.utility - transferCost;
//            double reward = (double) selectedTask.utility;

            System.out.println( "Round: " + round + " selected manager: " + selectedManager.getLocalName() + " task util: " + selectedTask.utility + " transferCost: " + transferCost);


            MasterAction action = new MasterAction( selectedTask, selectedManager);
            MasterStateAction currentStateAction = new MasterStateAction (currentState, action);

            for (var resource : action.selectedTask.requiredResources.entrySet()) {
                currentAllocatedQuantity += resource.getValue();
            }

            MasterState nextState = generateMasterState (currentAllocatedQuantity);

            updateMasterQFunction(currentStateAction, nextState, reward);

            // or update based on episodes
            C += 1;
            if (C % targetUpdateFreq == 0) {
                targetNetwork = policyNetwork;
            }
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

        // utilitiesSum or efficienciesSum, available resources per type
        double[][] array = new double[numberOfAgents][2 + ResourceType.getSize()];

        for (int i=0; i<numberOfAgents; i++) {

            array[i][0] = graph.degreeOf(String.valueOf(i+1));

            double utilitiesSum = 0;
            AID aid = new AID(agentType + (i+1), AID.ISLOCALNAME);
            for (Task task : toDoAgentTasks.get(aid)) {
                if (hasEnoughResources(task, agentAvailableResources)) {
                    utilitiesSum += task.utility;
                }
            }
            array[i][1] = utilitiesSum;
            ResourceType[] resourceTypeValues = ResourceType.getValues();
            for (int j = 0; j < resourceTypeValues.length; j++) {
                if (agentAvailableResources.get(aid).containsKey(resourceTypeValues[j])) {
                    array[i][j+2] = agentAvailableResources.get(aid).get(resourceTypeValues[j]).size();
                } else {
                    array[i][j+2] = 0;
                }
            }
        }

        INDArray indArray = Nd4j.create(array);
        INDArray flattenedArray = indArray.ravel();
        INDArray reshapedInput = flattenedArray.reshape(1, (2 + ResourceType.getSize()) * numberOfAgents);
        Observation observation = new Observation(reshapedInput);

        MasterState masterState = new MasterState( tasksCopy, efficiencies, agentResourcesCopy, availableQuantities, currentAllocatedQuantity);
        masterState.deepObservation = observation;
        return masterState;
    }


    Set<AID> generatePossibleMasterActions (MasterState currentState) {

        Set<AID> managers = new HashSet<>();
        for (var agentTasks : toDoAgentTasks.entrySet()) {
            for (Task task : agentTasks.getValue()) {
                if (hasEnoughResources(task, agentAvailableResources)) {
                    managers.add(task.manager);
                    break;
                }
            }
        }
        return managers;
    }


    AID selectEplisonGreedyMasterAction (MasterState currentState, Set<AID> possibleManagers) {

        AID selectedManager = null;
        MasterStateAction masterStateAction;
        Random random = new Random();
        double r = random.nextDouble();
        Iterator<AID> iter1 = possibleManagers.iterator();
        if (r < epsilon) {
            //exploration: pick a random action from possible actions in this state
            int index = random.nextInt(possibleManagers.size());
            for (int i = 0; i < index; i++) {
                iter1.next();
            }
            selectedManager = iter1.next();
        } else {
            INDArray input = currentState.deepObservation.getData();
//            predict only returns one value, the best action. but it may not be a possible one.
//            int[] qValues = policyNetwork.predict(data);
            INDArray qValues = policyNetwork.output(input);
            double[] qVector = qValues.toDoubleVector();
            //exploitation: pick the best known action from possible actions in this state using Q table
            AID manager;
            Double highestQ = -Double.MAX_VALUE;
            for (int i = 0; i < qVector.length; i++) {
                manager = new AID(agentType + (i+1), AID.ISLOCALNAME);
                if (possibleManagers.contains(manager)) {
                    if (qVector[i] > highestQ) {
                        highestQ = qVector[i];
                        selectedManager = manager;
                    }
                }
            }
        }
        return selectedManager;
    }


    void updateMasterQFunction( MasterStateAction currentStateAction, MasterState nextState, double currentReward) {

//        StateActionRewardState stateActionRewardState = new StateActionRewardState(currentStateAction.state.deepObservation, currentStateAction.action, currentReward, false);
//        stateActionRewardState.setNextObservation(nextState.deepObservation);

        replayMemoryExperienceHandler.addExperience(currentStateAction.state.deepObservation, currentStateAction.action, currentReward, false);

        if (replayMemoryExperienceHandler.isTrainingBatchReady()) {

            List<StateActionRewardState> trainingBatch = replayMemoryExperienceHandler.generateTrainingBatch();

            // Define the input shape and label shape
            int inputSize = (2 + ResourceType.getSize()) * numberOfAgents; // Specify the size of the input data
            int numActions = numberOfAgents; // Specify the number of discrete actions
            int batchSize = trainingBatch.size();

            // Create INDArrays to hold input data and labels
            INDArray inputArray = Nd4j.create(batchSize, inputSize);
            INDArray labelArray = Nd4j.create(batchSize, numActions);

            // Fill in the input data and labels based on the training batch
            for (int i = 0; i < batchSize; i++) {
                StateActionRewardState<MasterAction> experience = trainingBatch.get(i);
                Observation observation = experience.getObservation();
                INDArray stateInput = observation.getData();
                inputArray.putRow(i, stateInput);

                MasterAction action = experience.getAction();
                double reward = experience.getReward();

                Observation nextObservation = experience.getNextObservation();
                INDArray nextStateInput = nextObservation.getData();

                INDArray targetValues = targetNetwork.output(stateInput);
                INDArray nextTargetValues = targetNetwork.output(nextStateInput);
                INDArray nextPolicyValues = policyNetwork.output(nextStateInput);
//                int[] bestNextPolicyAction = policyNetwork.predict(nextStateInput);

                int bestNextPolicyActionIndex = Nd4j.argMax(nextPolicyValues, 1).getInt(0);
                // Calculate the maximum Q-value from the next state
                double maxNextTargetValue = nextTargetValues.maxNumber().doubleValue();

                Set<AID> possibleNextManagers = generatePossibleMasterActions (nextState);

                int actionIndex = Integer.valueOf(action.selectedManager.getLocalName().replace(agentType, "")) - 1;
                if (possibleNextManagers.isEmpty()) {
                    targetValues.putScalar(0, actionIndex, reward);
                } else {
                    if (doubleLearning) {
                        targetValues.putScalar(0, actionIndex, reward + gamma * nextTargetValues.getDouble(bestNextPolicyActionIndex));
                    } else {
                        targetValues.putScalar(0, actionIndex, reward + gamma * maxNextTargetValue);
                    }
                }

                labelArray.putRow(i, targetValues);
            }

            policyNetwork.fit( inputArray, labelArray);
        }
    }


    double processTask (Task task) {

        // When No cascading, it only uses resources of the task manager and its direct neighbors, already checked in hasEnoughResources method.
        long allocatedQuantity;
        double transferCost = 0;
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
                transferCost += computeTransferCost(task.manager, selectedProvider);
                allocatedQuantity++;
            }
        }
        return transferCost;
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


    AID selectBestProvider(Set<AID> providers, ResourceType resourceType) {

        // should be deterministic
        //TODO: sort the providers by their workload + shortest distance from task manager

        Set<AID> potentialProviders = new HashSet<>();
        for (AID aid : providers) {
            if (agentAvailableResources.get(aid).containsKey(resourceType)) {
                if (agentAvailableResources.get(aid).get(resourceType).size() > 0) {
                    potentialProviders.add(aid);
                }
            }
        }

        AID selectedProvider = null;
        String selectedId;
        String id;
        int maxDegree = 0;
        int degree;
        for (AID aid : potentialProviders) {
            id = aid.getLocalName().replace(agentType, "");
            degree = graph.degreeOf(id);
            if (degree > maxDegree) {
                maxDegree = degree;
                selectedProvider = aid;
            } else if (degree == maxDegree) {
                selectedId = selectedProvider.getLocalName().replace(agentType, "");
                if (Integer.valueOf(id) < Integer.valueOf(selectedId)) {
                    selectedProvider = aid;
                }
            }
        }

//        int size = potentialProviders.size();
//        int item = new Random().nextInt(size);
//        int i = 0;
//        for(AID aid : potentialProviders) {
//            if (i == item) {
//                selectedProvider = aid;
//                break;
//            }
//            i++;
//        }

        return selectedProvider;
    }


    void allocateResource (AID selectedProvider, ResourceType resourceType) {

        ResourceItem item = agentAvailableResources.get(selectedProvider).get(resourceType).first();
        agentAvailableResources.get(selectedProvider).get(resourceType).remove((item));
    }


    double computeTransferCost(AID taskManager, AID provider) {

        String taskManagerName = taskManager.getLocalName();
        String taskManagerId = taskManagerName.replace(agentType, "");
        String providerName = provider.getLocalName();
        String providerId = providerName.replace(agentType, "");

        int i = Integer.valueOf(taskManagerId);
        int j = Integer.valueOf(providerId);
        double distance = 0;
        if (adjacency[i-1][j-1] == null) {
            try {
                distance = shortestPathAlgorithm.getPathWeight (taskManagerId, providerId);
            } catch (Exception e) {
                System.out.println("Exception: incurTransferCost " + e.getMessage());
            }
        } else {
            // when there is an edge, we consider it as the selected path even if it is not the shortest path
            distance = adjacency[i-1][j-1];
        }

        if( debugMode) {
//            logInf("transfer cost from " + provider.getLocalName() + " to " + taskManager.getLocalName() + " : " + distance);
            System.out.println("task manager: " + taskManagerId + " provider: " + providerId + " distance: " + distance);
        }

        if (distance > 1) {
            System.out.println();
            System.out.print("");
        }

        return distance;
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
                if(task.manager.getLocalName().contains("A8")) {
//                    System.out.println();
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
        for( int r=0; r<=numberOfRounds; r=r+1000) {
            sum = 0;
            for( var utilInfo: utilitiesInfo.entrySet()) {
                if (r==0) {
                    sum += utilInfo.getValue().get(r);
                } else {
                    sum += utilInfo.getValue().get(r) - utilInfo.getValue().get(r-1000);
                }
            }
            System.out.println("At round " + (r+1) + " : " + sum);
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


    protected void logInf(String msg) {

//      System.out.println("Time:" + System.currentTimeMillis() + " " + agentType + "0: " + msg);

        try {
            PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(logFileName, true)));
            out.println(System.currentTimeMillis() + " " + agentType + "0: " + msg);
            out.close();
        } catch (IOException e) {
            System.err.println("Error writing file..." + e.getMessage());
        }
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
