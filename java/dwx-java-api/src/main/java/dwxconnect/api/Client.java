package dwxconnect.api;

import static dwxconnect.api.Helpers.sleep;
import static dwxconnect.api.Helpers.tryDeleteFile;
import static dwxconnect.api.Helpers.tryReadFile;
import static dwxconnect.api.Helpers.tryWriteToFile;

import java.io.File;
import java.math.BigDecimal;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*Client class

This class includes all of the functions for communication with MT4/MT5. 


JSON jar from here:
https://mvnrepository.com/artifact/org.json/json
or:
https://github.com/stleary/JSON-java

*/

public class Client {

	private EventHandler eventHandler;
	private String metaTraderDirPath;
	private int sleepDelay;
	private int maxRetryCommandSeconds;
	private boolean loadOrdersFromFile;
	private boolean verbose;

	public static final String dwxDir = "DWX";
	public static final String ordersFileName = "DWX_Orders.txt";
	public static final String messagesFileName = "DWX_Messages.txt";
	public static final String marketDataFileName = "DWX_Market_Data.txt";
	public static final String barDataFileName = "DWX_Bar_Data.txt";
	public static final String historicDataFileName = "DWX_Historic_Data.txt";
	public static final String historicTradesFileName = "DWX_Historic_Trades.txt";
	public static final String ordersStoredFileName = "DWX_Orders_Stored.txt";
	public static final String messagesStoredFileName = "DWX_Messages_Stored.txt";
	public static final String commandsPrefixFileName = "DWX_Commands_";

	private String pathOrders;
	private String pathMessages;
	private String pathMarketData;
	private String pathBarData;
	private String pathHistoricData;
	private String pathHistoricTrades;
	private String pathOrdersStored;
	private String pathMessagesStored;
	private String pathCommandsPrefix;

	private int maxCommandFiles = 20;
	private long lastMessagesMillis = 0;
	private String lastOpenOrdersStr = "";
	private String lastMessagesStr = "";
	private String lastMarketDataStr = "";
	private String lastBarDataStr = "";
	private String lastHistoricDataStr = "";
	private String lastHistoricTradesStr = "";

	public JSONObject openOrders = new JSONObject();
	public JSONObject accountInfo = new JSONObject();
	public JSONObject marketData = new JSONObject();
	public JSONObject barData = new JSONObject();
	public JSONObject historicData = new JSONObject();
	public JSONObject historicTrades = new JSONObject();

	private JSONObject lastBarData = new JSONObject();
	private JSONObject lastMarketData = new JSONObject();

	public boolean ACTIVE = true;
	private boolean START = false;

	private Thread openOrdersThread;
	private Thread messageThread;
	private Thread marketDataThread;
	private Thread barDataThread;
	private Thread historicDataThread;
	private List<Thread> threadList = new ArrayList<>();

	private Logger logger = LoggerFactory.getLogger(Client.class);
	
	private int correlationId=0;

	// private WatchService watchService =
	// FileSystems.getDefault().newWatchService();

	@FunctionalInterface
	public interface DWXFileElab {
		boolean elab(JSONObject previousData, JSONObject data);
	}

	public Client(EventHandler eventHandler, String metaTraderDirPath, int sleepDelay, int maxRetryCommandSeconds,
			boolean loadOrdersFromFile, boolean verbose) throws Exception {

		this.eventHandler = eventHandler;
		this.metaTraderDirPath = metaTraderDirPath;
		this.sleepDelay = sleepDelay;
		this.maxRetryCommandSeconds = maxRetryCommandSeconds;
		this.loadOrdersFromFile = loadOrdersFromFile;
		this.verbose = verbose;

		File f = new File(metaTraderDirPath);
		if (!f.exists()) {
			logger.info("ERROR: MetaTraderDirPath does not exist!");
			System.exit(1);
		}

		pathOrders = Paths.get(metaTraderDirPath, dwxDir, ordersFileName).toString();
		pathMessages = Paths.get(metaTraderDirPath, dwxDir, messagesFileName).toString();
		pathMarketData = Paths.get(metaTraderDirPath, dwxDir, marketDataFileName).toString();
		pathBarData = Paths.get(metaTraderDirPath, dwxDir, barDataFileName).toString();
		pathHistoricData = Paths.get(metaTraderDirPath, dwxDir, historicDataFileName).toString();
		pathHistoricTrades = Paths.get(metaTraderDirPath, dwxDir, historicTradesFileName).toString();
		pathOrdersStored = Paths.get(metaTraderDirPath, dwxDir, ordersStoredFileName).toString();
		pathMessagesStored = Paths.get(metaTraderDirPath, dwxDir, messagesStoredFileName).toString();
		pathCommandsPrefix = Paths.get(metaTraderDirPath, dwxDir, commandsPrefixFileName).toString();

	}

