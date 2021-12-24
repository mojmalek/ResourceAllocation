package experiments;

import jade.core.AID;
import model.Bid;
import model.Request;
import model.ResourceAllocationAgent;
import model.ResourceType;

import java.util.*;

public class Tests {

    public static void main(String[] args) {
        try {
            processBids();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void processBids() throws Exception {

        AID agent1 = new AID("Agent1", false);
        AID agent2 = new AID("Agent2", false);
        AID agent3 = new AID("Agent3", false);
        AID agent4 = new AID("Agent4", false);

        ResourceAllocationAgent agent = new ResourceAllocationAgent();

        String reqId = UUID.randomUUID().toString();

        Map<Integer, Integer> utilityFunction = new LinkedHashMap<>();
        for (int i=1; i<=10; i++) {
            utilityFunction.put(i, i*3);
        }

        Request request = new Request (reqId, 10, ResourceType.A, utilityFunction, agent1);

        Map<String, Set<Bid>> receivedBids = new LinkedHashMap<>();

        String bidId = UUID.randomUUID().toString();

        Map<Integer, Integer> costFunction = new LinkedHashMap<>();
        costFunction.put(1, 1);
        costFunction.put(2, 2);
        costFunction.put(3, 3);

        Map<String, Integer> offeredItems = new LinkedHashMap<>();
        offeredItems.put ("res1", 10);
        offeredItems.put ("res2", 10);
        offeredItems.put ("res3", 10);

        Bid bid1 = new Bid (bidId, reqId,3, ResourceType.A, costFunction, offeredItems, agent2, agent1);

        bidId = UUID.randomUUID().toString();

        costFunction = new LinkedHashMap<>();
        costFunction.put(1, 10);
        costFunction.put(2, 20);
        costFunction.put(3, 30);
        costFunction.put(4, 40);
        costFunction.put(5, 50);

        offeredItems = new LinkedHashMap<>();
        offeredItems.put ("res1", 10);
        offeredItems.put ("res2", 10);
        offeredItems.put ("res3", 10);
        offeredItems.put ("res4", 10);
        offeredItems.put ("res5", 10);

        Bid bid2 = new Bid(bidId, reqId,5, ResourceType.A, costFunction, offeredItems, agent3, agent1);

        bidId = UUID.randomUUID().toString();

        costFunction = new LinkedHashMap<>();
        costFunction.put(1, 2);
        costFunction.put(2, 4);
        costFunction.put(3, 6);
        costFunction.put(4, 8);

        offeredItems = new LinkedHashMap<>();
        offeredItems.put ("res1", 10);
        offeredItems.put ("res2", 10);
        offeredItems.put ("res3", 10);
        offeredItems.put ("res4", 10);

        Bid bid3 = new Bid(bidId, reqId,4, ResourceType.A, costFunction, offeredItems, agent4, agent1);

        Set<Bid> bids = new HashSet<>();
        bids.add(bid1);
        bids.add(bid2);
        bids.add(bid3);

        receivedBids.put(reqId, bids);

        agent.receivedBids = receivedBids;

        Map<Bid, Integer> selectedBids = agent.processBids( request);

        System.out.println(selectedBids);
    }
}
