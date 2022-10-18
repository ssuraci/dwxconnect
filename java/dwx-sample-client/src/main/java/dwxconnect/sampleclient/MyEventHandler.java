package dwxconnect.sampleclient;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dwxconnect.api.Client;
import dwxconnect.api.EventHandler;

/*Custom event handler implementing the EventHandler interface. 
*/
class MyEventHandler implements EventHandler {

    private Logger logger = LoggerFactory.getLogger(Client.class);

	
	boolean first = true;
    
    public void start(Client dwx) {
        
		// account information is stored in dwx.accountInfo.
		logger.info("Account info:" + dwx.accountInfo);
		
        // subscribe to tick data:
		String[] symbols = {"EURUSD", "GBPUSD"};
        dwx.subscribeSymbols(symbols);
        
		// subscribe to bar data:
        String[][] symbolsBarData = {{"EURUSD", "M1"}, {"AUDCAD", "M5"}, {"GBPCAD", "M15"}};
        dwx.subscribeSymbolsBarData(symbolsBarData);
		
		// request historic data:
		long end = System.currentTimeMillis()/1000;
		long start = end - 10*24*60*60;  // last 10 days
		dwx.getHistoricData("AUDCAD", "D1", start, end);
		
		// dwx.closeOrdersByMagic(77);
		// sleep(2000);
    }
	
    
    // use synchronized so that price updates and execution updates are not processed one after the other. 
    public synchronized void onTick(Client dwx, String symbol, double bid, double ask) {
        
		logger.info("onTick: " + symbol + " | bid: " + bid + " | ask: " + ask);
        // logger.info(symbol + " ticks: " + app.history.get(symbol).history.size());
		
		// to open an order:
		// if (first) {
			// first = false;
			// for (int i=0; i<5; i++) {
				// dwx.openOrder(symbol, "buystop", 0.05, ask+0.01, 0, 0, 77, "", 0);
			// }
		// }
    }
	
    
    public synchronized void onBarData(Client dwx, String symbol, String timeFrame, String time, double open, double high, double low, double close, int tickVolume) {
        
		logger.info("onBarData: " + symbol + ", " + timeFrame + ", " + time + ", " + open + ", " + high + ", " + low + ", " + close + ", " + tickVolume);
    }
	
    
    public synchronized void onMessage(Client dwx, JSONObject message) {
		String correlationId = message.has("correlationId") ? message.get("correlationId").toString() : ""; 
        if (message.get("type").equals("ERROR")) 
			logger.info(correlationId + " | " + message.get("type") + " | " + message.get("error_type") + " | " + message.get("description"));
		else if (message.get("type").equals("INFO")) 
			logger.info(correlationId + " | " + message.get("type") + " | " + message.get("message"));
    }
	
	public synchronized void onHistoricTrades(Client dwx) {
        
		logger.info("onHistoricTrades: " + dwx.historicTrades);
    }
	
    // triggers when an order is added or removed, not when only modified. 
    public synchronized void onOrderEvent(Client dwx) {
		
        logger.info("onOrderEvent:");
        
        // dwx.openOrders is a JSONObject, which can be accessed like this:
        for (String ticket : dwx.openOrders.keySet()) 
            logger.info(ticket + ": " + dwx.openOrders.get(ticket));
    }
	
	
	public synchronized void onHistoricData(Client dwx, String symbol, String timeFrame, JSONObject data) {
        
		// you can also access historic data via: dwx.historicData
		logger.info("onHistoricData: " + symbol + ", " + timeFrame + ", " + data);
    }
}
