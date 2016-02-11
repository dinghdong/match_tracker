package com.match_tracker.tweets;

import java.io.IOException;
import java.util.Properties;
import java.util.StringJoiner;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import java.util.logging.StreamHandler;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;

public class TweetConsumerService {

	public static final String KAFKA_CONSUMER_PROPS = "/kafka_consumer.properties";
	public static final String KAFKA_SERVICE = "messagehub";
	public static final String TWEETS_TOPIC = "tweet_results_topic";
	public static final String CLOUDANT_SERVICE = "cloudantNoSQLDB";

	private static final Logger log = Logger.getLogger(TweetConsumerService.class.getName());
	
	public static void main(String[] args) {
		Logger globalLogger = Logger.getLogger("");
		Handler[] handlers = globalLogger.getHandlers();
		for(Handler handler : handlers) {
		    globalLogger.removeHandler(handler);
		}
		
		globalLogger.addHandler(new DualConsoleHandler());
		
		log.log(Level.INFO, "TwitterSearchService initialisation...");
		Properties consumerProps = new Properties();
		Properties producerProps = new Properties();

		try {
		    // load a properties file
		    consumerProps.load(TweetConsumerService.class.getResourceAsStream(KAFKA_CONSUMER_PROPS));
		} catch (IOException ex) {
			log.severe("Unable to read Kakfa message broker properties.");
			System.exit(-1);
		}
		
		String VCAP_SERVICES = System.getenv("VCAP_SERVICES");
		if (VCAP_SERVICES == null) {
			System.out.println("Unable to read VCAP_SERVICES.");
			throw new RuntimeException("Unable to read VCAP_SERVICES");
		}
		
		JsonObject vcapServices = Json.parse(VCAP_SERVICES).asObject();
		
		JsonObject cloudantService = vcapServices.get(CLOUDANT_SERVICE).asArray().get(0).asObject();
		JsonObject cloudantCreds = cloudantService.get("credentials").asObject();
		
		Properties cloudantProps = new Properties();
		cloudantProps.setProperty("host", cloudantCreds.get("host").asString());
		cloudantProps.setProperty("port", String.valueOf(cloudantCreds.get("port").asInt()));
		cloudantProps.setProperty("username", cloudantCreds.get("username").asString());
		cloudantProps.setProperty("password", cloudantCreds.get("password").asString());
		
		JsonObject kakfaService = vcapServices.get(KAFKA_SERVICE).asArray().get(0).asObject();
		JsonArray kafkaBrokers = kakfaService.get("credentials").asObject().get("kafka_brokers_sasl").asArray();
		
		StringJoiner brokerListJoiner = new StringJoiner(",");				 
		kafkaBrokers.forEach(action -> brokerListJoiner.add(action.asString()));
		consumerProps.setProperty("bootstrap.servers", brokerListJoiner.toString());
		producerProps.setProperty("bootstrap.servers", brokerListJoiner.toString());
		log.log(Level.INFO, "IBM MessageHub Brokers: {0}", consumerProps.get("bootstrap.servers"));
		log.log(Level.INFO, "Tweet Results Topic: {0}", consumerProps.getProperty(TWEETS_TOPIC));
		
		TweetConsumer service = new TweetConsumer(consumerProps, consumerProps.getProperty(TWEETS_TOPIC), new MatchTweetsDB(cloudantProps));
		
		log.log(Level.INFO, "TweetConsumerService initialisation finished.");
		log.log(Level.INFO, "Starting TweetConsumer...");
		service.start();
	}
	
	public static class DualConsoleHandler extends StreamHandler {

	    private final ConsoleHandler stderrHandler = new ConsoleHandler();

	    public DualConsoleHandler() {
	        super(System.out, new SimpleFormatter());
	    }

	    @Override
	    public void publish(LogRecord record) {
	        if (record.getLevel().intValue() <= Level.INFO.intValue()) {
	            super.publish(record);
	            super.flush();
	        } else {
	            stderrHandler.publish(record);
	            stderrHandler.flush();
	        }
	    }
	}
}
