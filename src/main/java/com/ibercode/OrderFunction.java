package com.ibercode;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;
import software.amazon.awssdk.services.eventbridge.model.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

public class OrderFunction implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    Gson gson = new GsonBuilder().disableHtmlEscaping().create();
    Logger logger = LoggerFactory.getLogger(OrderFunction.class);
    APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {
        String id = UUID.randomUUID().toString();

        HashMap<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        String jsonString = gson.toJson(id);

        response.setIsBase64Encoded(false);
        response.setStatusCode(200);
        response.setHeaders(headers);
        response.setBody(jsonString);

        logger.info("[ibcd]Order Received! id=" + id);

        EventBridgeClient eventBrClient = EventBridgeClient.builder()
                .region(Region.EU_CENTRAL_1)
                .build();

        putEBEvents(eventBrClient,id);

        return response;
    }

    public  void putEBEvents(EventBridgeClient eventBrClient,String event ) {

        try {
            List<String> resources = new ArrayList<>();
            resources.add(event);

            PutEventsRequestEntry reqEntry = PutEventsRequestEntry.builder()
                    .resources(resources)
                    .source("\"aws.lambda")
                    .detailType("AWS API Call via CloudTrail")
                    .detail("{ \"eventSource\": \"lambda.amazonaws.com\" }")
                    .eventBusName("default")
                    .build();



            // Add the PutEventsRequestEntry to a list
            List<PutEventsRequestEntry> list = new ArrayList<>();
            list.add(reqEntry);

            PutEventsRequest eventsRequest = PutEventsRequest.builder()
                    .entries(reqEntry)
                    .build();

            PutEventsResponse result = eventBrClient.putEvents(eventsRequest);

            System.out.println("[ibcd] put event result " + result);

            for (PutEventsResultEntry resultEntry : result.entries()) {

                result.entries().forEach(System.out::println);

                if (resultEntry.eventId() != null) {
                    System.out.println("Event Id: " + resultEntry.eventId());
                } else {
                    System.out.println("Injection failed with Error Code: " + resultEntry.errorCode());
                }
            }

        } catch (EventBridgeException e) {

            System.err.println(e.awsErrorDetails().errorMessage());
            System.exit(1);
        }
    }
}
