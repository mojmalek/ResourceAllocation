package agents;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.OneShotBehaviour;
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
import org.deeplearning4j.rl4j.experience.ReplayMemoryExperienceHandler;
import org.deeplearning4j.rl4j.experience.StateActionRewardState;
import org.deeplearning4j.rl4j.learning.sync.ExpReplay;
import org.deeplearning4j.rl4j.observation.Observation;
import org.deeplearning4j.util.ModelSerializer;
import org.json.simple.JSONArray;
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


public class DeepRLTimedSocialAdaptiveAgent extends Agent {

    TimedSimulationEngine simulationEngine;
    private boolean debugMode = true;
    private String logFileName, agentLogFileName;
    private String agentType;

    private Integer[] adjacency;
    private ArrayList<AID> neighbors = new ArrayList<>();

    private SortedSet<Task> toDoTasks = new TreeSet<>(new Task.taskComparator());
    private SortedSet<Task> blockedTasks = new TreeSet<>(new Task.taskComparator());
    private SortedSet<Task> doneTasks = new TreeSet<>(new Task.taskComparator());
    private long totalUtil;
    private long startTime, endTime;
    private long currentTime;
    private int numberOfAgents;
    private long maxRequestQuantity;
    private long offeringStateVectorSize, confirmingStateVectorSize;

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

    private long requestLifetime = 6000;
    private long minTimeToCascadeRequest = 200;
    private long minTimeToOffer = 200;
    private long requestTimeoutReduction = 30;
    private long offerTimeoutExtension = 200;
    private long waitUntilCascadeOffer = 100;
    private long waitUntilConfirm = 50;

//    private long requestLifetime = 5000;
//    private long minTimeToCascadeRequest = 0;
//    private long minTimeToOffer = 0;
//    private long requestTimeoutReduction = 10;
//    private long offerTimeoutExtension = 400;
//    private long waitUntilCascadeRequest = 0;
//    private long waitUntilOffer = 0;
//    private long waitUntilCascadeOffer = 0;
//    private long waitUntilConfirm = 0;

    private int errorCount;

    private double offeringEpsilon = 0; // With a small probability of epsilon, we choose to explore, i.e., not to exploit what we have learned so far
    private double offeringAlpha = 0.1; // Learning rate
    private final double offeringGamma = 0.95; // Discount factor - 0 looks in the near future, 1 looks in the distant future
    private double confirmingEpsilon = 0; // With a small probability of epsilon, we choose to explore, i.e., not to exploit what we have learned so far
    private double confirmingAlpha = 0.1; // Learning rate
    private final double confirmingGamma = 0.95; // Discount factor - 0 looks in the near future, 1 looks in the distant future

    // for 5k episodes
    private final double offeringEpsilonDecayRate = 0.9995;
    // for 10k episodes
//    private final double offeringEpsilonDecayRate = 0.99965;
    private final double offeringMinimumEpsilon = 0.1;
    private final double offeringAlphaDecayRate = 0.5;
    private final double offeringMinimumAlpha = 0.000001;
    // for 5k episodes
    private final double confirmingEpsilonDecayRate = 0.9995;
    // for 10k episodes
//    private final double confirmingEpsilonDecayRate = 0.99965;
    private final double confirmingMinimumEpsilon = 0.1;
    private final double confirmingAlphaDecayRate = 0.5;
    private final double confirmingMinimumAlpha = 0.000001;

    private boolean cascading;
    private boolean doubleLearning = true;
    private boolean loadTrainedModel = true;

    private MultiLayerNetwork offeringPolicyNetwork;
    private MultiLayerNetwork offeringTargetNetwork;
    private ReplayMemoryExperienceHandler offeringReplayMemoryExperienceHandler;
    private long offeringStepCount;
    private ReduceLROnPlateau offeringScheduler;
    private MultiLayerNetwork confirmingPolicyNetwork;
    private MultiLayerNetwork confirmingTargetNetwork;
    private ReplayMemoryExperienceHandler confirmingReplayMemoryExperienceHandler;
    private long confirmingStepCount;
    private ReduceLROnPlateau confirmingScheduler;
    private long targetUpdateFreq = 200;


