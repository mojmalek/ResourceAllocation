package model;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.util.*;


public class MasterAgent extends Agent {

    private ArrayList<AID> otherAgents = new ArrayList<>();
    private Map<AID, ProtocolPhase> otherAgentsPhases = new LinkedHashMap<>();

    private SortedSet<Task> toDoTasks = new TreeSet<>(new Task.taskComparator());
    private SortedSet<Task> doneTasks = new TreeSet<>(new Task.taskComparator());
    private int totalUtil;

    private Map<ResourceType, SortedSet<ResourceItem>> availableResources = new LinkedHashMap<>();
    private Map<ResourceType, ArrayList<ResourceItem>> expiredResources = new LinkedHashMap<>();


    @Override
    protected void setup() {

        System.out.println("Hello World. I’m the Master agent! My local-name is " + getAID().getLocalName());
        // Get ids of other agents as arguments
        Object[] args = getArguments();
        if (args != null && args.length > 0) {
            int numberOfAgents = (int) args[0];
            for (int i = 1; i <= numberOfAgents; i++) {
                AID aid = new AID("Agent"+i, AID.ISLOCALNAME);
                otherAgents.add(aid);
                otherAgentsPhases.put(aid, ProtocolPhase.REQUESTING);
            }
        }

//        addBehaviour(new CyclicBehaviour() {
//            @Override
//            public void action() {
//                receiveMessages( myAgent, ACLMessage.INFORM);
//            }
//        });

        addBehaviour(new CyclicBehaviour() {
            @Override
            public void action() {
                ACLMessage msg = myAgent.receive();
                if (msg != null) {
                    String content = msg.getContent();
                    switch (msg.getPerformative()) {
                        case ACLMessage.INFORM:
                            System.out.println (myAgent.getLocalName() + " received an INFORM message from " + msg.getSender().getLocalName());
                            try {
                                processNewTasksResourcesInfo (myAgent, msg);
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


    void perishResourceItems( Agent myAgent) {

        SortedSet<ResourceItem> availableItems;
        ArrayList<ResourceItem> expiredItems;
        ArrayList<ResourceItem> expiredItemsInThisRound = new ArrayList<>();
        for (var resource : availableResources.entrySet()) {
            availableItems = availableResources.get( resource.getKey());
            if (expiredResources.containsKey( resource.getKey())) {
                expiredItems = expiredResources.get( resource.getKey());
            } else {
                expiredItems = new ArrayList<>();
                expiredResources.put( resource.getKey(), expiredItems);
            }
            for (ResourceItem item : availableItems) {
                item.setLifetime( item.getLifetime() - 1);
                if (item.getLifetime() == 0) {
                    expiredItemsInThisRound.add( item);
                    expiredItems.add( item);
                }
            }
            availableItems.removeAll( expiredItemsInThisRound);
//            expiredResources.put( resource.getKey(), expiredItems);
//            availableResources.put( resource.getKey(), availableItems);
        }

        for (var entry : expiredResources.entrySet()) {
            System.out.println( myAgent.getLocalName() + " has " + entry.getValue().size() + " expired item of type: " + entry.getKey().name());
        }
    }


//    void waitForRequests( Agent myAgent) {
//
//        while(inRequestingPhase()) {
//            myAgent.doWait(1);
//            receiveMessages( myAgent, ACLMessage.INFORM);
//        }
//        receiveMessages( myAgent, ACLMessage.REQUEST);
//    }


    boolean inRequestingPhase () {

        boolean requesting = false;
        for (var agentPhase : otherAgentsPhases.entrySet() ) {
            if (agentPhase.getValue() == ProtocolPhase.REQUESTING) {
                requesting = true;
                break;
            }
        }
        return requesting;
    }


    void resetRound() {

//        blockedTasks.clear();
//        receivedRequests.clear();
//        sentRequests.clear();
//        receivedBids.clear();
//        sentBids.clear();
    }


    private void performTasks(Agent myAgent) {

        System.out.println (myAgent.getLocalName() +  " is performing tasks.");

        SortedSet<Task> doneTasksInThisRound = new TreeSet<>(new Task.taskComparator());

        for (Task task : toDoTasks) {
            if (hasEnoughResources(task, availableResources)) {
                processTask(task);
                doneTasksInThisRound.add(task);
                doneTasks.add(task);
                totalUtil = totalUtil + task.utility;
            }
        }

        toDoTasks.removeAll (doneTasksInThisRound);

        System.out.println( myAgent.getLocalName() + " has performed " + doneTasks.size() + " tasks and gained total utility of " + totalUtil);
    }


    Map<ResourceType, SortedSet<ResourceItem>> evaluateTask (Task task, Map<ResourceType, SortedSet<ResourceItem>> remainingResources) {

        try {
            for (var entry : task.requiredResources.entrySet()) {
                SortedSet<ResourceItem> resourceItems = remainingResources.get(entry.getKey());
                for (int i = 0; i < entry.getValue(); i++) {
                    ResourceItem item = resourceItems.first();
                    resourceItems.remove(item);
                }
//                remainingResources.replace(entry.getKey(), resourceItems);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        return remainingResources;
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



    public static Map<ResourceType, SortedSet<ResourceItem>> deepCopyResourcesMap(Map<ResourceType, SortedSet<ResourceItem>> original) {
        Map<ResourceType, SortedSet<ResourceItem>> copy = new LinkedHashMap<>();
        for (var entry : original.entrySet()) {
            copy.put(entry.getKey(), new TreeSet<>(entry.getValue()));
        }
        return copy;
    }


    private void processNewTasksResourcesInfo(Agent myAgent, ACLMessage msg) throws ParseException {

        String content = msg.getContent();

        Object obj = new JSONParser().parse(content);
        JSONObject jo = (JSONObject) obj;

        AID agentId = msg.getSender();
        JSONObject joNewTasks = (JSONObject) jo.get("newTasks");
        JSONObject joNewResources = (JSONObject) jo.get("newResources");

        if (joNewTasks != null) {
            String id, resourceType;
            Long utility, quantity;
            Map<ResourceType, Integer> requiredResources = new LinkedHashMap<>();
            Iterator<String> keysIterator1 = joNewTasks.keySet().iterator();
            while (keysIterator1.hasNext()) {
                requiredResources.clear();
                id = keysIterator1.next();
                JSONObject joTask = (JSONObject) joNewTasks.get(id);
                utility = (Long) joTask.get("utility");
                JSONObject joRequiredResources = (JSONObject) joTask.get("requiredResources");
                Iterator<String> keysIterator2 = joRequiredResources.keySet().iterator();
                while (keysIterator2.hasNext()) {
                    resourceType = keysIterator2.next();
                    quantity = (Long) joRequiredResources.get(resourceType);
                    requiredResources.put( ResourceType.valueOf(resourceType), quantity.intValue());
                }
                Task newTask = new Task(id , utility.intValue(), requiredResources);
                toDoTasks.add( newTask);
            }
        }

        if (joNewResources != null) {
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

        System.out.println("Hello");


//        String pp = (String) jo.get(Ontology.PROTOCOL_PHASE);
//        ProtocolPhase protocolPhase = ProtocolPhase.valueOf(pp);
//
//        otherAgentsPhases.put( msg.getSender(), protocolPhase);
    }


//    void receiveMessages(Agent myAgent, int performative) {
//
//        MessageTemplate mt = MessageTemplate.MatchPerformative( performative);
//
//        ACLMessage msg = myAgent.receive( mt);
//
//        while (msg != null) {
//            String content = msg.getContent();
//
//            switch (performative) {
//
//                case ACLMessage.INFORM:
////                    System.out.println(myAgent.getLocalName() + " received an INFORM message from " + msg.getSender().getLocalName());
//
//                    try {
//                        processNewTasksResourcesInfo (myAgent, msg);
//                    } catch (ParseException e) {
//                        e.printStackTrace();
//                    }
//                    break;
//            }
//
//            msg = myAgent.receive( mt);
//        }
//    }

}