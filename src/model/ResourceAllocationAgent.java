package model;

import behaviours.ThreeStepBehaviour;
import jade.core.Agent;
import jade.core.behaviours.Behaviour;

import java.util.Iterator;

public class ResourceAllocationAgent extends  Agent {

    @Override
    protected void setup() {

        // Printout a welcome message
        System.out.println("Hello World. I’m an agent!");
        System.out.println("My local-name is "+getAID().getLocalName());
        System.out.println("My GUID is "+getAID().getName());
        System.out.println("My addresses are:");
        Iterator it = getAID().getAllAddresses();
        while (it.hasNext()) {
            System.out.println("- "+it.next());
        }

        addBehaviour(new ThreeStepBehaviour());
    }


    /**
     * Agent clean-up
     */
    @Override
    protected void takeDown() {

    }





}