    @Override
    protected void setup() {

        Object[] args = getArguments();
        if (args != null && args.length > 0) {
            numberOfAgents = (int) args[0];
            int myId = (int) args[1];
            startTime = (long) args[2];
            endTime = (long) args[3];
            adjacency = (Integer[]) args[4];
            logFileName = (String) args[5];
            simulationEngine = (TimedSimulationEngine) args[6];
            cascading = (boolean) args[7];
            agentType = (String) args[8];
        }

        for (int i = 0; i < adjacency.length; i++) {
            if (adjacency[i] != null) {
                AID aid = new AID(agentType + (i + 1), AID.ISLOCALNAME);
                neighbors.add( aid);
            }
        }

        for (ResourceType resourceType : ResourceType.getValues()) {
            availableResources.put( resourceType, new TreeSet<>(new ResourceItem.resourceItemComparator()));
            expiredResources.put( resourceType, new TreeSet<>(new ResourceItem.resourceItemComparator()));
        }

        agentLogFileName = "logs/" + this.getLocalName() + "-" + new Date() + ".txt";

        //TODO: get as a param
        maxRequestQuantity = 10;
        offeringStateVectorSize = 2 + neighbors.size() * maxRequestQuantity;
        confirmingStateVectorSize = 1 + maxRequestQuantity + neighbors.size() * maxRequestQuantity;
        createOfferingNeuralNet();
        createConfirmingNeuralNet();

        offeringScheduler = new ReduceLROnPlateau(100, offeringAlphaDecayRate, offeringMinimumAlpha, Double.MAX_VALUE);
        confirmingScheduler = new ReduceLROnPlateau(100, confirmingAlphaDecayRate, confirmingMinimumAlpha, Double.MAX_VALUE);


        addBehaviour (new WakerBehaviour(this, new Date(startTime + 1000)) {
            protected void onWake() {
                if(cascading) {
                    currentTime = System.currentTimeMillis();
                    while (currentTime < startTime + 1600) {
                        currentTime = System.currentTimeMillis();
                        deliberateOnCascadingRequest(myAgent);
                    }
                }
            }
        });

        addBehaviour (new WakerBehaviour(this, new Date(startTime + 2000)) {
            protected void onWake() {
//                deliberateOnOfferingGreedy( myAgent);
                deliberateOnOfferingRL( myAgent);
            }
        });

        addBehaviour (new WakerBehaviour(this, new Date(startTime + 3000)) {
            protected void onWake() {
                if(cascading) {
                    currentTime = System.currentTimeMillis();
                    while (currentTime < startTime + 3600) {
                        currentTime = System.currentTimeMillis();
                        deliberateOnCascadingOffers(myAgent);
                    }
                }
            }
        });

        addBehaviour (new WakerBehaviour(this, new Date(startTime + 4000)) {
            protected void onWake() {
//                deliberateOnConfirmingGreedy( myAgent);
                deliberateOnConfirmingRL( myAgent);
            }
        });


        addBehaviour (new WakerBehaviour(this, new Date(startTime + 5000)) {
            protected void onWake() {

                performTasks(myAgent);
                sendTotalUtilToMasterAgent(totalUtil, myAgent);

                try {
                    offeringPolicyNetwork.save(new File("trained_models/offering_" + myAgent.getLocalName() + ".zip"));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                try {
                    confirmingPolicyNetwork.save(new File("trained_models/confirming_" + myAgent.getLocalName() + ".zip"));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
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
//                if( myAgent.getLocalName().contains("4")) {
//                    System.out.println("OneShotBehaviour");
//                }
                findTasks(myAgent);
                findResources(myAgent);
                deliberateOnRequesting (myAgent);
            }
        });


//        addBehaviour(new CyclicBehaviour() {
//            @Override
//            public void action() {
////        addBehaviour (new TickerBehaviour(this, 1) {
////            protected void onTick() {
//                currentTime = System.currentTimeMillis();
////                if (currentTime <= endTime - 5000) {
////                    if (getTickCount() == 5000 || getTickCount() == 10000) {
//                    if (currentTime == startTime + 5000 || currentTime == startTime + 10000) {
//                        performTasks(myAgent);
//                        sendTotalUtilToMasterAgent(totalUtil, myAgent);
//                        resetRound();
//                        decayEpsilon();
//                        decayAlpha();
//                        expireTasks( myAgent);
//                        expireResourceItems( myAgent);
//                        findTasks(myAgent);
//                        findResources(myAgent);
//                        deliberateOnRequesting (myAgent);
//                    }
//
////                    if (getTickCount() == 1000 || getTickCount() == 6000 || getTickCount() == 11000) {
//                    if (currentTime == startTime + 1000 || currentTime == startTime + 6000 || currentTime == startTime + 11000) {
//                        if(cascading) {
//                            deliberateOnCascadingRequest(myAgent);
//                        }
//                    }
//
////                    if (getTickCount() == 2000 || getTickCount() == 7000 || getTickCount() == 12000) {
//                    if (currentTime == startTime + 2000 || currentTime == startTime + 7000 || currentTime == startTime + 12000) {
//                        deliberateOnOfferingGreedy( myAgent);
////                    deliberateOnOfferingRL( myAgent);
//                    }
//
////                    if (getTickCount() == 3000 || getTickCount() == 8000 || getTickCount() == 13000) {
//                    if (currentTime == startTime + 3000 || currentTime == startTime + 8000 || currentTime == startTime + 13000) {
//                        if(cascading) {
//                            deliberateOnCascadingOffers( myAgent);
//                        }
//                    }
//
////                    if (getTickCount() == 4000 || getTickCount() == 9000 || getTickCount() == 14000) {
//                    if (currentTime == startTime + 4000 || currentTime == startTime + 9000 || currentTime == startTime + 14000) {
//                        deliberateOnConfirmingGreedy( myAgent);
////                    deliberateOnConfirmingRL( myAgent);
//                    }
////                }
//            }
//        });


//        addBehaviour (new TickerBehaviour(this, 1000) {
//            protected void onTick() {
//                currentTime = System.currentTimeMillis();
//                if (currentTime <= endTime - 5000) {
//                    if( myAgent.getLocalName().contains("4")) {
//                        System.out.println(getTickCount());
//                    }
//                    performTasks(myAgent);
//                    sendTotalUtilToMasterAgent(totalUtil, myAgent);
//                    resetRound();
//                    decayEpsilon();
//                    decayAlpha();
//                    expireTasks( myAgent);
//                    expireResourceItems( myAgent);
//                    findTasks(myAgent);
//                    findResources(myAgent);
//                    deliberateOnRequesting (myAgent);
//                    myAgent.doWait(1000);
//                    if(cascading) {
//                        deliberateOnCascadingRequest(myAgent);
//                    }
//                    myAgent.doWait(1000);
//                    deliberateOnOfferingGreedy( myAgent);
////                    deliberateOnOfferingRL( myAgent);
//                    myAgent.doWait(1000);
//                    if(cascading) {
//                        deliberateOnCascadingOffers( myAgent);
//                    }
//                    myAgent.doWait(1000);
//                    deliberateOnConfirmingGreedy( myAgent);
////                    deliberateOnConfirmingRL( myAgent);
//                }
//            }
//        });


//        addBehaviour (new TickerBehaviour(this, 1000) {
//            protected void onTick() {
//                currentTime = System.currentTimeMillis();
//                if (currentTime <= endTime - 2000) {
////                    System.out.println(getTickCount());
////                    decayEpsilon();
////                    decayAlpha();
//                    findTasks(myAgent);
//                    findResources(myAgent);
//                }
//            }
//        });


//        addBehaviour (new TickerBehaviour(this, 1) {
//            protected void onTick() {
//                currentTime = System.currentTimeMillis();
//                if (currentTime <= endTime) {
//                    negotiate(myAgent);
//                    expireResourceItems( myAgent);
//                    expireTasks( myAgent);
//                    performTasks(myAgent);
//                }
//            }
//        });


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


    void createOfferingNeuralNet() {

        offeringReplayMemoryExperienceHandler = new ReplayMemoryExperienceHandler( new ExpReplay(100000, 32, new DefaultRandom()));

        if (loadTrainedModel) {
            try {
                offeringPolicyNetwork = ModelSerializer.restoreMultiLayerNetwork("trained_models/offering_" + this.getLocalName() + ".zip");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else {
            int outputNum = (int) (neighbors.size() * maxRequestQuantity);
            // Hidden Layer 1: Approximately 2/3 of the input layer size + output layer size
            int hl1 = (int) (0.6 * offeringStateVectorSize) + outputNum;
            hl1 = 40;
            // Hidden Layer 2: Around half of the previous hidden layer
            int hl2 = hl1 / 2;
            hl2 = 20;
            MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder()
                    .seed(123)
                    .optimizationAlgo(OptimizationAlgorithm.STOCHASTIC_GRADIENT_DESCENT)
                    .activation(Activation.RELU)
                    .weightInit(WeightInit.XAVIER)
                    .updater(new Adam(offeringAlpha))
                    .l2(0.001)
                    .list()
                    .layer(new DenseLayer.Builder()
                            .nIn(offeringStateVectorSize)
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

            offeringPolicyNetwork = new MultiLayerNetwork(conf);
            offeringPolicyNetwork.init();
        }

        offeringTargetNetwork = offeringPolicyNetwork;
    }


    void createConfirmingNeuralNet() {

        confirmingReplayMemoryExperienceHandler = new ReplayMemoryExperienceHandler( new ExpReplay(100000, 32, new DefaultRandom()));

        if (loadTrainedModel) {
            try {
                confirmingPolicyNetwork = ModelSerializer.restoreMultiLayerNetwork("trained_models/confirming_" + this.getLocalName() + ".zip");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else {
            int outputNum = (int) (neighbors.size() * maxRequestQuantity);
            // Hidden Layer 1: Approximately 2/3 of the input layer size + output layer size
            int hl1 = (int) (0.6 * confirmingStateVectorSize) + outputNum;
            // Hidden Layer 2: Around half of the previous hidden layer
            int hl2 = hl1 / 2;
            MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder()
                    .seed(123)
                    .optimizationAlgo(OptimizationAlgorithm.STOCHASTIC_GRADIENT_DESCENT)
                    .activation(Activation.RELU)
                    .weightInit(WeightInit.XAVIER)
                    .updater(new Adam(confirmingAlpha))
                    .l2(0.001)
                    .list()
                    .layer(new DenseLayer.Builder()
                            .nIn(confirmingStateVectorSize)
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

            confirmingPolicyNetwork = new MultiLayerNetwork(conf);
            confirmingPolicyNetwork.init();
        }

        confirmingTargetNetwork = confirmingPolicyNetwork;
    }


    private void decayEpsilon() {

        offeringEpsilon = Math.max(offeringMinimumEpsilon, offeringEpsilon * offeringEpsilonDecayRate);
        confirmingEpsilon = Math.max(confirmingMinimumEpsilon, confirmingEpsilon * confirmingEpsilonDecayRate);
    }


    private void decayAlpha() {

        // At the end of the episode

//        if (offeringAlpha > offeringMinimumAlpha && offeringEpsilon == offeringMinimumEpsilon) {
        if (offeringAlpha > offeringMinimumAlpha && offeringEpsilon < 0.5) {
            double currentScore = offeringPolicyNetwork.score();
            offeringAlpha = offeringScheduler.adjustLearningRate(currentScore, offeringAlpha);
            offeringPolicyNetwork.setLearningRate(offeringAlpha);
        }

//        if (confirmingAlpha > confirmingMinimumAlpha && confirmingEpsilon == confirmingMinimumEpsilon) {
        if (confirmingAlpha > confirmingMinimumAlpha && confirmingEpsilon < 0.5) {
            double currentScore = confirmingPolicyNetwork.score();
            confirmingAlpha = confirmingScheduler.adjustLearningRate(currentScore, confirmingAlpha);
            confirmingPolicyNetwork.setLearningRate(confirmingAlpha);
        }
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
        deliberateOnOfferingGreedy( myAgent);
//        deliberateOnOfferingRL( myAgent);
        if(cascading) {
            deliberateOnCascadingOffers( myAgent);
        }
        deliberateOnConfirmingGreedy( myAgent);
//        deliberateOnConfirmingRL( myAgent);
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


    void resetRound() {

//        blockedTasks.clear();
        receivedRequests.clear();
        sentRequests.clear();
        receivedOffers.clear();
        sentOffers.clear();
    }


    private void performTasks(Agent myAgent) {

//        if( myAgent.getLocalName().contains("4")) {
//            System.out.println("===Performing Tasks===");
//        }

        boolean performed = false;
        int count = 0;
        SortedSet<Task> doneTasksNow = new TreeSet<>(new Task.taskComparator());
        // Greedy algorithm: tasks are sorted by utility in toDoTasks
        currentTime = System.currentTimeMillis();
        for (Task task : toDoTasks) {
//            if (task.deadline - currentTime < 200) {
//                if (currentTime <= task.deadline && hasEnoughResources(task, availableResources)) {
                if (hasEnoughResources(task, availableResources)) {
                    processTask(task);
                    doneTasksNow.add(task);
                    boolean check = doneTasks.add(task);
                    if (check == false) {
                        logErr(this.getLocalName(), "in performTasks");
                    }
                    totalUtil = totalUtil + task.utility;
                    count += 1;
                    performed = true;
                }
//            }
        }

        if (doneTasksNow.size() != count) {
            logErr(this.getLocalName(), "in performTasks");
        }

        int initialSize = toDoTasks.size();

        toDoTasks.removeAll (doneTasksNow);

        if ( initialSize - count != toDoTasks.size()) {
             logErr(this.getLocalName(), "in performTasks");
        }

        if (debugMode) {
//            System.out.println(myAgent.getLocalName() + " has performed " + doneTasks.size() + " tasks and gained total utility of " + totalUtil);
        }

//        if (performed == true) {
//            sendTotalUtilToMasterAgent(totalUtil, myAgent);
//        }
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
                for (int i = 0; i < adjacency.length; i++) {
                    if (adjacency[i] != null) {
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

        String requesterName = requester.getLocalName();
        int requesterId = Integer.valueOf(requesterName.replace(agentType, ""));
        int distance = adjacency[requesterId-1];

        long cost;
        Map<Long, Long> offerCostFunction = new LinkedHashMap<>();
        for (long q=1; q<=offerQuantity; q++) {
            cost = utilityOfResources( resourceType, availableQuantity) - utilityOfResources( resourceType, availableQuantity - q);
            cost += distance * q;
            offerCostFunction.put(q, cost);
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

//                    if( this.getLocalName().contains("4")) {
//                        System.out.println( "CascadingRequest - requests.size() for " + requestsForType.getKey() + " : " + copyOfRequests.size());
//                    }

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

//        return true;
    }


    private void deliberateOnOfferingGreedy(Agent myAgent) {

        long offerQuantity;
        for (var requestsForType : receivedRequests.entrySet()) {
            ArrayList<Request> requests = requestsForType.getValue();
            ArrayList<Request> copyOfRequests = new ArrayList<>(requests);
            if (availableResources.get(requestsForType.getKey()) != null) {
                long availableQuantity = availableResources.get(requestsForType.getKey()).size();
                while (availableQuantity > 0 && copyOfRequests.size() > 0) {
                    // Greedy approach

//                    if( this.getLocalName().contains("4")) {
//                        System.out.println( "Offering - requests.size() for " + requestsForType.getKey() + " : " + copyOfRequests.size());
//                    }

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
                            createOffer(selectedRequest.id, selectedRequest.originalId, myAgent.getAID(), selectedRequest.sender, selectedRequest.resourceType, offerQuantity, costFunction, availableResources.get(selectedRequest.resourceType), selectedRequest.originalTimeout, null, null);
                            availableQuantity = availableQuantity - offerQuantity;
                            requests.remove( selectedRequest);
                        }
                    }
                    copyOfRequests.remove( selectedRequest);
                }
            }
        }
    }


    private void deliberateOnOfferingRL(Agent myAgent) {

        for (var requestsForType : receivedRequests.entrySet()) {
            ResourceType resourceType = requestsForType.getKey();
            if (availableResources.get(resourceType) != null) {
                long availableQuantity = availableResources.get(resourceType).size();
                ArrayList<Request> requests = requestsForType.getValue();
                ArrayList<Request> copyOfRequests = new ArrayList<>(requests);
                while (availableQuantity > 0 && copyOfRequests.size() > 0) {
                    // RL approach
                    OfferingState currentState = generateOfferingState (resourceType, copyOfRequests, availableQuantity);

                    if (currentState.possibleActions.size() == 0) {
                        break;
                    }

                    // Choose action from state using epsilon-greedy policy derived from Q
                    OfferingAction action =  selectEpsilonGreedyOfferingAction(currentState);

                    OfferingStateAction currentStateAction = new OfferingStateAction (currentState, action);
                    Map<Long, Long> costFunction = computeOfferCostFunction(resourceType, availableQuantity, action.offerQuantity, action.selectedRequest.sender);
                    action.offerCostFunction = costFunction;
                    availableQuantity -= action.offerQuantity;
                    copyOfRequests.remove( action.selectedRequest);

                    OfferingState nextState = generateOfferingState (resourceType, copyOfRequests, availableQuantity);

                    createOffer(action.selectedRequest.id, action.selectedRequest.originalId, myAgent.getAID(), action.selectedRequest.sender, resourceType, action.offerQuantity, costFunction, availableResources.get(resourceType), action.selectedRequest.originalTimeout, currentStateAction, nextState);
                }
            }
        }
    }


    OfferingState generateOfferingState (ResourceType resourceType, ArrayList<Request> requests, long availableQuantity) {

        Set<OfferingAction> possibleActions = new HashSet<>();

        INDArray resourceVector = Nd4j.zeros( 2);
        resourceVector.putScalar(0, resourceType.ordinal());
        resourceVector.putScalar(1, availableQuantity);

        INDArray netUtilsMatrix = Nd4j.zeros(neighbors.size(), maxRequestQuantity);
        for (int i = 0; i < neighbors.size(); i++) {
            AID aid = neighbors.get(i);
            for (Request request : requests) {
                currentTime = System.currentTimeMillis();
                if (currentTime < request.timeout - minTimeToOffer) {
                    if (request.sender.equals(aid)) {
                        long cost;
                        for (long q = 1; q <= request.utilityFunction.size(); q++) {
                            if (request.utilityFunction.get(q) == null) {
                                System.out.println();
                            }
                            long util = request.utilityFunction.get(q);
                            if (q <= availableQuantity) {
                                cost = utilityOfResources(resourceType, availableQuantity) - utilityOfResources(resourceType, availableQuantity - q);
                                if (cost < util) {
                                    netUtilsMatrix.putScalar(i, q - 1, util - cost);
                                    possibleActions.add(new OfferingAction(request.resourceType, request, q));
                                }
                            }
                        }
                        break;
                    }
                }
            }
        }

        INDArray stateVector = Nd4j.hstack(resourceVector.ravel(), netUtilsMatrix.ravel());
        INDArray reshapedInput = stateVector.reshape(1, stateVector.length());
        Observation observation = new Observation(reshapedInput);
        OfferingState offeringState = new OfferingState(resourceType, requests, observation, possibleActions);
        return offeringState;
    }


    ConfirmingState generateConfirmingState (Request request, Set<Offer> offers, long remainingRequestedQuantity) {

        Set<ConfirmingAction> possibleActions = new HashSet<>();

        INDArray resourceVector = Nd4j.zeros( 1);
        resourceVector.putScalar(0, request.resourceType.ordinal());

        INDArray requestVector = Nd4j.zeros( maxRequestQuantity);
        for (long q = 1; q <= request.quantity; q++) {
            requestVector.putScalar(q - 1, request.utilityFunction.get(q));
        }

        INDArray offerCostsMatrix = Nd4j.zeros(neighbors.size(), maxRequestQuantity);
        for (int i = 0; i < neighbors.size(); i++) {
            AID aid = neighbors.get(i);
            for (Offer offer : offers) {
                if (offer.sender.equals(aid)) {
                    for (long q = 1; q <= offer.quantity; q++) {
                        if (q <= remainingRequestedQuantity) {
                            long util = request.utilityFunction.get(q);
                            long cost = offer.costFunction.get(q);
                            if (util > cost) {
                                offerCostsMatrix.putScalar(i, q - 1, cost);
                                possibleActions.add(new ConfirmingAction(request.resourceType, offer, q));
                            }
                        }
                    }
                    break;
                }
            }
        }

        INDArray stateVector = Nd4j.hstack(resourceVector.ravel(), requestVector.ravel(), offerCostsMatrix.ravel());
        INDArray reshapedInput = stateVector.reshape(1, stateVector.length());
        Observation observation = new Observation(reshapedInput);
        ConfirmingState confirmingState = new ConfirmingState(request.resourceType, offers, observation, possibleActions);
        return confirmingState;
    }


    OfferingAction selectEpsilonGreedyOfferingAction(OfferingState state) {

//        if (this.getLocalName().contains("9") && state.requests.size() == 1) {
//            System.out.println();
//        }

        OfferingAction selectedAction = null;
        Random random = new Random();
        double r = random.nextDouble();
        Iterator<OfferingAction> iter1 = state.possibleActions.iterator();
        if (r < offeringEpsilon) {
            //exploration: pick a random action from possible actions in this state
            int index = random.nextInt(state.possibleActions.size());
            for (int i = 0; i < index; i++) {
                iter1.next();
            }
            selectedAction = iter1.next();
        } else {
            INDArray input = state.observation.getData();
//            predict only returns one value, the best action. but it may not be a possible one.
//            int[] qValues = policyNetwork.predict(data);
            INDArray qValues = offeringPolicyNetwork.output(input);
            double[] qVector = qValues.toDoubleVector();
            //exploitation: pick the best known action from possible actions in this state using Q table
            OfferingAction action;
            Double highestQ = -Double.MAX_VALUE;
            //TODO: loop over possible actions instead of qVector
            for (int i = 0; i < qVector.length; i++) {
                int q = (int) ((i+1) % maxRequestQuantity);
                int neighborIndex = (int) (i / maxRequestQuantity);
//                if (neighborIndex == neighbors.size()) {
//                    System.out.println();
//                }
                AID aid = neighbors.get(neighborIndex);
                Request selectedRequest = null;
                for (Request request : state.requests) {
                    if (request.sender.equals(aid)) {
                        selectedRequest = request;
                        break;
                    }
                }
                if (selectedRequest != null) {
                    action = new OfferingAction(state.resourceType, selectedRequest, q);
                    if (state.possibleActions.contains(action)) {
                        if (qVector[i] > highestQ) {
                            highestQ = qVector[i];
                            selectedAction = action;
                        }
                    }
                }
            }
        }
//        if (selectedAction == null) {
//            System.out.println();
//        }
        return selectedAction;
    }


    ConfirmingAction selectEpsilonGreedyConfirmingAction(ConfirmingState state) {

        ConfirmingAction selectedAction = null;
        Random random = new Random();
        double r = random.nextDouble();
        Iterator<ConfirmingAction> iter1 = state.possibleActions.iterator();
        if (r < confirmingEpsilon) {
            //exploration: pick a random action from possible actions in this state
            int index = random.nextInt(state.possibleActions.size());
            for (int i = 0; i < index; i++) {
                iter1.next();
            }
            selectedAction = iter1.next();
        } else {
            INDArray input = state.observation.getData();
//            predict only returns one value, the best action. but it may not be a possible one.
//            int[] qValues = policyNetwork.predict(data);
            INDArray qValues = confirmingPolicyNetwork.output(input);
            double[] qVector = qValues.toDoubleVector();
            //exploitation: pick the best known action from possible actions in this state using Q table
            ConfirmingAction action;
            Double highestQ = -Double.MAX_VALUE;
            //TODO: loop over possible actions instead of qVector
            for (int i = 0; i < qVector.length; i++) {
                int q = (int) ((i+1) % maxRequestQuantity);
                int neighborIndex = (int) (i / maxRequestQuantity);
                AID aid = neighbors.get(neighborIndex);
                Offer selectedOffer = null;
//                if (state.offers == null) {
//                    System.out.println();
//                }
                for (Offer offer : state.offers) {
                    if (offer.sender.equals(aid)) {
                        selectedOffer = offer;
                        break;
                    }
                }
                if (selectedOffer != null) {
                    action = new ConfirmingAction(state.resourceType, selectedOffer, q);
                    if (state.possibleActions.contains(action)) {
                        if (qVector[i] > highestQ) {
                            highestQ = qVector[i];
                            selectedAction = action;
                        }
                    }
                }
            }
        }
//        if (selectedAction == null) {
//            System.out.println();
//        }
        return selectedAction;
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

        currentTime = System.currentTimeMillis();
        sendRequest(reqId, request.originalId, request.resourceType, missingQuantity, utilityFunction, allReceivers, receiverIds, currentTime, newTimeout, request.originalTimeout, request.originalSender);
        sentRequests.put(reqId, new Request(reqId, request.id, request.originalId, true, missingQuantity, request.resourceType, request.utilityFunction, request.sender, request.originalSender, allReceivers, reservedItems, currentTime, newTimeout, request.originalTimeout));
    }


    Set<AID> findNeighborsToCascadeRequest( Request request) {

        Set<AID> receiverIds = new HashSet<>();
        for (int i = 0; i < adjacency.length; i++) {
            AID aid = new AID(agentType+(i+1), AID.ISLOCALNAME);
            if (adjacency[i] != null && !request.allReceivers.contains(i+1) && !request.sender.equals(aid)) {
                receiverIds.add(aid);
            }
        }

        return receiverIds;
    }


    Request selectBestRequest(ArrayList<Request> requests, long remainingQuantity) {

        // should be deterministic
        // select the request with the highest efficiency
        // the request efficiency is defined as the ratio between its utility and requested quantity.

        Request selectedRequest = requests.get(0);
        double highestEfficiency = 0;
        long offerQuantity;
        String selectedId;
        String id;

        for (Request request : requests) {
            id = request.sender.getLocalName().replace(agentType, "");
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
            } else if (efficiency == highestEfficiency) {
                selectedId = selectedRequest.sender.getLocalName().replace(agentType, "");
                if (Integer.valueOf(id) < Integer.valueOf(selectedId)) {
                    selectedRequest = request;
                }
            }
        }

//        if( this.getLocalName().contains("4")) {
//            System.out.println("selectedRequest: " + selectedRequest.sender.getLocalName());
//        }

        return selectedRequest;
    }


    private void createOffer(String reqId, String originalReqId, AID offerer, AID requester, ResourceType resourceType, long offerQuantity, Map<Long, Long> costFunction, SortedSet<ResourceItem> availableItems, long originReqTimeout, OfferingStateAction currentStateAction, OfferingState nextState) {

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
        Offer offer = new Offer(offerId, reqId, originalReqId, false, offerQuantity, resourceType, costFunction, offeredItems, offeredItems, offerer, requester, null, offerTimeout);
        offer.currentStateAction = currentStateAction;
        offer.nextState = nextState;
        sentOffers.put( offerId, offer);
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
//        if (sentRequests.keySet().contains(reqId) == true) {
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
//                if (currentTime < request.getValue().timeout && request.getValue().timeout - currentTime < waitUntilCascadeOffer) {
                if (currentTime < request.getValue().timeout) {
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
            String requesterName = cascadedRequest.sender.getLocalName();
            int requesterId = Integer.valueOf(requesterName.replace(agentType, ""));
            int distance = adjacency[requesterId-1];
            long minCost, cost;
            Offer lowCostOffer;
            String selectedId;
            String id;
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
                    id = offer.sender.getLocalName().replace(agentType, "");
                    currentTime = System.currentTimeMillis();
//                    if (currentTime < offer.timeout) {
                        if (hasExtraItem(offer, offerQuantities)) {
                            cost = totalCost(offer, offerQuantities);
                            if (cost < minCost) {
                                minCost = cost;
                                lowCostOffer = offer;
                            } else if (cost == minCost) {
                                selectedId = lowCostOffer.sender.getLocalName().replace(agentType, "");
                                if (Integer.valueOf(id) < Integer.valueOf(selectedId)) {
                                    lowCostOffer = offer;
                                }
                            }
                        }
//                    }
                }
                if (lowCostOffer != null) {
                    minCost += distance * q;
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


    void deliberateOnConfirmingGreedy(Agent myAgent) {

//        if( myAgent.getLocalName().equals("4Agent1")) {
//            System.out.print("");
//        }

        Map<Request, Map<Offer, Long>> selectedOffersForAllRequests = new LinkedHashMap<>();

        for (var request : sentRequests.entrySet()) {
            currentTime = System.currentTimeMillis();
//            if (currentTime < request.getValue().timeout && request.getValue().timeout - currentTime < waitUntilConfirm) {
            if (currentTime < request.getValue().timeout) {
                if (request.getValue().cascaded == false && receivedOffers.containsKey(request.getKey())) {
                    Map<Offer, Long> confirmQuantities = processOffersGreedy(request.getValue());
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


    void deliberateOnConfirmingRL(Agent myAgent) {

//        if( myAgent.getLocalName().equals("4Agent1")) {
//            System.out.print("");
//        }

        Map<Request, Map<Offer, Long>> selectedOffersForAllRequests = new LinkedHashMap<>();

        for (var request : sentRequests.entrySet()) {
            currentTime = System.currentTimeMillis();
//            if (currentTime < request.getValue().timeout && request.getValue().timeout - currentTime < waitUntilConfirm) {
            if (currentTime < request.getValue().timeout) {
                if (request.getValue().cascaded == false && receivedOffers.containsKey(request.getKey())) {
                    Map<Offer, Long> confirmQuantities = processOffersRL(request.getValue());
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
                    if (q <= offerQuantity.getValue()) {
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
                incurTransferCost(offerQuantity.getKey().sender, confirmedItems.size());
            }
            confirmedOfferedItemsForAllRequests.put(selectedOffersForReq.getKey(), confirmedOfferedItems);
        }

        return confirmedOfferedItemsForAllRequests;
    }


    void incurTransferCost(AID provider, int quantity) {

        // The (original) requester pays the immediate transfer cost. When the request has been cascaded, any subsequent transfer cost is paid by the corresponding middle agent(s) who cascaded the request.
        // since we compute social welfare of all agents, we can incur the transfer cost locally

        String providerName = provider.getLocalName();
        String providerId = providerName.replace(agentType, "");

        int i = Integer.valueOf(providerId);
        double distance = adjacency[i-1];

        totalUtil -= distance * quantity;
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


    public Map<Offer, Long> processOffersGreedy(Request request) {

        // the requester selects the combination of offers that maximizes the difference between the utility of request and the total cost of all selected offers.
        // it is allowed to take partial amounts of offered resources in multiple offers up to the requested amount.
        // a greedy approach: we add 1 item from one offer in a loop up to the requested amount, without backtracking.

        Set<Offer> offers = receivedOffers.get(request.id);
        long minCost, cost;
        Offer lowCostOffer;
        String selectedId;
        String id;
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
                id = offer.sender.getLocalName().replace(agentType, "");
                currentTime = System.currentTimeMillis();
//                if(currentTime < offer.timeout) {
                    if (hasExtraItem(offer, confirmQuantities)) {
                        cost = totalCost(offer, confirmQuantities);
                        if (cost < minCost) {
                            minCost = cost;
                            lowCostOffer = offer;
                        } else if (cost == minCost) {
                            selectedId = lowCostOffer.sender.getLocalName().replace(agentType, "");
                            if (Integer.valueOf(id) < Integer.valueOf(selectedId)) {
                                lowCostOffer = offer;
                            }
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


    public Map<Offer, Long> processOffersRL(Request request) {

        // a reinforcement learning approach

        Set<Offer> offers = receivedOffers.get(request.id);

        Map<Offer, Long> confirmQuantities = new LinkedHashMap<>();
        for (Offer offer : offers) {
            confirmQuantities.put(offer, 0L);
        }

        long currentConfirmedQuantity = 0;
        long remainingRequestedQuantity = request.quantity;

//        while (currentConfirmedQuantity <= request.quantity && offers.size() > 0) {
        while (remainingRequestedQuantity > 0 && offers.size() > 0) {
            ConfirmingState currentState = generateConfirmingState (request, offers, remainingRequestedQuantity);

            if (currentState.possibleActions.size() == 0) {
                break;
            }

            // Choose action from state using epsilon-greedy policy derived from Q
            ConfirmingAction action =  selectEpsilonGreedyConfirmingAction(currentState);

            confirmQuantities.put(action.selectedOffer, action.confirmQuantity);
            offers.remove( action.selectedOffer);
            currentConfirmedQuantity += action.confirmQuantity;
            remainingRequestedQuantity -= action.confirmQuantity;

            long previousConfirmedQuantity = currentConfirmedQuantity - action.confirmQuantity;
            long previousUtil = 0;
            if (previousConfirmedQuantity > 0) {
                previousUtil = request.utilityFunction.get(previousConfirmedQuantity);
            }
//            long reward = request.utilityFunction.get(currentConfirmedQuantity) - previousUtil - action.selectedOffer.costFunction.get(action.confirmQuantity);
            long reward = request.utilityFunction.get(action.confirmQuantity) - action.selectedOffer.costFunction.get(action.confirmQuantity);

            ConfirmingStateAction currentStateAction = new ConfirmingStateAction (currentState, action);
            ConfirmingState nextState = generateConfirmingState (request, offers, remainingRequestedQuantity);

//            updateConfirmingPolicyNetwork(currentStateAction, nextState, reward);
//            updateConfirmingTargetNetwork();
        }

        return confirmQuantities;
    }


    void updateConfirmingTargetNetwork() {

        // or update based on episodes
        confirmingStepCount += 1;
        if (confirmingStepCount % targetUpdateFreq == 0) {
            confirmingTargetNetwork = confirmingPolicyNetwork;
        }
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
            logInf( this.getLocalName(), "cascadedRequest with id " + cascadedRequest.id + " originId " + cascadedRequest.originalId + " is confirmed and removed -- confirmed by " + confirmation.getSender().getLocalName());
            receivedOffers.remove(cascadedRequest.id);
        }

        if( sentOffer.cascaded == false) {
//            updateOfferingPolicyNetwork( sentOffer.currentStateAction, sentOffer.nextState, confirmQuantity);
//            updateOfferingTargetNetwork();
        }
    }


    void updateOfferingTargetNetwork() {

        // or update based on episodes
        offeringStepCount += 1;
        if (offeringStepCount % targetUpdateFreq == 0) {
            offeringTargetNetwork = offeringPolicyNetwork;
        }
    }


    private void cascadePartialConfirmations (Map<Offer, Map<String, Long>> confirmedOfferedItems) {

        for (var items : confirmedOfferedItems.entrySet()) {
            if( debugMode) {
                if (items.getValue().size() == 0) {
                    logInf(this.getLocalName(), "cascaded rejection with quantity " + items.getValue().size() + " for offerId  " + items.getKey().id + " for " + items.getKey().resourceType.name() + " to " + items.getKey().sender.getLocalName());
                } else {
                    logInf(this.getLocalName(), "cascaded confirmation with quantity " + items.getValue().size() + " for offerId  " + items.getKey().id + " for " + items.getKey().resourceType.name() + " to " + items.getKey().sender.getLocalName());
                }
            }
            sendConfirmation (items.getKey().id, items.getKey().sender, items.getKey().resourceType, items.getValue().size(), items.getValue());
            incurTransferCost( items.getKey().sender, items.getValue().size());
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


    void updateOfferingPolicyNetwork(OfferingStateAction currentStateAction, OfferingState nextState, long confirmQuantity) {

        long currentReward = 0;
        if (confirmQuantity > 0) {
            currentReward = currentStateAction.action.selectedRequest.utilityFunction.get(confirmQuantity) - currentStateAction.action.offerCostFunction.get(confirmQuantity);
//            System.out.println("reward: " + reward);
        }

        boolean isTerminal = false;
        if (nextState.possibleActions.isEmpty()) {
            isTerminal = true;
        }

        offeringReplayMemoryExperienceHandler.addExperience(currentStateAction.state.observation, currentStateAction.action, currentReward, isTerminal);

        if (offeringReplayMemoryExperienceHandler.isTrainingBatchReady()) {

            List<StateActionRewardState> trainingBatch = offeringReplayMemoryExperienceHandler.generateTrainingBatch();
            int batchSize = trainingBatch.size();

            INDArray statesBatch = Nd4j.create(batchSize, offeringStateVectorSize);
            INDArray nextStatesBatch = Nd4j.create(batchSize, offeringStateVectorSize);

            // Fill the states and nextStates batch
            for (int i = 0; i < batchSize; i++) {
                statesBatch.putRow(i, trainingBatch.get(i).getObservation().getData());
                nextStatesBatch.putRow(i, trainingBatch.get(i).getNextObservation().getData());
            }

            // Batch forward pass
            INDArray currentQValuesBatch = offeringPolicyNetwork.output(statesBatch);
            INDArray nextQValuesFromPolicyBatch = offeringPolicyNetwork.output(nextStatesBatch);
            INDArray nextQValuesFromTargetBatch = offeringTargetNetwork.output(nextStatesBatch);

            // Batch updates to Q-values
            for (int i = 0; i < batchSize; i++) {
                StateActionRewardState<OfferingAction> experience = trainingBatch.get(i);
                OfferingAction action = experience.getAction();
                double reward = experience.getReward();
                int neighborIndex = neighbors.indexOf(action.selectedRequest.sender);
                int actionIndex = (int) (neighborIndex * maxRequestQuantity + action.offerQuantity);
                if (experience.isTerminal()) {
                    currentQValuesBatch.putScalar(i, actionIndex, reward);
                } else {
                    if (doubleLearning) {
                        int bestNextPolicyActionIndex = Nd4j.argMax(nextQValuesFromPolicyBatch.getRow(i)).getInt(0);
                        currentQValuesBatch.putScalar(i, actionIndex, reward + offeringGamma * nextQValuesFromTargetBatch.getDouble(i, bestNextPolicyActionIndex));
                    } else {
                        double maxNextTargetValue = nextQValuesFromTargetBatch.getRow(i).maxNumber().doubleValue();
                        currentQValuesBatch.putScalar(i, actionIndex, reward + offeringGamma * maxNextTargetValue);
                    }
                }
            }

            offeringPolicyNetwork.fit(statesBatch, currentQValuesBatch);
        }
    }


    void updateConfirmingPolicyNetwork(ConfirmingStateAction currentStateAction, ConfirmingState nextState, long currentReward) {

        boolean isTerminal = false;
        if (nextState.possibleActions.isEmpty()) {
            isTerminal = true;
        }

        confirmingReplayMemoryExperienceHandler.addExperience(currentStateAction.state.observation, currentStateAction.action, currentReward, isTerminal);

        if (confirmingReplayMemoryExperienceHandler.isTrainingBatchReady()) {

            List<StateActionRewardState> trainingBatch = confirmingReplayMemoryExperienceHandler.generateTrainingBatch();
            int batchSize = trainingBatch.size();

            INDArray statesBatch = Nd4j.create(batchSize, confirmingStateVectorSize);
            INDArray nextStatesBatch = Nd4j.create(batchSize, confirmingStateVectorSize);

            // Fill the states and nextStates batch
            for (int i = 0; i < batchSize; i++) {
                statesBatch.putRow(i, trainingBatch.get(i).getObservation().getData());
                nextStatesBatch.putRow(i, trainingBatch.get(i).getNextObservation().getData());
            }

            // Batch forward pass
            INDArray currentQValuesBatch = confirmingPolicyNetwork.output(statesBatch);
            INDArray nextQValuesFromPolicyBatch = confirmingPolicyNetwork.output(nextStatesBatch);
            INDArray nextQValuesFromTargetBatch = confirmingTargetNetwork.output(nextStatesBatch);

            // Batch updates to Q-values
            for (int i = 0; i < batchSize; i++) {
                StateActionRewardState<ConfirmingAction> experience = trainingBatch.get(i);
                ConfirmingAction action = experience.getAction();
                double reward = experience.getReward();
                int neighborIndex = neighbors.indexOf(action.selectedOffer.sender);
                int actionIndex = (int) (neighborIndex * maxRequestQuantity + action.confirmQuantity);
                if (experience.isTerminal()) {
                    currentQValuesBatch.putScalar(i, actionIndex, reward);
                } else {
                    if (doubleLearning) {
                        int bestNextPolicyActionIndex = Nd4j.argMax(nextQValuesFromPolicyBatch.getRow(i)).getInt(0);
                        currentQValuesBatch.putScalar(i, actionIndex, reward + confirmingGamma * nextQValuesFromTargetBatch.getDouble(i, bestNextPolicyActionIndex));
                    } else {
                        double maxNextTargetValue = nextQValuesFromTargetBatch.getRow(i).maxNumber().doubleValue();
                        currentQValuesBatch.putScalar(i, actionIndex, reward + confirmingGamma * maxNextTargetValue);
                    }
                }
            }

            confirmingPolicyNetwork.fit(statesBatch, currentQValuesBatch);
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
