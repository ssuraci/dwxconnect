package dwxconnect.sampleclient;

import dwxconnect.api.Client;

/*

Example DWX_Connect client in java


This example client will subscribe to tick data and bar data. 
It will also request historic data. 

compile and run:

javac -cp ".;libs/*" "@sources.txt" 
java -cp ".;libs/*" DWXExampleClient


JSON jar from here:
https://mvnrepository.com/artifact/org.json/json
or:
https://github.com/stleary/JSON-java

*/

public class DWXExampleClient {
    
	static String metaTraderDirPath = null; 
	
	final static int sleepDelay = 5;  // 5 milliseconds
	final static int maxRetryCommandSeconds = 10;
	final static boolean loadOrdersFromFile = true;
	final static boolean verbose = true;
	
    public static void main(String args[]) throws Exception {
       
    	if (args.length != 1) {
    		System.out.println("Usage: java -jar sample-client-0.1.0.jar path-to-mt5-dir");
    		System.exit(0);
    	}
    	
    	metaTraderDirPath = args[0];
    	
        MyEventHandler eventHandler = new MyEventHandler();
        
        Client dwx = new Client(eventHandler, metaTraderDirPath, sleepDelay, 
                                maxRetryCommandSeconds, loadOrdersFromFile, verbose);
        
        dwx.start();
        Thread.sleep(120*1000);
        dwx.stop();
        
    }
}

