package agents;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.core.behaviours.WakerBehaviour;
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


public class DeepRLTimedMasterAgent extends Agent {

    private String logFileName, resultFileName;
    private String agentType;

    private Map<AID, ArrayList<Long>> utilitiesInfo = new LinkedHashMap<>();

    private Map<AID, SortedSet<Task>> toDoAgentTasks = new LinkedHashMap<>();
    private SortedSet<Task> toDoTasks = new TreeSet<>(new Task.taskComparator());
    private SortedSet<Task> doneTasks = new TreeSet<>(new Task.taskComparator());

    private long totalUtil, totalTransferCost;
    private long endTime, currentTime;
    private int episode, numberOfAgents, maxTaskNumPerAgent, masterStateVectorSize;

    Integer[][] adjacency;
    Graph<String, DefaultWeightedEdge> graph;
    ShortestPathAlgorithm shortestPathAlgorithm;

    private Map<AID, Map<ResourceType, SortedSet<ResourceItem>>> agentAvailableResources = new LinkedHashMap<>();
    private Map<AID, Map<ResourceType, SortedSet<ResourceItem>>> agentExpiredResources = new LinkedHashMap<>();

    private double epsilon = 1; // With a small probability of epsilon, we choose to explore, i.e., not to exploit what we have learned so far
    private double alpha = 0.1; // Learning rate
    private final double gamma = 0.95; // Discount factor - 0 looks in the near future, 1 looks in the distant future

    // for 5k episodes
//    private final double epsilonDecayRate = 0.9995;
    // for 10k episodes
    private final double epsilonDecayRate = 0.99965;
    private final double minimumEpsilon = 0.1;
    private final double alphaDecayRate = 0.5;
    private final double minimumAlpha = 0.000001;

    private boolean cascading = true;
    private boolean doubleLearning = true;
    private boolean loadTrainedModel = false;

    private MultiLayerNetwork policyNetwork;
    private MultiLayerNetwork targetNetwork;
    private ReplayMemoryExperienceHandler replayMemoryExperienceHandler;
    private long C;
    private long targetUpdateFreq = 200;

    private ReduceLROnPlateau scheduler;


