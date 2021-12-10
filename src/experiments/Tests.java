package experiments;

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

        ResourceAllocationAgent agent = new ResourceAllocationAgent();

        Map<ResourceType, Set<Bid>> receivedBids = new LinkedHashMap<>();

        String bidId = UUID.randomUUID().toString();

        Map<Integer, Integer> costFunction = new LinkedHashMap<>();

        costFunction.put(1, 1);
        costFunction.put(2, 2);
        costFunction.put(3, 3);

        Bid bid1 = new Bid(bidId, 3, ResourceType.A, costFunction);

        bidId = UUID.randomUUID().toString();

        costFunction = new LinkedHashMap<>();

        costFunction.put(1, 10);
        costFunction.put(2, 20);
        costFunction.put(3, 30);
        costFunction.put(4, 40);
        costFunction.put(5, 50);

        Bid bid2 = new Bid(bidId, 5, ResourceType.A, costFunction);

        bidId = UUID.randomUUID().toString();

        costFunction = new LinkedHashMap<>();

        costFunction.put(1, 2);
        costFunction.put(2, 4);
        costFunction.put(3, 6);
        costFunction.put(4, 8);

        Bid bid3 = new Bid(bidId, 4, ResourceType.A, costFunction);

        Set<Bid> bids = new HashSet<>();
        bids.add(bid1);
        bids.add(bid2);
        bids.add(bid3);

        receivedBids.put(ResourceType.A, bids);

        agent.receivedBids = receivedBids;

        String reqId = UUID.randomUUID().toString();

        Map<Integer, Integer> utilityFunction = new LinkedHashMap<>();
        for (int i=1; i<=10; i++) {
            utilityFunction.put(i, i*3);
        }

        Request request = new Request (reqId, 10, ResourceType.A, utilityFunction);

        Map<Bid, Integer> selectedBids = agent.processBids( request);

        System.out.println(selectedBids);
    }
}