	public void start() {
		logger.info("DWX Client started with path: " + metaTraderDirPath);
		loadMessages();

		if (loadOrdersFromFile)
			loadOrders();

		threadList.add(openOrdersThread = new Thread(() -> checkFile(pathOrders,
				(JSONObject previousData, JSONObject data) -> this.checkOpenOrders(previousData, data))));
		openOrdersThread.start();

		threadList.add(messageThread =  new Thread(() -> checkFile(pathMessages,
				(JSONObject previousData, JSONObject data) -> this.checkMessages(previousData, data))));
		messageThread.start();

		threadList.add(marketDataThread = new Thread(() -> checkFile(pathMarketData,
				(JSONObject previousData, JSONObject data) -> this.checkMarketData(previousData, data))));
		marketDataThread.start();

		threadList.add(barDataThread =  new Thread(() -> checkFile(pathBarData,
				(JSONObject previousData, JSONObject data) -> this.checkBarData(previousData, data))));
		barDataThread.start();

		threadList.add(historicDataThread = new Thread(() -> checkFile(pathHistoricData,
				(JSONObject previousData, JSONObject data) -> this.checkHistoricData(previousData, data))));
		historicDataThread.start();

		START = true;
		if (eventHandler != null) {
			eventHandler.start(this);
		}
	}

	public void stop() {
		logger.info("DWX Client stopping ... ");
		ACTIVE = false;
		START = false;
		for (Thread thread : threadList) {
			try {
				thread.join();
			} catch (InterruptedException e) {
				logger.warn(e.getMessage());
			}
		}
		threadList.clear();
		logger.info("DWX Client stopped");
	}

	private void checkFile(String fileName, DWXFileElab fileElab) {
		JSONObject data = null, previousData = null;
		String previousDataStr = null;
		while (ACTIVE) {

			sleep(sleepDelay);

			if (!START)
				continue;

			if (fileName.contains("Bar")) {
				data = null;
			}
			
			String text = tryReadFile(fileName);

			if (text.length() == 0 || text.equals(previousDataStr))
				continue;

			previousDataStr = text;

			try {
				data = new JSONObject(text);
			} catch (Exception e) {
				logger.warn(String.format("File: %s error: %s", fileName, e.getMessage()));
				continue;
			}

			fileElab.elab(previousData, data);
			previousData = data;
		}
	}

	private boolean checkMarketData(JSONObject previousData, JSONObject data) {

		if (eventHandler != null) {
			for (String symbol : data.keySet()) {

				if (previousData == null || !previousData.has(symbol)
						|| !data.get(symbol).equals(previousData.get(symbol))) {
					JSONObject jo = (JSONObject) data.get(symbol);
					eventHandler.onTick(this, symbol, asDouble(jo.get("bid")), asDouble(jo.get("ask")));
				}
			}
		}
		return true;
	}

	/*
	 * Regularly checks the file for open orders and triggers the
	 * eventHandler.onOrderEvent() function.
	 */
	private boolean checkOpenOrders(JSONObject previousData, JSONObject data) {

		JSONObject dataOrders = (JSONObject) data.get("orders");
		boolean newEvent = false;
		for (String ticket : openOrders.keySet()) {
			if (!dataOrders.has(ticket)) {
				newEvent = true;
				if (verbose)
					logger.info("Order removed: " + openOrders.get(ticket));
			}
		}

		for (String ticket : dataOrders.keySet()) {
			if (!openOrders.has(ticket)) {
				newEvent = true;
				if (verbose)
					logger.info("New order: " + dataOrders.get(ticket));
			} else if (!openOrders.get(ticket).equals(dataOrders.get(ticket))) {
				newEvent = true;
				if (verbose)
					logger.info("Order modified: " + dataOrders.get(ticket));
			}
		}

		openOrders = dataOrders;
		accountInfo = (JSONObject) data.get("account_info");

		if (loadOrdersFromFile)
			tryWriteToFile(pathOrdersStored, data.toString());

		if (eventHandler != null && newEvent)
			eventHandler.onOrderEvent(this);
		return true;
	}

