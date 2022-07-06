package experiments;

import jade.core.AID;
import model.Offer;
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

        Map<String, Set<Offer>> receivedBids = new LinkedHashMap<>();

        String bidId = UUID.randomUUID().toString();

        Map<Long, Long> costFunction = new LinkedHashMap<>();
        costFunction.put(1L, 1L);
        costFunction.put(2L, 2L);
        costFunction.put(3L, 3L);

        Map<String, Long> offeredItems = new LinkedHashMap<>();
        offeredItems.put ("res1", 10L);
        offeredItems.put ("res2", 10L);
        offeredItems.put ("res3", 10L);

        Offer offer1 = new Offer(bidId, reqId,3, ResourceType.A, costFunction, offeredItems, agent2, agent1);

        bidId = UUID.randomUUID().toString();

        costFunction = new LinkedHashMap<>();
        costFunction.put(1L, 10L);
        costFunction.put(2L, 20L);
        costFunction.put(3L, 30L);
        costFunction.put(4L, 40L);
        costFunction.put(5L, 50L);

        offeredItems = new LinkedHashMap<>();
        offeredItems.put ("res1", 10L);
        offeredItems.put ("res2", 10L);
        offeredItems.put ("res3", 10L);
        offeredItems.put ("res4", 10L);
        offeredItems.put ("res5", 10L);

        Offer offer2 = new Offer(bidId, reqId,5, ResourceType.A, costFunction, offeredItems, agent3, agent1);

        bidId = UUID.randomUUID().toString();

        costFunction = new LinkedHashMap<>();
        costFunction.put(1L, 2L);
        costFunction.put(2L, 4L);
        costFunction.put(3L, 6L);
        costFunction.put(4L, 8L);

        offeredItems = new LinkedHashMap<>();
        offeredItems.put ("res1", 10L);
        offeredItems.put ("res2", 10L);
        offeredItems.put ("res3", 10L);
        offeredItems.put ("res4", 10L);

        Offer offer3 = new Offer(bidId, reqId,4, ResourceType.A, costFunction, offeredItems, agent4, agent1);

        Set<Offer> offers = new HashSet<>();
        offers.add(offer1);
        offers.add(offer2);
        offers.add(offer3);

        receivedBids.put(reqId, offers);

        agent.receivedOffers = receivedBids;

        Map<Offer, Long> selectedBids = agent.processOffers( request);

        System.out.println(selectedBids);
    }
}
