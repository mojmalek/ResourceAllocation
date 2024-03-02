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

    private String logFileMaster, resultFileCen, resultFileDec;
    private String agentType;
    private String trainedModelPath;

    private Map<AID, ArrayList<JSONObject>> tasksInfo = new LinkedHashMap<>();
    private Map<AID, ArrayList<JSONObject>> resourcesInfo = new LinkedHashMap<>();
    private Map<AID, ArrayList<Long>> utilitiesInfo = new LinkedHashMap<>();

    private Map<AID, List<Task>> toDoAgentTasks = new LinkedHashMap<>();
    private List<Task> toDoTasks = new ArrayList<>();
    private List<Task> doneTasks = new ArrayList<>();

    private long totalUtil, totalTransferCost;
    private int numberOfEpisodes, episode, numberOfAgents, maxTaskNumPerAgent, resourceTypesNum, maxResourceTypesNum, masterStateVectorSize;
    private int packageSize;

    Integer[][] adjacency;
    Graph<String, DefaultWeightedEdge> graph;
    ShortestPathAlgorithm shortestPathAlgorithm;

    private Map<AID, Map<ResourceType, SortedSet<ResourceItem>>> agentAvailableResources = new LinkedHashMap<>();
    private Map<AID, Map<ResourceType, SortedSet<ResourceItem>>> agentExpiredResources = new LinkedHashMap<>();

    private double epsilon = 1; // With a small probability of epsilon, we choose to explore, i.e., not to exploit what we have learned so far
    private double alpha = 0.1; // Learning rate
    private final double gamma = 0.95; // Discount factor - 0 looks in the near future, 1 looks in the distant future

    private double epsilonDecayRate;
    private final double minimumEpsilon = 0.1;
    private final double alphaDecayRate = 0.5;
    private final double minimumAlpha = 0.000001;

    private boolean cascading = true;
    private boolean learning = false;
    private boolean evaluating = false;
    private boolean loadTrainedModel = false;
    private boolean doubleLearning = true;

    private MultiLayerNetwork policyNetwork;
    private MultiLayerNetwork targetNetwork;
    private ReplayMemoryExperienceHandler replayMemoryExperienceHandler;
    private long stepCount;
    private long targetUpdateFreq = 200;

    private ReduceLROnPlateau scheduler;


    @Override
    protected void setup() {

        // Get ids of other agents as arguments
        Object[] args = getArguments();
        if (args != null && args.length > 0) {
            numberOfAgents = (int) args[0];
            numberOfEpisodes = (int) args[1];
            graph = (Graph) args[2];
            adjacency = (Integer[][]) args[3];
            logFileMaster = (String) args[4];
            resultFileCen = (String) args[5];
            resultFileDec = (String) args[6];
            agentType = (String) args[7];
            maxTaskNumPerAgent = (int) args[8];
            resourceTypesNum = (int) args[9];
            maxResourceTypesNum = (int) args[10];
            trainedModelPath = (String) args[11];
            packageSize = (int) args[12];
        }

        shortestPathAlgorithm = new DijkstraShortestPath(graph);

        for (int i = 1; i <= numberOfAgents; i++) {
            AID aid = new AID(agentType + i, AID.ISLOCALNAME);
            tasksInfo.put( aid, new ArrayList<>());
            resourcesInfo.put( aid, new ArrayList<>());
            utilitiesInfo.put( aid, new ArrayList<>());
            agentAvailableResources.put(aid, new LinkedHashMap<>());
            agentExpiredResources.put(aid, new LinkedHashMap<>());
            toDoAgentTasks.put(aid, new ArrayList<>());
        }

        if (learning) {
            if (loadTrainedModel) {
                alpha = 0.000001;
            }
//            masterStateVectorSize = 2 * numberOfAgents * maxTaskNumPerAgent + numberOfAgents * maxResourceTypesNum + numberOfAgents * maxTaskNumPerAgent * maxResourceTypesNum;
            masterStateVectorSize = numberOfAgents * maxTaskNumPerAgent + numberOfAgents * maxResourceTypesNum + numberOfAgents * maxTaskNumPerAgent * maxResourceTypesNum;
//            masterStateVectorSize = numberOfAgents * maxTaskNumPerAgent + numberOfAgents * maxResourceTypesNum;
            createNeuralNet();
            scheduler = new ReduceLROnPlateau(100, alphaDecayRate, minimumAlpha, Double.MAX_VALUE);
            if (numberOfEpisodes == 5000) {
                epsilonDecayRate = 0.9995;
            } else if (numberOfEpisodes == 10000) {
                epsilonDecayRate = 0.99965;
            } else if (numberOfEpisodes == 20000) {
                epsilonDecayRate = 0.99982;
            } else {
                //ToDo: set for more number of episodes
            }
        }

        if (evaluating) {
            epsilon = 0;
            createNeuralNet();
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
                    for (int r = 0; r < numberOfEpisodes; r++) {
                        episode = r + 1;
                        if (episode % 100 == 0) {
                            System.out.println(myAgent.getLocalName() + "  Episode: " + episode + "  Epsilon: " + epsilon);
//                            System.out.println("LearningRate: " + policyNetwork.getLearningRate(1));
                        }
                        for (var taskInfo : tasksInfo.entrySet() ) {
                            findNewTasks (taskInfo.getValue().get(r), taskInfo.getKey());
                        }
                        for (var resourceInfo : resourcesInfo.entrySet() ) {
                            findNewResources (resourceInfo.getValue().get(r), resourceInfo.getKey());
                        }
//                        performTasksOptimal( myAgent);
                        performTasksGreedy( myAgent);
//                        performTasksRL( myAgent);
                        expireResourceItems( myAgent);
                        expireTasks( myAgent);
                        if (learning) {
                            decayAlpha();
                            decayEpsilon();
                        }
                    }
                    if (learning) {
                        try {
                            policyNetwork.save(new File(trainedModelPath + "_master_" + ".zip"));
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }
//                    printUtils();
                    System.out.println ("Centralized total util for " + agentType + " : " + totalUtil);
//                    System.out.println ("Centralized total transferCost for " + agentType + " : " + totalTransferCost);
                    System.out.println ("Decentralized total util for " + agentType + " : " + agentUtilitiesSum());
                    System.out.println ("Percentage ratio for " + agentType + " : " + ((double) agentUtilitiesSum() / totalUtil * 100));
                    System.out.println ("");
                    logResultsCen( String.valueOf(totalUtil / numberOfEpisodes));
                    logResultsDec( String.valueOf(agentUtilitiesSum() / numberOfEpisodes));
                    block();
                    this.getAgent().doSuspend();
                }
            }
        });
    }


    private void decayEpsilon() {

        epsilon = Math.max(minimumEpsilon, epsilon * epsilonDecayRate);
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

        List<Task> newTasks = new ArrayList<>();
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

        List<Task> lateTasksInThisRound = new ArrayList<>();

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

        // unless it is needed to keep track of all done tasks in all rounds
        doneTasks.clear();
    }


    boolean receivedInfoFromAll() {

        for (var taskInfo : tasksInfo.entrySet() ) {
            if (taskInfo.getValue().size() < numberOfEpisodes) {
                return false;
            }
        }
        for (var taskInfo : resourcesInfo.entrySet() ) {
            if (taskInfo.getValue().size() < numberOfEpisodes) {
                return false;
            }
        }
        for (var taskInfo : utilitiesInfo.entrySet() ) {
            if (taskInfo.getValue().size() < numberOfEpisodes) {
                return false;
            }
        }
        return true;
    }


    private void performTasksOptimal(Agent myAgent) {

        // formulate as an ILP and solve using Gurobi

        System.out.println("======== Started performTasksOptimal =========");

        int numTasks = toDoTasks.size();
        ResourceType[] resourceTypeValues = ResourceType.getValues();

        long[] util = new long[numTasks];  // Utility for each task
        long[][] distance = new long[numberOfAgents][numTasks];  // Distance between each agent and task location
        long[][] requirement = new long[numTasks][resourceTypesNum];  // Resource requirement of each task for each resource type
        long[][] resource = new long[numberOfAgents][resourceTypesNum];  // Resources of each type available to each agent

        int j = 0;
        for (Task task : toDoTasks) {
            util[j] = task.utility;
            for (int i=0; i < numberOfAgents; i++) {
                AID potentialProvider = new AID(agentType + (i+1), AID.ISLOCALNAME);
                distance[i][j] = getDistance(task.manager, potentialProvider);
            }
            for (int k = 0; k < resourceTypesNum; k++) {
                requirement[j][k] = task.requiredResources.get(resourceTypeValues[k]);
            }
            j++;
        }

        for (int i = 0; i < numberOfAgents; i++) {
            AID aid = new AID(agentType + (i + 1), AID.ISLOCALNAME);
            for (int k = 0; k < resourceTypesNum; k++) {
                if (agentAvailableResources.get(aid).containsKey(resourceTypeValues[k])) {
                    resource[i][k] = agentAvailableResources.get(aid).get(resourceTypeValues[k]).size();
                }
            }
        }

        GurobiOptimizer optimizer = new GurobiOptimizer( numberOfAgents,  numTasks,  resourceTypesNum, packageSize, util, distance, requirement, resource);

        totalUtil += (long) optimizer.run();
        toDoTasks.clear();
    }


    private void performTasksGreedy(Agent myAgent) {

        // Centralized greedy algorithm: tasks are sorted by efficiency in toDoTasks

        Collections.sort(toDoTasks, new Comparator<Task>() {
            @Override
            public int compare(Task t1, Task t2) {
                return Double.compare(taskNetEfficiency(t2), taskNetEfficiency(t1));
            }
        });

        for (Task task : toDoTasks) {
            if (hasEnoughResources(task, agentAvailableResources)) {
                double transferCost = processTask(task);
                doneTasks.add(task);
                totalUtil += task.utility;
                totalUtil -= (long) transferCost;
                totalTransferCost += (long) transferCost;

//                System.out.println( "Round: " + round + " selected manager: " + task.manager.getLocalName() + " task util: " + task.utility + " transferCost: " + transferCost);
            }
        }

        toDoTasks.removeAll (doneTasks);

//        logInf( myAgent.getLocalName() + " has performed " + doneTasks.size() + " tasks and gained total utility of " + totalUtil);
    }


    private void performTasksRL(Agent myAgent) {

        // Centralized reinforcement learning

        // sort tasks of each agent
        for (var agentTasks : toDoAgentTasks.entrySet()) {
            Collections.sort(agentTasks.getValue(), new Comparator<Task>() {
                @Override
                public int compare(Task t1, Task t2) {
                    return Double.compare(taskNetEfficiency(t2), taskNetEfficiency(t1));
                }
            });
        }

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

//            System.out.println( "Round: " + round + " selected manager: " + selectedManager.getLocalName() + " task util: " + selectedTask.utility + " transferCost: " + transferCost);

            MasterAction action = new MasterAction( selectedTask, selectedManager);
            MasterStateAction currentStateAction = new MasterStateAction (currentState, action);

            if (learning) {
                updatePolicyNetwork(currentStateAction, reward);
                updateTargetNetwork();
            }
        }
    }


    void updateTargetNetwork() {

        // or update based on episodes
        stepCount += 1;
        if (stepCount % targetUpdateFreq == 0) {
            targetNetwork = policyNetwork;
        }
    }


    void createNeuralNet() {

        replayMemoryExperienceHandler = new ReplayMemoryExperienceHandler( new ExpReplay(100000, 32, new DefaultRandom()));

        if (loadTrainedModel) {
            try {
                policyNetwork = ModelSerializer.restoreMultiLayerNetwork(trainedModelPath + "_master_" + ".zip");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else {
            int outputNum = numberOfAgents;
            int hl1 = firstHiddenLayerSize(outputNum);
            // Hidden Layer 2: Around half of the previous hidden layer
            int hl2 = hl1 / 2;
//            int hl3 = hl2 / 2;
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


    int firstHiddenLayerSize(int outputNum) {
        // Hidden Layer 1: Approximately 2/3 of the input layer size + output layer size
        int hl1 = (int) (0.6 * masterStateVectorSize) + outputNum;
//        System.out.println(this.getLocalName() + " hl1: " + hl1);
        int quotient = hl1 / 10;
        int adjustedSize = quotient * 10 + 10;
//        System.out.println(this.getLocalName() + " adjustedSize: " + adjustedSize);
        return adjustedSize;
    }


    MasterState generateMasterState() {

        ResourceType[] resourceTypeValues = ResourceType.getValues();
//        INDArray tasksMatrix = Nd4j.zeros(numberOfAgents, maxTaskNumPerAgent);
        INDArray utilitiesMatrix = Nd4j.zeros(numberOfAgents, maxTaskNumPerAgent);
        INDArray requirementsTensor = Nd4j.zeros(numberOfAgents, maxTaskNumPerAgent, maxResourceTypesNum);
        INDArray resourcesMatrix = Nd4j.zeros(numberOfAgents, maxResourceTypesNum);

        for (int i = 0; i < numberOfAgents; i++) {
            AID aid = new AID(agentType + (i+1), AID.ISLOCALNAME);
            int j = 0;
            for (Task task : toDoAgentTasks.get(aid)) {
//                tasksMatrix.putScalar(i, j, 1);
                utilitiesMatrix.putScalar(i, j, task.utility);
                for (int k = 0; k < resourceTypesNum; k++) {
                    requirementsTensor.putScalar(i, j, k, task.requiredResources.get(resourceTypeValues[k]));
                }
                j++;
            }
            for (int k = 0; k < resourceTypesNum; k++) {
                if (agentAvailableResources.get(aid).containsKey(resourceTypeValues[k])) {
                    resourcesMatrix.putScalar(i, k, agentAvailableResources.get(aid).get(resourceTypeValues[k]).size());
                }
            }
        }

//        INDArray stateVector = Nd4j.hstack(tasksMatrix.ravel(), utilitiesMatrix.ravel(), requirementsTensor.ravel(), resourcesMatrix.ravel());
        INDArray stateVector = Nd4j.hstack(utilitiesMatrix.ravel(), requirementsTensor.ravel(), resourcesMatrix.ravel());
//        INDArray stateVector = Nd4j.hstack(utilitiesMatrix.ravel(), resourcesMatrix.ravel());
        INDArray reshapedInput = stateVector.reshape(1, stateVector.length());
        Observation observation = new Observation(reshapedInput);

        MasterState masterState = new MasterState( observation);
        return masterState;
    }


    Set<AID> generatePossibleMasterActions() {

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
        long allocatedQuantity, transferredQuantity;
        double transferCost = 0;
        Set<AID> providers;
        for (var requiredResource : task.requiredResources.entrySet()) {
            allocatedQuantity = 0;
            transferredQuantity = allocateResource (task.manager, requiredResource.getKey(), requiredResource.getValue(), allocatedQuantity, agentAvailableResources);
            allocatedQuantity += transferredQuantity;
            providers = new HashSet<>();
            providers.add(task.manager);
            while (allocatedQuantity < requiredResource.getValue()) {
                while (isMissingResource( providers, requiredResource.getKey(), agentAvailableResources)) {
                    providers = addNeighbors( providers);
                }
                AID selectedProvider = selectBestProvider( providers, requiredResource.getKey(), agentAvailableResources);
                transferredQuantity = allocateResource (selectedProvider, requiredResource.getKey(), requiredResource.getValue(), allocatedQuantity, agentAvailableResources);
                transferCost += computeTransferCost(task.manager, selectedProvider, transferredQuantity, requiredResource.getKey());
                allocatedQuantity += transferredQuantity;
            }
        }
        return transferCost;
    }


    double evaluateTask (Task task) {

        Map<AID, Map<ResourceType, SortedSet<ResourceItem>>> agentAvailableResourcesCopy = deepCopyAgentResourcesMap( agentAvailableResources);

        // used for sorting the tasks based on net efficiency
        long allocatedQuantity, transferredQuantity;
        double transferCost = 0;
        Set<AID> providers;
        for (var requiredResource : task.requiredResources.entrySet()) {
            allocatedQuantity = 0;
            transferredQuantity = allocateResource (task.manager, requiredResource.getKey(), requiredResource.getValue(), allocatedQuantity, agentAvailableResourcesCopy);
            allocatedQuantity += transferredQuantity;
            providers = new HashSet<>();
            providers.add(task.manager);
            while (allocatedQuantity < requiredResource.getValue()) {
                while (isMissingResource( providers, requiredResource.getKey(), agentAvailableResourcesCopy)) {
                    providers = addNeighbors( providers);
                }
                AID selectedProvider = selectBestProvider( providers, requiredResource.getKey(), agentAvailableResourcesCopy);
                transferredQuantity = allocateResource (selectedProvider, requiredResource.getKey(), requiredResource.getValue(), allocatedQuantity, agentAvailableResourcesCopy);
                transferCost += computeTransferCost(task.manager, selectedProvider, transferredQuantity, requiredResource.getKey());
                allocatedQuantity += transferredQuantity;
            }
        }
        return transferCost;
    }


    public double taskNetEfficiency(Task task) {
        //This is used to sort tasks in the greedy approach
        double netEfficiency;
        if (hasEnoughResources(task, agentAvailableResources)) {
            int count = 0;
            for (var resource: task.requiredResources.entrySet()) {
                count += resource.getValue();
            }
            double transferCost = evaluateTask( task);
            netEfficiency = (task.utility - transferCost) / count;
//            netEfficiency = task.utility - transferCost;
//            netEfficiency = (double) task.utility / count;
        } else {
            netEfficiency = -Double.MAX_VALUE;
        }

        return netEfficiency;
    }


    boolean isMissingResource( Set<AID> providers, ResourceType resourceType, Map<AID, Map<ResourceType, SortedSet<ResourceItem>>> agentAvailableResources) {
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


    AID selectBestProvider(Set<AID> providers, ResourceType resourceType, Map<AID, Map<ResourceType, SortedSet<ResourceItem>>> agentAvailableResources) {

        // should be deterministic
        //TODO: sort the providers by shortest distance from task manager

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


    long allocateResource (AID selectedProvider, ResourceType resourceType, long requiredQuantity, long allocatedQuantity, Map<AID, Map<ResourceType, SortedSet<ResourceItem>>> agentAvailableResources) {

        long availableQuantity = 0;
        if (agentAvailableResources.get(selectedProvider).containsKey(resourceType)) {
            availableQuantity = agentAvailableResources.get(selectedProvider).get(resourceType).size();
        }
        long transferredQuantity = Math.min(requiredQuantity - allocatedQuantity, availableQuantity);

        for (int i=0; i<transferredQuantity; i++) {
            ResourceItem item = agentAvailableResources.get(selectedProvider).get(resourceType).first();
            agentAvailableResources.get(selectedProvider).get(resourceType).remove((item));
        }

        return transferredQuantity;
    }


    double computeTransferCost(AID taskManager, AID provider, long quantity, ResourceType resourceType) {

//        if (learning) {
//            // only for ExpTransferCost
//            int[] packageSizes = new int[]{1, 2, 3, 4, 5};
//            int index = episode % packageSizes.length;
//            packageSize = packageSizes[index];
//        }

        double transferCost = 0;
        if (quantity > 0) {
            long distance = getDistance(taskManager, provider);

            long numberOfPackages = quantity / packageSize;
            if (quantity % packageSize > 0) {
                numberOfPackages += 1;
            }

            transferCost = distance * numberOfPackages;
//            transferCost = distance * 1;

            //TODO: define transfer cost per resource type
            if (resourceType == ResourceType.A) {
                transferCost = transferCost * 1;
            } else {
                transferCost = transferCost * 1;
            }
        }

        return transferCost;
    }


    long getDistance(AID taskManager, AID provider) {

        String taskManagerName = taskManager.getLocalName();
        String taskManagerId = taskManagerName.replace(agentType, "");
        String providerName = provider.getLocalName();
        String providerId = providerName.replace(agentType, "");

        int i = Integer.valueOf(taskManagerId);
        int j = Integer.valueOf(providerId);
        long distance = 0;
        if (adjacency[i-1][j-1] == null) {
            try {
                distance = (long) shortestPathAlgorithm.getPathWeight (taskManagerId, providerId);
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
            sum += utilInfo.getValue().get( numberOfEpisodes - 1);
        }
        return sum;
    }


    void printUtils() {
        long sum;
        for(int r = 0; r< numberOfEpisodes; r=r+100) {
            sum = 0;
            for( var utilInfo: utilitiesInfo.entrySet()) {
                if (r==0) {
                    sum += utilInfo.getValue().get(r);
                } else {
                    sum += utilInfo.getValue().get(r) - utilInfo.getValue().get(r-1);
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

//      System.out.println( agentType + "0: " + msg);

        try {
            PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(logFileMaster, true)));
            out.println( agentType + "0: " + msg);
            out.close();
        } catch (IOException e) {
            System.err.println("Error writing file..." + e.getMessage());
        }
    }


    protected void logErr(String msg) {

      System.out.println( agentType + "0: " + msg);

        try {
            PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(logFileMaster, true)));
            out.println( agentType + "0: " + msg);
            out.close();
        } catch (IOException e) {
            System.err.println("Error writing file..." + e.getMessage());
        }
    }


    protected void logResultsCen(String msg) {

//        System.out.println(msg);

        try {
            PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(resultFileCen, true)));
            out.println(msg);
            out.close();
        } catch (IOException e) {
            System.err.println("Error writing file..." + e.getMessage());
        }
    }


    protected void logResultsDec(String msg) {

//        System.out.println(msg);

        try {
            PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(resultFileDec, true)));
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