	/*
	 * Regularly checks the file for messages and triggers the
	 * eventHandler.onMessage() function.
	 */
	private boolean checkMessages(JSONObject previousData, JSONObject data) {

			// the objects are not ordered. because of (millis > lastMessagesMillis) it
			// would miss messages if we just looped through them directly.
			ArrayList<String> millisList = new ArrayList<>();
			for (String millisStr : data.keySet()) {
				if (data.get(millisStr) != null) {
					millisList.add(millisStr);
				}
			}
			Collections.sort(millisList);
			for (String millisStr : millisList) {
				if (data.get(millisStr) != null) {
					String correlationId = millisStr.contains("_") ? millisStr.substring(millisStr.indexOf("_") + 1) : null;
					long millis = Long.parseLong(millisStr.contains("_") ? millisStr.substring(0, millisStr.indexOf("_")) : millisStr);
					if (correlationId != null) {
						((JSONObject) data.get(millisStr)).put("correlationId", correlationId);
					}
					if (millis > lastMessagesMillis) {
						lastMessagesMillis = millis;
						if (eventHandler != null)
							eventHandler.onMessage(this, (JSONObject) data.get(millisStr));
					}
				}
			}
			tryWriteToFile(pathMessagesStored, data.toString());
		return true;	
	}

	
	private Double asDouble(Object object) {
		return object instanceof Number ? ((Number) object).doubleValue()
				: (object instanceof BigDecimal ? ((BigDecimal) object).doubleValue() : null);
	}

	/*
	 * Regularly checks the file for bar data and triggers the
	 * eventHandler.onBarData() function.
	 */
	private boolean checkBarData(JSONObject previousData, JSONObject data) {

			barData = data;

			if (eventHandler != null) {
				for (String st : barData.keySet()) {

					if (!lastBarData.has(st) || !barData.get(st).equals(lastBarData.get(st))) {
						String[] stSplit = st.split("_");
						if (stSplit.length != 2)
							continue;
						JSONObject jo = (JSONObject) barData.get(st);
						eventHandler.onBarData(this, stSplit[0], stSplit[1], (String) jo.get("time"),
								asDouble(jo.get("open")), asDouble(jo.get("high")), asDouble(jo.get("low")),
								asDouble(jo.get("close")), (int) jo.get("tick_volume"));
					}
				}
			}
			lastBarData = data;
			return true;
		}

	/*
	 * Regularly checks the file for historic data and triggers the
	 * eventHandler.onHistoricData() function.
	 */
	private boolean checkHistoricData(JSONObject previousData, JSONObject data) {

				if (data != null) {

					for (String st : data.keySet()) {
						historicData.put(st, data.get(st));
					}

					tryDeleteFile(pathHistoricData);

					if (eventHandler != null) {
						for (String st : data.keySet()) {
							String[] stSplit = st.split("_");
							if (stSplit.length != 2)
								continue;
							eventHandler.onHistoricData(this, stSplit[0], stSplit[1], (JSONObject) data.get(st));
						}
					}
				}
			
				/*

			// also check historic trades in the same thread.
			String text = tryReadFile(pathHistoricTrades);

			if (text.length() > 0 && !text.equals(lastHistoricTradesStr)) {

				lastHistoricTradesStr = text;

				JSONObject data;

				try {
					data = new JSONObject(text);
				} catch (Exception e) {
					data = null;
				}

				if (data != null) {
					historicTrades = data;

					if (eventHandler != null)
						eventHandler.onHistoricTrades(this);
				}
			}
			return true;
			*/
				return true;
		}

