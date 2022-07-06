package model;

import jade.core.Agent;

import java.util.*;

public class SimulationEngine {

    long currentTime;

    public SortedSet<Task> findTasks(Agent myAgent) {

        SortedSet<Task> tasks = new TreeSet<>(new Task.taskComparator());
        Random random = new Random();
        ResourceType[] resourceTypeValues = ResourceType.getValues();
        Set<String> requesters = Set.of("8Agent1", "8Agent2", "8Agent3", "8Agent4");
        Set<String> bidders = Set.of("8Agent5", "8Agent6", "8Agent7", "8Agent8");
        int[] taskNums = new int[] {1, 2, 4, 8};
//        if( bidders.contains(myAgent.getLocalName())) {
//            taskNums = new int[] {1, 2, 3, 8};
//        }
        int numOfTasks = taskNums[random.nextInt( taskNums.length)];
        long[] quantities = new long[] {0, 1, 2, 4, 8};
//        long minUtil = 10;
//        long utilVariation = 5;
        long[] utilities = new long[] {1, 2, 4, 8, 16, 32, 64};
//        if( bidders.contains(myAgent.getLocalName())) {
//            minUtil = 20;
//        }
        long quantity, utility;
        for (int j=0; j<numOfTasks; j++) {
            Map<ResourceType, Long> requiredResources = new LinkedHashMap<>();
            for (int i=0; i<resourceTypeValues.length; i++) {
//                if( bidders.contains(myAgent.getLocalName()) && resourceTypeValues[i] == ResourceType.A ) {
//                    quantities = new long[] {4, 5};
//                } else {
//                    quantities = new long[] {0, 1, 2, 3, 4};
//                }
                quantity = quantities[random.nextInt( quantities.length)];
                if (quantity > 0) {
                    requiredResources.put(resourceTypeValues[i], quantity);
                }
            }
//            utility = minUtil + random.nextLong(utilVariation);
            utility = utilities[random.nextInt( utilities.length)];
            String id = UUID.randomUUID().toString();
            if (!requiredResources.isEmpty()) {
                currentTime = System.currentTimeMillis();
                Task newTask = new Task(id, utility, currentTime + 300000, requiredResources);
                tasks.add(newTask);
            } else {
//                System.out.println(" ");
            }
        }

        return tasks;
    }


    public Map<ResourceType, SortedSet<ResourceItem>> findResources (Agent myAgent) {

        Map<ResourceType, SortedSet<ResourceItem>> resources = new LinkedHashMap<>();
        Random random = new Random();
        ResourceType[] resourceTypeValues = ResourceType.getValues();
        long[] quantities = new long[] {0, 1, 2, 4, 8};
        int[] lifetimes = new int[] {3};
        Set<String> bidders = Set.of("8Agent5", "8Agent6", "8Agent7", "8Agent8");
//        if( bidders.contains(myAgent.getLocalName())) {
//            quantities = new long[] {4};
//        }
        long quantity;
        long lifetime;
        for (int i = 0; i < resourceTypeValues.length; i++) {
            quantity = quantities[random.nextInt( quantities.length)];
//            if (quantity > 0) {
                lifetime = lifetimes[random.nextInt( lifetimes.length)];
                SortedSet<ResourceItem> items = findResourceItems(resourceTypeValues[i], lifetime, quantity);
                resources.put(resourceTypeValues[i], items);
//            }
        }

        return resources;
    }


    public SortedSet<ResourceItem> findResourceItems( ResourceType resourceType, long lifeTime, long quantity) {

        SortedSet<ResourceItem> resourceItems = new TreeSet<>(new ResourceItem.resourceItemComparator());
        String id;
        for (long i=0; i<quantity; i++) {
            id = UUID.randomUUID().toString();
            currentTime = System.currentTimeMillis();
            resourceItems.add(new ResourceItem (id, resourceType, currentTime + lifeTime));
        }
        return resourceItems;
    }


    public Integer[][] generateSocialNetwork( int numberOfAgents, double connectivity) {

        Integer[][] socialNetwork = new Integer[numberOfAgents][numberOfAgents];
        Random random = new Random();
        int[] weights = new int[] {1, 2, 4, 8};
        int weight;

        // first connect each agent to its next
        for (int i = 0; i < numberOfAgents-1; i++) {
            weight = weights[random.nextInt( weights.length)];
            socialNetwork[i][i+1] = weight;
            socialNetwork[i+1][i] = weight;
        }

        // then consider connecting more agents based on the degree of connectivity
        for (int i = 0; i < numberOfAgents-1; i++) {
            for (int j = i+2; j < numberOfAgents; j++) {
                double r = random.nextDouble();
                if (r < connectivity) {
                    weight = weights[random.nextInt( weights.length)];
                    socialNetwork[i][j] = weight;
                    socialNetwork[j][i] = weight;
                }
            }
        }

        return socialNetwork;
    }

}
