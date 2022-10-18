package dwxconnect.test;

import static dwxconnect.api.Helpers.sleep;

import java.util.concurrent.TimeUnit;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dwxconnect.api.Client;
import dwxconnect.test.DWXTestEventHandler.TickData;
import dwxconnect.test.DWXTestMetaTraderSim.DwxMessage;

public class DWXSimUnitTest {
	
	private static DWXTestMetaTraderSim dwxMetaTraderSim; 
	private static Client dwxClient;
	private static DWXTestEventHandler dwxTestEventHandler= new DWXTestEventHandler();
	
	private static Logger logger = LoggerFactory.getLogger(Client.class);

	
	/*Initializes DWX_Client and closes all open orders. 
	*/
	@BeforeClass
    public static void setUp() throws Exception {
		String workDir = System.getProperty("java.io.tmpdir");
		dwxMetaTraderSim = new DWXTestMetaTraderSim(workDir  + "/" + Client.dwxDir, "test_1.json");
		dwxClient = new Client(dwxTestEventHandler, workDir, 5, 10, false, false);
		dwxClient.start();
		dwxMetaTraderSim.start();
		sleep(1000);
    }
    
	@AfterClass
    public static void tearDown() throws Exception {
		dwxClient.stop();
		dwxMetaTraderSim.stop();
    }
	
	@Test
	public void testSubcribeSymbols() {
		
		String[] symbols = {"EURUSD", "GBPUSD"};
        Integer correlationId = dwxClient.subscribeSymbols(symbols);
        Exception ex = null;
        try {
			TickData tickData = dwxTestEventHandler.getTickQueue().poll(2, TimeUnit.SECONDS);
			Assert.assertNotNull(tickData);
			Assert.assertEquals(1.098740, tickData.getBid(), 0.0000001);
			Assert.assertEquals(1.098760, tickData.getAsk(), 0.0000001);
			tickData = dwxTestEventHandler.getTickQueue().poll(2, TimeUnit.SECONDS);
			Assert.assertNotNull(tickData);
			Assert.assertEquals(1.098760, tickData.getBid(), 0.0000001);
			Assert.assertEquals(1.098780, tickData.getAsk(), 0.0000001);
			tickData = dwxTestEventHandler.getTickQueue().poll(2, TimeUnit.SECONDS);
			Assert.assertNull(tickData);
			DwxMessage message = dwxTestEventHandler.getMessageQueue().poll(2, TimeUnit.SECONDS);
			Assert.assertNotNull(message);
			Assert.assertEquals("INFO", message.getType());
			Assert.assertEquals(correlationId, message.getCorrelationId());
		} catch (InterruptedException e) {
			ex = e;
		}
        Assert.assertNull(ex);
	}
	
}
