package experiments;

import jade.core.AID;
import model.Bid;
import model.Request;
import model.AdaptiveAgent;
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

        AdaptiveAgent agent = new AdaptiveAgent();

        String reqId = UUID.randomUUID().toString();

        Map<Long, Long> utilityFunction = new LinkedHashMap<>();
        for (long i=1; i<=10; i++) {
            utilityFunction.put( i, i*3);
        }

        Request request = new Request (reqId, 10, ResourceType.A, utilityFunction, agent1);

        Map<String, Set<Bid>> receivedBids = new LinkedHashMap<>();

        String bidId = UUID.randomUUID().toString();

        Map<Long, Long> costFunction = new LinkedHashMap<>();
        costFunction.put(1L, 1L);
        costFunction.put(2L, 2L);
        costFunction.put(3L, 3L);

        Map<String, Integer> offeredItems = new LinkedHashMap<>();
        offeredItems.put ("res1", 10);
        offeredItems.put ("res2", 10);
        offeredItems.put ("res3", 10);

        Bid bid1 = new Bid (bidId, reqId,3, ResourceType.A, costFunction, offeredItems, agent2, agent1);

        bidId = UUID.randomUUID().toString();

        costFunction = new LinkedHashMap<>();
        costFunction.put(1L, 10L);
        costFunction.put(2L, 20L);
        costFunction.put(3L, 30L);
        costFunction.put(4L, 40L);
        costFunction.put(5L, 50L);

        offeredItems = new LinkedHashMap<>();
        offeredItems.put ("res1", 10);
        offeredItems.put ("res2", 10);
        offeredItems.put ("res3", 10);
        offeredItems.put ("res4", 10);
        offeredItems.put ("res5", 10);

        Bid bid2 = new Bid(bidId, reqId,5, ResourceType.A, costFunction, offeredItems, agent3, agent1);

        bidId = UUID.randomUUID().toString();

        costFunction = new LinkedHashMap<>();
        costFunction.put(1L, 2L);
        costFunction.put(2L, 4L);
        costFunction.put(3L, 6L);
        costFunction.put(4L, 8L);

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

        Map<Bid, Long> selectedBids = agent.processBids( request);

        System.out.println(selectedBids);
    }
}
