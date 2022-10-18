package dwxconnect.test;

import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dwxconnect.api.Client;
import dwxconnect.api.EventHandler;
import dwxconnect.test.DWXTestMetaTraderSim.DwxMessage;

public class DWXTestEventHandler implements EventHandler {
	private Logger logger = LoggerFactory.getLogger(Client.class);
	
	protected BlockingQueue<TickData> tickQueue = new LinkedBlockingDeque<>();	
	protected BlockingQueue<DwxMessage> messageQueue = new LinkedBlockingDeque<>();	
	
	public class TickData {
		protected String symbol;
		protected double bid;
		protected double ask;
		public String getSymbol() {
			return symbol;
		}
		public double getBid() {
			return bid;
		}
		public double getAsk() {
			return ask;
		}
		public TickData(String symbol, double bid, double ask) {
			super();
			this.symbol = symbol;
			this.bid = bid;
			this.ask = ask;
		}
		
		
	}
	
	
	
	public BlockingQueue<TickData> getTickQueue() {
		return tickQueue;
	}

	public void setTickQueue(BlockingQueue<TickData> tickQueue) {
		this.tickQueue = tickQueue;
	}

	public BlockingQueue<DwxMessage> getMessageQueue() {
		return messageQueue;
	}

	public void setMessageQueue(BlockingQueue<DwxMessage> messageQueue) {
		this.messageQueue = messageQueue;
	}

	@Override
	public void start(Client dwx) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onTick(Client dwx, String symbol, double bid, double ask) {
		logger.info(String.format("symbol: %s bid: %f ask: %f", symbol, bid ,ask));
		tickQueue.add(new TickData(symbol, bid, ask));
	}

	@Override
	public void onBarData(Client dwx, String symbol, String timeFrame, String time, double open, double high,
			double low, double close, int tickVolume) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onHistoricData(Client dwx, String symbol, String timeFrame, JSONObject data) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onHistoricTrades(Client dwx) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onMessage(Client dwx, JSONObject message) {
		messageQueue.add(new DwxMessage(message.getString("type"), message.getString("time"), message.getString("message"), message.getInt("correlationId")));		
	}

	@Override
	public void onOrderEvent(Client dwx) {
		// TODO Auto-generated method stub
		
	}

}
