package model;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.lang.acl.ACLMessage;

import java.util.*;
import java.util.logging.Logger;


public class ResourceAllocationAgent extends Agent {

    private ArrayList<Task> tasks = new ArrayList<>();

    private HashMap<ResourceType, ArrayList<ResourceItem>> availableResources = new HashMap<>();

    private ArrayList<AID> otherAgents = new ArrayList<>();

    SimulationEngine simulationEngine = new SimulationEngine();

    @Override
    protected void setup() {

        // Printout a welcome message
        System.out.println("Hello World. I’m an agent!");
        System.out.println("My local-name is " + getAID().getLocalName());
//        System.out.println("My GUID is " + getAID().getName());
//        System.out.println("My addresses are:");
//        Iterator it = getAID().getAllAddresses();
//        while (it.hasNext()) {
//            System.out.println("- "+it.next());
//        }


        // Get ids of other agents as arguments
        Object[] args = getArguments();
        if (args != null && args.length > 0) {

            int numberOfAgents = (int) args[0];
            int myId = (int) args[1];

            for (int i = 1; i <= numberOfAgents; i++) {
                if ( i != myId) {
                    AID aid = new AID("Agent"+i, AID.ISLOCALNAME);
                    otherAgents.add(aid);
                }
            }
        }


        addBehaviour(new TickerBehaviour(this, 1000) {

            protected void onTick() {

                Task newTask = simulationEngine.findTask();
                tasks.add(newTask);

                System.out.println( myAgent.getLocalName() + ": I have a new task to perform: " + newTask);

            }
        });


        addBehaviour(new TickerBehaviour(this, 5000) {

            protected void onTick() {

                ArrayList<Task> blockedTasks = new ArrayList<>();

                for (Task task : tasks) {
                    if (hasEnoughResources(task)) {
                        processTask (task);
                    }
                    else {
                        blockedTasks.add(task);
                    }
                }

                createRequest( blockedTasks);

//                System.out.println( myAgent.getLocalName() + ": I have a new task to perform: " + newTask);

            }
        });


        availableResources.put(ResourceType.A, simulationEngine.findResourceItems(ResourceType.A, 5, 100));


//        addBehaviour(new TickerBehaviour(this, 5000) {
//
//            protected void onTick() {
//
//                ResourceItem resourceItem = simulationEngine.findResourceItem();
//                availableResources.add(resourceItem);
//
//                System.out.println("My availableResources are:");
//
//                for (int i = 0; i < availableResources.size(); i++) {
//                    System.out.println(availableResources.get(i));
//                }
//            }
//        });


//        addBehaviour(new OneShotBehaviour() {
//            @Override
//            public void action() {
//
//                ACLMessage msg = new ACLMessage(ACLMessage.REQUEST);
//
//                for (int i = 0; i < otherAgents.size(); i++) {
//                // Send this message to all other agents
//                    msg.addReceiver(otherAgents.get(i));
//                }
//
//                HashMap<String,String> fields = new HashMap<String,String>();
//                fields.put(Ontology.RESOURCE_REQUESTED_QUANTITY, "10");
//
////                msg.setLanguage("English");
////                msg.setOntology("Weather-forecast-ontology");
//
//                msg.setContent( fields.toString());
//
////                msg.setReplyByDate();
//
//                send(msg);
//                System.out.println("Message sent by " + myAgent.getLocalName());
//
//            }
//        });


        addBehaviour(new CyclicBehaviour() {
            @Override
            public void action() {
                ACLMessage msg = myAgent.receive();
                if (msg != null) {
                    // Message received. Process it
                    String content = msg.getContent();

                    switch (msg.getPerformative()) {
                        case ACLMessage.REQUEST:
                            System.out.println (myAgent.getLocalName() + " received a REQUEST message from " + msg.getSender().getLocalName() + " with content: " + content);

                            processRequest(myAgent, msg);

                            break;

                        case ACLMessage.PROPOSE:
                            System.out.println (myAgent.getLocalName() + " received a PROPOSE message from " + msg.getSender().getLocalName() + " with content: " + content);


                            break;

                        case ACLMessage.ACCEPT_PROPOSAL:
                            System.out.println (myAgent.getLocalName() + " received a ACCEPT_PROPOSAL message from " + msg.getSender().getLocalName() + " with content: " + content);


                            break;

                        case ACLMessage.REFUSE:
                            System.out.println (myAgent.getLocalName() + " received a REFUSE message from " + msg.getSender().getLocalName() + " with content: " + content);

                            break;

                        case ACLMessage.REJECT_PROPOSAL:
                            System.out.println (myAgent.getLocalName() + " received a REJECT_PROPOSAL message from " + msg.getSender().getLocalName() + " with content: " + content);


                            break;

                        case ACLMessage.INFORM:
                            System.out.println (myAgent.getLocalName() + " received a INFORM message from " + msg.getSender().getLocalName() + " with content: " + content);


                            break;
                    }

                } else {
                    block();
                }
            }
        });


    }


    private void createRequest (ArrayList<Task> blockedTasks) {

        // creates a request based on the missing quantity for each resource type

        System.out.println( "This is a new request");
    }


    private void processRequest (Agent myAgent, ACLMessage msg) {

        String content = msg.getContent();

        HashMap<String,String> fields = parseMessageContent (content);

        String quantity = fields.get("quantity");

/*
        int quantity = Integer.parseInt(fields.get("quantity"));
*/

        System.out.println("Requested quantity is " + quantity);
    }


    private void createBid () {

        System.out.println( "This is a new bid");
    }


    private void createConfirm () {

        System.out.println( "This is a new confirmation");
    }


    private HashMap<String,String> parseMessageContent (String content) {

        String mainDelim = ",";
        String fieldDelim = "=";

        StringBuilder sb = new StringBuilder(content);

        sb.deleteCharAt(0);
        sb.deleteCharAt(content.length()-2);

//        content = content.replace('{', '');

        content = sb.toString();

        String[] contentList = content.split(mainDelim);

        HashMap<String,String> fields = new HashMap<String,String>();

        for (int i=0;i<contentList.length;i++)
        {
            String[] fieldTuple = contentList[i].split(fieldDelim);
            fields.put(fieldTuple[0], fieldTuple[1]);
        }

        return fields;
    }


    private boolean hasEnoughResources (Task task) {
        boolean enough = true;

        for (var entry : task.requiredResources.entrySet()) {
            System.out.println(entry.getKey() + "/" + entry.getValue());
            if (entry.getValue() > availableResources.get(entry.getKey()).size()) {
                enough = false;
                break;
            }
        }

        return enough;
    }


    void processTask (Task task) {

        for (var entry : task.requiredResources.entrySet()) {
            ArrayList<ResourceItem> resourceItems = availableResources.get(entry.getKey());
            for (int i=0; i<entry.getValue(); i++) {
                resourceItems.remove(i);
            }

            availableResources.replace( entry.getKey(), resourceItems);

        }

        tasks.remove(task);
    }

}