    @Override
    protected void setup() {

        Object[] args = getArguments();
        if (args != null && args.length > 0) {
            numberOfAgents = (int) args[0];
            endTime = (long) args[1];
            graph = (Graph) args[2];
            adjacency = (Integer[][]) args[3];
            logFileName = (String) args[4];
            resultFileName = (String) args[5];
            agentType = (String) args[6];
        }

        shortestPathAlgorithm = new DijkstraShortestPath(graph);

        for (int i = 1; i <= numberOfAgents; i++) {
            AID aid = new AID(agentType + i, AID.ISLOCALNAME);
            utilitiesInfo.put( aid, new ArrayList<>());
            agentAvailableResources.put(aid, new LinkedHashMap<>());
            agentExpiredResources.put(aid, new LinkedHashMap<>());
            toDoAgentTasks.put(aid, new TreeSet<>(new Task.taskComparator()));
        }

        //TODO: get as a param
        maxTaskNumPerAgent = 4;
        masterStateVectorSize = 2 * numberOfAgents * maxTaskNumPerAgent + numberOfAgents * ResourceType.getSize() + numberOfAgents * maxTaskNumPerAgent * ResourceType.getSize();
        createNeuralNet();

        scheduler = new ReduceLROnPlateau(100, alphaDecayRate, minimumAlpha, Double.MAX_VALUE);


        addBehaviour (new WakerBehaviour(this, new Date(endTime + 500)) {
            protected void onWake() {
                System.out.println ("Centralized total util for " + agentType + " : " + totalUtil);
//                System.out.println ("transfer cost: " + transferCost);
                System.out.println ("Decentralized total util for " + agentType + " : " + agentUtilitiesSum());
                System.out.println ("Percentage ratio for " + agentType + " : " + ((double) agentUtilitiesSum() / totalUtil * 100));
                System.out.println ("");
//                printUtils();
//                logResults( String.valueOf(agentUtilitiesSum()));

                try {
                    policyNetwork.save(new File("master_trained_model.zip"));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        } );


        addBehaviour (new TickerBehaviour(this, 1) {
            protected void onTick() {
                currentTime = System.currentTimeMillis();
                if (currentTime <= endTime) {
                    expireResourceItems (myAgent);
                    expireTasks (myAgent);
//                    performTasksOptimal( myAgent);
//                    performTasksGreedy( myAgent);
                    performTasksRL( myAgent);
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


    private void decayEpsilon() {

         epsilon = Math.max(minimumEpsilon, epsilon * epsilonDecayRate);
        // epsilon = 0;
    }


    private void decayAlpha() {

        // At the end of the episode
//        if (alpha > minimumAlpha && epsilon == minimumEpsilon) {
        if (alpha > minimumAlpha && epsilon < 0.5) {
            double currentScore = policyNetwork.score();
            alpha = scheduler.adjustLearningRate(currentScore, alpha);
            policyNetwork.setLearningRate(alpha);
        }
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
            newTask.agentType = agentType;
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
                ResourceItem item = new ResourceItem(id, ResourceType.valueOf(resourceType), lifetime, agentId);
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
        SortedSet<ResourceItem> expiredItemsNow = new TreeSet<>(new ResourceItem.resourceItemComparator());
        for (var agentResource : agentAvailableResources.entrySet()) {
            for (var resource : agentResource.getValue().entrySet()) {
                expiredItemsNow.clear();
                availableItems = agentAvailableResources.get(agentResource.getKey()).get(resource.getKey());
                if (agentExpiredResources.get(agentResource.getKey()).containsKey(resource.getKey())) {
                    expiredItems = agentExpiredResources.get(agentResource.getKey()).get(resource.getKey());
                } else {
                    expiredItems = new TreeSet<>(new ResourceItem.resourceItemComparator());
                    agentExpiredResources.get(agentResource.getKey()).put(resource.getKey(), expiredItems);
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
                    logErr("Error!!");
                }
            }
        }
    }


    void expireTasks(Agent myAgent) {

        SortedSet<Task> lateTasks = new TreeSet<>(new Task.taskComparator());

        for (var agentTasks : toDoAgentTasks.entrySet()) {
            lateTasks.clear();
            for (Task task : agentTasks.getValue()) {
                currentTime = System.currentTimeMillis();
                if (currentTime > task.deadline) {
                    lateTasks.add( task);
                }
            }
            agentTasks.getValue().removeAll( lateTasks);
            toDoTasks.removeAll( lateTasks);
        }

        // unless it is needed to keep track of all done tasks in all episodes
        doneTasks.clear();
    }


    private void performTasksOptimal(Agent myAgent) {

        // formulate as an ILP and solve using Gurobi

        System.out.println("======== Started performTasksOptimal =========");

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
            currentTime = System.currentTimeMillis();
            if (task.deadline - currentTime < 200) {
                if (currentTime <= task.deadline && hasEnoughResources(task, agentAvailableResources)) {
                    double transferCost = processTask(task);
                    doneTasks.add(task);
                    totalUtil += task.utility;
                    totalUtil -= (long) transferCost;
                    totalTransferCost += (long) transferCost;

                    System.out.println("Episode: " + episode + " selected manager: " + task.manager.getLocalName() + " task util: " + task.utility + " transferCost: " + transferCost);
                }
            }
        }

        toDoTasks.removeAll (doneTasks);

//        logInf( myAgent.getLocalName() + " has performed " + doneTasks.size() + " tasks and gained total utility of " + totalUtil);
    }


    private void performTasksRL(Agent myAgent) {

        // Centralized reinforcement learning

        while (toDoTasks.size() > 0) {
            Set<AID> possibleManagers = generatePossibleMasterActions();
            if (possibleManagers.size() == 0) {
                break;
            }
            MasterState currentState = generateMasterState();
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

            double reward = (double) selectedTask.utility - transferCost;

//            System.out.println( "Episode: " + episode + " selected manager: " + selectedManager.getLocalName() + " task util: " + selectedTask.utility + " transferCost: " + transferCost);

            MasterAction action = new MasterAction( selectedTask, selectedManager);

//            double transferCost;
//            double reward = 0;
//            for (Task task : toDoAgentTasks.get(selectedManager)) {
//                if (hasEnoughResources(task, agentAvailableResources)) {
//                    transferCost = processTask(task);
//                    doneTasks.add(task);
//                    totalUtil += task.utility;
//                    totalUtil -= (long) transferCost;
//                    totalTransferCost += (long) transferCost;
//                    reward += (double) task.utility;
//                    reward -= transferCost;
////                    System.out.println( "Round: " + round + " selected manager: " + selectedManager.getLocalName() + " task util: " + task.utility + " transferCost: " + transferCost);
//                }
//            }
//            toDoTasks.removeAll(doneTasks);
//            toDoAgentTasks.get(selectedManager).removeAll(doneTasks);
//            MasterAction action = new MasterAction( selectedManager);

            MasterStateAction currentStateAction = new MasterStateAction (currentState, action);

            updatePolicyNetwork(currentStateAction, reward);

            updateTargetNetwork();
        }
    }


    void updateTargetNetwork() {

        // or update based on episodes
        C += 1;
        if (C % targetUpdateFreq == 0) {
            targetNetwork = policyNetwork;
        }
    }


    void createNeuralNet() {

        replayMemoryExperienceHandler = new ReplayMemoryExperienceHandler( new ExpReplay(100000, 32, new DefaultRandom()));

        if (loadTrainedModel) {
            try {
                policyNetwork = ModelSerializer.restoreMultiLayerNetwork("master_trained_model.zip");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else {
            int outputNum = numberOfAgents;
            // Hidden Layer 1: Approximately 2/3 of the input layer size + output layer size
            int hl1 = (int) (0.6 * masterStateVectorSize) + outputNum;
            // Hidden Layer 2: Around half of the previous hidden layer
            int hl2 = hl1 / 2;
            MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder()
                    .seed(123)
                    .optimizationAlgo(OptimizationAlgorithm.STOCHASTIC_GRADIENT_DESCENT)
                    .activation(Activation.RELU)
                    .weightInit(WeightInit.XAVIER)
                    .updater(new Adam(alpha))
                    .l2(0.001)
                    .list()
                    .layer(new DenseLayer.Builder()
                            .nIn(masterStateVectorSize)
                            .nOut(hl1)
                            .activation(Activation.RELU)
                            .build())
                    .layer(new DenseLayer.Builder()
                            .nIn(hl1)
                            .nOut(hl2)
                            .activation(Activation.RELU)
                            .build())
//                    .layer(new DenseLayer.Builder()
//                            .nIn(hl2)
//                            .nOut(hl3)
//                            .activation(Activation.RELU)
//                            .build())
                    .layer( new OutputLayer.Builder(LossFunctions.LossFunction.MSE)
                            .nIn(hl2)
                            .nOut(outputNum)
                            .activation(Activation.IDENTITY)
                            .build())
                    .backpropType(BackpropType.Standard)
                    .build();

            policyNetwork = new MultiLayerNetwork(conf);
            policyNetwork.init();
        }

        targetNetwork = policyNetwork;
    }


    MasterState generateMasterState() {

        ResourceType[] resourceTypeValues = ResourceType.getValues();
        INDArray tasksMatrix = Nd4j.zeros(numberOfAgents, maxTaskNumPerAgent);
        INDArray utilitiesMatrix = Nd4j.zeros(numberOfAgents, maxTaskNumPerAgent);
        INDArray requirementsTensor = Nd4j.zeros(numberOfAgents, maxTaskNumPerAgent, resourceTypeValues.length);
        INDArray resourcesMatrix = Nd4j.zeros(numberOfAgents, resourceTypeValues.length);

        for (int i = 0; i < numberOfAgents; i++) {
            AID aid = new AID(agentType + (i+1), AID.ISLOCALNAME);
            int j = 0;
            for (Task task : toDoAgentTasks.get(aid)) {
                tasksMatrix.putScalar(i, j, 1);
                utilitiesMatrix.putScalar(i, j, task.utility);
                for (int k = 0; k < resourceTypeValues.length; k++) {
                    requirementsTensor.putScalar(i, j, k, task.requiredResources.get(resourceTypeValues[k]));
                }
                j++;
            }
            for (int k = 0; k < resourceTypeValues.length; k++) {
                if (agentAvailableResources.get(aid).containsKey(resourceTypeValues[k])) {
                    resourcesMatrix.putScalar(i, k, agentAvailableResources.get(aid).get(resourceTypeValues[k]).size());
                }
            }
        }

        INDArray stateVector = Nd4j.hstack(tasksMatrix.ravel(), utilitiesMatrix.ravel(), requirementsTensor.ravel(), resourcesMatrix.ravel());
        INDArray reshapedInput = stateVector.reshape(1, stateVector.length());
        Observation observation = new Observation(reshapedInput);

        MasterState masterState = new MasterState( observation);
        return masterState;
    }


    Set<AID> generatePossibleMasterActions() {

        Set<AID> managers = new HashSet<>();
        for (var agentTasks : toDoAgentTasks.entrySet()) {
            for (Task task : agentTasks.getValue()) {
                currentTime = System.currentTimeMillis();
                if (task.deadline - currentTime < 200) {
                    if (currentTime <= task.deadline && hasEnoughResources(task, agentAvailableResources)) {
                        managers.add(task.manager);
                        break;
                    }
                }
            }
        }
        return managers;
    }


    AID selectEplisonGreedyMasterAction (MasterState currentState, Set<AID> possibleManagers) {

        AID selectedManager = null;
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
            INDArray input = currentState.observation.getData();
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


    void updatePolicyNetwork(MasterStateAction currentStateAction, double currentReward) {

        Set<AID> possibleNextManagers = generatePossibleMasterActions();
        boolean isTerminal = false;
        if (possibleNextManagers.isEmpty()) {
            isTerminal = true;
        }

        replayMemoryExperienceHandler.addExperience(currentStateAction.state.observation, currentStateAction.action, currentReward, isTerminal);

        if (replayMemoryExperienceHandler.isTrainingBatchReady()) {

            List<StateActionRewardState> trainingBatch = replayMemoryExperienceHandler.generateTrainingBatch();
            int batchSize = trainingBatch.size();

            INDArray statesBatch = Nd4j.create(batchSize, masterStateVectorSize);
            INDArray nextStatesBatch = Nd4j.create(batchSize, masterStateVectorSize);

            // Fill the states and nextStates batch
            for (int i = 0; i < batchSize; i++) {
                statesBatch.putRow(i, trainingBatch.get(i).getObservation().getData());
                nextStatesBatch.putRow(i, trainingBatch.get(i).getNextObservation().getData());
            }

            // Batch forward pass
            INDArray currentQValuesBatch = policyNetwork.output(statesBatch);
            INDArray nextQValuesFromPolicyBatch = policyNetwork.output(nextStatesBatch);
            INDArray nextQValuesFromTargetBatch = targetNetwork.output(nextStatesBatch);

            // Batch updates to Q-values
            for (int i = 0; i < batchSize; i++) {
                StateActionRewardState<MasterAction> experience = trainingBatch.get(i);
                MasterAction action = experience.getAction();
                double reward = experience.getReward();
                int actionIndex = Integer.valueOf(action.selectedManager.getLocalName().replace(agentType, "")) - 1;
                if (experience.isTerminal()) {
                    currentQValuesBatch.putScalar(i, actionIndex, reward);
                } else {
                    if (doubleLearning) {
                        int bestNextPolicyActionIndex = Nd4j.argMax(nextQValuesFromPolicyBatch.getRow(i)).getInt(0);
                        currentQValuesBatch.putScalar(i, actionIndex, reward + gamma * nextQValuesFromTargetBatch.getDouble(i, bestNextPolicyActionIndex));
                    } else {
                        double maxNextTargetValue = nextQValuesFromTargetBatch.getRow(i).maxNumber().doubleValue();
                        currentQValuesBatch.putScalar(i, actionIndex, reward + gamma * maxNextTargetValue);
                    }
                }
            }

            policyNetwork.fit(statesBatch, currentQValuesBatch);
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

//        System.out.println("task manager: " + taskManagerId + " provider: " + providerId + " distance: " + distance);

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
            if (agentId.getLocalName().equals(agentType + "1")) {
                episode++;
                if (episode > 1) {
                    decayEpsilon();
                    decayAlpha();
                }
                System.out.println("LearningRate: " + policyNetwork.getLearningRate(1));
            }
            findNewTasks( joNewTasks, agentId);
        }

        if (joNewResources != null) {
            findNewResources( joNewResources, agentId);
        }

        if (totalUtil != null) {
            utilitiesInfo.get(agentId).add( totalUtil);
        }
    }


    long agentUtilitiesSum() {
        long sum = 0;
        for( var utilInfo: utilitiesInfo.entrySet()) {
            sum += utilInfo.getValue().get(episode - 1);
        }
        return sum;
    }


    void printUtils() {

        long sum;
        for( int i=0; i<episode; i=i+1000) {
            sum = 0;
            for( var utilInfo: utilitiesInfo.entrySet()) {
                if (i==0) {
                    sum += utilInfo.getValue().get(i);
                } else {
                    sum += utilInfo.getValue().get(i) - utilInfo.getValue().get(i-1000);
                }
            }
            System.out.println("At episode " + (i+1) + " : " + sum);
        }
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

/*
ToDO:
- how to deal with large action space ?


*/


}