	/*
	 * Loads stored orders from file (in case of a restart).
	 */
	private void loadOrders() {

		String text = tryReadFile(pathOrdersStored);

		if (text.length() == 0)
			return;

		JSONObject data;

		try {
			data = new JSONObject(text);
		} catch (Exception e) {
			return;
		}

		lastOpenOrdersStr = text;
		openOrders = (JSONObject) data.get("orders");
		accountInfo = (JSONObject) data.get("account_info");
	}

	/*
	 * Loads stored messages from file (in case of a restart).
	 */
	private void loadMessages() {

		String text = tryReadFile(pathMessagesStored);

		if (text.length() == 0)
			return;

		JSONObject data;

		try {
			data = new JSONObject(text);
		} catch (Exception e) {
			return;
		}

		lastMessagesStr = text;

		// here we don't have to sort because we just need the latest millis value.
		for (String millisStr : data.keySet()) {
			if (data.has(millisStr)) {
				long millis = Long.parseLong(millisStr.contains("_") ? millisStr.substring(0, millisStr.indexOf("_")) : millisStr);
				if (millis > lastMessagesMillis)
					lastMessagesMillis = millis;
			}
		}
	}

	/*
	 * Sends a SUBSCRIBE_SYMBOLS command to subscribe to market (tick) data.
	 * 
	 * Args: symbols (String[]): List of symbols to subscribe to.
	 * 
	 * Returns: null
	 * 
	 * The data will be stored in marketData. On receiving the data the
	 * eventHandler.onTick() function will be triggered.
	 */
	public int subscribeSymbols(String[] symbols) {
		return sendCommand("SUBSCRIBE_SYMBOLS", String.join(",", symbols));
	}

	/*
	 * Sends a SUBSCRIBE_SYMBOLS_BAR_DATA command to subscribe to bar data.
	 * 
	 * Args: symbols (String[][]): List of lists containing symbol/time frame
	 * combinations to subscribe to. For example: String[][] symbols = {{"EURUSD",
	 * "M1"}, {"USDJPY", "H1"}};
	 * 
	 * Returns: null
	 * 
	 * The data will be stored in barData. On receiving the data the
	 * eventHandler.onBarData() function will be triggered.
	 */
	public int subscribeSymbolsBarData(String[][] symbols) {
		String content = "";
		for (int i = 0; i < symbols.length; i++) {
			if (i != 0)
				content += ",";
			content += symbols[i][0] + "," + symbols[i][1];
		}
		return sendCommand("SUBSCRIBE_SYMBOLS_BAR_DATA", content);
	}

	/*
	 * Sends a GET_HISTORIC_DATA command to request historic data.
	 * 
	 * Args: symbol (String): Symbol to get historic data. timeFrame (String): Time
	 * frame for the requested data. start (long): Start timestamp (seconds since
	 * epoch) of the requested data. end (long): End timestamp of the requested
	 * data.
	 * 
	 * Returns: null
	 * 
	 * The data will be stored in historicData. On receiving the data the
	 * eventHandler.onHistoricData() function will be triggered.
	 */
	public int getHistoricData(String symbol, String timeFrame, long start, long end) {
		String content = symbol + "," + timeFrame + "," + start + "," + end;
		return sendCommand("GET_HISTORIC_DATA", content);
	}

	/*
	 * Sends a GET_HISTORIC_TRADES command to request historic trades.
	 * 
	 * Kwargs: lookbackDays (int): Days to look back into the trade history. The
	 * history must also be visible in MT4.
	 * 
	 * Returns: None
	 * 
	 * The data will be stored in historicTrades. On receiving the data the
	 * eventHandler.onHistoricTrades() function will be triggered.
	 */
	public int getHistoricTrades(int lookbackDays) {
		return sendCommand("GET_HISTORIC_TRADES", String.valueOf(lookbackDays));
	}

