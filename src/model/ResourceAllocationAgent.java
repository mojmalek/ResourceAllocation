package model;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.lang.acl.ACLMessage;

import java.util.*;

public class ResourceAllocationAgent extends  Agent {

    private ArrayList<Task> tasks = new ArrayList<Task>();

    private ArrayList<ResourceItem> availableResources = new ArrayList<ResourceItem>();

    SimulationEngine simulationEngine = new SimulationEngine();

    @Override
    protected void setup() {

        // Printout a welcome message
        System.out.println("Hello World. I’m an agent!");
        System.out.println("My local-name is " + getAID().getLocalName());
        System.out.println("My GUID is " + getAID().getName());
//        System.out.println("My addresses are:");
//        Iterator it = getAID().getAllAddresses();
//        while (it.hasNext()) {
//            System.out.println("- "+it.next());
//        }


//        addBehaviour(new TickerBehaviour(this, 3000) {
//
//            protected void onTick() {
//
//                Task newTask = simulationEngine.findTask();
//                tasks.add(newTask);
//
//                System.out.println("My tasks are:");
//
//                for (int i = 0; i < tasks.size(); i++) {
//                    System.out.println(tasks.get(i));
//                }
//            }
//        });


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


        addBehaviour(new OneShotBehaviour() {
            @Override
            public void action() {

                ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
                msg.addReceiver(new AID("Agent2", AID.ISLOCALNAME));
                msg.setLanguage("English");
                msg.setOntology("Weather-forecast-ontology");
                msg.setContent("Today it’s raining");
                send(msg);
                System.out.println("Message sent by " + myAgent.getLocalName());

            }
        });


        addBehaviour(new CyclicBehaviour() {
            @Override
            public void action() {
                ACLMessage msg = myAgent.receive();
                if (msg != null) {
                    // Message received. Process it
                    String content = msg.getContent();
                    System.out.println("Message received by " + myAgent.getLocalName() + " with content: " + content);
                } else {
                    block();
                }
            }
        });


    }
}
