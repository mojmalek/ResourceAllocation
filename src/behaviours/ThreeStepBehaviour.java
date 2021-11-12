package behaviours;

import jade.core.behaviours.Behaviour;


public class ThreeStepBehaviour extends Behaviour {

    private int step = 0;

    public void action() {

        switch (step) {
            case 0:
                // perform operation X
                System.out.println("Operation X is done.");
                step++;
                break;
            case 1:
                // perform operation Y
                System.out.println("Operation Y is done.");
                step++;
                break;
            case 2:
                // perform operation Z
                System.out.println("Operation Z is done.");
                step++;
                break;
        }
    }

    public boolean done() {
        return step == 3;
    }
}