	/*
	 * Sends an OPEN_ORDER command to open an order.
	 * 
	 * Args: symbol (String): Symbol for which an order should be opened. order_type
	 * (String): Order type. Can be one of: 'buy', 'sell', 'buylimit', 'selllimit',
	 * 'buystop', 'sellstop' lots (double): Volume in lots price (double): Price of
	 * the (pending) order. Can be zero for market orders. stop_loss (double): SL as
	 * absoute price. Can be zero if the order should not have an SL. take_profit
	 * (double): TP as absoute price. Can be zero if the order should not have a TP.
	 * magic (int): Magic number comment (String): Order comment expriation (long):
	 * Expiration time given as timestamp in seconds. Can be zero if the order
	 * should not have an expiration time.
	 */
	public int openOrder(String symbol, String orderType, double lots, double price, double stopLoss,
			double takeProfit, int magic, String comment, long expiration) {

		String content = symbol + "," + orderType + "," + lots + "," + price + "," + stopLoss + "," + takeProfit + ","
				+ magic + "," + comment + "," + expiration;
		return sendCommand("OPEN_ORDER", content);
	}

	/*
	 * Sends a MODIFY_ORDER command to modify an order.
	 * 
	 * Args: ticket (int): Ticket of the order that should be modified. lots
	 * (double): Volume in lots price (double): Price of the (pending) order.
	 * Non-zero only works for pending orders. stop_loss (double): New stop loss
	 * price. take_profit (double): New take profit price. expriation (long): New
	 * expiration time given as timestamp in seconds. Can be zero if the order
	 * should not have an expiration time.
	 */
	public int modifyOrder(int ticket, double lots, double price, double stopLoss, double takeProfit,
			long expiration) {

		String content = ticket + "," + lots + "," + price + "," + stopLoss + "," + takeProfit + "," + expiration;
		return sendCommand("MODIFY_ORDER", content);
	}

	/*
	 * Sends a CLOSE_ORDER command with lots=0 to close an order completely.
	 */
	public int closeOrder(int ticket) {

		String content = ticket + ",0";
		return sendCommand("CLOSE_ORDER", content);
	}

	/*
	 * Sends a CLOSE_ORDER command to close an order.
	 * 
	 * Args: ticket (int): Ticket of the order that should be closed. lots (double):
	 * Volume in lots. If lots=0 it will try to close the complete position.
	 */
	public int closeOrder(int ticket, double lots) {

		String content = ticket + "," + lots;
		return sendCommand("CLOSE_ORDER", content);
	}

	/*
	 * Sends a CLOSE_ALL_ORDERS command to close all orders.
	 */
	public void closeAllOrders() {

		sendCommand("CLOSE_ALL_ORDERS", "");
	}

	/*
	 * Sends a CLOSE_ORDERS_BY_SYMBOL command to close all orders with a given
	 * symbol.
	 * 
	 * Args: symbol (str): Symbol for which all orders should be closed.
	 */
	public int closeOrdersBySymbol(String symbol) {

		return sendCommand("CLOSE_ORDERS_BY_SYMBOL", symbol);
	}

	/*
	 * Sends a CLOSE_ORDERS_BY_MAGIC command to close all orders with a given magic
	 * number.
	 * 
	 * Args: magic (str): Magic number for which all orders should be closed.
	 */
	public int closeOrdersByMagic(int magic) {

		return sendCommand("CLOSE_ORDERS_BY_MAGIC", Integer.toString(magic));
	}

	/*
	 * Sends a command to the mql server by writing it to one of the command files.
	 * 
	 * Multiple command files are used to allow for fast execution of multiple
	 * commands in the correct chronological order.
	 */
	synchronized Integer sendCommand(String command, String content) {

		String text = "<:" + command + "|" + content + ":>" + (++correlationId)+"#";
		
		long now = System.currentTimeMillis();
		long endMillis = now + maxRetryCommandSeconds * 1000;

		// trying again for X seconds in case all files exist or are currently read from
		// mql side.
		while (now <= endMillis) {

			// using 10 different files to increase the execution speed for muliple
			// commands.
			for (int i = 0; i < maxCommandFiles; i++) {

				String filePath = pathCommandsPrefix + i + ".txt";
				File f = new File(filePath);
				if (!f.exists() && tryWriteToFile(filePath, text)) {
					return correlationId;
				}
			}
			now = System.currentTimeMillis();
		}
		return -1;
	}
}
